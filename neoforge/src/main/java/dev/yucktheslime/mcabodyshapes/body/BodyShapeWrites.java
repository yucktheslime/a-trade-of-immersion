package dev.yucktheslime.mcabodyshapes.body;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.Gender;
import dev.yucktheslime.tradeofimmersion.body.BustModelRules;
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
        syncBust(entity, clamped);
    }

    /** Rolls the bust model if this female NPC has none yet, then writes the breast gene that matches it. */
    public static void syncBust(Entity entity, BodyShape shape) {
        BustModels.ensure(entity);
        writeBustGene(entity, shape);
    }

    public static void markAssigned(Entity entity) {
        entity.setData(BodyShapeAttachments.ASSIGNED, Boolean.TRUE);
    }

    public static boolean isAssigned(Entity entity) {
        return entity.getExistingData(BodyShapeAttachments.ASSIGNED).orElse(Boolean.FALSE);
    }

    /**
     * Writes MCA's 0–1 {@link Genetics#BREAST} gene for a female villager from its bust model
     * ({@link BustModelRules#gene}): {@code toi} keeps it at 0 so mcabp draws nothing (TOI's own mesh is the
     * bust, no double bust); {@code mcabp} writes the Bust Size slider (0–2) mapped onto 0–1 so mcabp draws and
     * jiggles it. Male villagers keep a breast gene of 0. Companions have no gene here: their client-side MCA
     * record is set in {@link BustModels#companionGene}.
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
        genetics.setGene(Genetics.BREAST, BustModelRules.gene(BustModels.stored(entity), shape.bust()));
    }
}
