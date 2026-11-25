package dev.yucktheslime.tradeofimmersion.life.mixin;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.hooks.LifeHooks;
import dev.yucktheslime.tradeofimmersion.villager.VillagerTalk;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Companions and MCA villagers who talk get to know each other (affection both ways). */
@Mixin(value = VillagerTalk.class, remap = false)
public abstract class VillagerTalkMixin {
    @Inject(method = "companionSpeaks", at = @At("HEAD"), remap = false)
    private static void tradeofimmersion$acquainted(CompanionEntity c, VillagerEntityMCA v, String message, boolean initial, CallbackInfo ci) {
        LifeHooks.onCompanionTalksToVillager(c, v);
    }
}
