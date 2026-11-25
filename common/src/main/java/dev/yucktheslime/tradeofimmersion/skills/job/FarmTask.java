package dev.yucktheslime.tradeofimmersion.skills.job;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/** Harvests ripe crops around a spot (vanilla and modded crops with an age property), replants from the bag, collects the drops. */
public class FarmTask extends BlockJob {
    private int replanted;

    public FarmTask(BlockPos center, int radius) {
        super(center, radius, 20 * 60 * 5);
    }

    public static boolean ripe(BlockState st) {
        Block b = st.getBlock();
        if (b instanceof CropBlock crop) return crop.isMaxAge(st);
        if (b instanceof NetherWartBlock) return st.getValue(NetherWartBlock.AGE) >= 3;
        if (b == Blocks.MELON || b == Blocks.PUMPKIN) return false; // only next to a stem, see below
        if (st.is(BlockTags.CROPS)) {
            for (Property<?> p : st.getProperties()) {
                if (p instanceof IntegerProperty ip && p.getName().equals("age")) {
                    int max = ip.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                    return st.getValue(ip) >= max;
                }
            }
        }
        return false;
    }

    static boolean stemFruit(ServerLevel level, BlockPos pos, BlockState st) {
        if (st.getBlock() != Blocks.MELON && st.getBlock() != Blocks.PUMPKIN) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.getBlock() instanceof AttachedStemBlock && n.getValue(AttachedStemBlock.FACING) == d.getOpposite()) return true;
        }
        return false;
    }

    @Override
    protected BlockPos next(CompanionEntity c, ServerLevel level) {
        return nearest(c, level, 3, (p, st) -> ripe(st) || stemFruit(level, p, st));
    }

    @Override
    protected boolean stillWanted(ServerLevel level, BlockPos pos, BlockState st) {
        return ripe(st) || stemFruit(level, pos, st);
    }

    @Override
    protected boolean onBroken(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState was) {
        if (!SkillsConfig.get().replant || !(was.getBlock() instanceof CropBlock || was.getBlock() instanceof NetherWartBlock || was.is(BlockTags.CROPS))) return true;
        Item seed = seedFor(level, pos, was);
        if (seed == null || seed == Items.AIR) return true;
        for (int i = 0; i < c.inventory.getContainerSize(); i++) {
            ItemStack s = c.inventory.getItem(i);
            if (s.is(seed)) {
                if (FakeHands.useOn(c, i, pos.below(), Direction.UP)) replanted++;
                break;
            }
        }
        return true;
    }

    static Item seedFor(ServerLevel level, BlockPos pos, BlockState was) {
        try {
            return was.getBlock().getCloneItemStack(level, pos, was).getItem();
        } catch (Throwable t) {
            return was.getBlock().asItem();
        }
    }

    @Override
    protected void finished(CompanionEntity c) {
        FakeHands.collectNear(c, net.minecraft.world.phys.Vec3.atCenterOf(center), radius);
        event(c, "farming done: harvested " + broken + " crop(s), replanted " + replanted);
    }

    @Override
    public String describe() {
        return "farming (" + broken + " harvested)";
    }
}
