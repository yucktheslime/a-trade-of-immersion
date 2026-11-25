package dev.yucktheslime.tradeofimmersion.life.mixin;

import dev.yucktheslime.tradeofimmersion.ai.PromptBuilder;
import dev.yucktheslime.tradeofimmersion.ai.TurnRequest;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.LifePrompt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the companion's life (family, partner, feelings, jealousy, pregnancy) to its turn prompt and context. */
@Mixin(value = PromptBuilder.class, remap = false)
public abstract class PromptBuilderMixin {
    @Inject(method = "build", at = @At("RETURN"), cancellable = true, remap = false)
    private static void tradeofimmersion$life(CompanionEntity c, String speaker, String trigger, String eventType, CallbackInfoReturnable<TurnRequest> cir) {
        cir.setReturnValue(LifePrompt.augment(c, cir.getReturnValue()));
    }
}
