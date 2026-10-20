package it.impo.vyrautils.config;

import org.jetbrains.annotations.NotNull;

/**
 * A key of a configuration file.
 *
 * <p>Implemented by each plugin's own {@code ConfigKey} enum, so existing enums
 * only need to add {@code implements ConfigPath}:</p>
 *
 * <pre>{@code
 * public enum ConfigKey implements ConfigPath {
 *     LANG_FILE("generic.lang");
 *
 *     private final String path;
 *
 *     ConfigKey(String path) {
 *         this.path = path;
 *     }
 *
 *     @Override
 *     public String getPath() {
 *         return path;
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface ConfigPath {

    /**
     * @return the dotted YAML path, e.g. {@code database.host}
     */
    @NotNull
    String getPath();
}
