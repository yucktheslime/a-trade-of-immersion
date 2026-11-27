package dev.yucktheslime.tradeofimmersion.body.physics;

import dev.yucktheslime.tradeofimmersion.body.physics.JiggleSpring.Input;
import dev.yucktheslime.tradeofimmersion.body.physics.JiggleSpring.Tuning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Keeps TOI's own physics alive: the toi bust jiggle and the seat jiggle for every bust model. */
class PhysicsRulesTest {
    private static final String[] STORED = {"toi", "mcabp", null};

    /** A walk, a jump and a landing; returns the largest |y| the spring reached. */
    private static float peakOverWalkAndJump(Tuning tuning, float size, float intensity) {
        JiggleSpring s = new JiggleSpring(tuning, 0.0f);
        float peak = 0.0f;
        long tick = 0;
        for (int i = 0; i < 30; i++) {
            s.step(tick++, new Input(0.0f, 1.0f, i * 0.8f, 0.0f, false), size, intensity);
            peak = Math.max(peak, Math.abs(s.y(1.0f)));
        }
        float vy = 0.42f, height = 0.0f;
        do {
            s.step(tick++, new Input(vy, 0, 0, 0, false), size, intensity);
            height += vy;
            vy = (vy - 0.08f) * 0.98f;
        } while (height + vy > 0.0f);
        s.step(tick++, new Input(-height, 0, 0, 0, false), size, intensity);
        for (int i = 0; i < 12; i++) {
            s.step(tick++, Input.REST, size, intensity);
            peak = Math.max(peak, Math.abs(s.y(1.0f)));
        }
        return peak;
    }

    @Test
    void toiModelKeepsTheToiBustJiggle() {
        for (boolean mcabpInstalled : new boolean[] {false, true}) {
            for (String model : new String[] {"toi", null}) {
                assertTrue(PhysicsRules.toiDrivesBust(model, mcabpInstalled, null, false),
                        "model=" + model + " mcabp=" + mcabpInstalled);
            }
        }
        assertTrue(PhysicsRules.bustJiggles(true, 1.0f, 1.0f));
        float peak = peakOverWalkAndJump(Tuning.BUST, PhysicsRules.bustSize(1.0f), 1.0f);
        assertTrue(peak > 0.05f, "the TOI bust spring visibly moves with default config, peak=" + peak);
    }

    @Test
    void mcabpModelHasNoTOIBustButMcabpOwnsIt() {
        assertFalse(PhysicsRules.toiDrivesBust("mcabp", true, null, false));
        // mcabp absent, or its rendering off, falls back to the TOI bust and its jiggle.
        assertTrue(PhysicsRules.toiDrivesBust("mcabp", false, null, false));
        assertTrue(PhysicsRules.toiDrivesBust("mcabp", true, Boolean.FALSE, false));
        // Players always use the TOI bust.
        assertTrue(PhysicsRules.toiDrivesBust("mcabp", true, null, true));
    }

    @Test
    void seatJigglesForEveryBustModel() {
        for (String model : STORED) {
            for (boolean mcabpInstalled : new boolean[] {false, true}) {
                // The seat decision takes no bust model at all, so none can switch it off.
                assertTrue(PhysicsRules.seatJiggles(true, 1.0f, 1.0f),
                        "model=" + model + " mcabp=" + mcabpInstalled);
            }
        }
        float peak = peakOverWalkAndJump(Tuning.SEAT, PhysicsRules.seatSize(1.0f), 1.0f);
        assertTrue(peak > 0.05f, "the seat spring visibly moves with default config, peak=" + peak);
    }

    @Test
    void configStillSwitchesEachPartOff() {
        assertFalse(PhysicsRules.bustJiggles(false, 1.0f, 1.0f));
        assertFalse(PhysicsRules.bustJiggles(true, 0.0f, 1.0f));
        assertFalse(PhysicsRules.bustJiggles(true, 1.0f, 0.0f));
        assertFalse(PhysicsRules.seatJiggles(false, 1.0f, 1.0f));
        assertFalse(PhysicsRules.seatJiggles(true, 0.0f, 1.0f));
        assertFalse(PhysicsRules.seatJiggles(true, 1.0f, 0.0f));
    }

    @Test
    void sizeSharesMatchTheSliderCaps() {
        assertEquals(1.0f, PhysicsRules.bustSize(2.0f), 1e-6f);
        assertEquals(1.0f, PhysicsRules.bustSize(9.0f), 1e-6f);
        assertEquals(0.0f, PhysicsRules.bustSize(-1.0f));
        assertEquals(1.0f, PhysicsRules.seatSize(3.0f), 1e-6f);
        assertEquals(0.0f, PhysicsRules.seatSize(Float.NaN));
        assertEquals((float) Math.sqrt(0.5), PhysicsRules.bustSize(1.0f), 1e-6f);
    }
}
