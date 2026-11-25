package dev.yucktheslime.tradeofimmersion.skills.job;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Chops trees: only log columns standing on dirt with natural (non-placed) leaves near the top, so log houses are
 * left alone. Breaks what it can reach from the ground, then replants a sapling from the bag.
 */
public class ChopTask extends BlockJob {
    private final int max;
    private final List<BlockPos> stumps = new ArrayList<>();
    private int planted;

    public ChopTask(BlockPos center, int radius, int max) {
        super(center, radius, 20 * 60 * 5);
        this.max = max;
    }

    static boolean naturalTree(ServerLevel level, BlockPos log) {
        BlockPos bottom = log;
        while (level.getBlockState(bottom.below()).is(BlockTags.LOGS) && log.getY() - bottom.getY() < 32) bottom = bottom.below();
        if (!level.getBlockState(bottom.below()).is(BlockTags.DIRT)) return false;
        BlockPos top = log;
        while (level.getBlockState(top.above()).is(BlockTags.LOGS) && top.getY() - log.getY() < 32) top = top.above();
        for (BlockPos p : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 2, 2))) {
            BlockState st = level.getBlockState(p);
            if (st.is(BlockTags.LEAVES) && st.hasProperty(LeavesBlock.PERSISTENT) && !st.getValue(LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    @Override
    protected BlockPos next(CompanionEntity c, ServerLevel level) {
        if (broken >= max) return null;
        return nearest(c, level, 6, (p, st) -> st.is(BlockTags.LOGS) && p.getY() <= c.getBlockY() + 5 && naturalTree(level, p));
    }

    @Override
    protected boolean stillWanted(ServerLevel level, BlockPos pos, BlockState st) {
        return st.is(BlockTags.LOGS);
    }

    @Override
    protected boolean onBroken(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState was) {
        if (level.getBlockState(pos.below()).is(BlockTags.DIRT)) stumps.add(pos);
        return broken < max;
    }

    @Override
    protected void finished(CompanionEntity c) {
        if (SkillsConfig.get().replant && c.level() instanceof ServerLevel level) {
            for (BlockPos s : stumps) {
                if (!level.getBlockState(s).isAir()) continue;
                for (int i = 0; i < c.inventory.getContainerSize(); i++) {
                    if (c.inventory.getItem(i).is(ItemTags.SAPLINGS)) {
                        if (FakeHands.useOn(c, i, s.below(), Direction.UP)) planted++;
                        break;
                    }
                }
            }
        }
        FakeHands.collectNear(c, net.minecraft.world.phys.Vec3.atCenterOf(center), radius);
        event(c, "chopping done: " + broken + " log(s)" + (planted > 0 ? ", planted " + planted + " sapling(s)" : "")
                + (stopReason.isEmpty() ? "" : " (" + stopReason + ")"));
    }

    @Override
    public String describe() {
        return "chopping trees (" + broken + " logs)";
    }
}
