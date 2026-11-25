package dev.yucktheslime.tradeofimmersion.life.mixin;

import dev.yucktheslime.tradeofimmersion.life.Life;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Pregnancy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MCA's villager-villager gestation: when MCA starts one, the phases take over (and MCA's own timer is paused for any
 * villager this mod tracks a pregnancy for, so MCA never births it early).
 */
@Mixin(value = Pregnancy.class, remap = false)
public abstract class PregnancyMixin {
    @Shadow
    @Final
    private VillagerEntityMCA mother;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void tradeofimmersion$phasesRunInstead(CallbackInfo ci) {
        try {
            if (Pregnancies.handlesNative(mother)) ci.cancel();
        } catch (Throwable t) {
            Life.warnOnce("pregnancy-tick", t);
        }
    }

    @Inject(method = "tryStartGestation", at = @At("RETURN"), remap = false)
    private void tradeofimmersion$gestationStarted(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (Boolean.TRUE.equals(cir.getReturnValue()) && ((Pregnancy) (Object) this).isPregnant()) Pregnancies.onNativeGestation(mother);
        } catch (Throwable t) {
            Life.warnOnce("pregnancy-start", t);
        }
    }
}
