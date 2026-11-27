package dev.yucktheslime.tradeofimmersion.body.physics;

import dev.yucktheslime.tradeofimmersion.body.physics.JiggleSpring.Input;
import dev.yucktheslime.tradeofimmersion.body.physics.JiggleSpring.Tuning;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class JiggleSpringTest {
    private static final Tuning[] TUNINGS = {Tuning.BUST, Tuning.SEAT};

    /** Runs a jump: up 0.42 b/t, gravity, landing; then standing still. */
    private static long jumpAndLand(JiggleSpring s, long tick, float size, float intensity) {
        s.step(tick++, Input.REST, size, intensity);
        float vy = 0.42f;
        float height = 0.0f;
        do {
            s.step(tick++, new Input(vy, 0, 0, 0, false), size, intensity);
            height += vy;
            vy = (vy - 0.08f) * 0.98f;
        } while (height + vy > 0.0f);
        s.step(tick++, new Input(-height, 0, 0, 0, false), size, intensity); // touch down
        return tick;
    }

    @Test
    void landingBouncesThenSettles() {
        for (Tuning t : TUNINGS) {
            JiggleSpring s = new JiggleSpring(t, 0.0f);
            long tick = jumpAndLand(s, 0, 1.0f, 1.0f);
            float peak = 0.0f;
            for (int i = 0; i < 6; i++) {
                s.step(tick++, Input.REST, 1.0f, 1.0f);
                peak = Math.max(peak, Math.abs(s.y(1.0f)));
            }
            assertTrue(peak > 0.05f, "landing should visibly bounce, peak=" + peak);
            for (int i = 0; i < 60; i++) {
                s.step(tick++, Input.REST, 1.0f, 1.0f);
            }
            assertEquals(0.0f, s.y(1.0f), 1e-3f, "settles vertically");
            assertEquals(0.0f, s.x(1.0f), 1e-3f, "settles sideways");
            assertEquals(0.0f, s.velocityY(), 1e-3f);
        }
    }

    @Test
    void turnSwaySettles() {
        for (Tuning t : TUNINGS) {
            JiggleSpring s = new JiggleSpring(t, 0.0f);
            long tick = 0;
            for (int i = 0; i < 10; i++) {
                s.step(tick++, new Input(0, 0, 0, 20.0f, false), 1.0f, 1.0f);
            }
            assertTrue(s.x(1.0f) < -0.05f, "lags opposite to the turn");
            for (int i = 0; i < 60; i++) {
                s.step(tick++, Input.REST, 1.0f, 1.0f);
            }
            assertEquals(0.0f, s.x(1.0f), 1e-3f);
        }
    }

    @Test
    void boundedUnderWildInput() {
        Random r = new Random(42);
        for (Tuning t : TUNINGS) {
            JiggleSpring s = new JiggleSpring(t, 0.3f);
            for (long tick = 0; tick < 20_000; tick++) {
                Input in = new Input((r.nextFloat() - 0.5f) * 8.0f, r.nextFloat() * 5.0f,
                        r.nextFloat() * 1000.0f, (r.nextFloat() - 0.5f) * 720.0f, r.nextBoolean());
                s.step(tick, in, r.nextFloat() * 3.0f, r.nextFloat() * 5.0f);
                for (float pt : new float[] {0.0f, 0.5f, 1.0f}) {
                    assertTrue(Math.abs(s.y(pt)) <= t.maxY() + 1e-6f, "y bounded");
                    assertTrue(Math.abs(s.x(pt)) <= t.maxX() + 1e-6f, "x bounded");
                    assertTrue(Float.isFinite(s.y(pt)) && Float.isFinite(s.x(pt)));
                }
                assertTrue(Math.abs(s.velocityY()) <= t.maxVel() + 1e-6f);
            }
        }
    }

    @Test
    void nonFiniteInputIsIgnored() {
        JiggleSpring s = new JiggleSpring(Tuning.BUST, 0.0f);
        s.step(0, Input.REST, 1, 1);
        s.step(1, new Input(Float.NaN, Float.POSITIVE_INFINITY, 0, 0, false), 1, 1);
        assertEquals(0.0f, s.y(1.0f));
        assertEquals(0.0f, s.x(1.0f));
    }

    @Test
    void zeroIntensityOrSizeMeansNoMotion() {
        for (Tuning t : TUNINGS) {
            for (float[] sizeIntensity : new float[][] {{1.0f, 0.0f}, {0.0f, 1.0f}}) {
                JiggleSpring s = new JiggleSpring(t, 0.0f);
                long tick = jumpAndLand(s, 0, sizeIntensity[0], sizeIntensity[1]);
                for (int i = 0; i < 40; i++) {
                    s.step(tick++, new Input(0.3f, 1.3f, i * 0.8f, 30.0f, false), sizeIntensity[0], sizeIntensity[1]);
                    assertEquals(0.0f, s.y(0.5f), "no vertical motion");
                    assertEquals(0.0f, s.x(0.5f), "no sideways motion");
                }
            }
        }
    }

    @Test
    void intensityScalesMotion() {
        float low = peakAfterLanding(0.5f), mid = peakAfterLanding(1.0f), high = peakAfterLanding(2.0f);
        assertTrue(low < mid && mid < high, low + " < " + mid + " < " + high);
    }

    @Test
    void repeatedFramesInOneTickDoNotAdvance() {
        JiggleSpring s = new JiggleSpring(Tuning.SEAT, 0.0f);
        long tick = jumpAndLand(s, 0, 1, 1);
        s.step(tick, Input.REST, 1, 1);
        float y = s.y(1.0f);
        for (int i = 0; i < 10; i++) {
            s.step(tick, new Input(1.0f, 1, 0, 40, false), 1, 1);
        }
        assertEquals(y, s.y(1.0f));
    }

    @Test
    void partialTickInterpolates() {
        JiggleSpring s = new JiggleSpring(Tuning.BUST, 0.0f);
        long tick = jumpAndLand(s, 0, 1, 1);
        s.step(tick++, Input.REST, 1, 1);
        s.step(tick, Input.REST, 1, 1);
        float a = s.y(0.0f), b = s.y(1.0f), m = s.y(0.5f);
        assertEquals((a + b) / 2.0f, m, 1e-6f);
    }

    @Test
    void longGapResetsToRest() {
        JiggleSpring s = new JiggleSpring(Tuning.BUST, 0.0f);
        long tick = jumpAndLand(s, 0, 1, 1);
        s.step(tick + 100, new Input(0.5f, 0, 0, 0, false), 1, 1);
        assertEquals(0.0f, s.y(1.0f));
    }

    private static float peakAfterLanding(float intensity) {
        JiggleSpring s = new JiggleSpring(Tuning.BUST, 0.0f);
        long tick = jumpAndLand(s, 0, 0.5f, intensity);
        float peak = 0.0f;
        for (int i = 0; i < 10; i++) {
            s.step(tick++, Input.REST, 0.5f, intensity);
            peak = Math.max(peak, Math.abs(s.y(1.0f)));
        }
        return peak;
    }
}
