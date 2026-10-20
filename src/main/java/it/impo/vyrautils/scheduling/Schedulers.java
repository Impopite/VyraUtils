package it.impo.vyrautils.scheduling;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Scheduling helpers built on the regionised scheduler API, which works both on
 * Paper (everything runs on the main thread) and on Folia (tasks run on the
 * owning region).
 *
 * <p>Rules of thumb:</p>
 * <ul>
 *     <li>{@link #sync} for anything touching the world from an async callback;</li>
 *     <li>{@link #region} / {@link #entity} when you already own a location or an
 *     entity and the code touches only that area;</li>
 *     <li>{@link #async} for I/O, database and HTTP;</li>
 *     <li>{@link #syncFromAsync} to come back to the main thread from a
 *     {@link CompletableFuture}.</li>
 * </ul>
 *
 * <p>Tick based methods clamp the delay and the period to at least 1 tick, because
 * the regionised schedulers reject {@code <= 0}. Tasks must be cancelled by the
 * plugin that created them, usually in {@code onDisable}.</p>
 */
public final class Schedulers {

    public static void sync(@NotNull Plugin plugin, @NotNull Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static @NotNull ScheduledTask syncNextTick(@NotNull Plugin plugin, @NotNull Runnable task) {
        return Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
    }

    public static @NotNull ScheduledTask syncLater(@NotNull Plugin plugin, @NotNull Runnable task, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(),
                Math.max(1L, delayTicks));
    }

    public static @NotNull ScheduledTask syncTimer(@NotNull Plugin plugin, @NotNull Runnable task,
                                                   long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> task.run(),
                Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static @NotNull ScheduledTask async(@NotNull Plugin plugin, @NotNull Runnable task) {
        return Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    public static @NotNull ScheduledTask asyncLater(@NotNull Plugin plugin, @NotNull Runnable task,
                                                    long delay, @NotNull TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(),
                Math.max(0L, delay), unit);
    }

    public static @NotNull ScheduledTask asyncLater(@NotNull Plugin plugin, @NotNull Runnable task,
                                                    @NotNull Duration delay) {
        return asyncLater(plugin, task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static @NotNull ScheduledTask asyncTimer(@NotNull Plugin plugin, @NotNull Runnable task,
                                                    long initialDelay, long period, @NotNull TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(),
                Math.max(0L, initialDelay), Math.max(1L, period), unit);
    }

    public static @NotNull ScheduledTask asyncTimer(@NotNull Plugin plugin, @NotNull Runnable task,
                                                    @NotNull Duration initialDelay, @NotNull Duration period) {
        return asyncTimer(plugin, task, initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static @NotNull ScheduledTask region(@NotNull Plugin plugin, @NotNull Location location,
                                                @NotNull Runnable task) {
        return Bukkit.getRegionScheduler().run(plugin, location, scheduled -> task.run());
    }

    public static @NotNull ScheduledTask regionLater(@NotNull Plugin plugin, @NotNull Location location,
                                                     @NotNull Runnable task, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduled -> task.run(),
                Math.max(1L, delayTicks));
    }

    /**
     * @return the task, or {@code null} when the entity is already retired
     */
    public static @Nullable ScheduledTask entity(@NotNull Plugin plugin, @NotNull Entity entity,
                                                 @NotNull Runnable task) {
        return entity.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    /**
     * @return the task, or {@code null} when the entity is already retired
     */
    public static @Nullable ScheduledTask entityLater(@NotNull Plugin plugin, @NotNull Entity entity,
                                                      @NotNull Runnable task, long delayTicks) {
        return entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null,
                Math.max(1L, delayTicks));
    }

    /**
     * Runs on the entity thread as soon as possible.
     *
     * @return {@code false} when the entity was retired before the task could run
     */
    public static boolean executeOnEntity(@NotNull Plugin plugin, @NotNull Entity entity,
                                          @NotNull Runnable task) {
        return entity.getScheduler().execute(plugin, task, null, 1L);
    }

    /**
     * Runs {@code onMain} on the main thread when the future completes, logging
     * failures through the plugin logger.
     */
    public static <T> void syncFromAsync(@NotNull Plugin plugin,
                                         @NotNull CompletableFuture<T> future,
                                         @NotNull Consumer<T> onMain) {
        syncFromAsync(plugin, future, onMain, throwable ->
                plugin.getLogger().log(Level.WARNING, "Async task failed", throwable));
    }

    /**
     * Runs {@code onMain} or {@code onError} on the main thread when the future
     * completes, so both callbacks may touch the world.
     */
    public static <T> void syncFromAsync(@NotNull Plugin plugin,
                                         @NotNull CompletableFuture<T> future,
                                         @NotNull Consumer<T> onMain,
                                         @NotNull Consumer<Throwable> onError) {
        future.whenComplete((value, throwable) -> {
            if (throwable != null) {
                sync(plugin, () -> onError.accept(throwable));
                return;
            }

            sync(plugin, () -> onMain.accept(value));
        });
    }

    /**
     * @return {@code true} when the current thread ticks the global region, i.e.
     * it may touch global state such as time, weather or console commands
     */
    public static boolean isGlobalThread() {
        return Bukkit.getServer().isGlobalTickThread();
    }

    /**
     * @return {@code true} when the current thread owns the region of the location,
     * use it instead of {@link #isGlobalThread()} on Folia
     */
    public static boolean owns(@NotNull Location location) {
        return Bukkit.isOwnedByCurrentRegion(location);
    }

    /**
     * @return {@code true} when the current thread owns the entity
     */
    public static boolean owns(@NotNull Entity entity) {
        return Bukkit.isOwnedByCurrentRegion(entity);
    }

    /**
     * Cancels a single task that may be {@code null}, for the nullable results of
     * {@link #entity} and {@link #entityLater}.
     */
    public static void cancel(@Nullable ScheduledTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Cancels every given task, ignoring {@code null} entries.
     */
    public static void cancelAll(@Nullable ScheduledTask... tasks) {
        for (ScheduledTask task : tasks) {
            cancel(task);
        }
    }
}
