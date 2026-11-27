package dev.yucktheslime.tradeofimmersion.mixin.client;

import dev.yucktheslime.tradeofimmersion.client.voice.VoiceClient;
import net.conczin.mca.client.tts.OnlineSpeechManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hide MCA's "Your game language is not supported by the text-to-speech" chat line while this mod's
 * PocketTTS voice is handling villager lines. Optional: skipped when MCA changes this method.
 */
@Mixin(value = OnlineSpeechManager.class, remap = false)
public abstract class McaLanguageNotSupportedMixin {
    @Inject(method = "languageNotSupported", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tradeofimmersion$quiet(CallbackInfo ci) {
        try {
            if (VoiceClient.suppressLanguageWarning()) ci.cancel();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }
}
