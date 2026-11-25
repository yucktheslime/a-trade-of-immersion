package dev.yucktheslime.tradeofimmersion.life;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.life.hooks.LifeHooks;
import dev.yucktheslime.tradeofimmersion.life.hooks.LifeWatcher;
import dev.yucktheslime.tradeofimmersion.life.marriage.RomanceTicker;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipStore;
import net.minecraft.server.MinecraftServer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 2, "MCA life": life hooks and memory, pregnancy with phases, companion marriage in all directions, the
 * relationship layer and the family-tree restriction. One call from {@code TradeOfImmersion.init}; everything else
 * hooks in through its own events, mixins ({@code tradeofimmersion.life.mixins.json}) and config
 * ({@code config/tradeofimmersion-life.json}).
 */
public final class Life {
    private static volatile MinecraftServer server;
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Life() {
    }

    public static MinecraftServer server() {
        return server;
    }

    public static void warnOnce(String what, Throwable t) {
        if (WARNED.add(what)) TradeOfImmersion.LOGGER.warn("[tradeofimmersion] Phase 2 '{}' hook failed (shown once)", what, t);
    }

    /**
     * Ask a companion to react in character (pregnancy, birth, jealousy, a proposal...). Notes the
     * interaction for Phase 4's active-NPC cap and skips the LLM turn when that companion is culled.
     */
    public static void react(dev.yucktheslime.tradeofimmersion.entity.CompanionEntity c, String type, String text) {
        if (c == null || text == null || text.isBlank()) return;
        try {
            dev.yucktheslime.tradeofimmersion.ai.CompanionBrain.onEvent(c, type, text);
        } catch (Throwable t) {
            warnOnce("react", t);
        }
    }

    public static void init() {
        LifeConfig.load();
        LifeRegistry.init();
        LifeNet.init();
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> LifeCommand.register(dispatcher));
        LifecycleEvent.SERVER_STARTED.register(s -> {
            server = s;
            RelationshipStore.get().store.open(s);
            Pregnancies.STORE.open(s);
            LifeWatcher.reset();
            LifeNet.reset();
        });
        LifecycleEvent.SERVER_LEVEL_SAVE.register(level -> save());
        LifecycleEvent.SERVER_STOPPING.register(s -> {
            RelationshipStore.get().store.close();
            Pregnancies.STORE.close();
            LifeWatcher.reset();
            server = null;
        });
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            LifeHooks.onDeath(entity);
            return EventResult.pass();
        });
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (amount > 0) LifeHooks.onHurt(entity, source.getEntity());
            return EventResult.pass();
        });
        TickEvent.SERVER_POST.register(Life::tick);
        TradeOfImmersion.LOGGER.info("[tradeofimmersion] MCA life (Phase 2) ready: enabled={}, pregnancyPhases={}, companionMarriage={}, relationships={}",
                LifeConfig.get().enabled, LifeConfig.get().pregnancyPhases, LifeConfig.get().companionMarriage, LifeConfig.get().relationships);
    }

    static void save() {
        RelationshipStore.get().store.save();
        Pregnancies.STORE.save();
    }

    private static void tick(MinecraftServer s) {
        if (server == null || !LifeConfig.get().enabled) return;
        int t = s.getTickCount();
        try {
            if (t % 20 == 0) Pregnancies.tick(s);
            if (t % Math.max(20, LifeConfig.get().lifeScanSeconds * 20) == 7) LifeWatcher.scan(s);
            if (t % RomanceTicker.PERIOD == 13) RomanceTicker.tick(s);
            if (t % 100 == 3) LifeNet.sync(s, false);
        } catch (Throwable e) {
            warnOnce("tick", e);
        }
    }
}
