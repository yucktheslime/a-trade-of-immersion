package dev.yucktheslime.mcaplayer2.neoforge.mixin.compat;

import dev.yucktheslime.mcaplayer2.client.CompanionBodyClient;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets MCA: Female Gender Physics treat companions as villagers (gender, genes and physics ticking). */
@Pseudo
@Mixin(targets = "com.mcabp.wildfire.main.entitydata.EntityConfig", remap = false)
public abstract class McabpEntityConfigMixin {
    @Inject(method = "isSupportedEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private static void mcaplayer2$supported(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (CompanionBodyClient.villagerFor(entity) != null) cir.setReturnValue(true);
    }

    @Inject(method = "resolveVillagerLike", at = @At("HEAD"), cancellable = true, require = 0)
    private static void mcaplayer2$resolve(LivingEntity entity, CallbackInfoReturnable<VillagerLike<?>> cir) {
        VillagerEntityMCA v = CompanionBodyClient.villagerFor(entity);
        if (v != null) cir.setReturnValue(v);
    }
}
