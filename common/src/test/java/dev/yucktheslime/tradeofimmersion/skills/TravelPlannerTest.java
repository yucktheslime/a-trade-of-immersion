package dev.yucktheslime.tradeofimmersion.skills;

import dev.yucktheslime.tradeofimmersion.skills.travel.TravelPlanner;
import dev.yucktheslime.tradeofimmersion.skills.travel.TravelPlanner.Mode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TravelPlannerTest {
    @Test
    void distanceTrigger() {
        assertEquals(Mode.WALK, TravelPlanner.plan(100, 150, true, false, false));
        assertEquals(Mode.WALK, TravelPlanner.plan(150, 150, true, false, false));
        assertEquals(Mode.SEEK_VEHICLE, TravelPlanner.plan(151, 150, true, false, false));
        assertEquals(Mode.SEEK_VEHICLE, TravelPlanner.plan(2000, 150, true, false, false));
    }

    @Test
    void switchesAndRiding() {
        assertEquals(Mode.WALK, TravelPlanner.plan(500, 150, false, false, false));
        assertEquals(Mode.WALK, TravelPlanner.plan(500, 0, true, false, false));
        assertEquals(Mode.KEEP_RIDING, TravelPlanner.plan(10, 150, true, true, true));
        assertEquals(Mode.KEEP_RIDING, TravelPlanner.plan(500, 150, false, true, true));
        // on something it can't steer: look for a better ride on long trips
        assertEquals(Mode.SEEK_VEHICLE, TravelPlanner.plan(500, 150, true, true, false));
    }

    @Test
    void detourAndScore() {
        assertTrue(TravelPlanner.worthDetour(300, 30));
        assertFalse(TravelPlanner.worthDetour(300, 120));
        assertTrue(TravelPlanner.score(0, 30) < TravelPlanner.score(1, 2));
        assertTrue(TravelPlanner.score(1, 5) < TravelPlanner.score(1, 20));
    }
}
