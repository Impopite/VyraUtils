package it.impo.vyrautils.config;

import it.impo.vyrautils.scheduling.Durations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed access to the plugin's main {@code config.yml}.
 *
 * <p>Every getter takes a {@link ConfigPath} (so the enum {@code ConfigKey} of the
 * plugin keeps working) or a raw dotted path, falls back to the given default and
 * logs a warning the first time a path is missing or has the wrong type. The warning
 * is logged once per path and reset on {@link #reload()}, to keep hot paths quiet.</p>
 *
 * <p>{@link #getComponent(String)} and {@link #getComponentList(String)} cache the
 * parsed {@link Component}s, so repeated reads of the same path do not re-parse
 * MiniMessage. The cache is invalidated by {@link #load()}, {@link #reload()},
 * {@link #set(String, Object)}, {@link #addDefault(String, Object, String...)} and
 * {@link #invalidate()}.</p>
 *
 * <p>{@link #load()}, {@link #reload()} and the setters must be called from the main
 * thread. After loading, the getters may be called from other threads: the cached
 * configuration reference is volatile and getters never mutate it.</p>
 *
 * <pre>{@code
 * ConfigLoader config = new ConfigLoader(this).load();
 * String language = config.getString(ConfigKey.LANG_FILE, "EN_us");
 * }</pre>
 */
public final class ConfigLoader {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Upper bound of the parsed component caches. A caller passing dynamic paths
     * (for example one per player) would otherwise grow them without a limit.
     */
    private static final int MAX_CACHED_COMPONENTS = 512;

    private final Plugin plugin;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final Map<String, Component> componentCache = new ConcurrentHashMap<>();
    private final Map<String, List<Component>> componentListCache = new ConcurrentHashMap<>();

    private volatile FileConfiguration config;

    public ConfigLoader(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Copies the bundled {@code config.yml} if missing, then caches the config.
     */
    public @NotNull ConfigLoader load() {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        invalidate();
        return this;
    }

    /**
     * Re-reads the config from disk. Always goes through the plugin, so the
     * cached {@code config.yml} is refreshed too.
     */
    public void reload() {
        plugin.reloadConfig();
        this.warned.clear();
        this.config = plugin.getConfig();
        invalidate();
    }

    /**
     * Clears the parsed component caches. Call it after mutating the configuration
     * directly through {@link #raw()}: the getters of this class invalidate the caches
     * themselves, a direct mutation cannot be detected.
     */
    public void invalidate() {
        componentCache.clear();
        componentListCache.clear();
    }

    /**
     * @return the underlying configuration, for features not wrapped here
     */
    public @NotNull FileConfiguration raw() {
        if (config == null) {
            throw new IllegalStateException("ConfigLoader.load() has not been called yet");
        }

        return config;
    }

    /**
     * Saves the config to disk, comments included.
     */
    public void save() {
        plugin.saveConfig();
    }

    public boolean isSet(@NotNull ConfigPath key) {
        return isSet(key.getPath());
    }

    public boolean isSet(@NotNull String path) {
        return raw().isSet(path);
    }

    public @NotNull String getString(@NotNull ConfigPath key, @NotNull String def) {
        return getString(key.getPath(), def);
    }

    public @NotNull String getString(@NotNull String path, @NotNull String def) {
        String value = raw().getString(path);
        return value == null ? warn(path, def) : value;
    }

    public int getInt(@NotNull ConfigPath key, int def) {
        return getInt(key.getPath(), def);
    }

    public int getInt(@NotNull String path, int def) {
        return number(path, def).intValue();
    }

    public long getLong(@NotNull ConfigPath key, long def) {
        return getLong(key.getPath(), def);
    }

    public long getLong(@NotNull String path, long def) {
        return number(path, def).longValue();
    }

    public double getDouble(@NotNull ConfigPath key, double def) {
        return getDouble(key.getPath(), def);
    }

    public double getDouble(@NotNull String path, double def) {
        return number(path, def).doubleValue();
    }

    public boolean getBoolean(@NotNull ConfigPath key, boolean def) {
        return getBoolean(key.getPath(), def);
    }

    public boolean getBoolean(@NotNull String path, boolean def) {
        Object value = raw().get(path);
        return value instanceof Boolean bool ? bool : warn(path, def);
    }

    /**
     * Reads an optional value, without logging anything when it is missing.
     * Use it for the keys that are allowed to be absent.
     *
     * @return the value, or {@code null} when the path is missing
     */
    public @Nullable String getOptionalString(@NotNull ConfigPath key) {
        return getOptionalString(key.getPath());
    }

    /**
     * @see #getOptionalString(ConfigPath)
     */
    public @Nullable String getOptionalString(@NotNull String path) {
        return raw().getString(path);
    }

    /**
     * @return the section, or {@code null} when the path is missing or is not a section
     */
    public @Nullable ConfigurationSection getSection(@NotNull ConfigPath key) {
        return getSection(key.getPath());
    }

    /**
     * @see #getSection(ConfigPath)
     */
    public @Nullable ConfigurationSection getSection(@NotNull String path) {
        return raw().getConfigurationSection(path);
    }

    /**
     * Reads a duration written as {@code 1d2h30m}, see {@link Durations#parse(String)}.
     *
     * @return the parsed duration, or {@code def} when the path is missing or invalid
     */
    public @NotNull Duration getDuration(@NotNull ConfigPath key, @NotNull Duration def) {
        return getDuration(key.getPath(), def);
    }

    /**
     * @see #getDuration(ConfigPath, Duration)
     */
    public @NotNull Duration getDuration(@NotNull String path, @NotNull Duration def) {
        String value = raw().getString(path);
        if (value == null) return warn(path, def);

        try {
            return Durations.parse(value);
        } catch (IllegalArgumentException exception) {
            return warn(path, def);
        }
    }

    /**
     * @return an immutable copy of the list, empty when the path is missing
     */
    public @NotNull List<String> getStringList(@NotNull ConfigPath key) {
        return getStringList(key.getPath());
    }

    /**
     * @return an immutable copy of the list, empty when the path is missing
     */
    public @NotNull List<String> getStringList(@NotNull String path) {
        if (!raw().isSet(path)) {
            warn(path, List.of());
            return List.of();
        }

        if (!raw().isList(path)) {
            warn(path, List.of());
            return List.of();
        }

        return List.copyOf(raw().getStringList(path));
    }

    /**
     * Parses a MiniMessage string from the config.
     *
     * <p>The result is cached until {@link #reload()}, {@link #set(String, Object)},
     * {@link #addDefault(String, Object, String...)} or {@link #invalidate()} is called.</p>
     *
     * @return an empty component when the path is missing or blank
     */
    public @NotNull Component getComponent(@NotNull ConfigPath key) {
        return getComponent(key.getPath());
    }

    /**
     * Parses a MiniMessage string from the config.
     *
     * <p>The result is cached until {@link #reload()}, {@link #set(String, Object)},
     * {@link #addDefault(String, Object, String...)} or {@link #invalidate()} is called.</p>
     *
     * @return an empty component when the path is missing or blank
     */
    public @NotNull Component getComponent(@NotNull String path) {
        Component cached = componentCache.get(path);
        if (cached != null) return cached;

        String raw = raw().getString(path, "");
        Component component = raw.isEmpty() ? Component.empty() : MINI_MESSAGE.deserialize(raw);

        cache(componentCache, path, component);
        return component;
    }

    /**
     * Parses every entry of a string list as MiniMessage.
     *
     * <p>The result is cached until {@link #reload()}, {@link #set(String, Object)},
     * {@link #addDefault(String, Object, String...)} or {@link #invalidate()} is called.</p>
     */
    public @NotNull List<Component> getComponentList(@NotNull ConfigPath key) {
        return getComponentList(key.getPath());
    }

    /**
     * Parses every entry of a string list as MiniMessage.
     *
     * <p>The result is cached until {@link #reload()}, {@link #set(String, Object)},
     * {@link #addDefault(String, Object, String...)} or {@link #invalidate()} is called.</p>
     */
    public @NotNull List<Component> getComponentList(@NotNull String path) {
        List<Component> cached = componentListCache.get(path);
        if (cached != null) return cached;

        List<String> raw = getStringList(path);
        List<Component> parsed;
        if (raw.isEmpty()) {
            parsed = List.of();
        } else {
            List<Component> lines = new ArrayList<>(raw.size());
            for (String line : raw) {
                lines.add(line.isEmpty() ? Component.empty() : MINI_MESSAGE.deserialize(line));
            }

            parsed = List.copyOf(lines);
        }

        cache(componentListCache, path, parsed);
        return parsed;
    }

    /**
     * Generic getter kept for compatibility with hand-written config wrappers.
     * For numbers prefer {@link #getInt(String, int)} and friends, which coerce
     * between {@code Integer}, {@code Long} and {@code Double}.
     *
     * <p>When both the stored value and the default are lists, an immutable copy
     * is returned instead of the live configuration list.</p>
     */
    @SuppressWarnings("unchecked")
    public <T> T get(@NotNull ConfigPath key, @NotNull T def) {
        return get(key.getPath(), def);
    }

    /**
     * @see #get(ConfigPath, Object)
     */
    @SuppressWarnings("unchecked")
    public <T> T get(@NotNull String path, @NotNull T def) {
        Object value = raw().get(path);
        if (value == null) return warn(path, def);

        if (value instanceof List<?> list && def instanceof List<?>) {
            return (T) Collections.unmodifiableList(new ArrayList<>(list));
        }

        if (def.getClass().isInstance(value)) return (T) value;

        return warn(path, def);
    }

    public void set(@NotNull ConfigPath key, @Nullable Object value) {
        set(key.getPath(), value);
    }

    public void set(@NotNull String path, @Nullable Object value) {
        raw().set(path, value);
        invalidate();
    }

    public void setComments(@NotNull ConfigPath key, @NotNull List<String> comments) {
        setComments(key.getPath(), comments);
    }

    public void setComments(@NotNull String path, @NotNull List<String> comments) {
        raw().setComments(path, comments);
    }

    public void setInlineComments(@NotNull ConfigPath key, @NotNull List<String> comments) {
        setInlineComments(key.getPath(), comments);
    }

    public void setInlineComments(@NotNull String path, @NotNull List<String> comments) {
        raw().setInlineComments(path, comments);
    }

    /**
     * Writes the value and its comments only when the path is missing.
     * Use it to ship new keys with an existing config without overwriting user values.
     *
     * @return {@code true} when the value was written (the caller still has to {@link #save()})
     */
    public boolean addDefault(@NotNull ConfigPath key, @Nullable Object value, @NotNull String... comments) {
        return addDefault(key.getPath(), value, comments);
    }

    /**
     * @see #addDefault(ConfigPath, Object, String...)
     */
    public boolean addDefault(@NotNull String path, @Nullable Object value, @NotNull String... comments) {
        if (raw().isSet(path)) return false;

        raw().set(path, value);
        if (comments.length > 0) raw().setComments(path, List.of(comments));
        invalidate();

        return true;
    }

    private @NotNull Number number(@NotNull String path, @NotNull Number def) {
        Object value = raw().get(path);
        return value instanceof Number number ? number : warn(path, def);
    }

    private <T> T warn(@NotNull String path, T def) {
        if (warned.add(path)) {
            plugin.getLogger().warning("[Config] Missing or invalid value for '" + path + "', using default: " + describe(def));
        }

        return def;
    }

    private static <T> void cache(@NotNull Map<String, T> cache, @NotNull String path, @NotNull T value) {
        if (cache.size() >= MAX_CACHED_COMPONENTS) cache.clear();

        cache.put(path, value);
    }

    private static @NotNull String describe(@Nullable Object value) {
        if (value == null) return "null";
        if (value instanceof List<?> list) return list.isEmpty() ? "[]" : list.toString();

        return String.valueOf(value);
    }
}
