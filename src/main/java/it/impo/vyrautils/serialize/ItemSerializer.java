package it.impo.vyrautils.serialize;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;
import java.util.logging.Level;

/**
 * Converts {@link ItemStack}s to bytes or Base64 for database storage.
 *
 * <p>For YAML files use the native Bukkit methods
 * ({@code config.set(path, item)} / {@code config.getItemStack(path)}).</p>
 */
public final class ItemSerializer {

    /**
     * @return the serialized item, or an empty array for {@code null} and air items
     */
    public static byte @NotNull [] toBytes(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) return new byte[0];

        return item.serializeAsBytes();
    }

    /**
     * @return the deserialized item, or {@code null} for {@code null} and empty arrays
     * @throws IllegalArgumentException when the bytes are not a valid serialized item
     */
    public static @Nullable ItemStack fromBytes(byte @Nullable [] bytes) {
        if (bytes == null || bytes.length == 0) return null;

        return ItemStack.deserializeBytes(bytes);
    }

    public static @NotNull String toBase64(@Nullable ItemStack item) {
        return Base64.getEncoder().encodeToString(toBytes(item));
    }

    /**
     * @return the deserialized item, or {@code null} for {@code null} and blank strings
     * @throws IllegalArgumentException when the string is not valid Base64 or not a valid item
     */
    public static @Nullable ItemStack fromBase64(@Nullable String base64) {
        if (base64 == null || base64.isBlank()) return null;

        return fromBytes(Base64.getDecoder().decode(base64));
    }

    public static byte @NotNull [] safeToBytes(@Nullable ItemStack item, @NotNull Plugin plugin) {
        try {
            return toBytes(item);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[Items] Cannot serialize item", exception);
            return new byte[0];
        }
    }

    public static @Nullable ItemStack safeFromBytes(byte @Nullable [] bytes, @NotNull Plugin plugin) {
        try {
            return fromBytes(bytes);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[Items] Cannot deserialize item", exception);
            return null;
        }
    }

    public static @NotNull String safeToBase64(@Nullable ItemStack item, @NotNull Plugin plugin) {
        return Base64.getEncoder().encodeToString(safeToBytes(item, plugin));
    }

    public static @Nullable ItemStack safeFromBase64(@Nullable String base64, @NotNull Plugin plugin) {
        try {
            return fromBase64(base64);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[Items] Cannot deserialize item from Base64", exception);
            return null;
        }
    }
}
