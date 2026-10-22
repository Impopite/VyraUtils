package it.impo.vyrautils.update;

import org.jetbrains.annotations.NotNull;

/**
 * A version published on Modrinth that is newer than the running one.
 *
 * @param version     the published version number, as written on Modrinth
 * @param downloadUrl the CDN url of the main file, empty when the version has no files
 * @param pageUrl     the project page, always available and safe to open
 * @param versionType {@code release}, {@code beta} or {@code alpha}
 */
public record UpdateInfo(@NotNull String version, @NotNull String downloadUrl, @NotNull String pageUrl, @NotNull String versionType) {

    public @NotNull String bestUrl() {
        return downloadUrl.isEmpty() ? pageUrl : downloadUrl;
    }
}
