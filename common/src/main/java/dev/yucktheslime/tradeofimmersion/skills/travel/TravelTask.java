package dev.yucktheslime.tradeofimmersion.skills.travel;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.SkillTask;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountClassifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * Goes somewhere. Trips longer than {@code longTravelDistance} first look for an allowed mount or vehicle within
 * {@code vehicleSearchRadius} (mount rules apply) and ride it there; otherwise, or when the ride gets stuck, walk.
 * Far destinations are reached through waypoints because pathfinding only reaches about the follow range.
 */
public class TravelTask extends SkillTask {
    enum Phase { DECIDE, TO_VEHICLE, RIDING, WALKING }

    @Nullable
    private final Entity target;
    private final Vec3 pos;
    private final String label;
    private Phase phase = Phase.DECIDE;
    @Nullable
    private Entity vehicle;
    private int phaseTicks;
    private boolean rode;

    public TravelTask(@Nullable Entity target, Vec3 pos, String label) {
        super(20 * 60 * 10);
        this.target = target;
        this.pos = pos;
        this.label = label;
    }

    @Override
    public Vec3 destination(CompanionEntity c) {
        Vec3 d = target != null && target.isAlive() ? target.position() : pos;
        if (phase != Phase.RIDING) return d;
        return d;
    }

    private void go(Phase p) {
        phase = p;
        phaseTicks = 0;
        resetStuck();
    }

    @Override
    protected boolean step(CompanionEntity c) {
        SkillsConfig cfg = SkillsConfig.get();
        Vec3 dest = destination(c);
        double dist = flatDist(c.position(), dest);
        double arrive = c.isPassenger() ? 4.0 : 2.5;
        phaseTicks++;
        if (dist < arrive && Math.abs(c.getY() - dest.y) < 6) {
            if (c.isPassenger() && cfg.dismountOnArrival) {
                c.stopVehicle();
                c.stopRiding();
            }
            event(c, "arrived at " + label + (rode ? " (rode there)" : ""));
            return true;
        }
        switch (phase) {
            case DECIDE -> {
                boolean steerable = c.isPassenger() && c.getVehicle() != null && MountClassifier.decide(c, c.getVehicle()).steerable();
                TravelPlanner.Mode mode = TravelPlanner.plan(dist, cfg.longTravelDistance, cfg.longTravel && cfg.mountControl, c.isPassenger(), steerable);
                if (mode == TravelPlanner.Mode.KEEP_RIDING) {
                    rode = true;
                    go(Phase.RIDING);
                } else if (mode == TravelPlanner.Mode.SEEK_VEHICLE) {
                    VehicleFinder.Pick p = VehicleFinder.best(c, cfg.vehicleSearchRadius, dist, dest);
                    if (p != null) {
                        if (c.isPassenger()) c.stopRiding();
                        vehicle = p.vehicle();
                        event(c, "long trip (" + Math.round(dist) + " blocks): taking " + vehicle.getName().getString() + " (" + p.decision().reason() + ")");
                        go(Phase.TO_VEHICLE);
                    } else {
                        event(c, "long trip (" + Math.round(dist) + " blocks) but no ride you may use nearby, walking");
                        go(Phase.WALKING);
                    }
                } else {
                    go(Phase.WALKING);
                }
            }
            case TO_VEHICLE -> {
                if (vehicle == null || !vehicle.isAlive() || !MountClassifier.decide(c, vehicle).allowed() || phaseTicks > 20 * 30) {
                    go(Phase.WALKING);
                    return false;
                }
                if (c.distanceToSqr(vehicle) < 2.5 * 2.5) {
                    boolean ok = c.startRiding(vehicle);
                    if (!ok) {
                        int seats = vehicle instanceof Boat ? (vehicle instanceof ChestBoat ? 1 : 2) : 1;
                        if (vehicle.getPassengers().size() < seats) ok = c.startRiding(vehicle, true);
                    }
                    if (ok) {
                        rode = true;
                        go(Phase.RIDING);
                    } else {
                        event(c, "could not get on " + vehicle.getName().getString() + ", walking");
                        go(Phase.WALKING);
                    }
                } else {
                    walkTo(c, vehicle.position(), 1.2);
                }
            }
            case RIDING -> {
                if (!c.isPassenger()) {
                    go(Phase.WALKING);
                    return false;
                }
                // CompanionEntity.rideTick2 steers toward destination() through the vehicle adapters every tick
                if (stuckTicks(c, 2.0) > 20 * 12) {
                    Entity v = c.getVehicle();
                    event(c, "the ride got stuck, walking the rest" + (v == null ? "" : " (left " + v.getName().getString() + ")"));
                    c.stopVehicle();
                    c.stopRiding();
                    go(Phase.WALKING);
                }
            }
            case WALKING -> {
                walkFar(c, dest, 1.1);
                if (stuckTicks(c, 1.5) > 20 * 30) {
                    event(c, "got stuck on the way to " + label + " (" + Math.round(dist) + " blocks left)");
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return (phase == Phase.RIDING ? "riding to " : phase == Phase.TO_VEHICLE ? "getting a ride to " : "going to ") + label;
    }
}
