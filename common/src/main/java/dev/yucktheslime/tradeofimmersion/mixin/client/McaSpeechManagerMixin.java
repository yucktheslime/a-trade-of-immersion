package dev.yucktheslime.tradeofimmersion.mixin.client;

import dev.yucktheslime.tradeofimmersion.client.voice.VoiceClient;
import net.conczin.mca.client.tts.SpeechManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * When this mod is already voicing the line, skip MCA's built-in TTS (no double voice, no language error).
 * Optional: skipped when MCA changes this method.
 */
@Mixin(value = SpeechManager.class, remap = false)
public abstract class McaSpeechManagerMixin {
    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true, remap = false)
    private void tradeofimmersion$skipIfToiVoices(Component message, UUID uuid, CallbackInfo ci) {
        try {
            if (VoiceClient.shouldSuppressMcaTts(uuid)) ci.cancel();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }
}
