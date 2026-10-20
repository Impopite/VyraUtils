package it.impo.vyrautils.serialize;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Conversions between MiniMessage strings and {@link Component}s.
 *
 * <p>Parsing is forgiving: invalid MiniMessage never throws, it falls back to a
 * literal text component. Use {@code LangLoader} when you need the config palette.</p>
 */
public final class Components {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * @return the parsed component, or an empty component for {@code null}/blank input
     */
    public static @NotNull Component parse(@Nullable String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        try {
            return MINI_MESSAGE.deserialize(input);
        } catch (RuntimeException exception) {
            return Component.text(input);
        }
    }

    public static @NotNull List<Component> parseList(@Nullable Collection<String> input) {
        if (input == null || input.isEmpty()) return List.of();

        List<Component> components = new ArrayList<>(input.size());
        for (String line : input) components.add(parse(line));

        return List.copyOf(components);
    }

    public static @NotNull String miniMessage(@NotNull Component component) {
        return MINI_MESSAGE.serialize(component);
    }

    public static @NotNull String plain(@NotNull Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Removes every MiniMessage tag and returns the readable text.
     * Equivalent to {@code plain(parse(input))}, but never throws.
     */
    public static @NotNull String stripTags(@Nullable String input) {
        return input == null || input.isEmpty() ? "" : plain(parse(input));
    }

    /**
     * Joins components with the given separator, without relying on Adventure's
     * {@code Component#join}.
     */
    public static @NotNull Component join(@NotNull Collection<? extends Component> parts,
                                          @NotNull Component separator) {
        if (parts.isEmpty()) {
            return Component.empty();
        }

        Component result = Component.empty();
        boolean first = true;
        for (Component part : parts) {
            if (!first) result = result.append(separator);

            result = result.append(part);
            first = false;
        }
        return result;
    }
}
