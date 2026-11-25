package dev.yucktheslime.tradeofimmersion.skills;

import dev.yucktheslime.tradeofimmersion.skills.item.ItemActionPlanner;
import dev.yucktheslime.tradeofimmersion.skills.item.TooltipParser;
import dev.yucktheslime.tradeofimmersion.skills.item.TooltipParser.Hint;
import dev.yucktheslime.tradeofimmersion.skills.item.TooltipParser.Trigger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TooltipParserTest {
    static Hint one(String line) {
        List<Hint> h = TooltipParser.parse(List.of(line));
        assertEquals(1, h.size(), "hints for: " + line + " -> " + h);
        return h.get(0);
    }

    @Test
    void rightClickVariants() {
        Hint h = one("Right-click to summon lightning");
        assertEquals(Trigger.USE, h.trigger());
        assertFalse(h.sneak());
        assertEquals("summon lightning", h.effect());
        assertEquals(Trigger.USE, one("Right Click: Heal nearby allies.").trigger());
        assertEquals(Trigger.USE, one("When used: grants Speed II").trigger());
        assertEquals(Trigger.USE_ON_BLOCK, one("Right-click a block to mark it").trigger());
        assertEquals(Trigger.USE_ON_ENTITY, one("Right click on a mob to capture it").trigger());
        assertEquals(Trigger.USE_ON_ENTITY, one("Use on an animal to tame it").trigger());
    }

    @Test
    void sneak() {
        Hint h = one("Shift + Right-click to change mode");
        assertTrue(h.sneak());
        assertEquals(Trigger.USE, h.trigger());
        Hint h2 = one("Right-click while sneaking to open the menu");
        assertTrue(h2.sneak());
        assertTrue(one("Sneak-right-click a block to bind").sneak());
    }

    @Test
    void holdAndAttack() {
        assertEquals(Trigger.HOLD_USE, one("Hold right-click to charge a fireball").trigger());
        assertEquals(Trigger.HOLD_USE, one("Hold use to channel").trigger());
        assertEquals(Trigger.ATTACK, one("Left-click to swing a wave").trigger());
    }

    @Test
    void keybinds() {
        Hint h = one("Press [V] to dash forward");
        assertEquals(Trigger.KEYBIND, h.trigger());
        assertEquals("V", h.key());
        assertEquals("dash forward", h.effect());
        assertEquals("R", one("Press R to reload").key());
        assertEquals("key.mymod.ability", one("Press key.mymod.ability to blink").key());
        assertEquals("G", one("Push the G key to toggle flight").key());
        assertEquals("ability", one("Press the ability key to cast").key());
        assertEquals("Z", one("[Z] Ground slam").key());
        assertEquals("Left Alt", one("Press <Left Alt> to activate").key());
    }

    @Test
    void ignoresPlainLines() {
        assertTrue(TooltipParser.parse(List.of("Attack Damage: 7", "When in Main Hand:", "+7 Attack Damage", "Rare", "minecraft:diamond_sword")).isEmpty());
        assertTrue(TooltipParser.parse(List.of("\u00a77A shiny gem")).isEmpty());
    }

    @Test
    void multipleLinesKeepOrderAndDedupe() {
        List<Hint> hs = TooltipParser.parse(List.of("Right-click to throw", "Shift + right-click to recall", "Right-click to throw again", "Press [V] to dash"));
        assertEquals(3, hs.size());
        assertEquals(Trigger.USE, hs.get(0).trigger());
        assertTrue(hs.get(1).sneak());
        assertEquals(Trigger.KEYBIND, hs.get(2).trigger());
    }

    @Test
    void planner() {
        List<Hint> hs = TooltipParser.parse(List.of("Right-click to throw", "Shift + right-click to recall", "Right-click a block to plant", "Press [V] to dash"));
        var auto = ItemActionPlanner.plan(hs, "", false, false, Set.of());
        assertEquals(Trigger.USE, auto.get(0).trigger());
        assertEquals(Trigger.KEYBIND, auto.get(auto.size() - 1).trigger());
        var onBlock = ItemActionPlanner.plan(hs, "auto", false, true, Set.of());
        assertEquals(Trigger.USE_ON_BLOCK, onBlock.get(0).trigger());
        var sneak = ItemActionPlanner.plan(hs, "sneak right click", false, false, Set.of());
        assertTrue(sneak.get(0).sneak());
        var key = ItemActionPlanner.plan(hs, "key:v", false, false, Set.of());
        assertEquals("V", key.get(0).key());
        var byEffect = ItemActionPlanner.plan(hs, "dash", false, false, Set.of());
        assertEquals(Trigger.KEYBIND, byEffect.get(0).trigger());
        // unusable steps are skipped
        String keyId = key.get(0).id();
        assertTrue(ItemActionPlanner.plan(hs, "dash", false, false, Set.of(keyId)).stream().noneMatch(s -> s.id().equals(keyId)));
        // nothing learned: plain use as a fallback, unless that failed too
        assertEquals(1, ItemActionPlanner.plan(List.of(), "", false, false, Set.of()).size());
        assertTrue(ItemActionPlanner.plan(List.of(), "", false, false, Set.of("use")).isEmpty());
        assertTrue(ItemActionPlanner.giveUp(3, 3));
        assertFalse(ItemActionPlanner.giveUp(2, 3));
    }
}
