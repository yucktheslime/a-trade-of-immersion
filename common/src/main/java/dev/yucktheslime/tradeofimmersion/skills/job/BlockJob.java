package dev.yucktheslime.tradeofimmersion.skills.job;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * A job made of "go to a block, then break it" steps: picks targets, walks into reach (giving up on blocks it can't
 * reach), breaks them over time with the best tool like a survival player, then hands each broken block to
 * {@link #onBroken}.
 */
public abstract class BlockJob extends SkillTask {
    protected final BlockPos center;
    protected final int radius;
    protected final Set<BlockPos> skip = new HashSet<>();
    @Nullable
    protected BlockPos target;
    private float progress;
    private int approachTicks;
    private int lastStage = -1;
    protected int broken;
    protected String stopReason = "";

    protected BlockJob(BlockPos center, int radius, int timeoutTicks) {
        super(timeoutTicks);
        this.center = center;
        this.radius = radius;
    }

    /** Next block to work on, or null when done. */
    @Nullable
    protected abstract BlockPos next(CompanionEntity c, ServerLevel level);

    protected abstract boolean stillWanted(ServerLevel level, BlockPos pos, BlockState st);

    /** After a successful break. Return false to finish the job. */
    protected boolean onBroken(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState was) {
        return true;
    }

    protected abstract void finished(CompanionEntity c);

    @Override
    public Vec3 destination(CompanionEntity c) {
        return target != null ? Vec3.atCenterOf(target) : null;
    }

    @Override
    protected boolean step(CompanionEntity c) {
        if (!(c.level() instanceof ServerLevel level)) return true;
        if (c.isPassenger()) c.stopRiding();
        if (target == null) {
            target = next(c, level);
            progress = 0;
            approachTicks = 0;
            lastStage = -1;
            if (target == null) {
                finished(c);
                return true;
            }
        }
        BlockState st = level.getBlockState(target);
        if (!stillWanted(level, target, st) || !BlockWork.mayBreak(level, target, st)) {
            clearTarget(level);
            return false;
        }
        if (!BlockWork.canReach(c, target)) {
            if (++approachTicks > 20 * 8 || approachTicks > 40 && c.getNavigation().isDone() && c.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > 36) {
                skip.add(target);
                clearTarget(level);
                return false;
            }
            if (c.tickCount % 10 == 0 || c.getNavigation().isDone()) c.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return false;
        }
        c.getNavigation().stop();
        if (progress == 0 && !BlockWork.equipBestTool(c, st)) {
            stopReason = "needs a proper tool for " + st.getBlock().getName().getString();
            skip.add(target);
            clearTarget(level);
            return false;
        }
        float add = FakeHands.destroyProgress(c, target, st);
        if (add <= 0) {
            skip.add(target);
            clearTarget(level);
            return false;
        }
        progress += add;
        c.getLookControl().setLookAt(Vec3.atCenterOf(target));
        if (c.tickCount % 5 == 0) c.swing(InteractionHand.MAIN_HAND);
        int stage = Math.min(9, (int) (progress * 10));
        if (stage != lastStage) {
            level.destroyBlockProgress(c.getId(), target, stage);
            lastStage = stage;
        }
        if (progress >= 1f) {
            BlockPos pos = target;
            level.destroyBlockProgress(c.getId(), pos, -1);
            boolean ok = FakeHands.destroy(c, pos);
            target = null;
            if (!ok || !level.getBlockState(pos).isAir() && level.getBlockState(pos).equals(st)) {
                skip.add(pos);
                return false;
            }
            broken++;
            FakeHands.collectNear(c, Vec3.atCenterOf(pos), 2.5);
            if (!onBroken(c, level, pos, st)) {
                finished(c);
                return true;
            }
        }
        return false;
    }

    private void clearTarget(ServerLevel level) {
        if (target != null && lastStage >= 0) level.destroyBlockProgress(-1, target, -1);
        target = null;
    }

    @Override
    public void stop(CompanionEntity c) {
        super.stop(c);
        if (target != null && c.level() instanceof ServerLevel level) level.destroyBlockProgress(c.getId(), target, -1);
    }

    /** Scans the job area for blocks that pass {@code test}, nearest to the companion first. */
    @Nullable
    protected BlockPos nearest(CompanionEntity c, ServerLevel level, int vertical, java.util.function.BiPredicate<BlockPos, BlockState> test) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -vertical; dy <= vertical; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(m)) continue;
                    BlockState st = level.getBlockState(m);
                    if (st.isAir() || skip.contains(m)) continue;
                    if (!test.test(m, st)) continue;
                    double d = c.distanceToSqr(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5);
                    if (d < bestD) {
                        bestD = d;
                        best = m.immutable();
                    }
                }
            }
        }
        return best;
    }
}
