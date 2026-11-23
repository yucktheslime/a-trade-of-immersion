package dev.yucktheslime.mcaplayer2.mixin;

import com.player2.playerengine.player2api.AgentSideEffects;
import dev.yucktheslime.mcaplayer2.bridge.BridgeCircuitBreaker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes (never cancels or hides) PlayerEngine's companion error path,
 * {@code AgentSideEffects.onError(MinecraftServer, String, ServerPlayer)}, which is where
 * "Player2: no billing player/token available for this API request." is sent to the owner. When such a
 * billing/auth/quota error shows up, the bridge's circuit breaker opens so the bridge stops feeding events
 * that would trigger more failing turns.
 */
@Mixin(value = AgentSideEffects.class, remap = false)
public abstract class PlayerEngineErrorObserverMixin {
    @Inject(method = "onError", at = @At("HEAD"), require = 0)
    private static void mcaplayer2$observeError(MinecraftServer server, String errMsg, ServerPlayer player, CallbackInfo ci) {
        try {
            if (BridgeCircuitBreaker.isBillingFailureText(errMsg)) BridgeCircuitBreaker.trip("PlayerEngine: " + errMsg);
        } catch (Throwable ignored) {
            // observation only
        }
    }
}
