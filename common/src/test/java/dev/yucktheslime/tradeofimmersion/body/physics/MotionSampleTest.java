package dev.yucktheslime.tradeofimmersion.body.physics;

import dev.yucktheslime.tradeofimmersion.body.physics.JiggleSpring.Input;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MotionSampleTest {
    @Test
    void firstSampleUsesYo() {
        MotionSample s = new MotionSample();
        Input in = s.sample(10.4, 10.0, 0.0, 0, 0, 0, 0, false);
        assertEquals(0.4f, in.verticalSpeed(), 1e-6f);
    }

    @Test
    void firstSampleFallsBackToDeltaMovementWhenYoLooksStale() {
        MotionSample s = new MotionSample();
        Input in = s.sample(5.0, 5.0, -0.3, 0, 0, 0, 0, false);
        assertEquals(-0.3f, in.verticalSpeed(), 1e-6f);
    }

    @Test
    void laterSamplesUseOwnLastYEvenIfYoIsStale() {
        MotionSample s = new MotionSample();
        s.sample(2.0, 2.0, 0.0, 0, 0, 0, 0, false);
        // Interpolation already copied yo up to the current Y, but the entity did move.
        Input in = s.sample(2.25, 2.25, 0.0, 0, 0, 0, 0, false);
        assertEquals(0.25f, in.verticalSpeed(), 1e-6f);
    }

    @Test
    void resetDropsTheLastPoseSoAGapDoesNotBecomeAHugeImpulse() {
        MotionSample s = new MotionSample();
        s.sample(1.0, 1.0, 0, 0, 0, 0, 0, false);
        s.sample(1.1, 1.0, 0, 0, 0, 0, 0, false);
        s.reset();
        Input in = s.sample(40.0, 40.0, 0.0, 0, 0, 0, 0, false);
        assertEquals(0.0f, in.verticalSpeed(), 1e-6f);
    }

    @Test
    void yawUsesOwnLastValueAndWraps() {
        MotionSample s = new MotionSample();
        s.sample(0, 0, 0, 0, 0, 170.0f, 170.0f, false);
        Input in = s.sample(0, 0, 0, 0, 0, -170.0f, -170.0f, false);
        assertEquals(20.0f, in.yawDelta(), 1e-4f);
    }

    @Test
    void wrapDegreesStaysInRange() {
        assertEquals(-170.0f, MotionSample.wrapDegrees(190.0f), 1e-4f);
        assertEquals(170.0f, MotionSample.wrapDegrees(-190.0f), 1e-4f);
        assertEquals(20.0f, MotionSample.wrapDegrees(20.0f), 1e-4f);
        assertEquals(0.0f, MotionSample.wrapDegrees(360.0f), 1e-4f);
    }

    @Test
    void seatedFlagAndWalkArePassedThrough() {
        MotionSample s = new MotionSample();
        Input in = s.sample(1, 1, 0, 1.3f, 4.5f, 10, 10, true);
        assertEquals(1.3f, in.walkSpeed());
        assertEquals(4.5f, in.walkPosition());
        assertTrue(in.seated());
    }

    @Test
    void nonFiniteVerticalBecomesZero() {
        MotionSample s = new MotionSample();
        Input in = s.sample(Double.NaN, 0.0, Double.POSITIVE_INFINITY, 0, 0, 0, 0, false);
        assertEquals(0.0f, in.verticalSpeed());
        assertTrue(Float.isFinite(in.yawDelta()));
    }
}
