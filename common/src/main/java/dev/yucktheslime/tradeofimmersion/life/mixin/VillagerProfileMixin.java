package dev.yucktheslime.tradeofimmersion.life.mixin;

import dev.yucktheslime.tradeofimmersion.life.LifePrompt;
import dev.yucktheslime.tradeofimmersion.util.VillagerProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MCA villager descriptions (in companion prompts and the villager's own prompt) mention pregnancy and recent life events. */
@Mixin(value = VillagerProfile.class, remap = false)
public abstract class VillagerProfileMixin {
    @Inject(method = "describe", at = @At("RETURN"), cancellable = true, remap = false)
    private void tradeofimmersion$life(CallbackInfoReturnable<String> cir) {
        String extra = LifePrompt.villagerExtra(((VillagerProfile) (Object) this).uuid());
        if (!extra.isEmpty()) cir.setReturnValue(cir.getReturnValue() + extra);
    }
}
