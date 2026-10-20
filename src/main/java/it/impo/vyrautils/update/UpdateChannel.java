package it.impo.vyrautils.update;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The least stable release type the update checker is allowed to report.
 *
 * <p>{@link #BETA} accepts stable and beta versions, {@link #ALPHA} accepts all
 * three types. Modrinth returns {@code release}, {@code beta} and {@code alpha}.</p>
 */
public enum UpdateChannel {

    RELEASE,
    BETA,
    ALPHA;

    public boolean accepts(@NotNull String versionType) {
        String type = versionType.toLowerCase(Locale.ROOT);

        return switch (this) {
            case RELEASE -> type.equals("release");
            case BETA -> type.equals("release") || type.equals("beta");
            case ALPHA -> type.equals("release") || type.equals("beta") || type.equals("alpha");
        };
    }
}
