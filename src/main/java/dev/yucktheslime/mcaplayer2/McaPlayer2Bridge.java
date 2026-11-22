package dev.yucktheslime.mcaplayer2;

import dev.yucktheslime.mcaplayer2.bridge.ConversationBridge;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import dev.yucktheslime.mcaplayer2.context.OwnedCompanionContextProvider;
import dev.yucktheslime.mcaplayer2.context.PromptContextProvider;
import dev.yucktheslime.mcaplayer2.goal.TalkToMcaVillagerGoal;
import dev.yucktheslime.mcaplayer2.memory.ConversationMemoryStore;
import dev.yucktheslime.mcaplayer2.util.McaVillagerUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entry point.
 *
 * <ul>
 *   <li>PlayerEngine side: {@code mixin.PlayerEngineCommandsMixin} registers the LLM-callable
 *       {@code talk_to_villager} / {@code nearby_villagers} commands on every companion, and
 *       {@code mixin.PlayerEngineConversationManagerMixin} captures every line a companion speaks.</li>
 *   <li>MCA side: a server-tick listener ({@link ConversationBridge#onServerTick}) lets villagers notice
 *       companions in range and greet / answer them through MCA's villager chat.</li>
 *   <li>Memory: {@link ConversationMemoryStore} keeps a JSON log per villager in the world save.</li>
 * </ul>
 */
public class McaPlayer2Bridge implements ModInitializer {
    public static final String MOD_ID = "mcaplayer2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BridgeConfig.load();
        PromptContextProvider.register(OwnedCompanionContextProvider.INSTANCE);
        LOGGER.info("MCA-Player2 Bridge initializing (enabled={})", BridgeConfig.get().enabled);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ConversationMemoryStore.get().open(server);
            ConversationBridge.get().reset();
        });
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> ConversationMemoryStore.get().saveDirty());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ConversationMemoryStore.get().saveDirty();
            ConversationMemoryStore.get().close();
            ConversationBridge.get().reset();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> ConversationBridge.get().onServerTick(server));
    }

    /**
     * Helper from the original scaffold: build a talk goal for the nearest MCA villager. Run it with
     * {@code controller.runUserTask(goal)} on the companion's {@code PlayerEngineController}.
     */
    public static TalkToMcaVillagerGoal createTalkGoal(LivingEntity companion, double searchRadius) {
        return McaVillagerUtil.findNearestMcaVillager(companion, searchRadius)
                .map(villager -> new TalkToMcaVillagerGoal(companion, villager))
                .orElse(null);
    }
}
