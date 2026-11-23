package dev.yucktheslime.mcaplayer2;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.yucktheslime.mcaplayer2.ai.CompanionBrain;
import dev.yucktheslime.mcaplayer2.body.CompanionBodyStore;
import dev.yucktheslime.mcaplayer2.body.CompanionBodySync;
import dev.yucktheslime.mcaplayer2.command.CompanionBodyCommand;
import dev.yucktheslime.mcaplayer2.command.NpcCommand;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import dev.yucktheslime.mcaplayer2.memory.ConversationMemoryStore;
import dev.yucktheslime.mcaplayer2.memory.ItemKnowledgeStore;
import dev.yucktheslime.mcaplayer2.memory.NpcMemoryStore;
import dev.yucktheslime.mcaplayer2.registry.ModRegistry;
import dev.yucktheslime.mcaplayer2.world.WorldEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Trade of Immersion - loader-agnostic entry point (called from the NeoForge and Fabric entry classes).
 *
 * <ul>
 *   <li>{@code entity.CompanionEntity}: the companion itself (inventory, riding, combat, item use).</li>
 *   <li>{@code ai.CompanionBrain}: the AI loop (OpenAI-compatible / Kagrenac / mock backends).</li>
 *   <li>{@code world.WorldEvents}: chat, deaths and attacks become memories and turns.</li>
 *   <li>{@code villager.VillagerTalk}: companions talk with MCA villagers through the same backend.</li>
 * </ul>
 */
public final class McaPlayer2Bridge {
    public static final String MOD_ID = "mcaplayer2";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private McaPlayer2Bridge() {
    }

    public static void init() {
        BridgeConfig.load();
        LOGGER.info("A Trade of Immersion initializing (enabled={}, backend={})", BridgeConfig.get().enabled, BridgeConfig.get().aiBackend);
        ModRegistry.init();
        CompanionBodySync.init();
        WorldEvents.init();
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            CompanionBodyCommand.register(dispatcher);
            NpcCommand.register(dispatcher);
        });
        LifecycleEvent.SERVER_STARTED.register(server -> {
            CompanionBodyStore.get().load(server);
            ConversationMemoryStore.get().open(server);
            NpcMemoryStore.get().open(server);
            ItemKnowledgeStore.get().open(server);
            CompanionBrain.start(server);
        });
        LifecycleEvent.SERVER_LEVEL_SAVE.register(level -> {
            ConversationMemoryStore.get().saveDirty();
            NpcMemoryStore.get().saveDirty();
            ItemKnowledgeStore.get().save();
        });
        LifecycleEvent.SERVER_STOPPING.register(server -> {
            CompanionBrain.stop();
            CompanionBodyStore.get().save();
            CompanionBodyStore.get().unload();
            ConversationMemoryStore.get().saveDirty();
            ConversationMemoryStore.get().close();
            NpcMemoryStore.get().close();
            ItemKnowledgeStore.get().close();
        });
        TickEvent.SERVER_POST.register(CompanionBrain::tick);
    }
}
