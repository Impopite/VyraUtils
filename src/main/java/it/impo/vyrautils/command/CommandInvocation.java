package it.impo.vyrautils.command;

import dev.jorel.commandapi.executors.CommandArguments;
import it.impo.vyrautils.lang.LangLoader;
import it.impo.vyrautils.lang.MessageKey;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single command execution: the sender, its arguments and the services it needs.
 *
 * <p>Provides the checks every command repeats ({@link #require(String)},
 * {@link #player()}) and typed argument access without unchecked casts.</p>
 */
public final class CommandInvocation {

    private final CommandService service;
    private final CommandSender sender;
    private final CommandArguments args;

    CommandInvocation(@NotNull CommandService service, @NotNull CommandSender sender, @NotNull CommandArguments args) {
        this.service = service;
        this.sender = sender;
        this.args = args;
    }

    public @NotNull CommandSender sender() {
        return sender;
    }

    public @NotNull CommandArguments args() {
        return args;
    }

    public @NotNull LangLoader lang() {
        return service.lang();
    }

    /**
     * Checks the permission and sends the {@code no-permission} message when it is missing.
     *
     * @param permission the node to check, blank to skip the check
     * @return {@code true} when the command may continue
     */
    public boolean require(@NotNull String permission) {
        return service.hasPermission(sender, permission);
    }

    /**
     * Checks that the sender has at least one of the given permissions.
     *
     * @param permissions the nodes to check, empty to skip the check
     * @return {@code true} when the command may continue
     */
    public boolean requireAny(@NotNull String... permissions) {
        return service.hasAnyPermission(sender, permissions);
    }

    public boolean isPlayer() {
        return sender instanceof Player;
    }

    /**
     * @return the sender as a player, or {@code null} after sending the
     * {@code console-cant-do-command} message
     */
    public @Nullable Player player() {
        if (sender instanceof Player player) {
            return player;
        }

        service.lang().send(sender, service.messages().consoleCantDo());
        return null;
    }

    /**
     * Reads an argument with a checked cast.
     *
     * @return the value, or {@code null} when missing or of a different type
     */
    public <T> @Nullable T arg(@NotNull String name, @NotNull Class<T> type) {
        Object value = args.get(name);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    public @Nullable String string(@NotNull String name) {
        return arg(name, String.class);
    }

    /**
     * @return the value, or {@code 0} when missing or of a different type
     */
    public int integer(@NotNull String name) {
        Object value = args.get(name);
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * @return the value, or {@code 0} when missing or of a different type
     */
    public long asLong(@NotNull String name) {
        Object value = args.get(name);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * @return the value, or {@code 0} when missing or of a different type
     */
    public double asDouble(@NotNull String name) {
        Object value = args.get(name);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    /**
     * @return the argument as an integer
     * @throws IllegalArgumentException when the argument is missing, not a number,
     *                                  has a fractional part or is outside the
     *                                  integer range
     */
    public int requireInt(@NotNull String name) {
        long value = requireWholeNumber(name, "integer");

        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalidArgument("integer", name);
        }

        return (int) value;
    }

    /**
     * @return the argument as a long
     * @throws IllegalArgumentException when the argument is missing, not a number
     *                                  or has a fractional part
     */
    public long requireLong(@NotNull String name) {
        return requireWholeNumber(name, "long");
    }

    /**
     * @return the argument as a double
     * @throws IllegalArgumentException when the argument is missing or not a number
     */
    public double requireDouble(@NotNull String name) {
        Object value = args.get(name);

        if (!(value instanceof Number number)) {
            throw invalidArgument("double", name);
        }

        return number.doubleValue();
    }

    /**
     * @return the argument as a string
     * @throws IllegalArgumentException when the argument is missing or not a string
     */
    public @NotNull String requireString(@NotNull String name) {
        Object value = args.get(name);

        if (!(value instanceof String string)) {
            throw invalidArgument("string", name);
        }

        return string;
    }

    /**
     * @return the argument as a boolean
     * @throws IllegalArgumentException when the argument is missing or not a boolean
     */
    public boolean requireBoolean(@NotNull String name) {
        Object value = args.get(name);

        if (!(value instanceof Boolean bool)) {
            throw invalidArgument("boolean", name);
        }

        return bool;
    }

    public boolean flag(@NotNull String name) {
        Object value = args.get(name);
        return value instanceof Boolean bool && bool;
    }

    /**
     * Sends a message with the prefix, without importing the language keys.
     */
    public void send(@NotNull MessageKey key, TagResolver... resolvers) {
        service.lang().send(sender, key, resolvers);
    }

    private long requireWholeNumber(@NotNull String name, @NotNull String type) {
        Object value = args.get(name);

        if (!(value instanceof Number number)) {
            throw invalidArgument(type, name);
        }

        if (number instanceof Double || number instanceof Float) {
            double decimal = number.doubleValue();

            if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)) {
                throw invalidArgument(type, name);
            }
        }

        return number.longValue();
    }

    private static @NotNull IllegalArgumentException invalidArgument(@NotNull String type, @NotNull String name) {
        return new IllegalArgumentException("Missing or invalid " + type + " argument '" + name + "'");
    }
}
