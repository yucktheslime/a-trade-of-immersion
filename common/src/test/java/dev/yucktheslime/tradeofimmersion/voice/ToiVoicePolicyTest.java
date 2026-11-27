package dev.yucktheslime.tradeofimmersion.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToiVoicePolicyTest {

    @Test
    void scriptedLinesFollowTheNewToggle() {
        assertTrue(ToiVoicePolicy.shouldVoiceScriptedMcaLine(true, true, true));
        assertFalse(ToiVoicePolicy.shouldVoiceScriptedMcaLine(false, true, true));
        assertFalse(ToiVoicePolicy.shouldVoiceScriptedMcaLine(true, false, true));
        assertFalse(ToiVoicePolicy.shouldVoiceScriptedMcaLine(true, true, false));
    }

    @Test
    void suppressMcaTtsOnlyWhenWeWillPlayAnImminentClip() {
        assertTrue(ToiVoicePolicy.shouldSuppressMcaTts(true, true, true));
        assertFalse(ToiVoicePolicy.shouldSuppressMcaTts(false, true, true));
        assertFalse(ToiVoicePolicy.shouldSuppressMcaTts(true, false, true));
        assertFalse(ToiVoicePolicy.shouldSuppressMcaTts(true, true, false));
    }

    @Test
    void languageWarningIsQuietWhenToiIsHandlingLines() {
        assertTrue(ToiVoicePolicy.shouldSuppressLanguageWarning(true, true, false));
        assertTrue(ToiVoicePolicy.shouldSuppressLanguageWarning(true, false, true));
        assertFalse(ToiVoicePolicy.shouldSuppressLanguageWarning(false, true, true));
        assertFalse(ToiVoicePolicy.shouldSuppressLanguageWarning(true, false, false));
    }
}
