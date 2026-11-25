package dev.yucktheslime.tradeofimmersion.skills.learn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a player tends to do (farm, chop, mine, build, fish...), as decaying scores. Each observed action adds 1 to
 * its activity and to the block/item it involved; scores halve every {@code halfLifeTicks}. Pure logic, unit tested;
 * Gson-serializable (public fields).
 */
public final class HabitModel {
    public Map<String, Habit> habits = new LinkedHashMap<>();

    public static final class Habit {
        public double score;
        public long lastTick;
        /** block or item id -> score */
        public Map<String, Double> subjects = new LinkedHashMap<>();
        /** Where it happened last (x, y, z). */
        public int x, y, z;
        public String dimension = "";
    }

    public record Top(String activity, double score, String subject) {
    }

    public void observe(String activity, String subject, long tick, double halfLifeTicks, int x, int y, int z, String dim) {
        Habit h = habits.computeIfAbsent(activity, k -> new Habit());
        decay(h, tick, halfLifeTicks);
        h.score += 1.0;
        if (subject != null && !subject.isBlank()) {
            h.subjects.merge(subject, 1.0, Double::sum);
            if (h.subjects.size() > 12) {
                String low = h.subjects.entrySet().stream().min(Map.Entry.comparingByValue()).get().getKey();
                h.subjects.remove(low);
            }
        }
        h.x = x;
        h.y = y;
        h.z = z;
        h.dimension = dim == null ? "" : dim;
    }

    static void decay(Habit h, long tick, double halfLifeTicks) {
        if (h.lastTick > 0 && tick > h.lastTick && halfLifeTicks > 0) {
            double f = Math.pow(0.5, (tick - h.lastTick) / halfLifeTicks);
            h.score *= f;
            h.subjects.replaceAll((k, v) -> v * f);
        }
        h.lastTick = tick;
    }

    public double score(String activity, long tick, double halfLifeTicks) {
        Habit h = habits.get(activity);
        if (h == null) return 0;
        if (h.lastTick <= 0 || tick <= h.lastTick || halfLifeTicks <= 0) return h.score;
        return h.score * Math.pow(0.5, (tick - h.lastTick) / halfLifeTicks);
    }

    /** Activities scoring at least {@code min}, best first, with the most common subject. */
    public List<Top> top(long tick, double halfLifeTicks, double min, int limit) {
        List<Top> out = new ArrayList<>();
        for (var e : habits.entrySet()) {
            double s = score(e.getKey(), tick, halfLifeTicks);
            if (s < min) continue;
            String subj = e.getValue().subjects.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
            out.add(new Top(e.getKey(), s, subj));
        }
        out.sort(Comparator.comparingDouble(Top::score).reversed());
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** True when the activity is a habit worth helping with without being asked. */
    public boolean isHabit(String activity, long tick, double halfLifeTicks, double threshold) {
        return score(activity, tick, halfLifeTicks) >= threshold;
    }
}
