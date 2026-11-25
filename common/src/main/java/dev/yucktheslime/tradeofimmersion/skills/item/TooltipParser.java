package dev.yucktheslime.tradeofimmersion.skills.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an item's tooltip lines into the actions they describe ("Right-click to summon lightning",
 * "Sneak + right click a block to mark it", "Press [V] to dash", "Hold use to charge"). Pure logic, unit tested.
 */
public final class TooltipParser {
    private TooltipParser() {
    }

    public enum Trigger {
        /** Use the item in the air. */
        USE,
        USE_ON_BLOCK,
        USE_ON_ENTITY,
        /** Hold use (charge, channel) then let go. */
        HOLD_USE,
        /** Left click / attack with it. */
        ATTACK,
        /** A key binding (from another mod) does it. */
        KEYBIND
    }

    /** One thing the tooltip says the item does. {@code key} is the key label for KEYBIND (e.g. "V", "key.mymod.dash"). */
    public record Hint(Trigger trigger, boolean sneak, String key, String effect, String line) {
        public String describe() {
            String how = switch (trigger) {
                case USE -> "right-click";
                case USE_ON_BLOCK -> "right-click a block";
                case USE_ON_ENTITY -> "right-click a creature";
                case HOLD_USE -> "hold right-click";
                case ATTACK -> "left-click/attack";
                case KEYBIND -> "press key " + key;
            };
            return (sneak && trigger != Trigger.KEYBIND ? "sneak + " : "") + how + (effect.isBlank() ? "" : ": " + effect);
        }
    }

    private static final String SNEAK = "(?:(?:shift|sneak|crouch)(?:ing)?\\s*(?:\\+|and|while|&|-)?\\s*)";
    private static final String RCLICK = "(?:right[- ]?click(?:ing)?|r[- ]?click|use(?:\\s+key)?|rmb)";
    private static final String LCLICK = "(?:left[- ]?click(?:ing)?|l[- ]?click|lmb|attack|hit)";
    private static final Pattern KEY_TOKEN = Pattern.compile("(?:\\[([^\\]]{1,24})\\]|<([^>]{1,24})>|\\b(key\\.[a-z0-9_.]+)\\b|\\bkey\\s+([A-Z0-9]{1,6})\\b)");
    private static final Pattern PRESS = Pattern.compile("\\b(?:press|tap|hit|push)(?:\\s+(?:the|your))?\\s+(?:\\[([^\\]]{1,24})\\]|<([^>]{1,24})>|(key\\.[a-z0-9_.]+)|([A-Z0-9]{1,6}|[a-z]{1,8}(?=\\s+key))(?:\\s+key)?|(?:the\\s+)?([a-z ]{2,24}?)\\s+key(?:bind(?:ing)?)?)(?:\\s+(?:key|button))?\\s*(?:to|for|:|-)\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOLD = Pattern.compile("\\bhold(?:ing)?\\s+(?:down\\s+)?" + RCLICK + "?\\s*(?:button|key)?\\s*(?:to|for|:|-)\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RIGHT = Pattern.compile("(" + SNEAK + ")?" + RCLICK + "(?:\\s+(?:on|at))?(?:\\s+(?:a|an|the|any))?(?:\\s+(block|blocks|ground|mob|mobs|entity|entities|creature|creatures|animal|animals|player|players|villager|target))?\\s*(?:while\\s+(?:sneaking|crouching|shifting))?\\s*(?:to|for|:|-|will|and)\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHILE_SNEAK = Pattern.compile("while\\s+(?:sneaking|crouching|shifting)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEFT = Pattern.compile("(" + SNEAK + ")?" + LCLICK + "(?:\\s+(?:a|an|the))?(?:\\s+(block|mob|entity|creature|enemy|target))?\\s*(?:to|for|:|-)\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHEN_USED = Pattern.compile("^(?:when\\s+used|on\\s+use|use)\\s*[:,-]?\\s*(.+)", Pattern.CASE_INSENSITIVE);

    public static List<Hint> parse(List<String> lines) {
        Map<String, Hint> out = new LinkedHashMap<>();
        if (lines == null) return new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) continue;
            String line = clean(raw);
            if (line.length() < 4 || line.length() > 200) continue;
            Hint h = parseLine(line);
            if (h != null) out.putIfAbsent(h.trigger() + "|" + h.sneak() + "|" + h.key().toLowerCase(Locale.ROOT), h);
        }
        return new ArrayList<>(out.values());
    }

    static String clean(String s) {
        return s.replaceAll("\u00a7.", "").replaceAll("\\s+", " ").trim();
    }

    static Hint parseLine(String line) {
        Matcher m = PRESS.matcher(line);
        if (m.find()) {
            String key = firstNonNull(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5));
            if (key != null && !isMouseWord(key)) {
                return new Hint(Trigger.KEYBIND, false, key.trim(), effect(m.group(6)), line);
            }
        }
        m = HOLD.matcher(line);
        if (m.find()) return new Hint(Trigger.HOLD_USE, WHILE_SNEAK.matcher(line).find() || line.toLowerCase(Locale.ROOT).matches(".*\\b(shift|sneak)\\b.*hold.*"), "", effect(m.group(1)), line);
        m = RIGHT.matcher(line);
        if (m.find()) {
            boolean sneak = m.group(1) != null || WHILE_SNEAK.matcher(line).find();
            String target = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
            Trigger t = target.isEmpty() ? Trigger.USE : target.startsWith("block") || target.equals("ground") ? Trigger.USE_ON_BLOCK : Trigger.USE_ON_ENTITY;
            return new Hint(t, sneak, "", effect(m.group(3)), line);
        }
        m = LEFT.matcher(line);
        if (m.find()) return new Hint(Trigger.ATTACK, m.group(1) != null, "", effect(m.group(3)), line);
        m = WHEN_USED.matcher(line);
        if (m.find()) return new Hint(Trigger.USE, false, "", effect(m.group(1)), line);
        // a lone key token with an ability verb: "[V] Dash forward"
        Matcher k = KEY_TOKEN.matcher(line);
        if (k.find() && k.start() == 0) {
            String key = firstNonNull(k.group(1), k.group(2), k.group(3), k.group(4));
            String rest = line.substring(k.end()).replaceFirst("^\\s*[:-]?\\s*", "");
            if (key != null && !rest.isBlank() && !isMouseWord(key)) return new Hint(Trigger.KEYBIND, false, key.trim(), effect(rest), line);
        }
        return null;
    }

    private static boolean isMouseWord(String key) {
        String k = key.toLowerCase(Locale.ROOT).trim();
        return k.matches("(right|left)[- ]?click|use|attack|rmb|lmb|shift|sneak|use key|the use|the attack|jump|ctrl");
    }

    private static String effect(String s) {
        if (s == null) return "";
        String e = s.trim().replaceAll("[.!]+$", "");
        return e.length() > 80 ? e.substring(0, 80) : e;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... xs) {
        for (T x : xs) if (x != null) return x;
        return null;
    }
}
