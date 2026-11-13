package com.paimc.anticheat.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses durations such as "30m", "2h", "1d12h", "perm". */
public final class Durations {
    private static final Pattern FULL = Pattern.compile("^(\\d+[smhdw])+$");
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])");

    private Durations() {}

    /** @return milliseconds, or -1 for permanent. */
    public static long parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("empty duration");
        String t = input.trim().toLowerCase(Locale.ROOT);
        if (t.equals("perm") || t.equals("permanent") || t.equals("-1")) return -1;
        if (!FULL.matcher(t).matches()) throw new IllegalArgumentException("invalid duration: " + input);
        Matcher m = PART.matcher(t);
        long total = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            long unit = switch (m.group(2)) {
                case "s" -> 1000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> 604_800_000L;
            };
            total += n * unit;
        }
        if (total <= 0) throw new IllegalArgumentException("duration must be positive: " + input);
        return total;
    }

    public static String format(long ms) {
        if (ms < 0) return "permanent";
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }
}
