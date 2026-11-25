package dev.yucktheslime.tradeofimmersion.skills.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out which serverbound mod packet a key press caused. The client says "key X was pressed at time t while
 * holding item I"; the server keeps the recent custom packets of that player. A channel counts when one of its
 * packets arrived within {@code [t - before, t + after]} and it was quiet for {@code quiet} ms before that (so
 * packets a mod sends all the time, like position or config sync, are not blamed). Pure logic, unit tested.
 */
public final class KeybindCorrelator {
    private KeybindCorrelator() {
    }

    public record Packet(String channel, long timeMs, int index) {
    }

    public static final long BEFORE = 250, AFTER = 700, QUIET = 1500;

    /** @return the first matching packet per channel, in arrival order. */
    public static List<Packet> correlate(List<Packet> recent, long pressMs) {
        return correlate(recent, pressMs, BEFORE, AFTER, QUIET);
    }

    public static List<Packet> correlate(List<Packet> recent, long pressMs, long before, long after, long quiet) {
        Map<String, Packet> hit = new LinkedHashMap<>();
        Map<String, Boolean> noisy = new LinkedHashMap<>();
        long winStart = pressMs - before, winEnd = pressMs + after;
        for (Packet p : recent) {
            if (p.timeMs() >= winStart - quiet && p.timeMs() < winStart) noisy.put(p.channel(), true);
        }
        for (Packet p : recent) {
            if (p.timeMs() < winStart || p.timeMs() > winEnd) continue;
            if (noisy.containsKey(p.channel())) continue;
            hit.putIfAbsent(p.channel(), p);
        }
        // a channel that fires more than 3 times inside the window is a stream, not a key press
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Packet p : recent) if (p.timeMs() >= winStart && p.timeMs() <= winEnd) counts.merge(p.channel(), 1, Integer::sum);
        List<Packet> out = new ArrayList<>();
        for (Packet p : hit.values()) if (counts.getOrDefault(p.channel(), 0) <= 3) out.add(p);
        return out;
    }

    /** Recordings become trusted after the same channel was seen for the same key this many times. */
    public static boolean trusted(int sightings) {
        return sightings >= 1;
    }
}
