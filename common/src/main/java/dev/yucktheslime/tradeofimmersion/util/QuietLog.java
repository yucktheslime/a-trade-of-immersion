package dev.yucktheslime.tradeofimmersion.util;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limited warnings: the same kind of problem (for example "AI backend offline") is logged at most once per
 * minute, with a count of how many were suppressed since, so an unreachable service never floods the server log.
 */
public final class QuietLog {
    private static final long INTERVAL_MS = 60_000;
    private static final Map<String, long[]> STATE = new ConcurrentHashMap<>(); // key -> {lastLogged, suppressed}

    private QuietLog() {
    }

    public static void warn(String key, String message, Object... args) {
        long now = System.currentTimeMillis();
        long[] s = STATE.computeIfAbsent(key, k -> new long[]{0, 0});
        long suppressed;
        synchronized (s) {
            if (now - s[0] < INTERVAL_MS) {
                s[1]++;
                return;
            }
            suppressed = s[1];
            s[0] = now;
            s[1] = 0;
        }
        String msg = suppressed > 0 ? message + " (" + suppressed + " similar message(s) suppressed in the last minute)" : message;
        TradeOfImmersion.LOGGER.warn(msg, args);
    }

    /** The problem went away: the next occurrence is logged right away again. */
    public static void clear(String key) {
        STATE.remove(key);
    }
}
