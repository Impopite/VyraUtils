package it.impo.vyrautils.lang;

import org.jetbrains.annotations.NotNull;

/**
 * A key of a localized message.
 *
 * <p>Implemented by each plugin's own {@code LangKey} enum, so the library never
 * needs to know the concrete message set:</p>
 *
 * <pre>{@code
 * public enum LangKey implements MessageKey {
 *     NO_PERMISSION("message.no-permission");
 *
 *     private final String path;
 *
 *     LangKey(String path) {
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
public interface MessageKey {

    /**
     * @return the YAML path of the message inside the language file
     */
    @NotNull
    String getPath();
}
