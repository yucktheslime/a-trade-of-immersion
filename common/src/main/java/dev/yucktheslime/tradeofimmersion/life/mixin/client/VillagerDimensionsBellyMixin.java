package dev.yucktheslime.tradeofimmersion.life.mixin.client;

import dev.yucktheslime.tradeofimmersion.life.client.BellyVisual;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MCA repositions breasts every frame; bump them forward after that so they sit on the growing belly. */
@Mixin(value = CommonVillagerModel.class, remap = false)
public interface VillagerDimensionsBellyMixin {
    @Inject(method = "applyVillagerDimensions", at = @At("RETURN"), remap = false)
    default void tradeofimmersion$belly(VillagerLike<?> villager, CallbackInfo ci) {
        try {
            if (villager instanceof LivingEntity e) BellyVisual.applyBreasts(e, (CommonVillagerModel<?>) (Object) this);
        } catch (Throwable ignored) {
        }
    }
}
