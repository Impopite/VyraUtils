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

    public int integer(@NotNull String name) {
        Object value = args.get(name);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public long asLong(@NotNull String name) {
        Object value = args.get(name);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public double asDouble(@NotNull String name) {
        Object value = args.get(name);
        return value instanceof Number number ? number.doubleValue() : 0.0;
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
}
