package dev.yucktheslime.mcabodyshapes.body;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Gives each adult MCA villager a random body the first time it has no finalized
 * shape (new spawn, first load, or a 0.3.0 adult still sitting on untouched
 * defaults). The result is stored on the villager and kept until a command or
 * slider change overwrites it.
 *
 * <p>Babies, toddlers, children, and teens are left alone. When a villager reaches
 * {@link net.conczin.mca.entity.ai.relationship.AgeState#ADULT} without a finalized shape, a shape is generated then.
 */
public final class BodyShapeAssignment {
    private BodyShapeAssignment() {
    }

    public static void onJoinLevel(EntityJoinLevelEvent event) {
        assignIfAbsent(event.getEntity());
    }

    /**
     * Covers growing up: MCA updates {@link AgeState} during the tick. Once the
     * villager is an adult and still has no finalized shape, generate one.
     */
    public static void onTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || (entity.tickCount & 15) != 0) {
            return;
        }
        assignIfAbsent(entity);
    }

    public static void assignIfAbsent(Entity entity) {
        if (entity.level().isClientSide) {
            return;
        }
        if (!isAdultNpcVillager(entity)) {
            return;
        }

        BodyShape existing = BodyShape.get(entity);
        if (BodyShapeWrites.isAssigned(entity)) {
            // Already finalized (including a player Reset to vanilla). Keep the
            // breast gene in sync in case a 0.3.0 shape never wrote it.
            BodyShapeWrites.syncBust(entity, existing);
            return;
        }

        if (!BodyShape.hasSaved(entity) || existing.isVanilla() || existing.isNearDefault()) {
            BodyShapeWrites.apply(entity, BodyShape.random(((LivingEntity) entity).getRandom()));
            return;
        }

        // Distinctive shape from an earlier slice, but no assigned flag yet.
        BodyShapeWrites.apply(entity, existing);
    }

    /**
     * NPC villagers only. Players can still be shaped with {@code /mcabody}, but
     * they are not given a random body automatically.
     */
    public static boolean isAdultNpcVillager(Entity entity) {
        return BodyShapeAge.isShapeableAdult(entity) && !(entity instanceof Player);
    }
}
