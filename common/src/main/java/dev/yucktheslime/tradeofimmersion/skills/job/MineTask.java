package dev.yucktheslime.tradeofimmersion.skills.job;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

/**
 * Mines exposed blocks of one kind (stone, an ore, "ores", a block id or #tag) that the companion can walk up to.
 * Buried blocks are not dug out (use the dig job for tunnels).
 */
public class MineTask extends BlockJob {
    private final Predicate<BlockState> what;
    private final String label;
    private final int max;

    public MineTask(BlockPos center, int radius, Predicate<BlockState> what, String label, int max) {
        super(center, radius, 20 * 60 * 6);
        this.what = what;
        this.label = label;
        this.max = max;
    }

    @Override
    protected BlockPos next(CompanionEntity c, ServerLevel level) {
        if (broken >= max) return null;
        return nearest(c, level, Math.min(radius, 8), (p, st) -> what.test(st) && BlockWork.exposed(level, p) && !BlockWork.isLavaNear(level, p));
    }

    @Override
    protected boolean stillWanted(ServerLevel level, BlockPos pos, BlockState st) {
        return what.test(st);
    }

    @Override
    protected boolean onBroken(CompanionEntity c, ServerLevel level, BlockPos pos, BlockState was) {
        return broken < max;
    }

    @Override
    protected void finished(CompanionEntity c) {
        event(c, "mining " + label + " done: broke " + broken + " block(s)" + (stopReason.isEmpty() ? "" : " (" + stopReason + ")")
                + (broken == 0 && stopReason.isEmpty() ? " - none exposed and reachable nearby" : ""));
    }

    @Override
    public String describe() {
        return "mining " + label + " (" + broken + "/" + max + ")";
    }
}
