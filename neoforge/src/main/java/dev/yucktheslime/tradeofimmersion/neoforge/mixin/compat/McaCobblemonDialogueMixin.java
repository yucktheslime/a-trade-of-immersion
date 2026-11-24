package dev.yucktheslime.tradeofimmersion.neoforge.mixin.compat;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes a server crash in "MCA: Cobblemon" 1.0.1 (mca_cobblemon) on MCA Reborn 7.7.36+.
 *
 * <p>mca_cobblemon was compiled when MCA's {@code Personality} was an enum and switches on it, which makes
 * the JVM call {@code Personality.values()}. MCA 7.7.36 turned Personality into a registry class, so that
 * call throws NoSuchMethodError the first time a villager picks a dialogue line (gifting a Poké Ball, a
 * send-out, trainer chat) and the server tick crashes. This replaces the lookup with the same mapping keyed
 * on the personality's id, so it never touches the missing enum methods.
 */
@Pseudo
@Mixin(targets = "com.jeremycandoit.mcacobblemon.trainer.VillagerDialogueHelper", remap = false)
public abstract class McaCobblemonDialogueMixin {
    @Inject(method = "getPersonalityBucket", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tradeofimmersion$bucket(VillagerEntityMCA villager, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(tradeofimmersion$bucketFor(villager));
    }

    private static String tradeofimmersion$bucketFor(VillagerEntityMCA villager) {
        try {
            if (villager == null || villager.getVillagerBrain() == null) return "neutral";
            Personality p = villager.getVillagerBrain().getPersonality();
            if (p == null || p.getId() == null) return "neutral";
            return switch (p.getId().getPath()) {
                case "friendly", "extroverted", "upbeat", "playful" -> "enthusiastic";
                case "gloomy", "introverted", "anxious" -> "reluctant";
                case "flirty" -> "flirty";
                case "sensitive", "peaceful" -> "kind";
                case "greedy" -> "confident";
                case "crabby" -> "grumpy";
                case "odd", "relaxed" -> "casual";
                default -> "neutral";
            };
        } catch (Throwable t) {
            return "neutral";
        }
    }
}
