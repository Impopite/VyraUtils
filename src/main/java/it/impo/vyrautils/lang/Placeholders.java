package it.impo.vyrautils.lang;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Builds {@link TagResolver}s from {@code Map}s, for the very common case where
 * a message needs a handful of dynamic values.
 *
 * <pre>{@code
 * lang.send(player, LangKey.QUEST_PROGRESS,
 *         Placeholders.parsed(Map.of("quest", quest.name(), "progress", progress)));
 * }</pre>
 */
public final class Placeholders {

    private Placeholders() {
    }

    /**
     * Placeholders whose value is itself parsed as MiniMessage.
     */
    public static @NotNull TagResolver parsed(@NotNull Map<String, ?> values) {
        return build(values, Placeholder::parsed);
    }

    /**
     * Placeholders whose value is inserted literally, tags included.
     * Use this for player input: it cannot inject MiniMessage tags.
     */
    public static @NotNull TagResolver unparsed(@NotNull Map<String, ?> values) {
        return build(values, Placeholder::unparsed);
    }

    /**
     * Convenience overload for a single placeholder.
     */
    public static @NotNull TagResolver parsed(@NotNull String name, @Nullable Object value) {
        return value == null ? TagResolver.empty() : Placeholder.parsed(name, String.valueOf(value));
    }

    /**
     * Convenience overload for a single placeholder, inserted literally.
     */
    public static @NotNull TagResolver unparsed(@NotNull String name, @Nullable Object value) {
        return value == null ? TagResolver.empty() : Placeholder.unparsed(name, String.valueOf(value));
    }

    private static @NotNull TagResolver build(@NotNull Map<String, ?> values,
                                              @NotNull BiFunction<String, String, TagResolver> factory) {
        if (values.isEmpty()) {
            return TagResolver.empty();
        }

        TagResolver.Builder builder = TagResolver.builder();
        values.forEach((name, value) -> {
            if (name != null && value != null) {
                builder.resolver(factory.apply(name, String.valueOf(value)));
            }
        });
        return builder.build();
    }
}
