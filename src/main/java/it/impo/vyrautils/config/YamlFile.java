package it.impo.vyrautils.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

/**
 * A single YAML file inside the plugin data folder, e.g. {@code quests/*.yml}.
 *
 * <p>Parent folders are created before loading, so nested paths such as
 * {@code quests/daily/first.yml} work out of the box.</p>
 */
public final class YamlFile {

    private final Plugin plugin;
    private final File file;

    private FileConfiguration config;

    private YamlFile(@NotNull Plugin plugin, @NotNull File file, @NotNull FileConfiguration config) {
        this.plugin = plugin;
        this.file = file;
        this.config = config;
    }

    /**
     * Loads the file, creating an empty one in memory when it does not exist yet.
     */
    public static @NotNull YamlFile load(@NotNull Plugin plugin, @NotNull String path) {
        File file = new File(plugin.getDataFolder(), path);
        createParentFolders(file);
        return new YamlFile(plugin, file, YamlConfiguration.loadConfiguration(file));
    }

    /**
     * Copies the bundled resource (when present) and then loads the file.
     */
    public static @NotNull YamlFile loadOrCopy(@NotNull Plugin plugin, @NotNull String path) {
        copyDefault(plugin, path);
        return load(plugin, path);
    }

    /**
     * Copies the bundled resource to the plugin data folder when the target file
     * is missing. Does nothing (and logs a warning) when the plugin jar does not
     * contain the resource, instead of throwing.
     *
     * @return {@code true} when a file was written
     */
    public static boolean copyDefault(@NotNull Plugin plugin, @NotNull String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (file.exists()) {
            return false;
        }

        if (!hasBundledResource(plugin, path)) {
            plugin.getLogger().warning("[Yaml] Resource '" + path
                    + "' is not bundled in the plugin jar, skipping the default copy");
            return false;
        }

        createParentFolders(file);
        plugin.saveResource(path, false);
        return true;
    }

    /**
     * Re-reads the file from disk, discarding unsaved changes.
     */
    public void reload() {
        createParentFolders(file);
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * @throws IllegalStateException when the file cannot be written
     */
    public void save() {
        createParentFolders(file);
        try {
            config.save(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save " + file.getName(), exception);
        }
    }

    /**
     * Saves without throwing: logs the failure instead.
     * Useful in reload paths, where a broken file must not kill the command.
     *
     * @return {@code true} when the file was written
     */
    public boolean saveQuietly() {
        createParentFolders(file);
        try {
            config.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[Yaml] Cannot save " + file.getName(), exception);
            return false;
        }
    }

    /**
     * @param create whether to create the section when it is missing
     * @throws IllegalArgumentException when the section is missing and {@code create} is {@code false}
     */
    public @NotNull ConfigurationSection section(@NotNull String path, boolean create) {
        ConfigurationSection section = config.getConfigurationSection(path);

        if (section == null && create) {
            section = config.createSection(path);
        }

        if (section == null) {
            throw new IllegalArgumentException("Missing configuration section '" + path
                    + "' in " + file.getName());
        }

        return section;
    }

    public @NotNull FileConfiguration config() {
        return config;
    }

    public @NotNull File file() {
        return file;
    }

    public @NotNull String name() {
        return file.getName();
    }

    public boolean exists() {
        return file.isFile();
    }

    /**
     * Deletes the file from disk.
     *
     * @return {@code true} when the file no longer exists
     */
    public boolean delete() {
        if (!file.exists()) {
            return true;
        }
        return file.delete();
    }

    /**
     * Parses the file into a fresh {@link YamlConfiguration} without touching the
     * current in-memory copy. Useful for validating user provided files.
     *
     * @return the parsed configuration, or {@code null} when the file is invalid
     */
    public static @Nullable YamlConfiguration readOnly(@NotNull File file) {
        YamlConfiguration parsed = new YamlConfiguration();
        try {
            parsed.load(file);
            return parsed;
        } catch (IOException | InvalidConfigurationException exception) {
            return null;
        }
    }

    private static boolean hasBundledResource(@NotNull Plugin plugin, @NotNull String path) {
        try (InputStream resource = plugin.getResource(path)) {
            return resource != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void createParentFolders(@NotNull File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create folder " + parent.getAbsolutePath());
        }
    }
}
