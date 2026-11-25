package dev.yucktheslime.tradeofimmersion.skills.mixin;

import dev.yucktheslime.tradeofimmersion.skills.item.PayloadRecorder;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sees every serverbound custom payload before NeoForge or Fabric dispatch it (network thread) and keeps the recent
 * ones per player for key-press learning ({@link PayloadRecorder}). Never changes or cancels anything.
 */
@Mixin(ServerboundCustomPayloadPacket.class)
public abstract class ServerboundCustomPayloadSpyMixin {
    @Inject(method = "handle(Lnet/minecraft/network/protocol/common/ServerCommonPacketListener;)V", at = @At("HEAD"))
    private void tradeofimmersion$spy(ServerCommonPacketListener listener, CallbackInfo ci) {
        try {
            if (listener instanceof ServerGamePacketListenerImpl g && g.player != null) {
                PayloadRecorder.record(g.player.getUUID(), ((ServerboundCustomPayloadPacket) (Object) this).payload());
            }
        } catch (Throwable ignored) {
            // observation only
        }
    }
}
