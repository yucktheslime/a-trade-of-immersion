package dev.yucktheslime.tradeofimmersion.mixin;

import dev.yucktheslime.tradeofimmersion.compat.McaEditorBridge;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.conczin.mca.network.c2s.VillagerEditorSyncRequest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MCA's editor saves a villager: for a companion, apply it to the companion instead (MCA would ignore it). */
@Mixin(value = VillagerEditorSyncRequest.class, remap = false)
public abstract class VillagerEditorSyncRequestMixin {
    @Inject(method = "saveEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void tradeofimmersion$companion(ServerPlayer player, Entity entity, CompoundTag patch, CallbackInfo ci) {
        if (entity instanceof CompanionEntity c) {
            McaEditorBridge.saveFromEditor(player, c, patch);
            ci.cancel();
        }
    }
}
