package dev.yucktheslime.mcaplayer2.player2;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.AgentConversationData;
import com.player2.playerengine.player2api.Event;
import com.player2.playerengine.player2api.manager.ConversationManager;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
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
 *       {@code Event.UserMessage(String message, String userName)} is how PlayerEngine itself feeds chat in;
 *       {@code Event.InfoMessage(String)} is a system/info note.</li>
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

    /** Adds background context to the companion's next prompt (does not by itself demand a reply). */
    public static void injectInfo(AgentConversationData d, String info) {
        try {
            d.onEvent(new Event.InfoMessage(info));
        } catch (Throwable t) {
            McaPlayer2Bridge.LOGGER.warn("Failed to inject info into companion {}", name(d), t);
        }
    }

    /**
     * Delivers a line spoken by a villager to the companion as if a chat participant named
     * {@code speakerName} had said it. The companion's LLM will answer on its next turn.
     */
    public static void deliverSpeech(AgentConversationData d, String speakerName, String line) {
        try {
            d.onEvent(new Event.UserMessage(line, speakerName));
        } catch (Throwable t) {
            McaPlayer2Bridge.LOGGER.warn("Failed to deliver villager speech to companion {}", name(d), t);
        }
    }
}
