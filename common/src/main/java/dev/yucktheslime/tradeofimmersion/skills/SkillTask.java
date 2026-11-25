package dev.yucktheslime.tradeofimmersion.skills;

import dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.CompanionTask;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import dev.yucktheslime.tradeofimmersion.skills.mount.VehicleAdapters;
import net.minecraft.world.phys.Vec3;

/** Base for Phase 3 jobs: outcome events, walking by waypoints (paths only reach ~follow range), stuck detection. */
public abstract class SkillTask extends CompanionTask {
    private Vec3 progressPos;
    private int progressTick;

    protected SkillTask(int timeoutTicks) {
        super(timeoutTicks);
    }

    /** A job outcome: remembered by the companion and, with Kagrenac, logged as an action_result event. */
    public static void event(CompanionEntity c, String text) {
        NpcMemoryStore.get().event(c.getUUID(), "task", text);
        try {
            KagrenacWorld.event("action_result", c, c.name() + ": " + text);
        } catch (Throwable ignored) {
            // event log is optional
        }
    }

    @Override
    protected void onTimeout(CompanionEntity c) {
        event(c, describe() + " (stopped: took too long)");
    }

    /** Walk toward {@code dest}, through a reachable waypoint when it is far. */
    protected static void walkFar(CompanionEntity c, Vec3 dest, double speed) {
        if (c.isPassenger()) return;
        if (c.tickCount % 10 == 0 || c.getNavigation().isDone()) {
            Vec3 wp = VehicleAdapters.waypoint(c, dest, VehicleAdapters.pathStep(c));
            c.getNavigation().moveTo(wp.x, wp.y, wp.z, speed);
        }
    }

    /** Ticks since the companion last moved {@code minMove} blocks (call every tick while it should be moving). */
    protected int stuckTicks(CompanionEntity c, double minMove) {
        Vec3 here = c.isPassenger() && c.getVehicle() != null ? c.getVehicle().position() : c.position();
        if (progressPos == null || here.distanceToSqr(progressPos) > minMove * minMove) {
            progressPos = here;
            progressTick = ticks;
        }
        return ticks - progressTick;
    }

    protected void resetStuck() {
        progressPos = null;
    }

    protected static double flatDist(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
