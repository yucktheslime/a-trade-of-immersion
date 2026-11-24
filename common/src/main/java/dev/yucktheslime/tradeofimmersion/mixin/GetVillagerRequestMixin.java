package dev.yucktheslime.tradeofimmersion.mixin;

import dev.yucktheslime.tradeofimmersion.compat.McaEditorBridge;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.conczin.mca.network.c2s.GetVillagerRequest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MCA's editor asks for a villager's data: for a companion, answer with its MCA villager record. */
@Mixin(value = GetVillagerRequest.class, remap = false)
public abstract class GetVillagerRequestMixin {
    @Inject(method = "getVillagerData", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tradeofimmersion$companion(Entity e, CallbackInfoReturnable<CompoundTag> cir) {
        if (e instanceof CompanionEntity c) {
            CompoundTag data = McaEditorBridge.editorData(c);
            if (data != null) cir.setReturnValue(data);
        }
    }
}
