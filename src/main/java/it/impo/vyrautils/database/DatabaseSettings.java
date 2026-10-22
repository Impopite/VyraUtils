package it.impo.vyrautils.database;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Connection settings for {@link DatabasePool}.
 *
 * <p>Numeric values are clamped instead of throwing, so a broken config file cannot
 * stop the plugin from starting: a non positive port falls back to {@code 3306},
 * the pool size is at least 1, the connection timeout is at least one second and
 * the idle timeout and max lifetime are at least thirty seconds.</p>
 *
 * @param host                     database host, for example {@code localhost}
 * @param port                     database port, {@code 3306} when not positive
 * @param database                 schema name
 * @param username                 database user
 * @param password                 database password
 * @param ssl                      {@code true} to require TLS ({@code sslMode=REQUIRED})
 * @param poolSize                 maximum number of pooled connections
 * @param connectionTimeoutSeconds how long a caller waits for a free connection
 * @param maxLifetimeSeconds       maximum lifetime of a pooled connection
 * @param idleTimeoutSeconds       how long an unused connection is kept
 * @param allowPublicKeyRetrieval  {@code true} to let the driver fetch the server
 *                                 public key, needed by MySQL 8 with
 *                                 {@code caching_sha2_password} when {@code ssl} is
 *                                 {@code false}
 */
public record DatabaseSettings(@NotNull String host, int port, @NotNull String database,
                               @NotNull String username, @NotNull String password, boolean ssl,
                               int poolSize, int connectionTimeoutSeconds, int maxLifetimeSeconds,
                               int idleTimeoutSeconds, boolean allowPublicKeyRetrieval) {

    private static final int DEFAULT_PORT = 3306;
    private static final int DEFAULT_POOL_SIZE = 5;
    private static final int DEFAULT_CONNECTION_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_MAX_LIFETIME_SECONDS = 180;
    private static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 120;
    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MIN_LIFETIME_SECONDS = 30;

    public DatabaseSettings {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");

        if (port <= 0) port = DEFAULT_PORT;

        poolSize = Math.max(1, poolSize);
        connectionTimeoutSeconds = Math.max(MIN_TIMEOUT_SECONDS, connectionTimeoutSeconds);
        maxLifetimeSeconds = Math.max(MIN_LIFETIME_SECONDS, maxLifetimeSeconds);
        idleTimeoutSeconds = Math.max(MIN_LIFETIME_SECONDS, idleTimeoutSeconds);
    }

    /**
     * Creates settings with {@code allowPublicKeyRetrieval} set to {@code false},
     * so calls written before the field was added keep compiling.
     */
    public DatabaseSettings(@NotNull String host, int port, @NotNull String database, @NotNull String username, @NotNull String password, boolean ssl, int poolSize, int connectionTimeoutSeconds, int maxLifetimeSeconds, int idleTimeoutSeconds) {
        this(host, port, database, username, password, ssl, poolSize, connectionTimeoutSeconds, maxLifetimeSeconds, idleTimeoutSeconds, false);
    }

    /**
     * @return the JDBC url, without connection properties
     */
    public @NotNull String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    /**
     * @return settings with the default port, no TLS and the default pool sizes
     */
    public static @NotNull DatabaseSettings defaults(@NotNull String host, @NotNull String database, @NotNull String username, @NotNull String password) {
        return new DatabaseSettings(host, DEFAULT_PORT, database, username, password, false, DEFAULT_POOL_SIZE, DEFAULT_CONNECTION_TIMEOUT_SECONDS, DEFAULT_MAX_LIFETIME_SECONDS, DEFAULT_IDLE_TIMEOUT_SECONDS, false);
    }

    /**
     * The password is masked, so the record can be logged safely.
     */
    @Override
    public @NotNull String toString() {
        return "DatabaseSettings[host=" + host
                + ", port=" + port
                + ", database=" + database
                + ", username=" + username
                + ", password=***"
                + ", ssl=" + ssl
                + ", poolSize=" + poolSize
                + ", connectionTimeoutSeconds=" + connectionTimeoutSeconds
                + ", maxLifetimeSeconds=" + maxLifetimeSeconds
                + ", idleTimeoutSeconds=" + idleTimeoutSeconds
                + ", allowPublicKeyRetrieval=" + allowPublicKeyRetrieval
                + "]";
    }
}
