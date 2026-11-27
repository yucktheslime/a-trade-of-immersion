package dev.yucktheslime.tradeofimmersion.body;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BustModelRulesTest {
    @Test
    void parsing() {
        assertEquals("toi", BustModelRules.parseModel(" TOI "));
        assertEquals("mcabp", BustModelRules.parseModel("McaBp"));
        assertNull(BustModelRules.parseModel("random"));
        assertNull(BustModelRules.parseModel(null));
        assertEquals("random", BustModelRules.parseMode("bogus"));
        assertEquals("mcabp", BustModelRules.parseMode("MCABP"));
    }

    @Test
    void forcedModeAlwaysWins() {
        for (double r : new double[] {0.0, 0.49, 0.5, 0.99}) {
            assertEquals("toi", BustModelRules.roll("toi", 1.0, r));
            assertEquals("mcabp", BustModelRules.roll("mcabp", 0.0, r));
        }
        assertEquals("toi", BustModelRules.resolveStored("mcabp", "toi", 0.5, 0.0));
        assertEquals("mcabp", BustModelRules.resolveStored("toi", "mcabp", 0.5, 0.99));
    }

    @Test
    void randomRollFollowsTheChance() {
        assertEquals("mcabp", BustModelRules.roll("random", 0.5, 0.49));
        assertEquals("toi", BustModelRules.roll("random", 0.5, 0.50));
        assertEquals("toi", BustModelRules.roll("random", 0.0, 0.0));
        assertEquals("mcabp", BustModelRules.roll("random", 1.0, 0.999));
        assertEquals("mcabp", BustModelRules.roll("random", 5.0, 0.999), "chance is clamped to 0..1");
        assertEquals("toi", BustModelRules.roll("random", -1.0, 0.0));
        assertEquals("mcabp", BustModelRules.roll("random", Double.NaN, 0.1), "bad chance falls back to 0.5");
    }

    @Test
    void randomRollIsRoughlyTheConfiguredShare() {
        Random r = new Random(7);
        int mcabp = 0;
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            if (BustModelRules.roll("random", 0.3, r.nextDouble()).equals("mcabp")) {
                mcabp++;
            }
        }
        assertEquals(0.3, mcabp / (double) n, 0.02);
    }

    @Test
    void storedChoiceIsKeptInRandomMode() {
        assertEquals("toi", BustModelRules.resolveStored("toi", "random", 1.0, 0.0), "never re-rolled");
        assertEquals("mcabp", BustModelRules.resolveStored("mcabp", "random", 0.0, 0.99));
        assertEquals("mcabp", BustModelRules.resolveStored(null, "random", 1.0, 0.0), "first sight rolls");
        assertEquals("toi", BustModelRules.resolveStored("junk", "random", 0.0, 0.0), "invalid value rolls again");
    }

    @Test
    void rendersMcabpOnlyWhenEverythingAllowsIt() {
        assertTrue(BustModelRules.rendersMcabp("mcabp", true, null, false), "unreadable toggle counts as on");
        assertTrue(BustModelRules.rendersMcabp("mcabp", true, Boolean.TRUE, false));
        assertFalse(BustModelRules.rendersMcabp("mcabp", false, null, false), "no mcabp installed: toi");
        assertFalse(BustModelRules.rendersMcabp("mcabp", true, Boolean.FALSE, false), "mcabp rendering off: toi");
        assertFalse(BustModelRules.rendersMcabp("mcabp", true, null, true), "players stay on toi");
        assertFalse(BustModelRules.rendersMcabp("toi", true, null, false));
        assertFalse(BustModelRules.rendersMcabp(null, true, null, false), "unrolled is toi");
    }

    @Test
    void geneFollowsTheModel() {
        assertEquals(0.0f, BustModelRules.gene("toi", 2.0f), "toi keeps the gene at 0: no double bust");
        assertEquals(0.0f, BustModelRules.gene(null, 1.0f));
        assertEquals(0.5f, BustModelRules.gene("mcabp", 1.0f), 1e-6f);
        assertEquals(1.0f, BustModelRules.gene("mcabp", 2.0f), 1e-6f);
        assertEquals(1.0f, BustModelRules.gene("mcabp", 9.0f), "clamped to 1");
        assertEquals(0.0f, BustModelRules.gene("mcabp", -3.0f), "clamped to 0");
        assertEquals(0.0f, BustModelRules.gene("mcabp", Float.NaN));
    }
}
