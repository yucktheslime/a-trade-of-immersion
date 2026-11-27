package dev.yucktheslime.tradeofimmersion.body.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsGatingTest {
    @Test
    void toiDrivesBustWhenMcabpIsAbsent() {
        assertTrue(PhysicsGating.toiDrivesBust(true, false, null));
        assertTrue(PhysicsGating.toiDrivesBust(true, false, Boolean.FALSE));
        assertFalse(PhysicsGating.toiDrivesBust(false, false, null));
    }

    @Test
    void toiDrivesBustWhenMcabpIsLoaded() {
        assertTrue(PhysicsGating.toiDrivesBust(true, true, Boolean.TRUE),
                "TOI still jiggles its own bust mesh when mcabp is present");
        assertTrue(PhysicsGating.toiDrivesBust(true, true, null),
                "a failed or missing mcabp config read must not disable physics");
        assertFalse(PhysicsGating.toiDrivesBust(true, true, Boolean.FALSE),
                "an explicit mcabp master-off is honored");
        assertFalse(PhysicsGating.toiDrivesBust(false, true, Boolean.TRUE));
    }

    @Test
    void intensityUnchangedWithoutMcabp() {
        assertEquals(1.0f, PhysicsGating.bustIntensity(1.0f, false, 2.0f));
        assertEquals(0.5f, PhysicsGating.bustIntensity(0.5f, false, Float.NaN));
    }

    @Test
    void intensityScaledByMcabpBounce() {
        assertEquals(1.0f, PhysicsGating.bustIntensity(1.0f, true, 1.0f));
        assertEquals(2.0f, PhysicsGating.bustIntensity(1.0f, true, 2.0f), 1e-6f);
        assertEquals(0.5f, PhysicsGating.bustIntensity(1.0f, true, 0.5f), 1e-6f);
        assertEquals(1.0f, PhysicsGating.bustIntensity(1.0f, true, Float.NaN));
        assertEquals(1.0f, PhysicsGating.bustIntensity(1.0f, true, Float.POSITIVE_INFINITY));
    }

    @Test
    void bounceScaleMapsDefaultHalfToOne() {
        assertEquals(1.0f, PhysicsGating.bounceScale(0.5d), 1e-6f);
        assertEquals(2.0f, PhysicsGating.bounceScale(1.0d), 1e-6f);
        assertEquals(0.0f, PhysicsGating.bounceScale(0.0d), 1e-6f);
        assertEquals(1.0f, PhysicsGating.bounceScale(null));
        assertEquals(1.0f, PhysicsGating.bounceScale(Double.NaN));
        assertEquals(1.0f, PhysicsGating.bounceScale(Double.POSITIVE_INFINITY));
    }
}
