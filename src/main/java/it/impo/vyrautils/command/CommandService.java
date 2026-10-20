package it.impo.vyrautils.command;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandArguments;
import it.impo.vyrautils.lang.LangLoader;
import it.impo.vyrautils.lang.MessageKey;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Registers commands and the two checks every command repeats.
 *
 * <p>Only the commands registered through this service are tracked, so
 * {@link #unregisterAll()} never removes commands of another plugin sharing the
 * CommandAPI instance. Everything must be called from the main thread.</p>
 *
 * <pre>{@code
 * CommandService commands = new CommandService(this, plugin::getLangLoader,
 *         new CommandMessages(LangKey.NO_PERMISSION, LangKey.CONSOLE_CANT_DO));
 *
 * commands.register(new CommandAPICommand("guardian")
 *         .withSubcommands(
 *                 new CommandAPICommand("reload").executes((sender, args) -> {
 *                     CommandInvocation ctx = commands.context(sender, args);
 *                     if (!ctx.require("guardian.reload")) return;
 *                     plugin.reloadPlugin();
 *                     ctx.send(LangKey.RELOAD_SUCCESS);
 *                 }),
 *                 new CommandAPICommand("inspect").executesPlayer((player, args) -> {
 *                     CommandInvocation ctx = commands.context(player, args);
 *                     if (!ctx.require("guardian.inspect")) return;
 *                 })));
 * }</pre>
 *
 * <p>CommandAPI itself must be initialised by the consuming plugin, because the
 * library is not a plugin: {@code CommandAPI.onLoad(new CommandAPIPaperConfig(this))}
 * in {@code onLoad()}, {@code CommandAPI.onEnable()} in {@code onEnable()} and
 * {@code CommandAPI.onDisable()} in {@code onDisable()}.</p>
 */
public final class CommandService {

    private final Plugin plugin;
    private final Supplier<LangLoader> lang;
    private final CommandMessages messages;
    private final Set<String> registered = new LinkedHashSet<>();

    /**
     * @param lang supplier re-read on every message, so a plugin reload that
     *             recreates the {@link LangLoader} is picked up automatically
     */
    public CommandService(@NotNull Plugin plugin,
                          @NotNull Supplier<LangLoader> lang,
                          @NotNull CommandMessages messages) {
        this.plugin = plugin;
        this.lang = lang;
        this.messages = messages;
    }

    /**
     * @param lang fixed loader, use the {@link Supplier} constructor when the
     *             plugin can reload its language file
     */
    public CommandService(@NotNull Plugin plugin,
                          @NotNull LangLoader lang,
                          @NotNull CommandMessages messages) {
        this(plugin, () -> lang, messages);
    }

    /**
     * Registers the commands and tracks their names and aliases for
     * {@link #unregisterAll()}, because CommandAPI does not remove aliases
     * when the main name is unregistered.
     */
    public void register(@NotNull CommandAPICommand... commands) {
        for (CommandAPICommand command : commands) {
            command.register();
            registered.add(command.getName());
            Collections.addAll(registered, command.getAliases());
        }
    }

    /**
     * Registers a command tracking an explicit name, for the rare cases where the
     * registered name is not the one returned by the command object.
     */
    public void register(@NotNull String name, @NotNull CommandAPICommand command) {
        command.register();
        registered.add(name);
        Collections.addAll(registered, command.getAliases());
    }

    /**
     * Unregisters every command registered through this service, logging and
     * continuing when a single name cannot be removed.
     */
    public void unregisterAll() {
        for (String name : List.copyOf(registered)) {
            try {
                CommandAPI.unregister(name);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not unregister command '" + name + "'", exception);
            }
        }
        registered.clear();
    }

    /**
     * @return an immutable, ordered view of the registered command names
     */
    public @NotNull Set<String> registeredNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(registered));
    }

    /**
     * @param permission the node to check, blank to skip the check
     * @return {@code true} when the sender has the permission
     */
    public boolean hasPermission(@NotNull CommandSender sender, @NotNull String permission) {
        if (permission.isBlank() || sender.hasPermission(permission)) {
            return true;
        }

        lang().send(sender, messages.noPermission());
        return false;
    }

    /**
     * @param permissions the nodes to check, empty to skip the check
     * @return {@code true} when the sender has at least one of the permissions
     */
    public boolean hasAnyPermission(@NotNull CommandSender sender, @NotNull String... permissions) {
        if (permissions.length == 0) {
            return true;
        }

        for (String permission : permissions) {
            if (permission.isBlank() || sender.hasPermission(permission)) {
                return true;
            }
        }

        lang().send(sender, messages.noPermission());
        return false;
    }

    /**
     * @return {@code true} when the sender is a player, otherwise sends the
     * {@code console-cant-do-command} message
     */
    public boolean playerOnly(@NotNull CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }

        lang().send(sender, messages.consoleCantDo());
        return false;
    }

    public @NotNull CommandInvocation context(@NotNull CommandSender sender, @NotNull CommandArguments args) {
        return new CommandInvocation(this, sender, args);
    }

    public @NotNull LangLoader lang() {
        return lang.get();
    }

    public @NotNull CommandMessages messages() {
        return messages;
    }

    /**
     * Convenience for sending through the current loader.
     */
    public void send(@NotNull CommandSender sender, @NotNull MessageKey key,
                     TagResolver... resolvers) {
        lang().send(sender, key, resolvers);
    }
}