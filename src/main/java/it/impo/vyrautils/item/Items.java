package it.impo.vyrautils.item;

import it.impo.vyrautils.serialize.Components;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Small helpers for editing {@link ItemStack}s without repeating the
 * clone / getItemMeta / setItemMeta dance.
 *
 * <p>Every method returns a <b>copy</b>: templates and items already stored
 * somewhere are never modified in place.</p>
 */
public final class Items {

    public static @NotNull ItemStack edit(@NotNull ItemStack template,
                                          @NotNull Consumer<? super ItemMeta> editor) {
        ItemStack copy = template.clone();
        if (copy.getType().isAir()) return copy;

        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return copy;

        editor.accept(meta);
        copy.setItemMeta(meta);
        return copy;
    }

    public static @NotNull ItemStack withName(@NotNull ItemStack template, @NotNull Component name) {
        return edit(template, meta -> meta.displayName(name));
    }

    public static @NotNull ItemStack withName(@NotNull ItemStack template, @NotNull String miniMessageName) {
        return withName(template, Components.parse(miniMessageName));
    }

    public static @NotNull ItemStack withLore(@NotNull ItemStack template, @NotNull List<Component> lore) {
        return edit(template, meta -> meta.lore(List.copyOf(lore)));
    }

    public static @NotNull ItemStack withLore(@NotNull ItemStack template, @NotNull Component... lore) {
        return withLore(template, List.of(lore));
    }

    /**
     * @throws IllegalArgumentException when {@code amount} is lower than 1 or higher
     *                                  than the item max stack size
     */
    public static @NotNull ItemStack withAmount(@NotNull ItemStack template, int amount) {
        int max = template.getMaxStackSize();
        if (amount < 1 || amount > max) {
            throw new IllegalArgumentException("Amount must be between 1 and " + max + ", got " + amount);
        }

        ItemStack copy = template.clone();
        copy.setAmount(amount);
        return copy;
    }

    /**
     * @return the custom display name, or the translated item name when none is set
     */
    public static @NotNull Component name(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component name = meta.displayName();
            if (name != null) return name;
        }

        return Component.translatable(item.getType().translationKey());
    }

    /**
     * @return the item lore, empty when the item has none
     */
    public static @NotNull List<Component> lore(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta == null ? null : meta.lore();
        return lore == null ? List.of() : List.copyOf(lore);
    }

    public static @Nullable Component displayName(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.displayName();
    }
}
