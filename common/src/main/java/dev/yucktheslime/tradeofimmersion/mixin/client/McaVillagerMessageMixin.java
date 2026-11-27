package dev.yucktheslime.tradeofimmersion.mixin.client;

import dev.yucktheslime.tradeofimmersion.client.dialogue.DialogueClient;
import net.conczin.mca.client.tts.SpeechManager;
import net.conczin.mca.network.ClientHandlerImpl;
import net.conczin.mca.network.s2c.VillagerMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCA villager lines (MCA's own and the ones our AI gives villagers) arrive as MCA's VillagerMessage and MCA puts
 * them in chat. With the dialogue box on they go to the box instead. MCA's own text-to-speech is skipped when
 * this mod is already voicing the line ({@code voiceEnabled} / {@code mcaLinesUseToiVoice}). With
 * mirrorToChat on, MCA's handler runs as usual too (SpeechManager is still skipped by
 * {@code McaSpeechManagerMixin} when we are voicing). Optional: skipped when MCA changes this method.
 */
@Mixin(value = ClientHandlerImpl.class, remap = false)
public abstract class McaVillagerMessageMixin {
    @Inject(method = "handleVillagerMessage", at = @At("HEAD"), cancellable = true, remap = false)
    private void tradeofimmersion$toDialogueBox(VillagerMessage message, CallbackInfo ci) {
        try {
            if (DialogueClient.onMcaVillagerLine(message.prefix(), message.message(), message.uuid())) {
                if (!dev.yucktheslime.tradeofimmersion.client.voice.VoiceClient.shouldSuppressMcaTts(message.uuid())) {
                    SpeechManager.INSTANCE.onChatMessage(message.message(), message.uuid());
                }
                ci.cancel();
            }
        } catch (RuntimeException | LinkageError e) {
            // never lose the line: fall through to MCA's chat
        }
    }
}
