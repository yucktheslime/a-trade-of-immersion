package dev.yucktheslime.tradeofimmersion.skills.job;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillTask;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple mining and bridging, one block step at a time in a straight line: a 1x2 tunnel, a staircase down or up, or
 * a bridge over a gap. Each step clears the blocks in the way (like a survival player, best tool), places a block
 * from the bag under the next step when there is no floor, then steps forward. Stops at lava, at a block it may not
 * break (chests, machines, unbreakable), without a tool or blocks, or after {@code length} steps.
 */
public class DigTask extends SkillTask {
    public enum Mode { TUNNEL, STAIRS_DOWN, STAIRS_UP, BRIDGE }

    private final Direction dir;
    private final Mode mode;
    private final int length;
    private BlockPos cur;
    private int done;
    private int placed;
    private final List<BlockPos> toClear = new ArrayList<>();
    private BlockPos next;
    private BlockPos breaking;
    private float progress;
    private int moveTicks;
    private String stop = "";

    public DigTask(BlockPos start, Direction dir, Mode mode, int length) {
        super(20 * 60 * 8);
        this.cur = start;
        this.dir = dir;
        this.mode = mode;
        this.length = length;
    }

    public static Mode mode(String s) {
        String m = s == null ? "" : s.toLowerCase(Locale.ROOT);
        if (m.contains("bridge")) return Mode.BRIDGE;
        if (m.contains("up")) return Mode.STAIRS_UP;
        if (m.contains("down") || m.contains("stair")) return Mode.STAIRS_DOWN;
        return Mode.TUNNEL;
    }

    private void plan() {
        int dy = mode == Mode.STAIRS_DOWN ? -1 : mode == Mode.STAIRS_UP ? 1 : 0;
        next = cur.relative(dir).offset(0, dy, 0);
        toClear.clear();
        toClear.add(next);
        toClear.add(next.above());
        if (mode == Mode.STAIRS_DOWN) toClear.add(next.above(2)); // head room while stepping down
        if (mode == Mode.STAIRS_UP) toClear.add(cur.above(2));
    }

    @Override
    public Vec3 destination(CompanionEntity c) {
        return next == null ? null : Vec3.atBottomCenterOf(next);
    }

    @Override
    protected boolean step(CompanionEntity c) {
        if (!(c.level() instanceof ServerLevel level)) return true;
        SkillsConfig cfg = SkillsConfig.get();
        if (c.isPassenger()) c.stopRiding();
        if (next == null) {
            if (done >= length) return finish(c, "");
            plan();
            moveTicks = 0;
        }
        // 1) clear the way
        for (BlockPos p : toClear) {
            BlockState st = level.getBlockState(p);
            if (BlockWork.isLavaNear(level, p)) return finish(c, "lava ahead");
            if (st.isAir() || st.canBeReplaced()) continue; // air, grass, water: walk/swim through
            if (!cfg.miningEnabled) return finish(c, "breaking blocks is turned off");
            if (!BlockWork.mayBreak(level, p, st)) return finish(c, st.getBlock().getName().getString() + " is in the way and I won't break it");
            return breakTick(c, level, p, st);
        }
        breaking = null;
        // 2) floor under the next step
        BlockPos floor = next.below();
        BlockState fs = level.getBlockState(floor);
        if (!fs.isFaceSturdy(level, floor, Direction.UP)) {
            if (!cfg.bridgingEnabled) return finish(c, "no floor ahead and bridging is turned off");
            if (!fs.isAir() && !fs.canBeReplaced()) return finish(c, "can't build a floor ahead");
            int slot = BlockWork.bridgeBlockSlot(c);
            if (slot < 0) return finish(c, "no blocks to bridge with");
            c.getLookControl().setLookAt(Vec3.atCenterOf(floor));
            if (!FakeHands.placeAt(c, slot, floor)) return finish(c, "couldn't place a block ahead");
            placed++;
            return false;
        }
        // 3) step forward
        Vec3 to = Vec3.atBottomCenterOf(next);
        double dx = to.x - c.getX(), dz = to.z - c.getZ();
        if (dx * dx + dz * dz < 0.2 * 0.2 && Math.abs(c.getY() - next.getY()) < 0.6) {
            cur = next;
            next = null;
            done++;
            return false;
        }
        c.getMoveControl().setWantedPosition(to.x, to.y, to.z, 0.8);
        if (to.y > c.getY() + 0.5 && c.onGround()) c.getJumpControl().jump();
        if (++moveTicks > 60) {
            // nudge into the cleared cell (it is free and has a floor)
            c.moveTo(to.x, next.getY(), to.z, c.getYRot(), c.getXRot());
        }
        return false;
    }

    private boolean breakTick(CompanionEntity c, ServerLevel level, BlockPos p, BlockState st) {
        if (!p.equals(breaking)) {
            breaking = p;
            progress = 0;
            if (!BlockWork.equipBestTool(c, st)) return finish(c, "needs a proper tool for " + st.getBlock().getName().getString());
        }
        if (!BlockWork.canReach(c, p) && c.getEyePosition().distanceToSqr(Vec3.atCenterOf(p)) > BlockWork.REACH * BlockWork.REACH) {
            c.getMoveControl().setWantedPosition(cur.getX() + 0.5, cur.getY(), cur.getZ() + 0.5, 0.8);
            if (++moveTicks > 100) return finish(c, "can't reach the next block");
            return false;
        }
        float add = FakeHands.destroyProgress(c, p, st);
        if (add <= 0) return finish(c, "can't break " + st.getBlock().getName().getString());
        progress += add;
        c.getLookControl().setLookAt(Vec3.atCenterOf(p));
        if (c.tickCount % 5 == 0) c.swing(InteractionHand.MAIN_HAND);
        level.destroyBlockProgress(c.getId(), p, Math.min(9, (int) (progress * 10)));
        if (progress >= 1f) {
            level.destroyBlockProgress(c.getId(), p, -1);
            if (!FakeHands.destroy(c, p)) return finish(c, "something stopped me breaking " + st.getBlock().getName().getString());
            FakeHands.collectNear(c, Vec3.atCenterOf(p), 2.5);
            breaking = null;
            // gravel/sand may fall in: the loop checks the cell again next tick
        }
        return false;
    }

    private boolean finish(CompanionEntity c, String why) {
        stop = why;
        event(c, (mode == Mode.BRIDGE ? "bridging " : mode == Mode.TUNNEL ? "tunnel " : "staircase ") + dir.getName() + ": " + done + "/" + length
                + " block(s) done" + (placed > 0 ? ", placed " + placed + " block(s)" : "") + (why.isEmpty() ? "" : " (stopped: " + why + ")"));
        return true;
    }

    @Override
    public void stop(CompanionEntity c) {
        super.stop(c);
        if (breaking != null && c.level() instanceof ServerLevel level) level.destroyBlockProgress(c.getId(), breaking, -1);
    }

    @Override
    public String describe() {
        return (mode == Mode.BRIDGE ? "bridging " : "digging ") + dir.getName() + " (" + done + "/" + length + ")";
    }
}
