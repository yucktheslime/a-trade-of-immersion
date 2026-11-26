package dev.yucktheslime.mcabodyshapes.body;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Hard adult-only rule for every body-shape write and render path.
 *
 * <p>Bust, seat, curves, hips, thighs, and the pregnancy belly must never
 * appear on MCA baby / toddler / child / teen villagers, or on a child
 * player / model. Non-MCA entities are also left alone. Only a fully
 * {@link AgeState#ADULT} villager (or a player whose MCA model is ADULT
 * or still {@link AgeState#UNASSIGNED}) is shapeable.
 */
public final class BodyShapeAge {
    private BodyShapeAge() {
    }

    /**
     * {@code true} only when this living entity may receive or render any
     * body-shape geometry. Config cannot override this.
     */
    public static boolean isShapeableAdult(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.isBaby()) {
            return false;
        }
        if (entity instanceof VillagerLike<?> villager) {
            AgeState age = villager.getAgeState();
            if (age == AgeState.ADULT) {
                return true;
            }
            // A player's MCA-styled model can be UNASSIGNED rather than ADULT.
            return entity instanceof Player && age == AgeState.UNASSIGNED;
        }
        return false;
    }
}
