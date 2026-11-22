package dev.yucktheslime.mcaplayer2.mixin;

import com.player2.playerengine.player2api.Event;
import com.player2.playerengine.player2api.manager.ConversationManager;
import dev.yucktheslime.mcaplayer2.bridge.ConversationBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Captures every line a Player2 companion speaks.
 *
 * <p>In PlayerEngine 1.21.1-1.4.0, {@code AgentSideEffects.onEntityMessage} broadcasts a companion's
 * chat line and then calls the public static
 * {@code ConversationManager.onAICharacterMessage(Event.CharacterMessage, UUID)} so other companions
 * can hear it (verified by decompiling). We hook the head of that method so MCA villagers can hear it too.
 */
@Mixin(value = ConversationManager.class, remap = false)
public abstract class PlayerEngineConversationManagerMixin {
    @Inject(method = "onAICharacterMessage", at = @At("HEAD"), require = 0)
    private static void mcap2b$onCompanionMessage(Event.CharacterMessage msg, UUID senderId, CallbackInfo ci) {
        try {
            if (msg != null && msg.sendingCharacterData() != null) {
                ConversationBridge.get().onCompanionSpoke(msg.sendingCharacterData(), msg.message());
            }
        } catch (Throwable ignored) {
            // never break PlayerEngine's own message passing
        }
    }
}
