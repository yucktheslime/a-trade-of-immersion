package dev.yucktheslime.tradeofimmersion.skills.travel;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountClassifier;
import dev.yucktheslime.tradeofimmersion.skills.mount.MountRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/** Finds the best allowed, steerable ride near a companion for a trip. */
public final class VehicleFinder {
    private VehicleFinder() {
    }

    public record Pick(Entity vehicle, MountRules.Decision decision, double detour) {
    }

    @Nullable
    public static Pick best(CompanionEntity c, double radius, double tripDistance, Vec3 dest) {
        Pick best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : c.level().getEntities(c, c.getBoundingBox().inflate(radius), x -> x.isAlive() && x != c.getVehicle())) {
            MountRules.Kind kind = MountClassifier.kind(e);
            if (kind == MountRules.Kind.NOT_RIDEABLE) continue;
            if (e instanceof AbstractMinecart) continue; // rails rarely go where we want
            if (e instanceof Boat b && !(b.isInWater() && c.level().getFluidState(BlockPos.containing(dest).below()).isSource())) continue;
            MountRules.Decision d = MountClassifier.decide(c, e);
            if (!d.allowed() || !d.steerable()) continue;
            double detour = c.distanceTo(e);
            if (tripDistance > 0 && !TravelPlanner.worthDetour(tripDistance, detour)) continue;
            double s = TravelPlanner.score(d.preference(), detour);
            if (s < bestScore) {
                bestScore = s;
                best = new Pick(e, d, detour);
            }
        }
        return best;
    }
}
