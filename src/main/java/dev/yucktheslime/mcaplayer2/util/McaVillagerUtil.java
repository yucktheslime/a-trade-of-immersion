package dev.yucktheslime.mcaplayer2.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * Utility helpers for locating and identifying MCA villagers.
 *
 * In production this should check against VillagerEntityMCA (or the correct
 * MCA class for the version being targeted) instead of a string match.
 */
public final class McaVillagerUtil {
    private McaVillagerUtil() {}

    public static Optional<LivingEntity> findNearestMcaVillager(LivingEntity origin, double radius) {
        if (origin == null || origin.level() == null) return Optional.empty();

        Level level = origin.level();
        AABB box = origin.getBoundingBox().inflate(radius);

        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, box, entity ->
                entity != origin && isMcaVillager(entity)
        );

        return nearby.stream()
                .min((a, b) -> Double.compare(origin.distanceTo(a), origin.distanceTo(b)));
    }

    public static boolean isMcaVillager(Entity entity) {
        if (entity == null) return false;

        // Placeholder detection. Replace with proper class check:
        // return entity instanceof VillagerEntityMCA;
        String className = entity.getClass().getName();
        return className.contains("VillagerEntityMCA") || className.contains("mca");
    }
}
