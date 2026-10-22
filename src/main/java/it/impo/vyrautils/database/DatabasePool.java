package it.impo.vyrautils.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.impo.vyrautils.scheduling.Schedulers;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HikariCP connection pool used by the plugins, with the MySQL settings the
 * projects share.
 *
 * <p>HikariCP is relocated inside the shaded jar, so it never appears in this
 * public API: use {@link #connection()} and the async helpers instead of asking
 * for the data source. The MySQL driver is relocated as well and is resolved by
 * name at pool creation, so the pool never depends on the global
 * {@link java.sql.DriverManager} registration.</p>
 *
 * <p>Async methods run with {@link Schedulers#async(Plugin, Runnable)} and
 * complete their future on that async thread. Use
 * {@link Schedulers#syncFromAsync(Plugin, CompletableFuture, java.util.function.Consumer)}
 * before touching the world with the result. A failed task completes the future
 * exceptionally with the original {@link SQLException}: {@link CompletableFuture#join()}
 * throws a {@link java.util.concurrent.CompletionException} whose cause is it.</p>
 *
 * <p>The pool must be closed in {@code onDisable}, otherwise Hikari keeps its
 * threads (and the plugin classloader with them) alive, leaking on every reload.</p>
 *
 * <p>{@link #connection()} must not be called on the main thread: use
 * {@link #query}, {@link #update} or {@link #transaction}, which run on an async
 * thread.</p>
 */
public final class DatabasePool implements AutoCloseable {

    /**
     * Idle connections kept open after a burst of queries, capped by
     * {@link DatabaseSettings#poolSize()}.
     */
    private static final int MINIMUM_IDLE = 2;

    /**
     * Candidate names of the MySQL driver, in priority order.
     *
     * <p>The first is the original class name and the second the relocated one: in
     * the shaded jar both are rewritten to the relocated name, while in an unshaded
     * build the first one matches.</p>
     */
    private static final List<String> DRIVER_CLASS_NAMES = List.of("com.mysql.cj.jdbc.Driver", "it.impo.vyrautils.libs.mysql.jdbc.Driver");

    private final Plugin plugin;
    private final DatabaseSettings settings;
    private final HikariDataSource dataSource;

    /**
     * Creates a pool named after the plugin.
     */
    public DatabasePool(@NotNull Plugin plugin, @NotNull DatabaseSettings settings) {
        this(plugin, plugin.getName() + "-pool", settings);
    }

    /**
     * Creates the pool and opens its first connection, so wrong credentials fail
     * at startup instead of at the first query. This may block startup for up to
     * {@link DatabaseSettings#connectionTimeoutSeconds() connectionTimeoutSeconds}.
     *
     * @throws RuntimeException when Hikari cannot create the pool
     */
    public DatabasePool(@NotNull Plugin plugin, @NotNull String poolName, @NotNull DatabaseSettings settings) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(poolName, "poolName");
        Objects.requireNonNull(settings, "settings");

        this.plugin = plugin;
        this.settings = settings;
        this.dataSource = create(plugin, poolName, settings);
    }

    public @NotNull DatabaseSettings settings() {
        return settings;
    }

    public boolean isClosed() {
        return dataSource.isClosed();
    }

    /**
     * Borrows a connection from the pool. Always close it with try-with-resources.
     * Never call it on the main thread: use {@link #query}, {@link #update} or
     * {@link #transaction}, which run on an async thread.
     */
    public @NotNull Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Runs a read on an async thread and completes with its result. The function must
     * materialize the result before returning (for example copy a {@code ResultSet}
     * into a list): the connection, and with it the result set, is closed afterwards.
     */
    public <T> @NotNull CompletableFuture<T> query(@NotNull SqlFunction<T> function) {
        Objects.requireNonNull(function, "function");

        return run(() -> {
            try (Connection connection = connection()) {
                return function.apply(connection);
            }
        });
    }

    /**
     * Runs a write on an async thread. The connection is closed, and therefore
     * committed, as soon as {@code update} returns.
     */
    public @NotNull CompletableFuture<Void> update(@NotNull SqlUpdate update) {
        Objects.requireNonNull(update, "update");

        return run(() -> {
            try (Connection connection = connection()) {
                update.apply(connection);
                return null;
            }
        });
    }

    /**
     * Runs {@code function} inside a transaction: it is committed when the
     * function returns normally and rolled back when it throws.
     *
     * @return the value returned by {@code function}
     */
    public <T> @NotNull CompletableFuture<T> transaction(@NotNull SqlFunction<T> function) {
        Objects.requireNonNull(function, "function");

        return run(() -> {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);

                try {
                    T result = function.apply(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException exception) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                    throw exception;
                }
            }
        });
    }

    /**
     * Closes the pool and every idle connection. Called by the plugin in
     * {@code onDisable}; calling it twice is harmless.
     */
    @Override
    public void close() {
        if (!dataSource.isClosed()) dataSource.close();
    }

    private <T> @NotNull CompletableFuture<T> run(@NotNull ConnectionAction<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();

        try {
            Schedulers.async(plugin, () -> {
                try {
                    future.complete(action.run());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }

        return future;
    }

    private static @NotNull HikariDataSource create(@NotNull Plugin plugin, @NotNull String poolName, @NotNull DatabaseSettings settings) {
        HikariConfig config = new HikariConfig();

        config.setPoolName(poolName);
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.username());
        config.setPassword(settings.password());

        config.setDriverClassName(driverClassName(plugin));
        config.setMaximumPoolSize(settings.poolSize());
        config.setMinimumIdle(Math.min(settings.poolSize(), MINIMUM_IDLE));
        config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(settings.connectionTimeoutSeconds()));
        config.setIdleTimeout(TimeUnit.SECONDS.toMillis(settings.idleTimeoutSeconds()));
        config.setMaxLifetime(TimeUnit.SECONDS.toMillis(settings.maxLifetimeSeconds()));

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        config.addDataSourceProperty("sslMode", settings.ssl() ? "REQUIRED" : "DISABLED");

        if (settings.allowPublicKeyRetrieval()) {
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
        }

        return new HikariDataSource(config);
    }

    /**
     * Finds the MySQL driver on the classpath of the plugin.
     *
     * @throws IllegalStateException when the driver is missing
     */
    private static @NotNull String driverClassName(@NotNull Plugin plugin) {
        ClassLoader loader = plugin.getClass().getClassLoader();

        for (String candidate : DRIVER_CLASS_NAMES) {
            try {
                Class.forName(candidate, false, loader);
                return candidate;
            } catch (ClassNotFoundException ignored) {}
        }

        throw new IllegalStateException("The MySQL driver is not on the classpath, expected one of "
                + DRIVER_CLASS_NAMES);
    }

    /**
     * A JDBC read that may throw {@link SQLException}.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(@NotNull Connection connection) throws SQLException;
    }

    /**
     * A JDBC write that may throw {@link SQLException}.
     */
    @FunctionalInterface
    public interface SqlUpdate {
        void apply(@NotNull Connection connection) throws SQLException;
    }

    @FunctionalInterface
    private interface ConnectionAction<T> {
        T run() throws SQLException;
    }
}
