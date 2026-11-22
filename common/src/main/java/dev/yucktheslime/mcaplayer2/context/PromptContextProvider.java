package dev.yucktheslime.mcaplayer2.context;

import com.player2.playerengine.player2api.AgentConversationData;
import net.conczin.mca.entity.VillagerEntityMCA;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extra context lines appended to the MCA context the bridge injects into a companion's Player2 prompt
 * when a conversation with a villager opens (proximity greeting or {@code TalkToMcaVillagerGoal}).
 * Register more with {@link #register(PromptContextProvider)}.
 */
@FunctionalInterface
public interface PromptContextProvider {
    List<PromptContextProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    static void register(PromptContextProvider provider) {
        PROVIDERS.add(provider);
    }

    /** @return zero or more context lines; never null. */
    List<String> contextLines(AgentConversationData companion, VillagerEntityMCA villager);

    /** Collects lines from all registered providers, isolating failures. */
    static List<String> collect(AgentConversationData companion, VillagerEntityMCA villager) {
        List<String> out = new java.util.ArrayList<>();
        for (PromptContextProvider p : PROVIDERS) {
            try {
                List<String> lines = p.contextLines(companion, villager);
                if (lines != null) out.addAll(lines);
            } catch (Throwable t) {
                dev.yucktheslime.mcaplayer2.McaPlayer2Bridge.LOGGER.debug("Context provider failed", t);
            }
        }
        return out;
    }
}
