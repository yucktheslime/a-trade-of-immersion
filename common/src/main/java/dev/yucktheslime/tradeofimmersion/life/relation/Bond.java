package dev.yucktheslime.tradeofimmersion.life.relation;

/**
 * How one person feels about another (directional: A's bond to B can differ from B's to A). Plain data, saved as
 * JSON by {@link RelationshipStore}. Times are overworld game ticks.
 */
public final class Bond {
    /** -100 (hatred) .. 100 (devotion). */
    public double affection;
    /** 0 .. 100. Rises when the other person flirts with or gifts someone else; fades with time. */
    public double jealousy;
    /** Last time the two interacted (chat, gift, time together); drift starts after a grace period. */
    public long lastInteraction;
    /** Last time drift/decay was applied. */
    public long lastUpdate;
    /** Interactions in the current day, for diminishing returns on spam. */
    public int todayCount;
    public long todayStart;
    /** Who made this person jealous last (display only). */
    public String jealousOf = "";

    public Bond() {
    }

    public Bond(double affection) {
        this.affection = affection;
    }

    public Bond copy() {
        Bond b = new Bond(affection);
        b.jealousy = jealousy;
        b.lastInteraction = lastInteraction;
        b.lastUpdate = lastUpdate;
        b.todayCount = todayCount;
        b.todayStart = todayStart;
        b.jealousOf = jealousOf;
        return b;
    }
}
