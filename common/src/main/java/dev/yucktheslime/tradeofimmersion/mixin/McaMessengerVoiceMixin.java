package dev.yucktheslime.tradeofimmersion.mixin;

import dev.yucktheslime.tradeofimmersion.voice.McaLineVoice;
import net.conczin.mca.entity.ai.Messenger;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When {@code mcaLinesUseToiVoice} is on, voice MCA's own scripted villager lines through Kagrenac PocketTTS.
 * Injected at HEAD so the "clip is coming" packet is sent before MCA's {@code VillagerMessage}.
 * Optional: skipped when MCA changes these methods.
 */
@Mixin(value = Messenger.class, remap = false)
public interface McaMessengerVoiceMixin {
    @Inject(method = "sendChatToAllAround(Lnet/minecraft/network/chat/MutableComponent;)V", at = @At("HEAD"), remap = false)
    default void tradeofimmersion$voiceComponent(MutableComponent message, CallbackInfo ci) {
        McaLineVoice.maybeVoiceScripted(this, message == null ? "" : message.getString());
    }

    @Inject(method = "sendChatToAllAround(Ljava/lang/String;[Ljava/lang/Object;)V", at = @At("HEAD"), remap = false)
    default void tradeofimmersion$voiceKey(String key, Object[] args, CallbackInfo ci) {
        String text = key == null ? "" : key;
        try {
            text = Component.translatable(key == null ? "" : key, args == null ? new Object[0] : args).getString();
        } catch (RuntimeException ignored) {
        }
        McaLineVoice.maybeVoiceScripted(this, text);
    }
}
