package dev.yucktheslime.tradeofimmersion;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.yucktheslime.tradeofimmersion.ai.CompanionBrain;
import dev.yucktheslime.tradeofimmersion.body.CompanionBodyStore;
import dev.yucktheslime.tradeofimmersion.body.CompanionBodySync;
import dev.yucktheslime.tradeofimmersion.command.CompanionBodyCommand;
import dev.yucktheslime.tradeofimmersion.command.NpcCommand;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.memory.ConversationMemoryStore;
import dev.yucktheslime.tradeofimmersion.memory.ItemKnowledgeStore;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import dev.yucktheslime.tradeofimmersion.registry.ModRegistry;
import dev.yucktheslime.tradeofimmersion.world.WorldEvents;
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
public final class TradeOfImmersion {
    public static final String MOD_ID = "tradeofimmersion";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private TradeOfImmersion() {
    }

    public static void init() {
        ToiConfig.load();
        LOGGER.info("A Trade of Immersion initializing (enabled={}, backend={})", ToiConfig.get().enabled, ToiConfig.get().aiBackend);
        ModRegistry.init();
        CompanionBodySync.init();
        dev.yucktheslime.tradeofimmersion.voice.VoiceSync.init();
        dev.yucktheslime.tradeofimmersion.menu.CompanionMenu.init();
        WorldEvents.init();
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            CompanionBodyCommand.register(dispatcher);
            NpcCommand.register(dispatcher);
        });
        LifecycleEvent.SERVER_STARTED.register(server -> {
            dev.yucktheslime.tradeofimmersion.config.Migration.migrateWorld(server);
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
        TickEvent.SERVER_POST.register(dev.yucktheslime.tradeofimmersion.body.CompanionLooks::flush);
        TickEvent.SERVER_POST.register(dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld::tick);
    }
}
