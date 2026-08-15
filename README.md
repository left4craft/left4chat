# Left4Chat

Cross-server chat, private messages, AFK tracking and Discord account linking
for the Left4Craft network. Runs on every backend Paper server and talks to the
rest of the network over Redis, so a message typed on survival appears on hub
and in Discord.

## Features

- **Network chat** — publishes local chat to the `minecraft.chat` Redis channel
  and renders whatever comes back from other servers and the Discord bot.
  Colour (`left4chat.color`) and formatting (`left4chat.format`) codes are
  permission-gated.
- **Private messages** — `/msg` and `/reply` reach anyone on the network,
  addressed by username or nickname.
- **`/list`** — everyone online across all servers, staff first.
- **`/game`** — the server selector menu.
- **`/afk`** — away status, shared network-wide.
- **`/discord <code>`** — links a Minecraft account to a Discord account,
  stored in PostgreSQL.
- **Spam defence** — `/verify` opens an item-picking captcha; `/chatlock`
  restricts chat to verified players while riding out spambot waves.
- **Console relay** — runs commands published to
  `minecraft.console.<server-name>.in`, so the proxy and Discord bot can
  execute commands on this server. Every relayed command is logged. `restart`
  maps to `stop`, and players are moved to `fallback-server` first.
- **Admin commands** — `/announce`, `/ggivecosmetic`, `/chatreload`.

The console relay replaces the old standalone **Left4Craft** plugin — remove
that plugin when installing Left4Chat, or every relayed command runs twice.

## Requirements

| | |
|---|---|
| Server | Paper 26.2 |
| Java | 25 |
| Required | LuckPerms, Redis, PostgreSQL |
| Optional | Nicky (nicknames) |
| Replaces | Left4Craft (remove it) |

## Configuration

`config.yml` is generated on first start. It covers the Redis and PostgreSQL
connections, the Redis channel/key names, announcement formats, the Discord
invite, the `/game` menu contents and the permission node names.

The Redis keys under `redis.keys` are shared with the Velocity proxy plugin
and the Discord bot. **All three have to agree**, so do not change one in
isolation.

## Database

Left4Chat owns one table, `discord_users`, and creates it if missing:

```sql
CREATE TABLE discord_users (
  uuid      UUID PRIMARY KEY,
  nick      VARCHAR(64),
  discordID BIGINT UNIQUE
)
```

## Building

Needs **JDK 25** — Paper 26.2's API class files are major version 69, so an
older compiler cannot read them.

```sh
gradle build
# -> build/libs/left4chat-2.0.0.jar
```

`libs/Nicky-2.0.0.jar` is the sibling [left4craft/Nicky](https://github.com/left4craft/Nicky)
build, used at compile time only. Refresh it after changing Nicky:

```sh
gradle -p ../Nicky build && cp ../Nicky/build/libs/Nicky-*.jar libs/
```
