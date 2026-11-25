package dev.yucktheslime.tradeofimmersion.skills.job;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.function.Predicate;

/** Block helpers for jobs: what may be broken, picking tools and bridge blocks, reach, matching names to blocks. */
public final class BlockWork {
    public static final double REACH = 4.5;
    private static final TagKey<Block> C_ORES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));

    private BlockWork() {
    }

    public static String id(BlockState st) {
        return BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
    }

    /** Never break unbreakable blocks, blocks with storage/machines (block entities), beds or liquids. */
    public static boolean mayBreak(ServerLevel level, BlockPos pos, BlockState st) {
        if (st.isAir() || st.getBlock() instanceof LiquidBlock) return false;
        if (st.getDestroySpeed(level, pos) < 0) return false;
        if (level.getBlockEntity(pos) != null) return false;
        if (st.is(BlockTags.BEDS) || st.is(BlockTags.PORTALS)) return false;
        return level.getWorldBorder().isWithinBounds(pos);
    }

    public static boolean isLavaNear(ServerLevel level, BlockPos pos) {
        if (level.getFluidState(pos).is(Fluids.LAVA) || level.getFluidState(pos).is(Fluids.FLOWING_LAVA)) return true;
        for (Direction d : Direction.values()) {
            var f = level.getFluidState(pos.relative(d));
            if (f.is(Fluids.LAVA) || f.is(Fluids.FLOWING_LAVA)) return true;
        }
        return false;
    }

    public static boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.isAir() || n.canBeReplaced()) return true;
        }
        return false;
    }

    /** Within arm's reach and nothing solid in between. */
    public static boolean canReach(CompanionEntity c, BlockPos pos) {
        Vec3 eye = c.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        if (eye.distanceToSqr(center) > REACH * REACH) return false;
        BlockHitResult hit = c.level().clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    /** Swaps the best tool for {@code st} from the bag into the main hand. @return false if the block needs a tool the companion doesn't have */
    public static boolean equipBestTool(CompanionEntity c, BlockState st) {
        ItemStack hand = c.getMainHandItem();
        float best = hand.getDestroySpeed(st);
        int bestSlot = -1;
        for (int i = 0; i < c.inventory.getContainerSize(); i++) {
            ItemStack s = c.inventory.getItem(i);
            if (s.isEmpty() || s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 1) continue;
            float sp = s.getDestroySpeed(st);
            boolean right = !st.requiresCorrectToolForDrops() || s.isCorrectToolForDrops(st);
            boolean handRight = !st.requiresCorrectToolForDrops() || hand.isCorrectToolForDrops(st);
            if (right && (!handRight || sp > best)) {
                best = sp;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0) {
            ItemStack tool = c.inventory.getItem(bestSlot);
            c.inventory.setItem(bestSlot, hand);
            c.setItemSlot(EquipmentSlot.MAINHAND, tool);
        }
        return !st.requiresCorrectToolForDrops() || c.getMainHandItem().isCorrectToolForDrops(st);
    }

    /** A plain full solid block from the bag for bridging (cobblestone, dirt, stone...), no falling or block-entity blocks. */
    public static int bridgeBlockSlot(CompanionEntity c) {
        int fallback = -1;
        for (int i = 0; i < c.inventory.getContainerSize(); i++) {
            ItemStack s = c.inventory.getItem(i);
            if (!(s.getItem() instanceof BlockItem bi)) continue;
            Block b = bi.getBlock();
            BlockState st = b.defaultBlockState();
            if (b instanceof FallingBlock || st.hasBlockEntity() || !st.isCollisionShapeFullBlock(c.level(), BlockPos.ZERO)) continue;
            String p = BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (p.contains("cobble") || p.equals("dirt") || p.contains("netherrack") || p.contains("stone") || p.contains("deepslate")) return i;
            if (fallback < 0 && !st.is(BlockTags.LOGS) && !p.contains("ore") && !p.contains("glass")) fallback = i;
        }
        return fallback;
    }

    /** Turns "stone", "iron ore", "ores", "logs", "minecraft:deepslate" or "#c:ores" into a block test. */
    public static Predicate<BlockState> matcher(String query) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim().replaceAll("^(some|any|the)\\s+", "");
        if (q.isEmpty() || q.equals("ore") || q.equals("ores")) return st -> st.is(C_ORES) || id(st).endsWith("_ore");
        if (q.equals("log") || q.equals("logs") || q.equals("wood") || q.equals("trees") || q.equals("tree")) return st -> st.is(BlockTags.LOGS);
        if (q.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(q.substring(1));
            if (rl != null) {
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, rl);
                return st -> st.is(tag);
            }
        }
        String path = q.replace(' ', '_');
        if (path.endsWith("s") && !path.endsWith("ss") && !path.contains(":")) path = path.substring(0, path.length() - 1);
        String p = path;
        if (p.equals("stone")) return st -> {
            String id = id(st);
            return id.equals("minecraft:stone") || id.equals("minecraft:cobblestone") || id.equals("minecraft:deepslate")
                    || id.equals("minecraft:andesite") || id.equals("minecraft:diorite") || id.equals("minecraft:granite") || id.equals("minecraft:tuff");
        };
        return st -> {
            String id = id(st);
            return id.equals(p) || id.endsWith(":" + p) || id.substring(id.indexOf(':') + 1).contains(p);
        };
    }
}
