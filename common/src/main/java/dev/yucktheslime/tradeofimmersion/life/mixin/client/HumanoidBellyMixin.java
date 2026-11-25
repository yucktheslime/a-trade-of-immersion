package dev.yucktheslime.tradeofimmersion.life.mixin.client;

import dev.yucktheslime.tradeofimmersion.life.client.BellyVisual;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Growing belly on any humanoid (MCA villagers, companions with the MCA or player look, and pregnant players). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidBellyMixin {
    @Inject(method = "setupAnim", at = @At("RETURN"))
    private void tradeofimmersion$belly(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                       float netHeadYaw, float headPitch, CallbackInfo ci) {
        try {
            BellyVisual.applyBody(entity, (HumanoidModel<?>) (Object) this);
        } catch (Throwable ignored) {
        }
    }
}
