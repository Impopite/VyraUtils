# VyraUtils

Libreria condivisa per i plugin Paper 1.21.8 di Impoo.

## Funzionalità

- Lang MiniMessage con cache e palette
- ConfigLoader tipizzato
- YamlFile
- Serializzazione ItemStack / Location / Component
- CommandRegistrar basato su CommandAPI
- Modrinth update checker
- HikariCP database pool
- Utility: Tasks, Cooldowns, TimeParser

## Installazione

```groovy
repositories {
    maven { url = 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Impopite:VyraUtils:1.0.0'
}
```

La libreria è già shaded: CommandAPI, HikariCP e il driver MySQL sono inclusi.
I plugin consumatori non devono dichiararli né metterli in `plugin.yml`.

## Requisiti

- Java 21
- Paper 1.21.8

## Licenza

MIT
