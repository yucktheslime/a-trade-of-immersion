package dev.yucktheslime.tradeofimmersion.life.pregnancy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pregnancy as a state machine over game time: {@code phases} phases (trimesters by default) of
 * {@code ticksPerPhase} each, then birth. Miscarriage is rolled once per in-game day (seeded per pregnancy and day,
 * so the result doesn't depend on tick rate or on how often {@link #advance} is called). Pure (no Minecraft).
 */
public final class PregnancyPhases {
    public static final long DAY = 24000L;

    public enum Event { PHASE_CHANGED, BIRTH, MISCARRIAGE }

    /** Side effects a phase can cause (the server maps them to potion effects, mood and dialogue). */
    public enum Effect { NAUSEA, HUNGER, SLOWNESS, FATIGUE, MOOD_SWINGS, CRAVINGS, BACK_PAIN }

    public static final class Params {
        public int phases = 3;
        public long ticksPerPhase = 3 * DAY;
        public boolean miscarriage = true;
        /** Chance per in-game day, by phase (1-based; the last value repeats). */
        public double[] miscarriagePerDay = {0.02, 0.006, 0.002};
    }

    /** One pregnancy's saved state. */
    public static final class State {
        public long start;
        public long ticksPerPhase;
        public int phases;
        public long seed;
        /** Last phase reported (0 = none yet). */
        public int phaseSeen;
        /** Last in-game day index rolled for miscarriage. */
        public long dayRolled = -1;
        public boolean ended;
        public String outcome = "";

        public State() {
        }

        public State(long start, Params p, long seed) {
            this.start = start;
            this.ticksPerPhase = Math.max(1, p.ticksPerPhase);
            this.phases = Math.max(1, p.phases);
            this.seed = seed;
        }

        public long total() {
            return ticksPerPhase * phases;
        }
    }

    public record Step(Event event, int phase) {
    }

    private PregnancyPhases() {
    }

    /** 0 .. 1 over the whole pregnancy. */
    public static double progress(State s, long now) {
        if (now <= s.start) return 0;
        return Math.min(1.0, (now - s.start) / (double) s.total());
    }

    /** 1-based phase at {@code now} (never above {@code phases}). */
    public static int phase(State s, long now) {
        long el = Math.max(0, now - s.start);
        return (int) Math.min(s.phases, el / s.ticksPerPhase + 1);
    }

    public static long ticksLeft(State s, long now) {
        return Math.max(0, s.start + s.total() - now);
    }

    /**
     * Brings the state up to {@code now} and returns what happened, in order: PHASE_CHANGED for each new phase
     * (including phase 1 the first time), then BIRTH or MISCARRIAGE (which end it).
     */
    public static List<Step> advance(State s, long now, Params p) {
        List<Step> out = new ArrayList<>();
        if (s.ended) return out;
        long el = Math.max(0, now - s.start);
        // miscarriage rolls for each whole day passed, in the phase that day belonged to
        if (p.miscarriage) {
            long lastDay = Math.min(el, s.total() - 1) / DAY;
            for (long d = s.dayRolled + 1; d <= lastDay && el >= (d + 1) * DAY; d++) {
                s.dayRolled = d;
                int ph = (int) Math.min(s.phases, d * DAY / s.ticksPerPhase + 1);
                while (s.phaseSeen < ph) out.add(new Step(Event.PHASE_CHANGED, ++s.phaseSeen));
                double chance = p.miscarriagePerDay.length == 0 ? 0 : p.miscarriagePerDay[Math.min(ph, p.miscarriagePerDay.length) - 1];
                if (new Random(s.seed * 31 + d).nextDouble() < chance) {
                    s.ended = true;
                    s.outcome = "miscarriage";
                    out.add(new Step(Event.MISCARRIAGE, ph));
                    return out;
                }
            }
        }
        if (el >= s.total()) {
            while (s.phaseSeen < s.phases) out.add(new Step(Event.PHASE_CHANGED, ++s.phaseSeen));
            s.ended = true;
            s.outcome = "birth";
            out.add(new Step(Event.BIRTH, s.phases));
            return out;
        }
        int ph = phase(s, now);
        while (s.phaseSeen < ph) out.add(new Step(Event.PHASE_CHANGED, ++s.phaseSeen));
        return out;
    }

    /** Belly size 0..1: barely there in the first third, then growing faster (eased). */
    public static double belly(double progress) {
        double x = Math.max(0, Math.min(1, (progress - 0.15) / 0.85));
        return x * x * (3 - 2 * x);
    }

    /**
     * Side effects for this phase. {@code roll} is a fresh random 0..1 per check, so nausea comes and goes.
     * Phase numbers are scaled to three trimesters when there are more or fewer phases.
     */
    public static Set<Effect> effects(int phase, int phases, double roll) {
        int tri = phases <= 1 ? 3 : (int) Math.ceil(phase * 3.0 / phases);
        Set<Effect> e = EnumSet.noneOf(Effect.class);
        switch (tri) {
            case 1 -> {
                e.add(Effect.HUNGER);
                if (roll < 0.25) e.add(Effect.NAUSEA); // morning sickness
                if (roll > 0.6) e.add(Effect.MOOD_SWINGS);
            }
            case 2 -> {
                e.add(Effect.CRAVINGS);
                e.add(Effect.HUNGER);
                if (roll < 0.5) e.add(Effect.MOOD_SWINGS);
            }
            default -> {
                e.add(Effect.SLOWNESS);
                e.add(Effect.FATIGUE);
                e.add(Effect.BACK_PAIN);
                if (roll < 0.4) e.add(Effect.MOOD_SWINGS);
            }
        }
        return e;
    }

    /** Slowness amplifier by trimester (0 = level I), or -1 for none. */
    public static int slownessLevel(int phase, int phases) {
        int tri = phases <= 1 ? 3 : (int) Math.ceil(phase * 3.0 / phases);
        return tri >= 3 ? 1 : tri == 2 ? 0 : -1;
    }

    public static String phaseName(int phase, int phases) {
        if (phases == 3) return switch (phase) {
            case 1 -> "first trimester";
            case 2 -> "second trimester";
            default -> "third trimester";
        };
        return "phase " + phase + " of " + phases;
    }

    private static final String[] MOODS = {"weepy", "irritable", "giddy", "anxious", "affectionate", "snappish", "sentimental"};

    /** A mood-swing word for dialogue, changing every in-game quarter day. */
    public static String moodSwing(long seed, long now) {
        return MOODS[(int) Math.floorMod(new Random(seed ^ (now / 6000L) * 0x9E3779B97F4A7C15L).nextInt(), MOODS.length)];
    }
}
