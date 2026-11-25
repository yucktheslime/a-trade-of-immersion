package dev.yucktheslime.tradeofimmersion.chatfilter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatFilterTest {
    @Test
    void acceptsSpokenLines() {
        assertTrue(ChatFilter.isDialogue("Hello"));
        assertTrue(ChatFilter.isDialogue("hi Steve, can you follow me?"));
        assertTrue(ChatFilter.isDialogue("  Yes.  "));
    }

    @Test
    void dropsCommandsAndNoise() {
        assertFalse(ChatFilter.isDialogue(null));
        assertFalse(ChatFilter.isDialogue(""));
        assertFalse(ChatFilter.isDialogue("   "));
        assertFalse(ChatFilter.isDialogue("/home"));
        assertFalse(ChatFilter.isDialogue("!roll"));
        assertFalse(ChatFilter.isDialogue(".shrug"));
        assertFalse(ChatFilter.isDialogue("#channel"));
        assertFalse(ChatFilter.isDialogue("$pay"));
        assertFalse(ChatFilter.isDialogue("[tag]"));
        assertFalse(ChatFilter.isDialogue("ERROR: boom"));
        assertFalse(ChatFilter.isDialogue("WARN: x"));
        assertFalse(ChatFilter.isDialogue("ERR_TIMEOUT"));
        assertFalse(ChatFilter.isDialogue("404 not found"));
        assertFalse(ChatFilter.isDialogue("Steve has made the advancement [Stone Age]"));
        assertFalse(ChatFilter.isDialogue("123"));
        assertFalse(ChatFilter.isDialogue("!!!"));
        assertFalse(ChatFilter.isDialogue("a".repeat(501)));
    }
}
