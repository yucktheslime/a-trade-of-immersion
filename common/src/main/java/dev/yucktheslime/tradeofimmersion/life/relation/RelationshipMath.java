package dev.yucktheslime.tradeofimmersion.life.relation;

/**
 * The relationship layer's math: affection from interactions (with diminishing returns), jealousy, drift from
 * neglect, and the thresholds that drive dating, proposals and divorce. Pure (no Minecraft), unit tested.
 */
public final class RelationshipMath {
    public static final long DAY = 24000L;

    /** Tunables (filled from {@code LifeConfig}; defaults here so tests need no config). */
    public static final class Params {
        public double chat = 1.0, gift = 4.0, flirt = 3.0, help = 2.0, hurt = -15.0, timeTogetherPerDay = 2.0;
        /** Interactions per day after which gains are halved, then quartered... */
        public int dailySoftCap = 6;
        /** Days without contact before drift starts. */
        public double driftGraceDays = 2.0;
        /** Affection lost per day of neglect (toward the baseline). */
        public double driftPerDay = 3.0;
        /** Spouses drift toward this instead of 0 (they keep some love). */
        public double partnerBaseline = 25.0;
        public double jealousyGain = 20.0, jealousyDecayPerDay = 10.0;
        /** Affection lost per day while jealousy is above 50. */
        public double jealousyBitterPerDay = 2.0;
        public double datingAt = 40.0, proposeAt = 70.0, marryAt = 80.0, divorceBelow = -20.0, breakUpBelow = 5.0;
    }

    public enum Kind { CHAT, GIFT, FLIRT, HELP, HURT, TIME_TOGETHER }

    private RelationshipMath() {
    }

    public static double clampAffection(double a) {
        return Math.max(-100, Math.min(100, a));
    }

    /** Diminishing returns: full gain for the first dailySoftCap interactions of a day, then 1/2, 1/4, ... */
    static double diminish(Bond b, long now, int cap) {
        if (now - b.todayStart >= DAY || now < b.todayStart) {
            b.todayStart = now;
            b.todayCount = 0;
        }
        cap = Math.max(1, cap);
        int over = b.todayCount < cap ? 0 : 1 + (b.todayCount - cap) / cap;
        b.todayCount++;
        return Math.pow(0.5, over);
    }

    /**
     * Applies one interaction to {@code b} (the receiver's feelings). {@code weight} scales the base gain (e.g. gift
     * value 0.25..3). Losses (hurt) are never diminished. Returns the change in affection.
     */
    public static double interact(Bond b, Kind kind, double weight, long now, Params p) {
        decay(b, now, false, p);
        double base = switch (kind) {
            case CHAT -> p.chat;
            case GIFT -> p.gift;
            case FLIRT -> b.affection < 10 ? p.flirt * 0.3 : p.flirt; // flirting with a near-stranger lands softly
            case HELP -> p.help;
            case HURT -> p.hurt;
            case TIME_TOGETHER -> p.timeTogetherPerDay;
        };
        double delta = base * weight;
        if (delta > 0) {
            delta *= diminish(b, now, p.dailySoftCap);
            // the closer you already are, the slower it climbs
            delta *= 1.0 - Math.max(0, b.affection) / 150.0;
        }
        double before = b.affection;
        b.affection = clampAffection(b.affection + delta);
        if (kind != Kind.HURT) b.lastInteraction = now;
        return b.affection - before;
    }

    /**
     * Someone this person cares about flirted with / gifted / married a rival. Jealousy grows with how much they
     * care and how committed they are. Returns the new jealousy.
     */
    public static double makeJealous(Bond b, Stage stage, double intensity, long now, Params p) {
        decay(b, now, stage.romantic(), p);
        if (b.affection < 20 && !stage.romantic()) return b.jealousy;
        double care = Math.max(0, b.affection) / 100.0;
        double commit = switch (stage) {
            case NONE -> 0.5;
            case DATING -> 1.0;
            case ENGAGED -> 1.3;
            case MARRIED -> 1.6;
        };
        b.jealousy = Math.min(100, b.jealousy + p.jealousyGain * intensity * care * commit);
        return b.jealousy;
    }

    /**
     * Time passing: after the grace period affection drifts toward the baseline (0, or partnerBaseline for a
     * romantic partner); jealousy fades; strong jealousy sours affection. Idempotent for the same {@code now}.
     */
    public static void decay(Bond b, long now, boolean partner, Params p) {
        if (b.lastUpdate == 0) {
            b.lastUpdate = now;
            if (b.lastInteraction == 0) b.lastInteraction = now;
            return;
        }
        if (now <= b.lastUpdate) return;
        double days = (now - b.lastUpdate) / (double) DAY;
        b.lastUpdate = now;
        // jealousy first: time spent jealous sours affection
        if (b.jealousy > 50) {
            double hotDays = Math.min(days, (b.jealousy - 50) / Math.max(0.001, p.jealousyDecayPerDay));
            b.affection = clampAffection(b.affection - p.jealousyBitterPerDay * hotDays);
        }
        b.jealousy = Math.max(0, b.jealousy - p.jealousyDecayPerDay * days);
        if (b.jealousy == 0) b.jealousOf = "";
        double neglected = (now - b.lastInteraction) / (double) DAY - p.driftGraceDays;
        if (neglected <= 0) return;
        double driftDays = Math.min(days, neglected);
        double baseline = partner ? p.partnerBaseline : 0;
        double step = p.driftPerDay * driftDays;
        if (b.affection > baseline) b.affection = Math.max(baseline, b.affection - step);
        else if (b.affection < baseline && b.affection < 0) b.affection = Math.min(baseline, b.affection + step * 0.5); // grudges fade slower
    }

    /** Mutual feelings for a pair (the lower side counts: both have to want it). */
    public static double mutual(Bond a, Bond b) {
        return Math.min(a == null ? 0 : a.affection, b == null ? 0 : b.affection);
    }

    /** The stage a pair can move to next on its own (autonomous NPC courtship), or {@code current}. */
    public static Stage advance(Stage current, double mutual, double maxJealousy, Params p) {
        if (maxJealousy >= 70) return current;
        return switch (current) {
            case NONE -> mutual >= p.datingAt ? Stage.DATING : Stage.NONE;
            case DATING -> mutual >= p.proposeAt ? Stage.ENGAGED : Stage.DATING;
            case ENGAGED -> mutual >= p.marryAt ? Stage.MARRIED : Stage.ENGAGED;
            case MARRIED -> Stage.MARRIED;
        };
    }

    /** Whether a romance should end: dating/engaged break up below breakUpBelow; married divorce below divorceBelow. */
    public static boolean shouldEnd(Stage current, double mutual, double maxJealousy, Params p) {
        return switch (current) {
            case NONE -> false;
            case DATING, ENGAGED -> mutual < p.breakUpBelow || maxJealousy >= 95 && mutual < p.datingAt;
            case MARRIED -> mutual < p.divorceBelow || maxJealousy >= 95 && mutual < 0;
        };
    }

    /**
     * Chance (0..1) that someone accepts a proposal to move to {@code to} from a player, from their affection and
     * jealousy. Below the threshold it is a soft no; well above it a near-certain yes.
     */
    public static double acceptChance(Stage to, double affection, double jealousy, Params p) {
        double need = switch (to) {
            case NONE -> 0;
            case DATING -> p.datingAt;
            case ENGAGED -> p.proposeAt;
            case MARRIED -> p.marryAt - 10; // already engaged; the wedding is the easy part
        };
        double x = (affection - need) / 8.0 - Math.max(0, jealousy - 40) / 20.0;
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /** Words for prompts. */
    public static String feeling(double affection) {
        if (affection >= 85) return "deeply in love with";
        if (affection >= 65) return "very fond of";
        if (affection >= 40) return "fond of";
        if (affection >= 15) return "friendly with";
        if (affection > -15) return "neutral toward";
        if (affection > -50) return "annoyed with";
        return "bitter toward";
    }

    public static String jealousyWords(double jealousy) {
        if (jealousy >= 75) return "furiously jealous";
        if (jealousy >= 45) return "jealous";
        if (jealousy >= 15) return "a little jealous";
        return "";
    }
}
