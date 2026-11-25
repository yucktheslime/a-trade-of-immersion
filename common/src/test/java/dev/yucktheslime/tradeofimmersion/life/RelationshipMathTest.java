package dev.yucktheslime.tradeofimmersion.life;

import dev.yucktheslime.tradeofimmersion.life.relation.Bond;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipMath;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipMath.Kind;
import dev.yucktheslime.tradeofimmersion.life.relation.Stage;
import org.junit.jupiter.api.Test;

import static dev.yucktheslime.tradeofimmersion.life.relation.RelationshipMath.DAY;
import static org.junit.jupiter.api.Assertions.*;

class RelationshipMathTest {
    final RelationshipMath.Params p = new RelationshipMath.Params();

    @Test
    void interactionsRaiseAffectionWithDiminishingReturns() {
        Bond b = new Bond();
        long t = 1000;
        double first = RelationshipMath.interact(b, Kind.CHAT, 1, t, p);
        assertTrue(first > 0);
        for (int i = 0; i < p.dailySoftCap - 1; i++) RelationshipMath.interact(b, Kind.CHAT, 1, t + i, p);
        double spam = RelationshipMath.interact(b, Kind.CHAT, 1, t + 50, p);
        assertTrue(spam < first * 0.6, "spam is worth less: " + spam + " vs " + first);
        double nextDay = RelationshipMath.interact(b, Kind.CHAT, 1, t + DAY + 10, p);
        assertTrue(nextDay > spam, "a new day resets the cap");
    }

    @Test
    void giftsBeatChatAndHurtHurts() {
        Bond a = new Bond(), b = new Bond();
        RelationshipMath.interact(a, Kind.CHAT, 1, 10, p);
        RelationshipMath.interact(b, Kind.GIFT, 1, 10, p);
        assertTrue(b.affection > a.affection);
        RelationshipMath.interact(b, Kind.HURT, 1, 20, p);
        assertTrue(b.affection < 0);
        Bond c = new Bond(100);
        RelationshipMath.interact(c, Kind.GIFT, 10, 10, p);
        assertEquals(100, c.affection, 1e-9, "clamped");
    }

    @Test
    void neglectDriftsAfterGracePeriod() {
        Bond b = new Bond(60);
        RelationshipMath.decay(b, 100, false, p); // first call initialises
        b.lastInteraction = 100;
        RelationshipMath.decay(b, 100 + DAY, false, p);
        assertEquals(60, b.affection, 1e-9, "within grace");
        RelationshipMath.decay(b, 100 + 5 * DAY, false, p);
        assertEquals(60 - 3 * p.driftPerDay, b.affection, 1e-6, "3 days past grace");
        RelationshipMath.decay(b, 100 + 200 * DAY, false, p);
        assertEquals(0, b.affection, 1e-9, "never drifts below baseline");
        Bond spouse = new Bond(60);
        RelationshipMath.decay(spouse, 100, true, p);
        spouse.lastInteraction = 100;
        RelationshipMath.decay(spouse, 100 + 200 * DAY, true, p);
        assertEquals(p.partnerBaseline, spouse.affection, 1e-9, "spouses keep a baseline");
    }

    @Test
    void jealousyGrowsWithCommitmentAndFades() {
        Bond dating = new Bond(70), married = new Bond(70), stranger = new Bond(5);
        RelationshipMath.makeJealous(dating, Stage.DATING, 1, 10, p);
        RelationshipMath.makeJealous(married, Stage.MARRIED, 1, 10, p);
        RelationshipMath.makeJealous(stranger, Stage.NONE, 1, 10, p);
        assertTrue(married.jealousy > dating.jealousy);
        assertEquals(0, stranger.jealousy, 1e-9, "strangers don't get jealous");
        married.lastInteraction = 10;
        RelationshipMath.decay(married, 10 + 10 * DAY, true, p);
        assertEquals(0, married.jealousy, 1e-9);
    }

    @Test
    void strongJealousySoursAffection() {
        Bond b = new Bond(80);
        b.jealousy = 90;
        b.lastUpdate = 10;
        b.lastInteraction = 10;
        RelationshipMath.decay(b, 10 + DAY, true, p);
        assertTrue(b.affection < 80);
    }

    @Test
    void stagesAdvanceAndEnd() {
        assertEquals(Stage.DATING, RelationshipMath.advance(Stage.NONE, 45, 0, p));
        assertEquals(Stage.NONE, RelationshipMath.advance(Stage.NONE, 30, 0, p));
        assertEquals(Stage.ENGAGED, RelationshipMath.advance(Stage.DATING, 75, 0, p));
        assertEquals(Stage.DATING, RelationshipMath.advance(Stage.DATING, 75, 80, p), "too jealous to propose");
        assertEquals(Stage.MARRIED, RelationshipMath.advance(Stage.ENGAGED, 85, 0, p));
        assertTrue(RelationshipMath.shouldEnd(Stage.MARRIED, -30, 0, p));
        assertFalse(RelationshipMath.shouldEnd(Stage.MARRIED, 10, 0, p));
        assertTrue(RelationshipMath.shouldEnd(Stage.DATING, 2, 0, p));
        assertFalse(RelationshipMath.shouldEnd(Stage.NONE, -100, 100, p));
    }

    @Test
    void acceptChanceIsMonotonic() {
        double lo = RelationshipMath.acceptChance(Stage.ENGAGED, 40, 0, p);
        double mid = RelationshipMath.acceptChance(Stage.ENGAGED, 70, 0, p);
        double hi = RelationshipMath.acceptChance(Stage.ENGAGED, 95, 0, p);
        assertTrue(lo < 0.05 && Math.abs(mid - 0.5) < 1e-9 && hi > 0.95, lo + " " + mid + " " + hi);
        assertTrue(RelationshipMath.acceptChance(Stage.ENGAGED, 95, 90, p) < hi, "jealousy lowers it");
    }
}
