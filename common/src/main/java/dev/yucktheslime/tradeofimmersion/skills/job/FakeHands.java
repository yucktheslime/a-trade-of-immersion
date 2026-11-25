package dev.yucktheslime.tradeofimmersion.skills.job;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.platform.FakePlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * Runs block work through the companion's loader fake player, so breaking and placing go through the normal
 * player code paths (tool damage, drops, enchantments, other mods' and claim/protection mods' events).
 */
public final class FakeHands {
    private FakeHands() {
    }

    /** Puts the fake player where the companion is, looking at {@code look}, holding {@code hand}; runs {@code work}; puts everything back. */
    public static <R> R with(CompanionEntity c, ItemStack hand, Vec3 look, boolean sneak, Function<ServerPlayer, R> work) {
        ServerLevel level = (ServerLevel) c.level();
        ServerPlayer fp = FakePlayers.get(level, c.getUUID(), c.name());
        Vec3 eye = c.getEyePosition();
        Vec3 d = look.subtract(eye);
        float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
        fp.moveTo(c.getX(), c.getY(), c.getZ(), yaw, pitch);
        fp.setYHeadRot(yaw);
        fp.setOnGround(true);
        fp.setShiftKeyDown(sneak);
        fp.setItemInHand(InteractionHand.MAIN_HAND, hand);
        fp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        c.getLookControl().setLookAt(look.x, look.y, look.z);
        try {
            return work.apply(fp);
        } finally {
            fp.setShiftKeyDown(false);
            fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var inv = fp.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.removeItemNoUpdate(i);
                if (!s.isEmpty()) {
                    ItemStack rest = c.inventory.addItem(s);
                    if (!rest.isEmpty()) c.spawnAtLocation(rest);
                }
            }
        }
    }

    /** Destroy progress per tick for the companion's main-hand item (as the fake player). */
    public static float destroyProgress(CompanionEntity c, BlockPos pos, BlockState st) {
        ItemStack tool = c.getMainHandItem();
        return with(c, tool, Vec3.atCenterOf(pos), false, fp -> st.getDestroyProgress(fp, c.level(), pos));
    }

    /** Breaks the block like a survival player with the companion's main-hand item. */
    public static boolean destroy(CompanionEntity c, BlockPos pos) {
        ItemStack tool = c.getMainHandItem();
        boolean ok = with(c, tool, Vec3.atCenterOf(pos), false, fp -> {
            boolean r = fp.gameMode.destroyBlock(pos);
            ItemStack after = fp.getMainHandItem();
            c.setItemSlot(EquipmentSlot.MAINHAND, after);
            return r;
        });
        c.swing(InteractionHand.MAIN_HAND);
        return ok;
    }

    /**
     * Places (uses) inventory slot {@code slot}'s item against a solid neighbour of {@code target}, sneaking so the
     * clicked block's own menu doesn't open.
     */
    public static boolean placeAt(CompanionEntity c, int slot, BlockPos target) {
        ServerLevel level = (ServerLevel) c.level();
        for (Direction dir : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            BlockPos against = target.relative(dir);
            BlockState st = level.getBlockState(against);
            if (!st.isFaceSturdy(level, against, dir.getOpposite())) continue;
            if (useOn(c, slot, against, dir.getOpposite()) && !level.getBlockState(target).canBeReplaced()) return true;
        }
        return false;
    }

    /** Right-clicks face {@code face} of {@code pos} with inventory slot {@code slot}'s item (seeds on farmland, saplings on dirt). */
    public static boolean useOn(CompanionEntity c, int slot, BlockPos pos, Direction face) {
        ServerLevel level = (ServerLevel) c.level();
        ItemStack stack = c.inventory.getItem(slot);
        if (stack.isEmpty()) return false;
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        InteractionResult r = with(c, stack, hit, true, fp -> {
            InteractionResult res = fp.gameMode.useItemOn(fp, level, fp.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
            c.inventory.setItem(slot, fp.getMainHandItem());
            return res;
        });
        c.inventory.setChanged();
        if (r.consumesAction()) c.swing(InteractionHand.MAIN_HAND);
        return r.consumesAction();
    }

    /** Picks up item entities within {@code r} of {@code pos} into the companion's bag. @return stacks taken */
    public static int collectNear(CompanionEntity c, Vec3 pos, double r) {
        int n = 0;
        for (ItemEntity it : c.level().getEntitiesOfClass(ItemEntity.class, new AABB(pos, pos).inflate(r), e -> e.isAlive() && !e.getItem().isEmpty())) {
            ItemStack s = it.getItem();
            int before = s.getCount();
            ItemStack rest = c.inventory.addItem(s.copy());
            if (rest.getCount() < before) {
                c.take(it, before - rest.getCount());
                n++;
            }
            if (rest.isEmpty()) it.discard();
            else it.setItem(rest);
        }
        return n;
    }
}
