# VyraUtils

[![Build](https://github.com/Impopite/VyraUtils/actions/workflows/build.yml/badge.svg)](https://github.com/Impopite/VyraUtils/actions/workflows/build.yml)

Shared library for Impoo's Paper 1.21.8 plugins.

## Features

- MiniMessage `LangLoader` with cache, prefix, palette and missing-key report
- Typed `ConfigLoader` (enums or raw paths) and `YamlFile`
- `ItemStack` / `Component` serialization plus `Items` edit helpers
- `CommandService` / `CommandInvocation` built on CommandAPI, with reload-safe language
- `Schedulers` for Paper and Folia (sync, async, region, entity)
- `Durations` to parse `1d2h30m` and format durations
- `EventService` for reload-safe listeners
- `SemanticVersion` and `ModrinthUpdateChecker`
- HikariCP `DatabasePool`

## Packages

| Package | Classes |
| --- | --- |
| `lang` | `LangLoader`, `MessageKey`, `Placeholders` |
| `config` | `ConfigLoader`, `ConfigPath`, `YamlFile` |
| `serialize` | `ItemSerializer`, `Components` |
| `item` | `Items` |
| `command` | `CommandService`, `CommandInvocation`, `CommandMessages` |
| `event` | `EventService` |
| `scheduling` | `Schedulers`, `Durations` |
| `update` | `ModrinthUpdateChecker`, `SemanticVersion`, `UpdateChannel`, `UpdateInfo` |
| `database` | `DatabasePool`, `DatabaseSettings` |

## Installation

```groovy
repositories {
    maven { url = 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Impopite:VyraUtils:1.0.0'
}
```

## Bundled dependencies

The published jar is a shaded jar: everything the library needs at runtime is
already inside it. Consumer plugins **must not** declare these dependencies, must
not list them in `plugin.yml` under `libraries`, and must not install the
CommandAPI plugin.

| Dependency | Inside the jar | Relocated | Consumer plugin |
| --- | --- | --- | --- |
| CommandAPI | yes | no | can use `dev.jorel.commandapi.*` directly, has to call `CommandAPI.onLoad/onEnable` |
| HikariCP | yes | `it.impo.vyrautils.libs.hikari` | must use `DatabasePool`, not `com.zaxxer.hikari.*` |
| MySQL connector | yes | `it.impo.vyrautils.libs.mysql` | must use `DatabasePool`, not `com.mysql.*` |
| Gson | no | — | nothing to do, the Paper server already ships it |
| paper-api, JetBrains annotations | no | — | provided by the server / by the IDE |

### CommandAPI

CommandAPI is part of the public API of the library (`CommandService`,
`CommandInvocation`), so it is **not** relocated and the consumer plugin uses the
same class names as the library. It is initialized by the consumer plugin, not by
the library:

```java
@Override
public void onLoad() {
    CommandAPI.onLoad(new CommandAPIPaperConfig(this));
}

@Override
public void onEnable() {
    CommandAPI.onEnable();
}
```

### MySQL

The MySQL driver is relocated, so it never registers itself in the global
`DriverManager`: a copy of the driver shaded into another plugin cannot steal the
connection. `DatabasePool` resolves the driver by name when the pool is created,
so nothing has to be registered by hand:

```java
DatabaseSettings settings = DatabaseSettings.defaults("localhost", "vyra", "user", "password");
DatabasePool pool = new DatabasePool(this, settings);

pool.query(connection -> {
    try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM players")) {
        try (ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        }
    }
}).thenAccept(count -> Schedulers.sync(this, () ->
        getLogger().info("Players: " + count)));
```

The pool must be closed in `onDisable`, otherwise Hikari keeps its threads (and
the plugin classloader with them) alive.

## Requirements

- Java 21
- Paper 1.21.8
- Gson at runtime: it is already provided by the Paper server
  (dependency of `adventure-text-serializer-gson`), so it is not bundled

## Usage

### Configuration

`ConfigLoader` reads a file from the data folder, warns once per missing key and
falls back to the given default:

```java
ConfigLoader config = new ConfigLoader(this).load();

int limit = config.getInt(ConfigKey.LIMIT, 10);
Duration cooldown = config.getDuration("cooldown", Duration.ofMinutes(5));
String optional = config.getOptionalString("webhook.url"); // null when missing, no warning
```

`ConfigPath` is an interface with a single `getPath()` method, so an existing
enum only needs `implements ConfigPath`:

```java
public enum ConfigKey implements ConfigPath {
    LIMIT("limits.quests"),
    COOLDOWN("limits.cooldown");

    private final String path;

    ConfigKey(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return path;
    }
}
```

`YamlFile` handles a single YAML file, creating the parent folders on demand. A
file that cannot be parsed is **never** overwritten: `isBroken()` returns `true`,
`save()` throws and `saveQuietly()` returns `false`.

```java
YamlFile file = YamlFile.loadOrCopy(this, "quests/daily/first.yml");

file.config().set("rewards.gold", 100);
file.save();
```

### Language

`MessageKey` is an interface with a single `getPath()` method, so the plugin
defines its own enum:

```java
public enum LangKey implements MessageKey {
    WELCOME("message.welcome");

    private final String path;

    LangKey(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return path;
    }
}
```

```java
LangLoader lang = LangLoader.fromFile(this, "lang/en.yml", List.of(LangKey.values()));

lang.send(sender, LangKey.WELCOME, Placeholders.unparsed("player", player.getName()));
```

### Scheduling

`Schedulers` wraps the regionised scheduler API, so the same code works on Paper
and on Folia:

```java
Schedulers.sync(this, () -> player.sendMessage("hello"));
Schedulers.async(this, () -> readFile());
Schedulers.regionTimer(this, location, () -> tick(), 20L, 5L);
Schedulers.entityTimer(this, entity, () -> tick(), 20L, 5L);
```

### Events

```java
private final EventService events = new EventService(this);

@Override
public void onEnable() {
    events.register(new PlayerJoinListener(this));
}

@Override
public void onDisable() {
    events.unregisterAll();
}
```

Listeners are tracked by identity, so two different instances of the same
listener class are two different registrations.

### Update check

`ModrinthUpdateChecker.check()` performs a blocking HTTP request (up to ten
seconds): it refuses to run on the main thread and throws
`IllegalStateException` there. Use `checkAsync()`, which runs the request on an
async thread and completes the future there.

```java
checker.checkAsync().thenAccept(update -> Schedulers.sync(this, () ->
        update.ifPresent(info -> getLogger().info("Update available: " + info.version()))));
```

## License

MIT
