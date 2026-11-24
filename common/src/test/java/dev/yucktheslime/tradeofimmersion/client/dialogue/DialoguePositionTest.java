package dev.yucktheslime.tradeofimmersion.client.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialoguePositionTest {
    @Test
    void anchorsAndClamps() {
        assertArrayEquals(new int[]{4, 480 - 100 - 56}, DialogueHud.position("bottom_left", 640, 480, 260, 100, 4, 56));
        assertArrayEquals(new int[]{640 - 260 - 4, 480 - 100 - 56}, DialogueHud.position("bottom_right", 640, 480, 260, 100, 4, 56));
        assertArrayEquals(new int[]{(640 - 260) / 2, 12 + 10}, DialogueHud.position("top_center", 640, 480, 260, 100, 0, 10));
        // never off screen
        int[] p = DialogueHud.position("bottom_right", 200, 100, 260, 100, 4, 56);
        assertEquals(0, p[0]);
        assertTrue(p[1] >= 0);
        assertArrayEquals(DialogueHud.position("bottom_left", 640, 480, 260, 100, 4, 56), DialogueHud.position(null, 640, 480, 260, 100, 4, 56));
    }
}
