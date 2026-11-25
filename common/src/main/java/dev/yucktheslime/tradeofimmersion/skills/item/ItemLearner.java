package dev.yucktheslime.tradeofimmersion.skills.item;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.platform.FakePlayers;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads an item's tooltip and learns the ways to use it. The server builds the tooltip itself (with the companion's
 * fake player as viewer; mods whose tooltip code is client-only throw or add nothing there) and, when the owner's
 * client has the mod, also asks that client for the full tooltip and merges it in when it arrives.
 */
public final class ItemLearner {
    private ItemLearner() {
    }

    public static String id(ItemStack s) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    /** Server-side tooltip lines without the name line. Never throws. */
    public static List<String> serverTooltip(CompanionEntity c, ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (!(c.level() instanceof ServerLevel level)) return out;
        ServerPlayer fp = null;
        try {
            fp = FakePlayers.get(level, c.getUUID(), c.name());
            fp.setItemInHand(InteractionHand.MAIN_HAND, stack);
            List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(level), fp, TooltipFlag.NORMAL);
            for (int i = 1; i < lines.size() && out.size() < SkillNet.MAX_LINES; i++) {
                String s = lines.get(i).getString();
                if (!s.isBlank()) out.add(SkillNet.cut(s));
            }
        } catch (Throwable t) {
            // client-only tooltip code (NoClassDefFoundError on a dedicated server) or a mod bug
        } finally {
            if (fp != null) fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        return out;
    }

    /** Learns {@code stack}: returns a summary for the AI. */
    public static String learn(CompanionEntity c, ItemStack stack) {
        String id = id(stack);
        ItemSkillStore.Skill skill = ItemSkillStore.get().of(id);
        List<String> lines = serverTooltip(c, stack);
        mergeTooltip(id, lines, "server");
        boolean asked = false;
        if (c.getOwner() instanceof ServerPlayer owner && SkillsConfig.get().tooltipFromClient) asked = SkillNet.requestTooltip(owner, stack);
        return summary(stack.getHoverName().getString(), skill) + (asked ? " (asked for the full tooltip too)" : "");
    }

    /** Adds tooltip lines (from the server or a client) and re-parses the hints. */
    public static void mergeTooltip(String itemId, List<String> lines, String source) {
        ItemSkillStore.Skill skill = ItemSkillStore.get().of(itemId);
        Set<String> all = new LinkedHashSet<>(skill.tooltip);
        for (String l : lines) if (l != null && !l.isBlank() && all.size() < SkillNet.MAX_LINES * 2) all.add(SkillNet.cut(l));
        skill.tooltip = new ArrayList<>(all);
        if (!lines.isEmpty() && ("client".equals(source) || skill.tooltipSource.isEmpty())) skill.tooltipSource = source;
        List<TooltipParser.Hint> parsed = TooltipParser.parse(skill.tooltip);
        // keep hints learned by observation that the tooltip doesn't mention
        List<ItemSkillStore.HintData> keep = new ArrayList<>();
        for (ItemSkillStore.HintData h : skill.hints) if (h.line != null && h.line.startsWith("(observed)")) keep.add(h);
        skill.hints = new ArrayList<>();
        for (TooltipParser.Hint h : parsed) skill.hints.add(ItemSkillStore.HintData.of(h));
        for (ItemSkillStore.HintData h : keep) {
            boolean dup = skill.hints.stream().anyMatch(x -> x.trigger.equals(h.trigger) && x.key.equalsIgnoreCase(h.key));
            if (!dup) skill.hints.add(h);
        }
        ItemSkillStore.get().markDirty();
    }

    /** A key seen pressed with the item (and a packet that followed) becomes a usable hint even without a tooltip line. */
    public static void addObservedKeyHint(String itemId, String bound, String label) {
        ItemSkillStore.Skill skill = ItemSkillStore.get().of(itemId);
        for (ItemSkillStore.HintData h : skill.hints) {
            if ("KEYBIND".equals(h.trigger) && (h.key.equalsIgnoreCase(bound) || h.key.equalsIgnoreCase(label))) return;
        }
        ItemSkillStore.HintData d = new ItemSkillStore.HintData();
        d.trigger = "KEYBIND";
        d.key = bound;
        d.effect = label;
        d.line = "(observed) " + label + " [" + bound + "]";
        skill.hints.add(d);
    }

    public static List<TooltipParser.Hint> hints(ItemSkillStore.Skill s) {
        List<TooltipParser.Hint> out = new ArrayList<>();
        if (s != null) for (ItemSkillStore.HintData h : s.hints) out.add(h.toHint());
        return out;
    }

    public static String summary(String name, ItemSkillStore.Skill s) {
        List<TooltipParser.Hint> hs = hints(s);
        if (hs.isEmpty()) return name + ": the tooltip says nothing about how to use it (plain use_item may still work)";
        StringBuilder b = new StringBuilder(name).append(" can be used: ");
        int n = 0;
        for (TooltipParser.Hint h : hs) {
            if (n++ > 0) b.append("; ");
            b.append(h.describe());
            ItemPlanStatus st = status(s, ItemActionPlanner.of(h));
            if (st != ItemPlanStatus.OK) b.append(" [").append(st.text).append("]");
        }
        return b.toString();
    }

    enum ItemPlanStatus {
        OK(""), NOT_LEARNED("key packet not learned yet: press it once while holding the item"), UNUSABLE("doesn't work for me");
        final String text;

        ItemPlanStatus(String t) {
            text = t;
        }
    }

    static ItemPlanStatus status(ItemSkillStore.Skill s, ItemActionPlanner.Step step) {
        if (s.unusable.contains(step.id())) return ItemPlanStatus.UNUSABLE;
        if (step.trigger() == TooltipParser.Trigger.KEYBIND && ItemSkillRunner.packetsFor(s, step.key()).isEmpty() && KeybindAdapters.find("", step.key()) == null)
            return ItemPlanStatus.NOT_LEARNED;
        return ItemPlanStatus.OK;
    }

    /** Short "how to use" note for a prompt, or empty. */
    public static String brief(ItemStack stack) {
        ItemSkillStore.Skill s = ItemSkillStore.get().get(id(stack));
        if (s == null || s.hints.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (TooltipParser.Hint h : hints(s)) {
            ItemActionPlanner.Step st = ItemActionPlanner.of(h);
            if (s.unusable.contains(st.id())) continue;
            parts.add(h.describe().toLowerCase(Locale.ROOT));
            if (parts.size() >= 3) break;
        }
        return String.join("; ", parts);
    }
}
