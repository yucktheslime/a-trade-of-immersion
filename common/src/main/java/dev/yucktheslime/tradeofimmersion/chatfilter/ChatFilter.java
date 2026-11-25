package dev.yucktheslime.tradeofimmersion.chatfilter;

import java.util.regex.Pattern;

/**
 * Shared line filter: only real spoken dialogue is allowed to start an AI turn.
 * Architectury's {@code ChatEvent.RECEIVED} already excludes system messages (command feedback,
 * advancements, death messages); this also drops commands, pasted error codes and similar noise
 * a player might type or relay.
 */
public final class ChatFilter {
    private static final Pattern NOISE = Pattern.compile(
            "^(?:[/!.#$]|\\[[^\\]]{1,40}]\\s*$)" // commands, bot prefixes, lone [tags]
                    + "|^(?i:error|err|exception|warn(?:ing)?)\\b[:\\s]"
                    + "|^[A-Z][A-Z0-9]*[_0-9][A-Z0-9_]*(?::\\s.*)?$" // ERROR_CODES like ERR_TIMEOUT
                    + "|^\\d{3}(?:\\s|$)" // HTTP-ish codes
                    + "|(?i:has (?:made the advancement|completed the challenge|reached the goal))");

    private ChatFilter() {
    }

    public static boolean isDialogue(String text) {
        if (text == null) return false;
        String t = text.strip();
        if (t.length() < 1 || t.length() > 500) return false;
        if (NOISE.matcher(t).find()) return false;
        return t.chars().anyMatch(Character::isLetter);
    }
}
