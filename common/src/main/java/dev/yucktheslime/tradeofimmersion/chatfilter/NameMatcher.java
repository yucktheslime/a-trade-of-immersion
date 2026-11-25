package dev.yucktheslime.tradeofimmersion.chatfilter;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whole-word, accent-insensitive name matching (same idea as MCA's ChatAI) and group vocatives
 * at the start of a line ({@code everyone}, {@code team}, ...).
 */
public final class NameMatcher {
    public static final int MIN_TOKEN = 2;

    private NameMatcher() {
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    /** True if {@code text} mentions this NPC by a whole-word name token or alias (length at least 2). */
    public static boolean mentions(String text, String name, List<String> aliases) {
        String n = normalize(text);
        if (n.isEmpty()) return false;
        if (mentionsName(n, name)) return true;
        if (aliases != null) {
            for (String a : aliases) {
                if (mentionsName(n, a)) return true;
            }
        }
        return false;
    }

    private static boolean mentionsName(String normalizedText, String name) {
        String nn = normalize(name);
        if (nn.length() < MIN_TOKEN) return false;
        if (containsWhole(normalizedText, nn)) return true;
        for (String part : nn.split("[\\s\\-']+")) {
            if (part.length() >= MIN_TOKEN && containsWhole(normalizedText, part)) return true;
        }
        return false;
    }

    static boolean containsWhole(String text, String word) {
        if (word == null || word.isEmpty()) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    /**
     * True when the line starts with a group vocative (optionally after a leading interjection like
     * punctuation). {@code "everyone follow me"} matches; {@code "tell everyone hello"} and
     * {@code "all right"} do not (bare {@code all} is not a default vocative).
     */
    public static boolean isGroupAddress(String text, Collection<String> words) {
        if (words == null || words.isEmpty()) return false;
        String n = normalize(text);
        if (n.isEmpty()) return false;
        int i = 0;
        while (i < n.length() && !Character.isLetterOrDigit(n.charAt(i))) i++;
        String rest = n.substring(i);
        for (String w : words) {
            String ww = normalize(w);
            if (ww.length() < MIN_TOKEN) continue;
            if (rest.equals(ww)) return true;
            if (rest.startsWith(ww) && rest.length() > ww.length()) {
                char next = rest.charAt(ww.length());
                if (!Character.isLetterOrDigit(next)) return true;
            }
        }
        return false;
    }
}
