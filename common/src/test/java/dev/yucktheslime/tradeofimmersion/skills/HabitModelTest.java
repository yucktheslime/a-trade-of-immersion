package dev.yucktheslime.tradeofimmersion.skills;

import dev.yucktheslime.tradeofimmersion.skills.learn.HabitModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HabitModelTest {
    @Test
    void scoresAccumulateAndDecay() {
        HabitModel m = new HabitModel();
        double hl = 1000;
        for (int i = 0; i < 10; i++) m.observe("farm", "minecraft:wheat", 100 + i, hl, 0, 64, 0, "overworld");
        assertEquals(10, m.score("farm", 109, hl), 0.1);
        assertEquals(5, m.score("farm", 1109, hl), 0.2);
        assertTrue(m.isHabit("farm", 109, hl, 8));
        assertFalse(m.isHabit("farm", 5109, hl, 8));
    }

    @Test
    void topOrdersAndNamesSubject() {
        HabitModel m = new HabitModel();
        for (int i = 0; i < 6; i++) m.observe("mine", "minecraft:stone", 10, 1e9, 0, 0, 0, "");
        for (int i = 0; i < 2; i++) m.observe("mine", "minecraft:iron_ore", 10, 1e9, 0, 0, 0, "");
        for (int i = 0; i < 3; i++) m.observe("chop", "minecraft:oak_log", 10, 1e9, 0, 0, 0, "");
        var top = m.top(10, 1e9, 1, 5);
        assertEquals("mine", top.get(0).activity());
        assertEquals("minecraft:stone", top.get(0).subject());
        assertEquals("chop", top.get(1).activity());
        assertEquals(1, m.top(10, 1e9, 5, 5).size());
    }
}
