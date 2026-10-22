package it.impo.vyrautils.scheduling;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats human readable durations, for commands such as
 * {@code /tempban 1d2h} or {@code /warp cooldown 30s}.
 *
 * <pre>{@code
 * Durations.parse("1d2h30m");     // PT26H30M
 * Durations.parse("45");          // 45 seconds
 * Durations.parseOrDefault(text, Duration.ofMinutes(5));
 * Durations.format(Duration.ofSeconds(3725));   // "1h 2m 5s"
 * }</pre>
 *
 * <p>Supported units are {@code d} (days), {@code h} (hours), {@code m}
 * (minutes) and {@code s} (seconds). Numbers may be decimal and components may
 * be written without spaces or with spaces: {@code 1d2h}, {@code 1d 2h}.
 * A number without a unit is seconds.</p>
 */
public final class Durations {

    private static final Pattern COMPONENT = Pattern.compile("(\\d+(?:\\.\\d+)?)([dhms]?)");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final long SECONDS_PER_DAY = 86_400L;
    private static final long SECONDS_PER_HOUR = 3_600L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MILLIS_PER_TICK = 50L;

    /**
     * @return the parsed duration, never negative
     * @throws IllegalArgumentException when the text is not a duration
     */
    public static @NotNull Duration parse(@NotNull String text) {
        String input = WHITESPACE.matcher(text.trim()).replaceAll("").toLowerCase(Locale.ROOT);
        if (input.isEmpty()) throw new IllegalArgumentException("Empty duration");

        Matcher matcher = COMPONENT.matcher(input);
        int index = 0;
        double millis = 0;

        while (matcher.find()) {
            if (matcher.start() != index) throw invalid(text);

            double amount = Double.parseDouble(matcher.group(1));
            millis += amount * millisPerUnit(matcher.group(2), text);
            index = matcher.end();
        }

        if (index != input.length()) throw invalid(text);

        return Duration.ofMillis(Math.round(millis));
    }

    /**
     * @return the parsed duration, or {@code fallback} when the text is invalid
     */
    public static @NotNull Duration parseOrDefault(@NotNull String text, @NotNull Duration fallback) {
        try {
            return parse(text);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    /**
     * Formats a duration with its non zero units, from days to seconds, for
     * example {@code 2d 3h 5m}; durations below one second are shown in
     * milliseconds.
     */
    public static @NotNull String format(@NotNull Duration duration) {
        if (duration.isZero() || duration.isNegative()) return "0s";

        long seconds = duration.getSeconds();
        long days = seconds / SECONDS_PER_DAY;
        long hours = seconds % SECONDS_PER_DAY / SECONDS_PER_HOUR;
        long minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
        long remainingSeconds = seconds % SECONDS_PER_MINUTE;
        long millis = duration.getNano() / 1_000_000L;

        if (seconds == 0 && millis > 0) return millis + "ms";

        List<String> parts = new ArrayList<>(4);
        addPart(parts, days, "d");
        addPart(parts, hours, "h");
        addPart(parts, minutes, "m");
        addPart(parts, remainingSeconds, "s");

        if (parts.isEmpty()) return "0s";

        return String.join(" ", parts);
    }

    /**
     * @return the duration in ticks, rounded up so a non zero duration never
     * becomes a zero tick delay
     */
    public static long toTicks(@NotNull Duration duration) {
        long millis;

        try {
            millis = Math.max(0L, duration.toMillis());
        } catch (ArithmeticException exception) {
            millis = Long.MAX_VALUE;
        }

        return Math.ceilDiv(millis, MILLIS_PER_TICK);
    }

    /**
     * @return the number of ticks as a readable duration; negative ticks are read
     * as zero and an overflow saturates instead of wrapping
     */
    public static @NotNull String formatTicks(long ticks) {
        long millis;

        try {
            millis = Math.multiplyExact(Math.max(0L, ticks), MILLIS_PER_TICK);
        } catch (ArithmeticException exception) {
            millis = Long.MAX_VALUE;
        }

        return format(Duration.ofMillis(millis));
    }

    private static double millisPerUnit(@NotNull String unit, @NotNull String text) {
        return switch (unit) {
            case "", "s" -> 1_000d;
            case "m" -> 60_000d;
            case "h" -> 3_600_000d;
            case "d" -> 86_400_000d;
            default -> throw invalid(text);
        };
    }

    private static void addPart(@NotNull List<String> parts, long value, @NotNull String suffix) {
        if (value > 0) parts.add(value + suffix);
    }

    private static @NotNull IllegalArgumentException invalid(@NotNull String text) {
        return new IllegalArgumentException("Invalid duration: '" + text + "'");
    }
}
