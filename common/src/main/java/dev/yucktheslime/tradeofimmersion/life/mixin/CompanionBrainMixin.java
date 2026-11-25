package dev.yucktheslime.tradeofimmersion.life.mixin;

import dev.yucktheslime.tradeofimmersion.ai.CompanionBrain;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.hooks.LifeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every line said to a companion (chat, dialogue box, speech to text) feeds the relationship layer. */
@Mixin(value = CompanionBrain.class, remap = false)
public abstract class CompanionBrainMixin {
    @Inject(method = "onChat", at = @At("HEAD"), remap = false)
    private static void tradeofimmersion$heard(CompanionEntity c, String speaker, String text, CallbackInfo ci) {
        LifeHooks.onChatToCompanion(c, speaker, text);
    }
}
