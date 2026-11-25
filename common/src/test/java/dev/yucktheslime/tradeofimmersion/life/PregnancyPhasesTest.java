package dev.yucktheslime.tradeofimmersion.life;

import dev.yucktheslime.tradeofimmersion.life.pregnancy.PregnancyPhases;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.PregnancyPhases.Event;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.PregnancyPhases.Step;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.yucktheslime.tradeofimmersion.life.pregnancy.PregnancyPhases.DAY;
import static org.junit.jupiter.api.Assertions.*;

class PregnancyPhasesTest {
    PregnancyPhases.Params noMiscarriage() {
        PregnancyPhases.Params p = new PregnancyPhases.Params();
        p.miscarriage = false;
        return p;
    }

    @Test
    void walksThroughPhasesThenBirth() {
        PregnancyPhases.Params p = noMiscarriage();
        PregnancyPhases.State s = new PregnancyPhases.State(1000, p, 42);
        assertEquals(9 * DAY, s.total(), "3 phases x 3 days by default");
        List<Step> st = PregnancyPhases.advance(s, 1000, p);
        assertEquals(List.of(new Step(Event.PHASE_CHANGED, 1)), st);
        assertTrue(PregnancyPhases.advance(s, 1000 + DAY, p).isEmpty(), "still phase 1");
        assertEquals(List.of(new Step(Event.PHASE_CHANGED, 2)), PregnancyPhases.advance(s, 1000 + 3 * DAY, p));
        assertEquals(2, PregnancyPhases.phase(s, 1000 + 5 * DAY));
        assertEquals(List.of(new Step(Event.PHASE_CHANGED, 3)), PregnancyPhases.advance(s, 1000 + 6 * DAY + 1, p));
        assertFalse(s.ended);
        assertEquals(List.of(new Step(Event.BIRTH, 3)), PregnancyPhases.advance(s, 1000 + 9 * DAY, p));
        assertTrue(s.ended);
        assertEquals("birth", s.outcome);
        assertTrue(PregnancyPhases.advance(s, 1000 + 20 * DAY, p).isEmpty(), "ended stays ended");
    }

    @Test
    void catchesUpAfterALongGap() {
        PregnancyPhases.Params p = noMiscarriage();
        PregnancyPhases.State s = new PregnancyPhases.State(0, p, 1);
        List<Step> st = PregnancyPhases.advance(s, 100 * DAY, p);
        assertEquals(List.of(new Step(Event.PHASE_CHANGED, 1), new Step(Event.PHASE_CHANGED, 2), new Step(Event.PHASE_CHANGED, 3),
                new Step(Event.BIRTH, 3)), st);
    }

    @Test
    void configurableLength() {
        PregnancyPhases.Params p = noMiscarriage();
        p.phases = 4;
        p.ticksPerPhase = DAY / 2;
        PregnancyPhases.State s = new PregnancyPhases.State(0, p, 1);
        assertEquals(2 * DAY, s.total());
        assertEquals(4, PregnancyPhases.phase(s, 2 * DAY - 1));
        assertEquals("phase 2 of 4", PregnancyPhases.phaseName(2, 4));
    }

    @Test
    void miscarriageToggleAndDeterminism() {
        PregnancyPhases.Params always = new PregnancyPhases.Params();
        always.miscarriagePerDay = new double[]{1.0};
        PregnancyPhases.State s = new PregnancyPhases.State(0, always, 7);
        List<Step> st = PregnancyPhases.advance(s, DAY, always);
        assertEquals(Event.MISCARRIAGE, st.get(st.size() - 1).event());
        assertEquals("miscarriage", s.outcome);

        PregnancyPhases.Params off = new PregnancyPhases.Params();
        off.miscarriagePerDay = new double[]{1.0};
        off.miscarriage = false;
        PregnancyPhases.State s2 = new PregnancyPhases.State(0, off, 7);
        assertEquals(Event.BIRTH, last(PregnancyPhases.advance(s2, 9 * DAY, off)), "toggle off = always a birth");

        // same seed gives the same outcome whether advanced daily or all at once
        PregnancyPhases.Params half = new PregnancyPhases.Params();
        half.miscarriagePerDay = new double[]{0.3};
        for (long seed = 0; seed < 40; seed++) {
            PregnancyPhases.State a = new PregnancyPhases.State(0, half, seed), b = new PregnancyPhases.State(0, half, seed);
            Event ea = null;
            for (long t = 0; t <= 9 * DAY && !a.ended; t += 1000) {
                List<Step> x = PregnancyPhases.advance(a, t, half);
                if (!x.isEmpty()) ea = last(x);
            }
            Event eb = last(PregnancyPhases.advance(b, 9 * DAY, half));
            assertEquals(eb, ea, "seed " + seed);
            assertEquals(a.outcome, b.outcome);
        }
    }

    @Test
    void bellyGrowsSlowlyAndMonotonically() {
        assertEquals(0, PregnancyPhases.belly(0), 1e-9);
        assertEquals(0, PregnancyPhases.belly(0.1), 1e-9, "nothing shows early");
        assertEquals(1, PregnancyPhases.belly(1), 1e-9);
        double prev = 0;
        for (double x = 0; x <= 1; x += 0.05) {
            double b = PregnancyPhases.belly(x);
            assertTrue(b >= prev - 1e-12);
            prev = b;
        }
        assertTrue(PregnancyPhases.belly(0.33) < 0.2, "small at the end of the first trimester");
    }

    @Test
    void sideEffectsByTrimester() {
        assertTrue(PregnancyPhases.effects(1, 3, 0.1).contains(PregnancyPhases.Effect.NAUSEA));
        assertFalse(PregnancyPhases.effects(1, 3, 0.9).contains(PregnancyPhases.Effect.NAUSEA));
        assertTrue(PregnancyPhases.effects(3, 3, 0.9).contains(PregnancyPhases.Effect.SLOWNESS));
        assertEquals(-1, PregnancyPhases.slownessLevel(1, 3));
        assertEquals(1, PregnancyPhases.slownessLevel(3, 3));
        assertEquals(1, PregnancyPhases.slownessLevel(6, 6));
        assertNotNull(PregnancyPhases.moodSwing(5, 12345));
    }

    static Event last(List<Step> s) {
        return s.isEmpty() ? null : s.get(s.size() - 1).event();
    }
}
