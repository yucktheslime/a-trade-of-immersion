package dev.yucktheslime.tradeofimmersion.skills.item;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import dev.yucktheslime.tradeofimmersion.skills.job.FakeHands;
import dev.yucktheslime.tradeofimmersion.skills.item.TooltipParser.Trigger;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Uses an item the way its tooltip says: right-click (in the air, on a block, on a creature, sneaking, held) through
 * the companion's fake player, or a mod key by replaying the packet learned for it (or a per-mod key adapter) with
 * the fake player as sender. Ways that keep failing are marked unusable so the companion stops trying them.
 */
public final class ItemSkillRunner {
    private ItemSkillRunner() {
    }

    public record Outcome(boolean ok, String text) {
    }

    public static List<ItemSkillStore.KeyPacket> packetsFor(ItemSkillStore.Skill s, String key) {
        List<ItemSkillStore.KeyPacket> out = new ArrayList<>();
        if (s == null || key == null) return out;
        String k = key.toLowerCase(Locale.ROOT).trim();
        for (var e : s.keyPackets.entrySet()) {
            for (ItemSkillStore.KeyPacket p : e.getValue()) {
                if (e.getKey().equals(k) || p.keyBound.equalsIgnoreCase(k) || p.keyLabel.equalsIgnoreCase(k) || p.keyMapping.equalsIgnoreCase(k)
                        || k.length() > 2 && p.keyLabel.toLowerCase(Locale.ROOT).contains(k)) out.add(p);
            }
        }
        return out;
    }

    /** Uses the companion's main-hand item. */
    public static Outcome use(CompanionEntity c, String how, @Nullable Entity entity, @Nullable Vec3 block) {
        ItemStack stack = c.getMainHandItem();
        if (stack.isEmpty()) return new Outcome(false, "nothing in hand");
        String id = ItemLearner.id(stack);
        String name = stack.getHoverName().getString();
        ItemSkillStore.Skill skill = ItemSkillStore.get().get(id);
        if (skill == null || skill.tooltip.isEmpty() && skill.hints.isEmpty()) {
            if (SkillsConfig.get().itemLearning) ItemLearner.learn(c, stack);
            skill = ItemSkillStore.get().of(id);
        }
        List<ItemActionPlanner.Step> steps = ItemActionPlanner.plan(ItemLearner.hints(skill), how, entity != null, block != null, skill.unusable);
        if (steps.isEmpty()) {
            return new Outcome(false, "I know no way of using " + name + " that works" + (skill.unusableWhy.isEmpty() ? "" : " (" + String.join("; ", skill.unusableWhy.values()) + ")"));
        }
        StringBuilder tried = new StringBuilder();
        for (int i = 0; i < Math.min(2, steps.size()); i++) {
            ItemActionPlanner.Step step = steps.get(i);
            Outcome o;
            try {
                o = run(c, skill, step, entity, block);
            } catch (Throwable t) {
                o = new Outcome(false, "error " + t.getClass().getSimpleName());
            }
            if (o == null) {
                // not learned yet (a key without a packet): not the item's fault, don't count it
                if (tried.length() > 0) tried.append("; ");
                tried.append(step.trigger() == Trigger.KEYBIND ? "key " + step.key() + ": don't know its packet yet (press it once while holding the item near me)" : step.id());
                continue;
            }
            if (o.ok()) {
                skill.failures.remove(step.id());
                skill.uses++;
                ItemSkillStore.get().markDirty();
                return new Outcome(true, describe(step) + " with " + name + ": " + o.text());
            }
            int f = skill.failures.merge(step.id(), 1, Integer::sum);
            if (ItemActionPlanner.giveUp(f, SkillsConfig.get().maxUseAttempts)) {
                skill.unusable.add(step.id());
                skill.unusableWhy.put(step.id(), describe(step) + ": " + o.text());
                TradeOfImmersion.LOGGER.info("[tradeofimmersion] {} marked {} unusable: {}", id, step.id(), o.text());
            }
            ItemSkillStore.get().markDirty();
            if (tried.length() > 0) tried.append("; ");
            tried.append(describe(step)).append(": ").append(o.text());
        }
        return new Outcome(false, "couldn't use " + name + " (" + tried + ")");
    }

    static String describe(ItemActionPlanner.Step s) {
        return switch (s.trigger()) {
            case USE -> s.sneak() ? "sneak-used" : "used";
            case USE_ON_BLOCK -> s.sneak() ? "sneak-used on a block" : "used on a block";
            case USE_ON_ENTITY -> s.sneak() ? "sneak-used on a creature" : "used on a creature";
            case HOLD_USE -> "held use";
            case ATTACK -> "swung";
            case KEYBIND -> "pressed " + s.key();
        };
    }

    /** @return null when the step can't even be tried yet (unlearned key) */
    @Nullable
    private static Outcome run(CompanionEntity c, ItemSkillStore.Skill skill, ItemActionPlanner.Step step, @Nullable Entity entity, @Nullable Vec3 block) throws Exception {
        ServerLevel level = (ServerLevel) c.level();
        ItemStack stack = c.getMainHandItem();
        ItemStack before = stack.copy();
        if (step.trigger() == Trigger.KEYBIND) return pressKey(c, skill, step, entity, block);
        Vec3 look = entity != null ? entity.getEyePosition() : block != null ? block : c.getEyePosition().add(c.getLookAngle().scale(3));
        InteractionResult r = FakeHands.with(c, stack, look, step.sneak(), fp -> {
            InteractionResult res = InteractionResult.PASS;
            if (step.trigger() == Trigger.USE_ON_ENTITY) {
                if (entity == null) return InteractionResult.FAIL;
                res = fp.interactOn(entity, InteractionHand.MAIN_HAND);
            } else if (step.trigger() == Trigger.USE_ON_BLOCK) {
                HitResult hit = block != null
                        ? level.clip(new ClipContext(c.getEyePosition(), block.add(block.subtract(c.getEyePosition()).normalize().scale(0.5)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, c))
                        : c.pick(5.0, 1.0f, false);
                if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return InteractionResult.FAIL;
                res = fp.gameMode.useItemOn(fp, level, fp.getMainHandItem(), InteractionHand.MAIN_HAND, bhr);
            } else {
                res = fp.gameMode.useItem(fp, level, fp.getMainHandItem(), InteractionHand.MAIN_HAND);
                if (fp.isUsingItem()) {
                    ItemStack using = fp.getUseItem();
                    int dur = using.getUseDuration(fp);
                    int hold = step.trigger() == Trigger.HOLD_USE ? Math.min(dur, 40) : Math.min(dur, 20);
                    for (int t = 0; t < hold && fp.isUsingItem(); t++) using.onUseTick(level, fp, dur - t);
                    if (fp.isUsingItem()) {
                        using.releaseUsing(level, fp, dur - hold);
                        fp.stopUsingItem();
                    }
                    if (!res.consumesAction()) res = InteractionResult.CONSUME;
                }
            }
            c.setItemSlot(EquipmentSlot.MAINHAND, fp.getMainHandItem());
            return res;
        });
        c.swing(InteractionHand.MAIN_HAND);
        boolean changed = !ItemStack.isSameItemSameComponents(before, c.getMainHandItem()) || before.getCount() != c.getMainHandItem().getCount();
        if (r.consumesAction() || changed) return new Outcome(true, r.toString().toLowerCase(Locale.ROOT));
        return new Outcome(false, "nothing happened (" + r.toString().toLowerCase(Locale.ROOT) + ")");
    }

    @Nullable
    private static Outcome pressKey(CompanionEntity c, ItemSkillStore.Skill skill, ItemActionPlanner.Step step, @Nullable Entity entity, @Nullable Vec3 block) throws Exception {
        String itemId = ItemLearner.id(c.getMainHandItem());
        Vec3 look = entity != null ? entity.getEyePosition() : block != null ? block : c.getEyePosition().add(c.getLookAngle().scale(3));
        KeybindAdapters.Adapter a = KeybindAdapters.find(itemId, step.key());
        if (a != null) {
            String r = FakeHands.with(c, c.getMainHandItem(), look, false, fp -> {
                try {
                    String res = a.press(c, fp, fp.getMainHandItem(), step.key());
                    c.setItemSlot(EquipmentSlot.MAINHAND, fp.getMainHandItem());
                    return res;
                } catch (Exception e) {
                    return null;
                }
            });
            return r == null ? new Outcome(false, "the " + a.id() + " key adapter did nothing") : new Outcome(true, r);
        }
        if (!SkillsConfig.get().keybindReplay) return new Outcome(false, "key replay is turned off");
        List<ItemSkillStore.KeyPacket> packets = packetsFor(skill, step.key());
        if (packets.isEmpty()) return null;
        PayloadBridge bridge = PayloadBridge.Holder.get();
        if (bridge == null) return new Outcome(false, "this loader can't replay key packets");
        ServerLevel level = (ServerLevel) c.level();
        ItemSkillStore.KeyPacket kp = packets.get(packets.size() - 1);
        ResourceLocation ch = ResourceLocation.tryParse(kp.channel);
        if (ch == null) return new Outcome(false, "bad channel");
        CustomPacketPayload payload = bridge.decode(ch, Base64.getDecoder().decode(kp.data), level.registryAccess());
        if (payload == null) return new Outcome(false, "the " + kp.channel + " packet can't be rebuilt (mod removed or changed?)");
        String err = FakeHands.with(c, c.getMainHandItem(), look, false, fp -> {
            try {
                boolean ok = bridge.replay(fp, payload);
                c.setItemSlot(EquipmentSlot.MAINHAND, fp.getMainHandItem());
                return ok ? null : "no server handler for " + kp.channel;
            } catch (Throwable t) {
                return "the mod refused it (" + t.getClass().getSimpleName() + ")";
            }
        });
        if (err != null) return new Outcome(false, err);
        c.swing(InteractionHand.MAIN_HAND);
        return new Outcome(true, "sent " + kp.channel + " as " + c.name() + "'s hands (effects that target the player act on my stand-in)");
    }

    static BlockPos blockOf(Vec3 v) {
        return BlockPos.containing(v);
    }
}
