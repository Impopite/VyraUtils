package it.impo.vyrautils.event;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registers listeners and keeps track of them, so a plugin reload can remove
 * exactly its own listeners instead of every listener of the plugin.
 *
 * <pre>{@code
 * private final EventService events = new EventService(this);
 *
 * public void load() {
 *     events.register(new PlayerJoinListener(this));
 *     events.register(new MenuListener(this));
 * }
 *
 * public void unload() {
 *     events.unregisterAll();
 * }
 * }</pre>
 *
 * <p>Must be used from the main thread. Listeners are removed automatically by
 * Bukkit when the plugin is disabled; this class is only needed to reload them
 * without touching the rest of the plugin's listeners.</p>
 */
public final class EventService {

    private final Plugin plugin;
    private final List<Listener> listeners = new ArrayList<>();

    public EventService(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a listener and returns it, so it can be stored in one line.
     * Registering the same instance twice is ignored.
     */
    public <T extends Listener> @NotNull T register(@NotNull T listener) {
        if (listeners.contains(listener)) {
            return listener;
        }

        Bukkit.getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
        return listener;
    }

    public void registerAll(@NotNull Listener... listeners) {
        for (Listener listener : listeners) {
            register(listener);
        }
    }

    /**
     * Unregisters a single listener, even one registered outside this registrar.
     */
    public void unregister(@NotNull Listener listener) {
        HandlerList.unregisterAll(listener);
        listeners.remove(listener);
    }

    /**
     * Unregisters every listener tracked by this registrar.
     */
    public void unregisterAll() {
        for (Listener listener : List.copyOf(listeners)) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
    }

    /**
     * @return an immutable view of the tracked listeners
     */
    public @NotNull List<Listener> registered() {
        return Collections.unmodifiableList(new ArrayList<>(listeners));
    }

    public int count() {
        return listeners.size();
    }
}