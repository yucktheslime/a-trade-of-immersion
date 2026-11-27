package dev.yucktheslime.tradeofimmersion.voice;

/**
 * Pure rules for when this mod should voice an MCA villager line and when MCA's own text-to-speech
 * should stay quiet. Kept free of Minecraft so the cases are unit-tested.
 */
public final class ToiVoicePolicy {
    private ToiVoicePolicy() {
    }

    /** MCA's own scripted villager lines (greetings, interaction-screen replies, etc.). */
    public static boolean shouldVoiceScriptedMcaLine(boolean voiceEnabled, boolean voiceVillagers, boolean mcaLinesUseToiVoice) {
        return voiceEnabled && voiceVillagers && mcaLinesUseToiVoice;
    }

    /**
     * Skip MCA's {@code SpeechManager} for this speaker: this client will play our voice, and the server
     * has already said a clip is on the way ({@code imminent}).
     */
    public static boolean shouldSuppressMcaTts(boolean voicePlay, boolean voiceVillagers, boolean imminent) {
        return voicePlay && voiceVillagers && imminent;
    }

    /**
     * Hide MCA's "Your game language is not supported by the text-to-speech" chat line while we are (or
     * locally expect to be) voicing villager lines.
     */
    public static boolean shouldSuppressLanguageWarning(boolean voicePlay, boolean recentToiVoice,
                                                        boolean localVoiceHandlesMcaLines) {
        return voicePlay && (recentToiVoice || localVoiceHandlesMcaLines);
    }
}
