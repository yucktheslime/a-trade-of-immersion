package dev.yucktheslime.mcaplayer2;

import dev.yucktheslime.mcaplayer2.bridge.ConversationBridge;
import dev.yucktheslime.mcaplayer2.cobblemon.CobblemonHooks;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import dev.yucktheslime.mcaplayer2.context.OwnedCompanionContextProvider;
import dev.yucktheslime.mcaplayer2.context.PromptContextProvider;
import dev.yucktheslime.mcaplayer2.goal.TalkToMcaVillagerGoal;
import dev.yucktheslime.mcaplayer2.memory.ConversationMemoryStore;
import dev.yucktheslime.mcaplayer2.util.McaVillagerUtil;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic entry point; called from {@code neoforge.McaPlayer2BridgeNeoForge} and
 * {@code fabric.McaPlayer2BridgeFabric}. Events go through Architectury API so they work on both loaders.
 *
 * <ul>
 *   <li>PlayerEngine side: {@code mixin.PlayerEngineCommandsMixin} registers the LLM-callable
 *       {@code talk_to_villager} / {@code nearby_villagers} / {@code pokemon_party} (if Cobblemon is present)
 *       commands on every companion, and
 *       {@code mixin.PlayerEngineConversationManagerMixin} captures every line a companion speaks.</li>
 *   <li>MCA side: a server-tick listener ({@link ConversationBridge#onServerTick}) lets villagers notice
 *       companions in range and greet / answer them through MCA's villager chat.</li>
 *   <li>Memory: {@link ConversationMemoryStore} keeps a JSON log per villager in the world save.</li>
 *   <li>Cobblemon (optional): {@link dev.yucktheslime.mcaplayer2.cobblemon.CobblemonHooks} enables a
 *       per-companion party store when Cobblemon is installed; the bridge still loads without it.</li>
 * </ul>
 */
public final class McaPlayer2Bridge {
    public static final String MOD_ID = "mcaplayer2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private McaPlayer2Bridge() {
    }

    public static void init() {
        BridgeConfig.load();
        PromptContextProvider.register(OwnedCompanionContextProvider.INSTANCE);
        CobblemonHooks.init();
        LOGGER.info("MCA-Player2 Bridge initializing (enabled={})", BridgeConfig.get().enabled);

        dev.yucktheslime.mcaplayer2.body.CompanionBodySync.init();
        dev.architectury.event.events.common.CommandRegistrationEvent.EVENT.register(
                (dispatcher, registry, selection) -> dev.yucktheslime.mcaplayer2.command.CompanionBodyCommand.register(dispatcher));
        LifecycleEvent.SERVER_STARTED.register(server -> {
            dev.yucktheslime.mcaplayer2.body.CompanionBodyStore.get().load(server);
            ConversationMemoryStore.get().open(server);
            ConversationBridge.get().reset();
            CobblemonHooks.onServerStarted(server);
        });
        LifecycleEvent.SERVER_LEVEL_SAVE.register(level -> ConversationMemoryStore.get().saveDirty());
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            dev.yucktheslime.mcaplayer2.body.CompanionBodyStore.get().save();
            dev.yucktheslime.mcaplayer2.body.CompanionBodyStore.get().unload();
            CobblemonHooks.onServerStopping();
            ConversationMemoryStore.get().saveDirty();
            ConversationMemoryStore.get().close();
            ConversationBridge.get().reset();
        });
        TickEvent.SERVER_POST.register(server -> ConversationBridge.get().onServerTick(server));
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
