package it.impo.vyrautils.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.impo.vyrautils.lang.LangLoader;
import it.impo.vyrautils.lang.MessageKey;
import it.impo.vyrautils.scheduling.Schedulers;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Checks the Modrinth API for a newer release of the plugin.
 *
 * <pre>{@code
 * ModrinthUpdateChecker updater = new ModrinthUpdateChecker(this, "vyraquest");
 * updater.checkAsync();
 *
 * updater.notify(player, getLangLoader(), LangKey.UPDATE_AVAILABLE);
 * }</pre>
 *
 * <p>The version is read from {@code plugin.yml}. Every published version is
 * compared with {@link SemanticVersion}, so a non-ordered API response cannot
 * hide a newer release. Failures are logged and never thrown: a broken update
 * check must not break the plugin.</p>
 */
public final class ModrinthUpdateChecker {

    private static final String API_URL = "https://api.modrinth.com/v2/project/";
    private static final String PAGE_URL = "https://modrinth.com/plugin/";

    private final Plugin plugin;
    private final String projectSlug;
    private final String currentVersion;
    private final SemanticVersion current;
    private final UpdateChannel channel;
    private final String pageUrl;
    private final HttpClient httpClient;

    private volatile UpdateInfo latest;

    public ModrinthUpdateChecker(@NotNull Plugin plugin, @NotNull String projectSlug) {
        this(plugin, projectSlug, plugin.getPluginMeta().getVersion(), UpdateChannel.RELEASE);
    }

    public ModrinthUpdateChecker(@NotNull Plugin plugin, @NotNull String projectSlug,
                                 @NotNull String currentVersion) {
        this(plugin, projectSlug, currentVersion, UpdateChannel.RELEASE);
    }

    public ModrinthUpdateChecker(@NotNull Plugin plugin, @NotNull String projectSlug,
                                 @NotNull String currentVersion, @NotNull UpdateChannel channel) {
        this.plugin = plugin;
        this.projectSlug = projectSlug;
        this.currentVersion = currentVersion;
        this.current = SemanticVersion.parse(currentVersion);
        this.channel = channel;
        this.pageUrl = PAGE_URL + projectSlug;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Runs {@link #check()} on an async thread and completes with the update, if any.
     * The future completes on the async thread: use
     * {@link Schedulers#syncFromAsync(Plugin, CompletableFuture, java.util.function.Consumer)}
     * before touching the world with the result.
     */
    public @NotNull CompletableFuture<Optional<UpdateInfo>> checkAsync() {
        CompletableFuture<Optional<UpdateInfo>> future = new CompletableFuture<>();
        try {
            Schedulers.async(plugin, () -> {
                try {
                    future.complete(check());
                } catch (RuntimeException exception) {
                    future.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    /**
     * Blocking check. Call it from an async thread, or use {@link #checkAsync()}.
     *
     * @return the update, or empty when the plugin is up to date or the check failed
     */
    public @NotNull Optional<UpdateInfo> check() {
        if (Schedulers.isGlobalThread()) {
            plugin.getLogger().warning("[Update] check() called on the main thread, prefer checkAsync()");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + projectSlug + "/version"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", userAgent())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("[Update] Modrinth API returned status " + response.statusCode());
                return Optional.empty();
            }

            return parse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("[Update] Check interrupted");
            return Optional.empty();
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "[Update] Could not check for updates", exception);
            return Optional.empty();
        }
    }

    /**
     * @return the cached result of the last check, or empty when there is no update
     */
    public @NotNull Optional<UpdateInfo> updateInfo() {
        return Optional.ofNullable(latest);
    }

    public boolean isUpdateAvailable() {
        return latest != null;
    }

    public @Nullable String latestVersion() {
        UpdateInfo info = latest;
        return info == null ? null : info.version();
    }

    public @NotNull String pageUrl() {
        return pageUrl;
    }

    /**
     * Sends the update message with the {@code latest_version} and
     * {@code download_url} placeholders.
     */
    public void notify(@NotNull CommandSender sender,
                       @NotNull LangLoader lang,
                       @NotNull MessageKey key) {
        UpdateInfo info = latest;
        if (info == null) {
            return;
        }

        lang.send(sender, key,
                Placeholder.unparsed("latest_version", info.version()),
                Placeholder.unparsed("download_url", info.bestUrl()));
    }

    private @NotNull Optional<UpdateInfo> parse(@NotNull String body) {
        JsonArray versions = JsonParser.parseString(body).getAsJsonArray();

        UpdateInfo best = null;
        SemanticVersion bestVersion = SemanticVersion.UNKNOWN;

        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject version = element.getAsJsonObject();
            String versionType = string(version, "version_type");
            String versionNumber = string(version, "version_number");

            if (versionType == null || versionNumber == null || !channel.accepts(versionType)) {
                continue;
            }

            SemanticVersion parsed = SemanticVersion.parse(versionNumber);
            if (best != null && parsed.compareTo(bestVersion) <= 0) {
                continue;
            }

            best = new UpdateInfo(versionNumber, fileUrl(version), pageUrl, versionType);
            bestVersion = parsed;
        }

        if (best == null || !bestVersion.isNewerThan(current)) {
            latest = null;
            return Optional.empty();
        }

        latest = best;
        plugin.getLogger().info("[Update] New version available: " + best.version()
                + " (current: " + currentVersion + ")");
        return Optional.of(best);
    }

    private @NotNull String userAgent() {
        return plugin.getPluginMeta().getName() + "/" + currentVersion + " (VyraUtils)";
    }

    private static @Nullable String string(@NotNull JsonObject object, @NotNull String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static @NotNull String fileUrl(@NotNull JsonObject version) {
        JsonElement files = version.get("files");
        if (files == null || !files.isJsonArray()) {
            return "";
        }

        String fallback = "";
        for (JsonElement element : files.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject file = element.getAsJsonObject();
            String url = string(file, "url");
            if (url == null || url.isEmpty()) {
                continue;
            }

            JsonElement primary = file.get("primary");
            if (primary != null && primary.isJsonPrimitive() && primary.getAsBoolean()) {
                return url;
            }

            if (fallback.isEmpty()) {
                fallback = url;
            }
        }

        return fallback;
    }
}
