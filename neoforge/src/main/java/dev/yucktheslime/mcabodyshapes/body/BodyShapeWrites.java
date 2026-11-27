package dev.yucktheslime.mcabodyshapes.body;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Writes a shape onto an entity: attachment, one-time assigned flag, and MCA's
 * own breast gene so bust variety is visible even without Female Gender Physics.
 */
public final class BodyShapeWrites {
    private BodyShapeWrites() {
    }

    public static void apply(Entity entity, BodyShape shape) {
        // Every write path (commands, editor, rolls, presets) goes through
        // here, so the configured slider ranges always apply.
        BodyShape clamped = BodyShapeLimits.clamp(shape);
        BodyShape.set(entity, clamped);
        markAssigned(entity);
        writeBustGene(entity, clamped);
    }

    public static void markAssigned(Entity entity) {
        entity.setData(BodyShapeAttachments.ASSIGNED, Boolean.TRUE);
    }

    public static boolean isAssigned(Entity entity) {
        return entity.getExistingData(BodyShapeAttachments.ASSIGNED).orElse(Boolean.FALSE);
    }

    /**
     * Maps our 0–2 bust slider onto MCA's 0–1 {@link Genetics#BREAST} gene
     * (slider 1.0 → gene 0.5). Male villagers keep a breast gene of 0.
     * When mcabp is loaded the gene is written as 0 so that add-on does not
     * draw a second bust; TOI's BustRenderer still jiggles.
     */
    public static void writeBustGene(Entity entity, BodyShape shape) {
        if (!(entity instanceof VillagerLike<?> villager)) {
            return;
        }
        Genetics genetics = villager.getGenetics();
        if (genetics == null) {
            return;
        }
        if (genetics.getGender() != Gender.FEMALE) {
            return;
        }
        // When mcabp is loaded the breast gene stays at 0 so its mesh stays flat (no double bust);
        // TOI's BustRenderer still provides the jiggle.
        float gene = dev.yucktheslime.mcabodyshapes.compat.McaPhysicsCompat.isLoaded()
                ? 0.0f
                : Mth.clamp(shape.bust() * 0.5f, 0.0f, 1.0f);
        genetics.setGene(Genetics.BREAST, gene);
    }
}
