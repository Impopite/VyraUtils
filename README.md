# VyraUtils

[![Build](https://github.com/Impopite/VyraUtils/actions/workflows/build.yml/badge.svg)](https://github.com/Impopite/VyraUtils/actions/workflows/build.yml)

Shared library for Impoo's Paper 1.21.8 plugins.

## Features

- MiniMessage `LangLoader` with cache, prefix, palette and missing-key report
- Typed `ConfigLoader` (enums or raw paths) and `YamlFile`
- `ItemStack` / `Component` serialization plus `Items` edit helpers
- `CommandService` / `CommandInvocation` built on CommandAPI, with reload-safe language
- `Schedulers` for Paper and Folia (sync, async, region, entity)
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
| `scheduling` | `Schedulers` |
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

The library is already shaded, so CommandAPI, HikariCP and the MySQL driver are
bundled inside it. Consumer plugins must not declare them, must not list them in
`plugin.yml` `libraries`, and must not install the CommandAPI plugin.

CommandAPI is initialized by the consumer plugin, not by the library:

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

## Requirements

- Java 21
- Paper 1.21.8

## License

MIT
