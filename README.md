# VyraUtils

[![Build](https://github.com/Impopite/VyraUtils/actions/workflows/build.yml/badge.svg)](https://github.com/Impopite/VyraUtils/actions/workflows/build.yml)

Shared library for Impoo's Paper 1.21.8 plugins.

## Features

- MiniMessage lang loader with cache and palette
- Typed `ConfigLoader`
- `YamlFile` for extra YAML files
- `ItemStack` / `Component` serialization
- `CommandService` built on CommandAPI
- Modrinth update checker
- HikariCP database pool

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
