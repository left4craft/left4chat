package me.sisko.left4chat.redis;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.ConnectionPoolConfig;

/**
 * The plugin's one connection to Redis.
 *
 * <p>The old code opened {@code new Jedis(host, port)}, called {@code auth()},
 * issued a single command and closed the socket -- on every chat message, every
 * AFK check, every tab completion, every {@code /list}. A busy server did that
 * dozens of times a second. This holds a pooled client for the plugin's
 * lifetime.
 *
 * <p>Subscriptions get their own single-connection client. {@code subscribe}
 * blocks its connection for as long as it is listening, so sharing the command
 * pool with it would eventually starve everything else.
 */
public final class RedisBridge implements AutoCloseable {

    private final Logger logger;
    private final RedisClient client;
    private final RedisClient subscriberClient;
    private final Settings settings;

    private final AtomicBoolean closing = new AtomicBoolean();
    private final Map<String, Consumer<String>> handlers = new LinkedHashMap<>();
    private volatile Thread subscriberThread;
    private volatile JedisPubSub subscription;

    /**
     * Connection details.
     *
     * @param host           hostname
     * @param port           port
     * @param username       ACL username, empty for the default user
     * @param password       password, empty if the server takes none
     * @param database       database index
     * @param timeoutMillis  socket timeout
     * @param maxConnections command pool size
     */
    public record Settings(String host, int port, String username, String password,
                           int database, int timeoutMillis, int maxConnections) {
    }

    /**
     * Opens the pools. Nothing connects until the first command.
     *
     * @param logger   the plugin logger
     * @param settings the connection details
     */
    public RedisBridge(Logger logger, Settings settings) {
        this.logger = logger;
        this.settings = settings;
        this.client = build(settings, Math.max(2, settings.maxConnections()));
        // Exactly one connection: it is blocked by subscribe() the whole time.
        this.subscriberClient = build(settings, 1);
    }

    private static RedisClient build(Settings settings, int poolSize) {
        DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(settings.timeoutMillis())
                .socketTimeoutMillis(settings.timeoutMillis())
                .database(settings.database())
                .clientName("left4chat");

        if (!settings.password().isEmpty()) {
            config.password(settings.password());
        }
        if (!settings.username().isEmpty()) {
            config.user(settings.username());
        }

        ConnectionPoolConfig pool = new ConnectionPoolConfig();
        pool.setMaxTotal(poolSize);
        pool.setMaxIdle(poolSize);
        pool.setMinIdle(1);
        pool.setTestOnBorrow(true);

        return RedisClient.builder()
                .hostAndPort(settings.host(), settings.port())
                .clientConfig(config.build())
                .poolConfig(pool)
                .build();
    }

    /**
     * Publishes a message, swallowing and logging any failure.
     *
     * <p>Redis being unreachable should cost a chat message, not a stack trace
     * in the middle of someone's login.
     *
     * @param channel the channel
     * @param message the payload
     */
    public void publish(String channel, String message) {
        run("publish to " + channel, redis -> redis.publish(channel, message));
    }

    /**
     * Reads a key.
     *
     * @param key the key
     * @return the value, or null if unset or unreachable
     */
    public String get(String key) {
        try {
            return client.get(key);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Redis get " + key + " failed", e);
            return null;
        }
    }

    /**
     * Writes a key.
     *
     * @param key   the key
     * @param value the value
     */
    public void set(String key, String value) {
        run("set " + key, redis -> redis.set(key, value));
    }

    /**
     * Reads a key, writing and returning a default if it is unset.
     *
     * @param key      the key
     * @param fallback the value to store when the key is missing
     * @return the stored value
     */
    public String getOrSet(String key, String fallback) {
        String value = get(key);
        if (value == null) {
            set(key, fallback);
            return fallback;
        }
        return value;
    }

    /**
     * Registers a handler for a channel. Nothing is listened to until
     * {@link #startSubscriber()} is called.
     *
     * <p>Left4Chat listens on two channels: cross-server chat, and the console
     * relay it took over from Left4Craft. Both are served by one connection and
     * one thread -- Left4Craft ran a second Redis connection and a second bare
     * thread of its own to do the same job.
     *
     * @param channel  the channel to listen on
     * @param listener called for each message, on the subscriber thread
     */
    public void subscribe(String channel, Consumer<String> listener) {
        if (subscriberThread != null) {
            throw new IllegalStateException("subscriptions are already running");
        }
        handlers.put(channel, listener);
    }

    /**
     * Starts listening on every registered channel, reconnecting until
     * {@link #close} is called.
     *
     * <p>The old subscribers ran once on a bare thread and logged
     * "Subscriber closed!" when the connection dropped -- after which the server
     * saw no cross-server chat, and accepted no relayed console commands, until
     * someone restarted it.
     */
    public void startSubscriber() {
        if (handlers.isEmpty()) {
            return;
        }

        subscription = new JedisPubSub() {
            @Override
            public void onMessage(String receivedChannel, String message) {
                Consumer<String> handler = handlers.get(receivedChannel);
                if (handler == null) {
                    return;
                }
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    logger.log(Level.WARNING,
                            "Failed to handle a message on " + receivedChannel, e);
                }
            }
        };

        String[] channels = handlers.keySet().toArray(String[]::new);
        subscriberThread = Thread.ofPlatform()
                .name("left4chat-redis-subscriber")
                .daemon()
                .start(() -> listen(channels));
    }

    private void listen(String[] channels) {
        Duration backoff = Duration.ofSeconds(1);

        while (!closing.get()) {
            try {
                subscriberClient.subscribe(subscription, channels);
                // subscribe() returns when the subscription is unsubscribed.
                backoff = Duration.ofSeconds(1);
            } catch (Exception e) {
                if (closing.get()) {
                    return;
                }
                logger.log(Level.WARNING,
                        "Redis subscription to " + String.join(", ", channels)
                                + " dropped; retrying in " + backoff.toSeconds() + "s", e);
            }

            if (closing.get()) {
                return;
            }
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            backoff = backoff.multipliedBy(2);
            if (backoff.compareTo(Duration.ofSeconds(30)) > 0) {
                backoff = Duration.ofSeconds(30);
            }
        }
    }

    private void run(String what, Consumer<RedisClient> action) {
        try {
            action.accept(client);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Redis " + what + " failed", e);
        }
    }

    @Override
    public void close() {
        closing.set(true);

        JedisPubSub current = subscription;
        if (current != null && current.isSubscribed()) {
            try {
                current.unsubscribe();
            } catch (Exception e) {
                logger.log(Level.FINE, "Unsubscribe failed during shutdown", e);
            }
        }

        Thread thread = subscriberThread;
        if (thread != null) {
            thread.interrupt();
        }

        closeQuietly(subscriberClient);
        closeQuietly(client);
    }

    private void closeQuietly(RedisClient toClose) {
        try {
            toClose.close();
        } catch (Exception e) {
            logger.log(Level.FINE, "Closing the Redis client failed", e);
        }
    }

    /**
     * The connection settings this bridge was built with.
     *
     * @return the settings
     */
    public Settings settings() {
        return settings;
    }
}
