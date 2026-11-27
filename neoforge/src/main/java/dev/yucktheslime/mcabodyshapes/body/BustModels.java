package dev.yucktheslime.mcabodyshapes.body;

import dev.yucktheslime.mcabodyshapes.compat.McaPhysicsCompat;
import dev.yucktheslime.mcabodyshapes.config.BodyShapeCommonConfig;
import dev.yucktheslime.tradeofimmersion.body.BustModelRules;
import dev.yucktheslime.tradeofimmersion.body.CompanionShapeEligibility;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Per-entity bust model ({@code toi} or {@code mcabp}) for female NPCs: MCA villagers and A Trade of Immersion
 * companions. Rolled once on the server, saved and synced as {@link BodyShapeAttachments#BUST_MODEL}. Players
 * always use TOI's mesh and never get a stored value. Safe to call on a dedicated server (no client classes).
 */
public final class BustModels {
    private BustModels() {
    }

    /** The stored model string ({@code toi} / {@code mcabp}), or {@code null} if none was rolled yet. */
    public static String stored(Entity entity) {
        String value = entity.getExistingData(BodyShapeAttachments.BUST_MODEL).orElse("");
        return BustModelRules.parseModel(value);
    }

    /** What /mcabody get shows: the stored model, or {@code toi (not rolled)}. */
    public static String describe(Entity entity) {
        String value = stored(entity);
        return value != null ? value : "toi (not rolled)";
    }

    /** Female NPC that may get a stored model: an MCA villager or explicitly female adult companion. Never a player. */
    public static boolean isFemaleNpc(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity instanceof Player) {
            return false;
        }
        if (entity instanceof VillagerLike<?> villager) {
            return villager.getGenetics() != null && villager.getGenetics().getGender() == Gender.FEMALE;
        }
        return CompanionShapeEligibility.isShapeable(entity);
    }

    /**
     * Server side. Rolls the model the first time a female NPC is seen (spawn or first load of an existing
     * NPC), keeps it afterwards, and applies a forcing {@code boobs} config (toi or mcabp) to everyone.
     *
     * @return the stored model, or {@code null} if this entity does not take part
     */
    public static String ensure(Entity entity) {
        if (entity.level().isClientSide || !isFemaleNpc(entity)) {
            return null;
        }
        String current = stored(entity);
        String next = BustModelRules.resolveStored(current, BodyShapeCommonConfig.boobsMode(),
                BodyShapeCommonConfig.boobsMcabpChance(), ((LivingEntity) entity).getRandom().nextDouble());
        if (!next.equals(current)) {
            entity.setData(BodyShapeAttachments.BUST_MODEL, next);
        }
        return next;
    }

    /** Server side: stores {@code model} (toi / mcabp) on a female NPC. */
    public static void set(Entity entity, String model) {
        String valid = BustModelRules.parseModel(model);
        if (valid != null && !(entity instanceof Player)) {
            entity.setData(BodyShapeAttachments.BUST_MODEL, valid);
        }
    }

    /** Server side: rolls again per the config ({@code random} uses the chance, a forced mode wins). */
    public static String reroll(Entity entity) {
        if (entity.level().isClientSide || !isFemaleNpc(entity)) {
            return null;
        }
        String next = BustModelRules.roll(BodyShapeCommonConfig.boobsMode(), BodyShapeCommonConfig.boobsMcabpChance(),
                ((LivingEntity) entity).getRandom().nextDouble());
        entity.setData(BodyShapeAttachments.BUST_MODEL, next);
        return next;
    }

    /**
     * Client side: does this entity draw mcabp's bust? Only if the stored model is mcabp, this client has
     * mcabp, and mcabp's own rendering is on. Otherwise TOI's mesh is the bust.
     */
    public static boolean clientUsesMcabp(Entity entity) {
        return BustModelRules.rendersMcabp(stored(entity), McaPhysicsCompat.isLoaded(),
                McaPhysicsCompat.renderingEnabledOrNull(), entity instanceof Player);
    }

    /**
     * Client side, companions only: the Breast gene for the companion's client-side MCA record that
     * mcabp reads (mcabp draws that, not TOI's mesh). {@code null} leaves the record alone.
     */
    public static Float companionGene(LivingEntity entity) {
        if (!McaPhysicsCompat.isLoaded() || !BodyShapeAge.isShapeableAdult(entity)) {
            return null;
        }
        var edits = dev.yucktheslime.tradeofimmersion.body.ClientCompanionBodies.get(entity.getUUID());
        if (edits != null && edits.genes.containsKey("Breast")) {
            return null; // an explicit /toi body breast edit wins
        }
        return clientUsesMcabp(entity) ? BustModelRules.mcabpGene(BodyShape.get(entity).bust()) : BustModelRules.toiGene();
    }
}
