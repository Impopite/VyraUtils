package it.impo.vyrautils.update;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A lenient semantic version, used to compare the running plugin version with the
 * one published on Modrinth.
 *
 * <p>Accepts {@code 1.2.3}, {@code v1.2.3}, {@code 1.2}, {@code 1.0.0-beta.2} and
 * ignores build metadata ({@code +build}). Pre-releases sort before the matching
 * release, as in semver.</p>
 *
 * <p>Components after the third are ignored, so {@code 1.2.3.4} is read as
 * {@code 1.2.3}. Parsing is deliberately lenient: the leading digits of a component
 * are kept and the rest is ignored, so {@code 1.2.3foo} is {@code 1.2.3} and
 * {@code 1.x.3} is {@code 1.0.3}.</p>
 *
 * <p>{@link #parse(String)} never throws: unparseable input becomes
 * {@link #UNKNOWN} (0.0.0), so a checker can never report a bogus update.</p>
 */
public record SemanticVersion(int major, int minor, int patch, @NotNull List<String> preRelease) implements Comparable<SemanticVersion> {

    public static final SemanticVersion UNKNOWN = new SemanticVersion(0, 0, 0, List.of());

    public SemanticVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version parts cannot be negative");
        }

        preRelease = List.copyOf(preRelease);
    }

    public static @NotNull SemanticVersion parse(@Nullable String input) {
        if (input == null || input.isBlank()) return UNKNOWN;

        String value = input.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);

        int buildIndex = value.indexOf('+');
        if (buildIndex >= 0) value = value.substring(0, buildIndex);

        String core = value;
        List<String> pre = List.of();

        int dashIndex = value.indexOf('-');
        if (dashIndex >= 0) {
            core = value.substring(0, dashIndex);
            String suffix = value.substring(dashIndex + 1);
            pre = suffix.isEmpty() ? List.of() : List.of(suffix.split("\\."));
        }

        String[] parts = core.split("\\.");
        return new SemanticVersion(number(parts, 0), number(parts, 1), number(parts, 2), pre);
    }

    public boolean isNewerThan(@Nullable String other) {
        return isNewerThan(parse(other));
    }

    public boolean isNewerThan(@NotNull SemanticVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(@NotNull SemanticVersion other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) return result;

        result = Integer.compare(minor, other.minor);
        if (result != 0) return result;

        result = Integer.compare(patch, other.patch);
        if (result != 0) return result;

        return comparePreRelease(preRelease, other.preRelease);
    }

    @Override
    public @NotNull String toString() {
        String core = major + "." + minor + "." + patch;
        return preRelease.isEmpty() ? core : core + "-" + String.join(".", preRelease);
    }

    private static int comparePreRelease(@NotNull List<String> left, @NotNull List<String> right) {
        if (left.isEmpty() && right.isEmpty()) return 0;
        if (left.isEmpty()) return 1;
        if (right.isEmpty()) return -1;

        int length = Math.min(left.size(), right.size());
        for (int index = 0; index < length; index++) {
            int result = compareIdentifier(left.get(index), right.get(index));
            if (result != 0) return result;
        }

        return Integer.compare(left.size(), right.size());
    }

    private static int compareIdentifier(@NotNull String left, @NotNull String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);

        if (leftNumeric && rightNumeric) return compareNumeric(left, right);
        if (leftNumeric) return -1;
        if (rightNumeric) return 1;

        return left.toLowerCase(Locale.ROOT).compareTo(right.toLowerCase(Locale.ROOT));
    }

    private static int compareNumeric(@NotNull String left, @NotNull String right) {
        String a = trimZeros(left);
        String b = trimZeros(right);

        int result = Integer.compare(a.length(), b.length());
        return result != 0 ? result : a.compareTo(b);
    }

    private static @NotNull String trimZeros(@NotNull String value) {
        int start = 0;
        while (start < value.length() - 1 && value.charAt(start) == '0') start++;

        return value.substring(start);
    }

    private static boolean isNumeric(@NotNull String value) {
        if (value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }

        return true;
    }

    private static int number(@NotNull String[] parts, int index) {
        if (index >= parts.length) return 0;

        String part = parts[index].trim();
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) end++;
        if (end == 0) return 0;

        try {
            return Integer.parseInt(part.substring(0, end));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
