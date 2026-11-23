package dev.yucktheslime.tradeofimmersion.action;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.platform.FakePlayers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * Uses the companion's main-hand item exactly like a player would, through the loader's fake player, so other
 * mods' items run their normal {@code use}/{@code useOn}/{@code interactLivingEntity} code. Food is eaten by the
 * companion itself (heals it).
 */
public final class ItemUse {
    private ItemUse() {
    }

    /**
     * @param entityTarget entity to use the item on (feeding, shearing, saddling, modded interactions) or null
     * @param blockTarget  block position to use the item on (placing, tilling, flint and steel, ...) or null = what it looks at
     */
    public static String use(CompanionEntity c, @Nullable Entity entityTarget, @Nullable Vec3 blockTarget) {
        if (!(c.level() instanceof ServerLevel level)) return "not on server";
        ItemStack stack = c.getMainHandItem();
        if (stack.has(DataComponents.FOOD) && entityTarget == null && blockTarget == null) {
            ItemStack one = stack.split(1);
            String food = one.getHoverName().getString(); // read before eating: eating empties the stack
            c.eatNow(one);
            return "ate " + food;
        }
        ServerPlayer fp = FakePlayers.get(level, c.getUUID(), c.name());
        Vec3 look = entityTarget != null ? entityTarget.getEyePosition() : blockTarget;
        if (look != null) c.getLookControl().setLookAt(look.x, look.y, look.z);
        float yaw = c.getYRot(), pitch = c.getXRot();
        if (look != null) {
            Vec3 d = look.subtract(c.getEyePosition());
            yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
            pitch = (float) -(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
            c.setYRot(yaw);
            c.setXRot(pitch);
            c.setYHeadRot(yaw);
        }
        fp.moveTo(c.getX(), c.getY(), c.getZ(), yaw, pitch);
        fp.setYHeadRot(yaw);
        fp.setItemInHand(InteractionHand.MAIN_HAND, stack);
        fp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        String result;
        try {
            if (entityTarget != null) {
                InteractionResult r = fp.interactOn(entityTarget, InteractionHand.MAIN_HAND);
                result = "used " + stack.getHoverName().getString() + " on " + entityTarget.getName().getString() + ": " + r;
            } else {
                HitResult hit = c.pick(5.0, 1.0f, false);
                if (blockTarget != null) {
                    hit = level.clip(new net.minecraft.world.level.ClipContext(c.getEyePosition(), blockTarget.add(blockTarget.subtract(c.getEyePosition()).normalize().scale(0.5)),
                            net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, c));
                }
                InteractionResult r = InteractionResult.PASS;
                if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                    r = fp.gameMode.useItemOn(fp, level, fp.getMainHandItem(), InteractionHand.MAIN_HAND, bhr);
                }
                if (!r.consumesAction()) r = fp.gameMode.useItem(fp, level, fp.getMainHandItem(), InteractionHand.MAIN_HAND);
                if (fp.isUsingItem()) {
                    // charged items (bows, tridents, modded staffs): hold briefly then release
                    ItemStack using = fp.getUseItem();
                    using.releaseUsing(level, fp, Math.max(0, using.getUseDuration(fp) - 20));
                    fp.stopUsingItem();
                }
                result = "used " + stack.getHoverName().getString() + (hit.getType() == HitResult.Type.BLOCK ? " on a block" : "") + ": " + r;
            }
            c.swing(InteractionHand.MAIN_HAND);
        } catch (Throwable t) {
            result = "using " + stack.getHoverName().getString() + " failed: " + t.getClass().getSimpleName();
        } finally {
            c.setItemSlot(EquipmentSlot.MAINHAND, fp.getMainHandItem());
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // anything the item produced (buckets of milk, bowls, modded outputs) goes into the companion's bag
            var inv = fp.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.removeItemNoUpdate(i);
                if (!s.isEmpty()) {
                    ItemStack rest = c.inventory.addItem(s);
                    if (!rest.isEmpty()) c.spawnAtLocation(rest);
                }
            }
        }
        return result;
    }
}
