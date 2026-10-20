package it.impo.vyrautils.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads a MiniMessage language file and caches every message as a {@link Component}.
 *
 * <p>All messages are parsed once, both plain and with the configured prefix, so
 * sending a message never re-parses MiniMessage. Dynamic placeholders are passed
 * as {@link TagResolver}s and are parsed on demand.</p>
 *
 * <p>The loader is immutable: on reload create a new instance and stop holding
 * a reference to the old one.</p>
 */
public final class LangLoader {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String DEFAULT_FALLBACK = "lang/EN_us.yml";
    private static final int MISSING_KEYS_LOG_LIMIT = 10;

    private final YamlConfiguration config;
    private final TagResolver palette;
    private final @Nullable MessageKey prefixKey;
    private final Component prefix;

    private final Map<MessageKey, Component> plain;
    private final Map<MessageKey, Component> prefixed;
    private final List<MessageKey> missingKeys;

    private LangLoader(@NotNull YamlConfiguration config,
                       @NotNull Collection<? extends MessageKey> keys,
                       @Nullable MessageKey prefixKey,
                       @Nullable Plugin plugin) {
        this.config = config;
        this.prefixKey = prefixKey;
        this.palette = buildPalette(config);
        this.prefix = prefixKey == null
                ? Component.empty()
                : parse(config.getString(prefixKey.getPath(), ""));

        Map<MessageKey, Component> plainMessages = new LinkedHashMap<>();
        List<MessageKey> empty = new ArrayList<>();

        for (MessageKey key : keys) {
            String raw = config.getString(key.getPath(), "");
            if (raw == null || raw.isEmpty()) {
                empty.add(key);
            }
            plainMessages.put(key, parse(raw));
        }

        this.plain = Collections.unmodifiableMap(plainMessages);
        this.missingKeys = List.copyOf(empty);

        Map<MessageKey, Component> prefixedMessages = new LinkedHashMap<>();
        for (Map.Entry<MessageKey, Component> entry : plainMessages.entrySet()) {
            boolean isPrefix = prefixKey != null && entry.getKey().equals(prefixKey);
            prefixedMessages.put(entry.getKey(),
                    isPrefix ? entry.getValue() : prefix.append(entry.getValue()));
        }
        this.prefixed = Collections.unmodifiableMap(prefixedMessages);

        if (plugin != null) {
            reportMissingKeys(plugin, empty);
        }
    }

    /**
     * Loads {@code filePath} from the plugin data folder, falling back to the
     * bundled {@value #DEFAULT_FALLBACK} resource when the file does not exist.
     *
     * @param plugin    the owning plugin
     * @param filePath  path relative to the plugin data folder, e.g. {@code lang/EN_us.yml}
     * @param keys      every message key the plugin knows about
     * @param prefixKey key of the message used as prefix, or {@code null} for none
     */
    public static @NotNull LangLoader fromFile(@NotNull Plugin plugin,
                                               @NotNull String filePath,
                                               @NotNull Collection<? extends MessageKey> keys,
                                               @Nullable MessageKey prefixKey) {
        File file = new File(plugin.getDataFolder(), filePath);

        if (file.isFile()) {
            plugin.getLogger().info("[Lang] Loaded language file: " + filePath);
            return new LangLoader(YamlConfiguration.loadConfiguration(file), keys, prefixKey, plugin);
        }

        plugin.getLogger().severe("[Lang] Language file not found: " + filePath
                + ". Falling back to the bundled " + DEFAULT_FALLBACK + ".");

        InputStream stream = plugin.getResource(DEFAULT_FALLBACK);
        if (stream == null) {
            throw new IllegalStateException("Plugin '" + plugin.getName() + "' does not bundle "
                    + DEFAULT_FALLBACK + " and " + filePath + " is missing from the data folder");
        }

        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new LangLoader(YamlConfiguration.loadConfiguration(reader), keys, prefixKey, plugin);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled " + DEFAULT_FALLBACK, exception);
        }
    }

    /**
     * Loads messages without a prefix.
     */
    public static @NotNull LangLoader fromFile(@NotNull Plugin plugin,
                                               @NotNull String filePath,
                                               @NotNull Collection<? extends MessageKey> keys) {
        return fromFile(plugin, filePath, keys, null);
    }

    /**
     * Builds a loader from an already parsed configuration, without logging.
     * Useful for tests and for merging messages loaded from other sources.
     */
    public static @NotNull LangLoader fromConfiguration(@NotNull YamlConfiguration config,
                                                        @NotNull Collection<? extends MessageKey> keys,
                                                        @Nullable MessageKey prefixKey) {
        return new LangLoader(config, keys, prefixKey, null);
    }

    /**
     * Returns the message with the prefix, using the cache when no placeholder is given.
     */
    public @NotNull Component get(@NotNull MessageKey key, TagResolver... resolvers) {
        if (resolvers.length == 0) {
            return prefixed.getOrDefault(key, Component.empty());
        }

        if (prefixKey != null && key.equals(prefixKey)) {
            return component(key, resolvers);
        }

        return prefix.append(component(key, resolvers));
    }

    /**
     * Returns the message without the prefix.
     */
    public @NotNull Component component(@NotNull MessageKey key, TagResolver... resolvers) {
        if (resolvers.length == 0) {
            return plain.getOrDefault(key, Component.empty());
        }

        return parse(raw(key), resolvers);
    }

    /**
     * Returns the configured prefix, or an empty component when there is none.
     */
    public @NotNull Component prefix() {
        return prefix;
    }

    /**
     * Parses arbitrary text as MiniMessage, applying the config {@code vars} palette.
     */
    public @NotNull Component parse(@Nullable String raw, TagResolver... resolvers) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(raw, combine(resolvers));
    }

    /**
     * Returns the raw, unparsed value of a key.
     */
    public @NotNull String raw(@NotNull MessageKey key) {
        return config.getString(key.getPath(), "");
    }

    /**
     * @return {@code true} when the key is missing from the file, or its value is empty
     */
    public boolean isEmpty(@NotNull MessageKey key) {
        return raw(key).isEmpty();
    }

    /**
     * @return every key that was missing or empty at load time
     */
    public @NotNull List<MessageKey> missingKeys() {
        return missingKeys;
    }

    public void send(@NotNull CommandSender sender, @NotNull MessageKey key, TagResolver... resolvers) {
        sender.sendMessage(get(key, resolvers));
    }

    public void sendRaw(@NotNull CommandSender sender, @NotNull MessageKey key, TagResolver... resolvers) {
        sender.sendMessage(component(key, resolvers));
    }

    public void sendComponent(@NotNull CommandSender sender, @NotNull Component component) {
        sender.sendMessage(component);
    }

    /**
     * Sends a message as action bar. The prefix is not applied: an action bar
     * with a chat prefix is never what you want.
     */
    public void sendActionBar(@NotNull CommandSender sender, @NotNull MessageKey key, TagResolver... resolvers) {
        sender.sendActionBar(component(key, resolvers));
    }

    /**
     * Shows a title built from two messages. The prefix is not applied.
     *
     * @param subtitleKey subtitle message, or {@code null} for none
     * @param fadeIn     fade in time, use {@link Duration#ZERO} for defaults
     * @param stay       stay time
     * @param fadeOut    fade out time
     */
    public void sendTitle(@NotNull CommandSender sender,
                          @NotNull MessageKey titleKey,
                          @Nullable MessageKey subtitleKey,
                          @NotNull Duration fadeIn,
                          @NotNull Duration stay,
                          @NotNull Duration fadeOut,
                          TagResolver... resolvers) {
        Component title = component(titleKey, resolvers);
        Component subtitle = subtitleKey == null ? Component.empty() : component(subtitleKey, resolvers);

        sender.showTitle(Title.title(title, subtitle, Title.Times.times(fadeIn, stay, fadeOut)));
    }

    /**
     * Shows a title with the default timings (10 ticks in, 70 stay, 20 out).
     */
    public void sendTitle(@NotNull CommandSender sender,
                          @NotNull MessageKey titleKey,
                          @Nullable MessageKey subtitleKey,
                          TagResolver... resolvers) {
        sendTitle(sender, titleKey, subtitleKey,
                Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1), resolvers);
    }

    /**
     * Sends the prefixed message to every online player and to the console.
     */
    public void broadcast(@NotNull MessageKey key, TagResolver... resolvers) {
        Bukkit.getServer().broadcast(get(key, resolvers));
    }

    private @NotNull TagResolver combine(@NotNull TagResolver... resolvers) {
        if (resolvers.length == 0) {
            return palette;
        }

        TagResolver[] all = new TagResolver[resolvers.length + 1];
        all[0] = palette;
        System.arraycopy(resolvers, 0, all, 1, resolvers.length);
        return TagResolver.resolver(all);
    }

    private static void reportMissingKeys(@NotNull Plugin plugin, @NotNull List<MessageKey> missing) {
        if (missing.isEmpty()) {
            return;
        }

        String preview = missing.stream()
                .limit(MISSING_KEYS_LOG_LIMIT)
                .map(MessageKey::getPath)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");

        String suffix = missing.size() > MISSING_KEYS_LOG_LIMIT ? ", ..." : "";
        plugin.getLogger().warning("[Lang] " + missing.size()
                + " message key(s) are missing or empty: " + preview + suffix);
    }

    private static @NotNull TagResolver buildPalette(@NotNull YamlConfiguration config) {
        ConfigurationSection vars = config.getConfigurationSection("vars");
        if (vars == null) {
            return TagResolver.empty();
        }

        TagResolver.Builder builder = TagResolver.builder();
        for (String key : vars.getKeys(false)) {
            String value = vars.getString(key);
            if (value != null) {
                builder.resolver(Placeholder.parsed(key.toLowerCase(Locale.ROOT), value));
            }
        }
        return builder.build();
    }
}
