package me.sisko.left4chat.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * The Minecraft-account to Discord-account links.
 *
 * <p>Two things changed here, and both matter.
 *
 * <p><b>PostgreSQL.</b> The old code spoke MariaDB and stored the player UUID as
 * {@code BINARY(16)}, addressed with MySQL's {@code UNHEX('...')}. The column is
 * now a native Postgres {@code uuid}, which the driver binds directly. That also
 * makes the Discord bot's join against {@code litebans_*} simpler, because
 * Postgres accepts LiteBans' dashless 32-character UUIDs as {@code uuid}
 * literals without any conversion.
 *
 * <p><b>Prepared statements.</b> Every query was string concatenation, including
 * the nickname:
 * {@snippet : "UPDATE discord_users SET nick = \"" + nick + "\" WHERE ..." }
 * A nickname containing a double quote broke the statement, and one containing
 * a semicolon did rather more than that. Nicky allows {@code &} and {@code #} in
 * nicknames, so the input was never as constrained as it looked.
 */
public final class DiscordLinkRepository implements AutoCloseable {

    private final Logger logger;
    private final HikariDataSource dataSource;
    private final String table;

    /**
     * Connection details.
     *
     * @param host     hostname
     * @param port     port
     * @param database database name
     * @param user     role name
     * @param password role password
     * @param sslMode  libpq ssl mode, normally {@code verify-full}
     * @param poolSize maximum pooled connections
     * @param table    table holding the links
     */
    public record Settings(String host, int port, String database, String user, String password,
                           String sslMode, int poolSize, String table) {
    }

    /**
     * Opens the pool. Nothing connects until the first query.
     *
     * @param logger   the plugin logger
     * @param settings the connection details
     */
    public DiscordLinkRepository(Logger logger, Settings settings) {
        this.logger = logger;
        this.table = settings.table();

        HikariConfig config = new HikariConfig();
        config.setPoolName("left4chat-postgres");
        config.setJdbcUrl("jdbc:postgresql://" + settings.host() + ":" + settings.port()
                + "/" + settings.database());
        config.setDriverClassName(org.postgresql.Driver.class.getName());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(Math.max(1, settings.poolSize()));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000L);
        config.setMaxLifetime(1_800_000L);
        config.addDataSourceProperty("sslmode", settings.sslMode());
        // pgjdbc cannot read the OS trust store the way libpq's
        // "sslrootcert=system" does, so point it at the JVM's instead. Shadow
        // rewrites this to the relocated class name at build time, which is why
        // it comes from the class rather than a string literal.
        config.addDataSourceProperty("sslfactory",
                org.postgresql.ssl.DefaultJavaSSLFactory.class.getName());
        // PlanetScale's pooled endpoint (6432) is PgBouncer in transaction
        // mode, which hands a different backend to each transaction. Server-side
        // named statements do not survive that, so keep everything unprepared.
        config.addDataSourceProperty("prepareThreshold", "0");
        config.addDataSourceProperty("ApplicationName", "Left4Chat");

        this.dataSource = new HikariDataSource(config);
    }

    /**
     * Creates the table if this is a fresh database.
     *
     * <p>The old plugin assumed the table was already there, because it always
     * had been. Nothing has created it in PostgreSQL yet.
     *
     * @throws SQLException if the database is unreachable or refuses the DDL
     */
    public void createTable() throws SQLException {
        String ddl = """
                CREATE TABLE IF NOT EXISTS %s (
                  uuid      UUID PRIMARY KEY,
                  nick      VARCHAR(64),
                  discordID BIGINT UNIQUE
                )""".formatted(table);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(ddl)) {
            statement.execute();
        }
    }

    /**
     * Looks up the Discord account linked to a player. Blocks; call from an
     * async thread.
     *
     * @param uuid the player
     * @return the Discord snowflake, or empty if they have not linked
     */
    public OptionalLong findDiscordId(UUID uuid) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT discordID FROM " + table + " WHERE uuid = ?")) {
            statement.setObject(1, uuid);
            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return OptionalLong.empty();
                }
                long id = set.getLong("discordID");
                return set.wasNull() ? OptionalLong.empty() : OptionalLong.of(id);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not look up the Discord link for " + uuid, e);
            return OptionalLong.empty();
        }
    }

    /**
     * Records a player's nickname without touching their Discord link. Blocks;
     * call from an async thread.
     *
     * @param uuid the player
     * @param nick the plain nickname
     */
    public void saveNickname(UUID uuid, String nick) {
        execute("""
                INSERT INTO %s (uuid, nick) VALUES (?, ?)
                ON CONFLICT (uuid) DO UPDATE SET nick = EXCLUDED.nick
                """.formatted(table),
                statement -> {
                    statement.setObject(1, uuid);
                    statement.setString(2, nick);
                });
    }

    /**
     * Links a player to a Discord account, taking the account off whoever held
     * it before. Blocks; call from an async thread.
     *
     * @param uuid      the player
     * @param nick      the plain nickname
     * @param discordId the Discord snowflake
     * @return the snowflake this player was linked to before, if any
     */
    public OptionalLong link(UUID uuid, String nick, long discordId) {
        OptionalLong previous = findDiscordId(uuid);

        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                // A Discord account can only be attached to one Minecraft
                // account. The old code deleted the other row in a separate
                // statement after the update, so a failure between the two left
                // the snowflake on two rows.
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM " + table + " WHERE discordID = ? AND uuid <> ?")) {
                    statement.setLong(1, discordId);
                    statement.setObject(2, uuid);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO %s (uuid, nick, discordID) VALUES (?, ?, ?)
                        ON CONFLICT (uuid) DO UPDATE
                        SET nick = EXCLUDED.nick, discordID = EXCLUDED.discordID
                        """.formatted(table))) {
                    statement.setObject(1, uuid);
                    statement.setString(2, nick);
                    statement.setLong(3, discordId);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not link " + uuid + " to Discord account " + discordId, e);
            return OptionalLong.empty();
        }

        return previous;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private void execute(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Statement failed: " + sql, e);
        }
    }
}
