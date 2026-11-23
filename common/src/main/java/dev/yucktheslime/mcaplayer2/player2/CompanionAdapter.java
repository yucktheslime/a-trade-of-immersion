package dev.yucktheslime.mcaplayer2.player2;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.AgentConversationData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.player2api.Event;
import com.player2.playerengine.player2api.Player2ApiDispatcher;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.manager.ConversationManager;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All direct calls into PlayerEngine's conversation layer live here.
 *
 * <p>Verified against {@code playerengine-*-1.21.1-1.4.0.jar} (javap + CFR decompile):
 * <ul>
 *   <li>{@code ConversationManager.queueData} - public static {@code ConcurrentHashMap<UUID, AgentConversationData>}
 *       keyed by the companion entity UUID; one entry per live AI companion.</li>
 *   <li>{@code ConversationManager.getOrCreateEventQueueData(PlayerEngineController)}.</li>
 *   <li>{@code AgentConversationData#onEvent(Event)} - queues an event for the companion's next LLM turn.
 *       {@code Event.InfoMessage(String)} is a system/info note; {@code #deferInfo(InfoMessage)} merges info into the
 *       next turn without starting one.</li>
 *   <li>{@code AgentConversationData#previewBilling()}, {@code Player2PayerResolution.resolve(...)},
 *       {@code Player2ApiDispatcher.route(...)} - PlayerEngine's payer resolution and Player2 transport.</li>
 *   <li>{@code AgentConversationData#getEntity()}, {@code #getName()}, {@code #getMod()}, {@code #getUUID()}.</li>
 *   <li>{@code PlayerEngineController#getOwner()}.</li>
 * </ul>
 */
public final class CompanionAdapter {
    private CompanionAdapter() {
    }

    /** Live companions (entity loaded and alive). */
    public static List<AgentConversationData> liveCompanions() {
        List<AgentConversationData> out = new ArrayList<>();
        for (AgentConversationData d : ConversationManager.queueData.values()) {
            LivingEntity e = safeEntity(d);
            if (e != null && e.isAlive() && !e.isRemoved()) out.add(d);
        }
        return out;
    }

    public static LivingEntity safeEntity(AgentConversationData d) {
        try {
            return d.getEntity();
        } catch (Throwable t) {
            return null;
        }
    }

    public static AgentConversationData dataFor(PlayerEngineController controller) {
        return ConversationManager.getOrCreateEventQueueData(controller);
    }

    public static UUID id(AgentConversationData d) {
        return d.getUUID();
    }

    public static String name(AgentConversationData d) {
        String n = d.getName();
        return n == null || n.isBlank() ? "Companion" : n;
    }

    public static Player owner(AgentConversationData d) {
        try {
            return d.getMod().getOwner();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Adds background context to the companion's <em>next</em> prompt without starting an LLM turn by itself
     * ({@code AgentConversationData#deferInfo}, max 512 chars per entry, so long context is split).
     */
    public static void injectInfo(AgentConversationData d, String info) {
        try {
            for (String chunk : chunks(info, 500)) {
                if (!d.deferInfo(new Event.InfoMessage(chunk))) {
                    McaPlayer2Bridge.LOGGER.debug("deferInfo rejected a context chunk for {}", name(d));
                }
            }
        } catch (Throwable t) {
            McaPlayer2Bridge.LOGGER.warn("Failed to inject info into companion {}", name(d), t);
        }
    }

    /**
     * Delivers a line spoken by a villager to the companion; this starts a companion LLM turn.
     *
     * <p>Sent as an {@code Event.InfoMessage}, NOT a {@code UserMessage}: PlayerEngine 1.4.0 copies the
     * {@code userName} of the last queued UserMessage into {@code AgentConversationData.chainInitiatorUsername},
     * and (unless the server payer mode is OWNER_PAYS_ALL) {@code Player2PayerResolution.resolve} bills that
     * name as an online player. A villager name matches no player, so every following turn failed with
     * "Player2: no billing player/token available for this API request." and the queued events were retried
     * on every tick. InfoMessages leave the initiator untouched, so the turn bills the owner / last real user.
     */
    public static void deliverSpeech(AgentConversationData d, String speakerName, String line) {
        try {
            String text = speakerName + " says to you: \"" + line + "\" (Reply to " + speakerName.split(" ")[0]
                    + " by name in your message if you want to answer.)";
            d.onEvent(new Event.InfoMessage(text.length() > 900 ? text.substring(0, 900) : text));
        } catch (Throwable t) {
            McaPlayer2Bridge.LOGGER.warn("Failed to deliver villager speech to companion {}", name(d), t);
        }
    }

    /**
     * Whether PlayerEngine can currently pay for this companion's next turn, using its own
     * {@code AgentConversationData#previewBilling()} (same resolution {@code process()} uses).
     */
    public static boolean canBill(AgentConversationData d) {
        try {
            Player2PayerResolution.ApiBillingContext b = d.previewBilling();
            return b != null && (b.onlinePayer() != null || b.useStoredToken());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Owner-billed chat completion through PlayerEngine's own Player2 client
     * ({@code Player2ApiDispatcher.route}, the same transport/auth/payer path companions use), billed to the
     * companion's owner via {@code Player2PayerResolution.resolve(controller, ownerName, clientId)}.
     * Blocking - call off the server thread. Note: bypasses PlayerEngine's per-call budget counters.
     *
     * @return the assistant message content
     * @throws IllegalStateException with "billing" in the message if nobody can pay
     */
    public static String ownerBilledChatCompletion(AgentConversationData d, String system, String user) throws Exception {
        var mod = d.getMod();
        String clientId = d.getPlayer2apiService().getClientId();
        Player2PayerResolution.ApiBillingContext billing = Player2PayerResolution.resolve(mod, mod.getOwnerUsername(), clientId);
        if (billing.onlinePayer() == null && !billing.useStoredToken()) {
            throw new IllegalStateException("No billing player available (owner offline or not signed in to Player2)");
        }
        JsonObject body = new JsonObject();
        JsonArray messages = new JsonArray();
        messages.add(msg("system", system));
        messages.add(msg("user", user));
        body.add("messages", messages);
        Map<String, JsonElement> resp = Player2ApiDispatcher.route(mod, clientId, "POST", "/v1/chat/completions", body, billing);
        if (resp == null || !resp.containsKey("choices")) {
            throw new IllegalStateException("Invalid Player2 response: " + (resp == null ? "null" : resp.keySet()));
        }
        JsonArray choices = resp.get("choices").getAsJsonArray();
        return choices.get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private static List<String> chunks(String s, int size) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i += size) out.add(s.substring(i, Math.min(s.length(), i + size)));
        return out;
    }
}
