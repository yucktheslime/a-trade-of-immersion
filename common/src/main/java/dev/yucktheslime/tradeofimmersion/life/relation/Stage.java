package dev.yucktheslime.tradeofimmersion.life.relation;

/**
 * Courtship stage of a pair. Mapped onto MCA's own relationship states so MCA's family tree, family-tree screen and
 * marriage checks see it: DATING = MCA "promised", ENGAGED = "engaged", MARRIED = "married".
 */
public enum Stage {
    NONE, DATING, ENGAGED, MARRIED;

    public boolean romantic() {
        return this != NONE;
    }

    public Stage next() {
        return this == NONE ? DATING : this == DATING ? ENGAGED : MARRIED;
    }
}
