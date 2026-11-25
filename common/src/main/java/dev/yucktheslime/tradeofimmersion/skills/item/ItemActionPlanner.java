package dev.yucktheslime.tradeofimmersion.skills.item;

import dev.yucktheslime.tradeofimmersion.skills.item.TooltipParser.Hint;
import dev.yucktheslime.tradeofimmersion.skills.item.TooltipParser.Trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Picks which learned way of using an item to try, from the AI's "how" and target, skipping ways already marked
 * unusable. Pure logic, unit tested.
 */
public final class ItemActionPlanner {
    private ItemActionPlanner() {
    }

    /** One concrete attempt. */
    public record Step(Trigger trigger, boolean sneak, String key, String effect) {
        public String id() {
            return trigger.name().toLowerCase(Locale.ROOT) + (sneak ? "+sneak" : "") + (key.isBlank() ? "" : ":" + key.toLowerCase(Locale.ROOT));
        }
    }

    public static Step of(Hint h) {
        return new Step(h.trigger(), h.sneak(), h.key(), h.effect());
    }

    /**
     * @param how         AI wording: "right_click", "sneak", "block", "entity", "hold", "key", "key:V", a word from the effect
     *                    ("dash"), or blank/auto
     * @param hasEntity   a creature target was given
     * @param hasBlock    a block target was given
     * @param unusable    step ids that failed too often
     */
    public static List<Step> plan(List<Hint> hints, String how, boolean hasEntity, boolean hasBlock, Set<String> unusable) {
        List<Step> all = new ArrayList<>();
        for (Hint h : hints) {
            Step s = of(h);
            if (h.trigger() == Trigger.ATTACK) continue; // attacking is the "attack" action's job
            if (unusable != null && unusable.contains(s.id())) continue;
            all.add(s);
        }
        String w = how == null ? "" : how.toLowerCase(Locale.ROOT).trim().replace('-', '_');
        List<Step> out = new ArrayList<>();
        if (w.startsWith("key")) {
            String key = w.contains(":") ? w.substring(w.indexOf(':') + 1).trim() : "";
            for (Step s : all) if (s.trigger() == Trigger.KEYBIND && (key.isEmpty() || s.key().equalsIgnoreCase(key))) out.add(s);
            if (out.isEmpty() && !key.isEmpty()) out.add(new Step(Trigger.KEYBIND, false, key, ""));
            return out;
        }
        if (!w.isEmpty() && !w.equals("auto") && !w.equals("any")) {
            boolean sneak = w.contains("sneak") || w.contains("shift") || w.contains("crouch");
            Trigger want = w.contains("hold") || w.contains("charge") ? Trigger.HOLD_USE
                    : w.contains("block") ? Trigger.USE_ON_BLOCK
                    : w.contains("entity") || w.contains("mob") || w.contains("creature") ? Trigger.USE_ON_ENTITY
                    : w.contains("right") || w.contains("use") || w.contains("click") || sneak ? Trigger.USE : null;
            if (want != null) {
                for (Step s : all) if (s.trigger() == want && s.sneak() == sneak) out.add(s);
                if (out.isEmpty()) out.add(new Step(want, sneak, "", ""));
                return out;
            }
            // a word from the effect: "dash", "teleport", "summon lightning"
            for (Step s : all) if (s.effect().toLowerCase(Locale.ROOT).contains(w.replace('_', ' '))) out.add(s);
            if (!out.isEmpty()) return out;
        }
        // auto: prefer what fits the target, then plain use, then keybinds
        for (Step s : all) if (hasEntity && s.trigger() == Trigger.USE_ON_ENTITY) out.add(s);
        for (Step s : all) if (hasBlock && s.trigger() == Trigger.USE_ON_BLOCK) out.add(s);
        for (Step s : all) if (s.trigger() == Trigger.USE || s.trigger() == Trigger.HOLD_USE) if (!out.contains(s)) out.add(s);
        for (Step s : all) if (!out.contains(s) && s.trigger() != Trigger.KEYBIND) out.add(s);
        for (Step s : all) if (s.trigger() == Trigger.KEYBIND) out.add(s);
        if (out.isEmpty()) {
            Step fallback = new Step(hasEntity ? Trigger.USE_ON_ENTITY : hasBlock ? Trigger.USE_ON_BLOCK : Trigger.USE, false, "", "");
            if (unusable == null || !unusable.contains(fallback.id())) out.add(fallback);
        }
        return out;
    }

    /** After {@code max} failed attempts a step is marked unusable so the NPC stops retrying it. */
    public static boolean giveUp(int failures, int max) {
        return failures >= Math.max(1, max);
    }
}
