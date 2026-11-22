package dev.yucktheslime.mcaplayer2.bridge;

import com.player2.playerengine.player2api.AgentConversationData;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.config.BridgeConfig;
import dev.yucktheslime.mcaplayer2.context.OwnedCompanionContextProvider;
import dev.yucktheslime.mcaplayer2.context.PromptContextProvider;
import dev.yucktheslime.mcaplayer2.util.McaVillagerUtil;
import dev.yucktheslime.mcaplayer2.util.VillagerProfile;
import dev.yucktheslime.mcaplayer2.memory.ConversationMemoryStore;
import dev.yucktheslime.mcaplayer2.memory.ConversationMemoryStore.Speaker;
import dev.yucktheslime.mcaplayer2.player2.CompanionAdapter;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The two-way bridge.
 *
 * <ul>
 *   <li><b>Villager -> companion</b>: every {@code scanIntervalTicks} the server scans around each live
 *       Player2 companion for MCA villagers. A villager that hasn't greeted that companion within
 *       {@code greetCooldownTicks} greets it through MCA's chat pipeline, and the companion receives
 *       context (MCA profile + remembered exchanges) and the greeting as an incoming message.</li>
 *   <li><b>Companion -> villager</b>: {@code PlayerEngineConversationManagerMixin} reports every line a
 *       companion's LLM speaks. If an MCA villager is in range and is addressed by name (or is already
 *       in a conversation with that companion), the villager answers with a line generated from the
 *       companion's words, and that answer is fed back to the companion.</li>
 * </ul>
 * A per-pair turn limit stops two AIs from talking forever.
 */
public final class ConversationBridge {
    private static final ConversationBridge INSTANCE = new ConversationBridge();

    private final Map<PairKey, Exchange> exchanges = new ConcurrentHashMap<>();
    private long tickCounter;

    public static ConversationBridge get() {
        return INSTANCE;
    }

    public void reset() {
        exchanges.clear();
        tickCounter = 0;
    }

    // ---------------------------------------------------------------- tick / proximity (MCA side)

    public void onServerTick(MinecraftServer server) {
        BridgeConfig cfg = BridgeConfig.get();
        if (!cfg.enabled) return;
        tickCounter++;
        boolean scan = tickCounter % Math.max(1, cfg.scanIntervalTicks) == 0;

        for (AgentConversationData companion : CompanionAdapter.liveCompanions()) {
            LivingEntity ce = CompanionAdapter.safeEntity(companion);
            if (ce == null) continue;
            long now = ce.level().getGameTime();

            if (cfg.villagerLooksAtCompanion) {
                for (Map.Entry<PairKey, Exchange> en : exchanges.entrySet()) {
                    if (!en.getKey().companion.equals(CompanionAdapter.id(companion))) continue;
                    Exchange ex = en.getValue();
                    if (ex.villager != null && ex.isActive(now, cfg) && ex.villager.isAlive()) {
                        McaVillagerUtil.lookAt(ex.villager, ce);
                    }
                }
            }

            if (!scan) continue;
            List<VillagerEntityMCA> near = McaVillagerUtil.villagersNear(ce, cfg.detectionRadius);
            for (VillagerEntityMCA v : near) {
                if (!McaVillagerUtil.canTalk(v)) continue;
                Exchange ex = exchange(companion, v);
                if (ex.lastGreetTick != Long.MIN_VALUE && now - ex.lastGreetTick < cfg.greetCooldownTicks) continue;
                beginConversation(companion, v, false);
                break; // at most one new greeting per companion per scan
            }
        }
    }

    /**
     * Opens a conversation: injects MCA context into the companion's prompt and makes the villager
     * greet it. Called from the proximity scan and from {@code TalkToMcaVillagerGoal}.
     *
     * @param initiatedByCompanion true if the companion walked up on purpose (talk_to_villager)
     */
    public void beginConversation(AgentConversationData companion, VillagerEntityMCA villager, boolean initiatedByCompanion) {
        LivingEntity ce = CompanionAdapter.safeEntity(companion);
        if (ce == null || !villager.isAlive()) return;
        BridgeConfig cfg = BridgeConfig.get();
        long now = ce.level().getGameTime();
        Exchange ex = exchange(companion, villager);
        ex.lastGreetTick = now;
        ex.turns = 0;
        ex.lastActivityTick = now;

        String companionName = CompanionAdapter.name(companion);
        Player owner = CompanionAdapter.owner(companion);
        VillagerProfile profile = McaVillagerUtil.profile(villager, owner);
        List<String> history = ConversationMemoryStore.get().recentLines(villager.getUUID(), companionName, cfg.historyLinesInPrompt);

        String context = buildCompanionContext(profile, history, initiatedByCompanion);
        List<String> extra = PromptContextProvider.collect(companion, villager);
        if (!extra.isEmpty()) context += "\nAround you:\n" + String.join("\n", extra);
        CompanionAdapter.injectInfo(companion, context);

        if (!McaVillagerUtil.canTalk(villager)) return;
        MinecraftServer server = ce.getServer();
        VillagerReplyGenerator.greeting(profile, companionName, history, OwnedCompanionContextProvider.INSTANCE.villagerOwnLines(villager))
                .thenAccept(line -> runOnServer(server, () -> villagerSpeaks(companion, villager, line, now)));
    }

    // ---------------------------------------------------------------- companion speech (PlayerEngine side)

    /**
     * Called (from the mixin) whenever a companion's LLM says something out loud. May be called off
     * the server thread, so work is re-scheduled onto it.
     */
    public void onCompanionSpoke(AgentConversationData companion, String message) {
        if (!BridgeConfig.get().enabled || message == null || message.isBlank()) return;
        LivingEntity ce = CompanionAdapter.safeEntity(companion);
        if (ce == null || ce.getServer() == null) return;
        runOnServer(ce.getServer(), () -> handleCompanionLine(companion, ce, message));
    }

    private void handleCompanionLine(AgentConversationData companion, LivingEntity ce, String message) {
        BridgeConfig cfg = BridgeConfig.get();
        long now = ce.level().getGameTime();
        List<VillagerEntityMCA> near = McaVillagerUtil.villagersNear(ce, cfg.detectionRadius);
        if (near.isEmpty()) return;

        VillagerEntityMCA target = null;
        String lower = message.toLowerCase(Locale.ROOT);
        for (VillagerEntityMCA v : near) { // addressed by (first) name?
            String first = McaVillagerUtil.name(v).split(" ")[0].toLowerCase(Locale.ROOT);
            if (first.length() >= 2 && lower.contains(first)) {
                target = v;
                break;
            }
        }
        if (target == null) { // otherwise: the villager we're already talking with
            Exchange best = null;
            for (VillagerEntityMCA v : near) {
                Exchange ex = exchanges.get(new PairKey(CompanionAdapter.id(companion), v.getUUID()));
                if (ex != null && ex.isActive(now, cfg) && (best == null || ex.lastActivityTick > best.lastActivityTick)) {
                    best = ex;
                    target = v;
                }
            }
        }
        if (target == null || !McaVillagerUtil.canTalk(target)) return;

        Exchange ex = exchange(companion, target);
        if (!ex.isActive(now, cfg)) ex.turns = 0;
        String companionName = CompanionAdapter.name(companion);
        ConversationMemoryStore.get().append(target.getUUID(), McaVillagerUtil.name(target), companionName,
                Speaker.COMPANION, message, now);
        ex.lastActivityTick = now;
        if (ex.turns >= cfg.maxTurnsPerConversation) {
            McaPlayer2Bridge.LOGGER.debug("Turn limit reached for {} <-> {}", companionName, McaVillagerUtil.name(target));
            return;
        }

        VillagerProfile profile = McaVillagerUtil.profile(target, CompanionAdapter.owner(companion));
        List<String> history = ConversationMemoryStore.get().recentLines(target.getUUID(), companionName, cfg.historyLinesInPrompt);
        VillagerEntityMCA v = target;
        MinecraftServer server = ce.getServer();
        VillagerReplyGenerator.reply(profile, companionName, history, message, OwnedCompanionContextProvider.INSTANCE.villagerOwnLines(v))
                .thenAccept(line -> runOnServer(server, () -> villagerSpeaks(companion, v, line, now)));
    }

    /** Game tick of the last line exchanged between this companion and villager, or {@code Long.MIN_VALUE}. */
    public long lastActivity(AgentConversationData companion, VillagerEntityMCA villager) {
        Exchange ex = exchanges.get(new PairKey(CompanionAdapter.id(companion), villager.getUUID()));
        return ex == null ? Long.MIN_VALUE : ex.lastActivityTick;
    }

    // ---------------------------------------------------------------- shared

    private void villagerSpeaks(AgentConversationData companion, VillagerEntityMCA villager, String line, long startedAt) {
        if (line == null || line.isBlank() || !villager.isAlive() || villager.isRemoved()) return;
        LivingEntity ce = CompanionAdapter.safeEntity(companion);
        if (ce == null || ce.distanceTo(villager) > BridgeConfig.get().detectionRadius * 2) return;

        String vName = McaVillagerUtil.name(villager);
        String cName = CompanionAdapter.name(companion);
        McaVillagerUtil.say(villager, line);
        long now = villager.level().getGameTime();
        ConversationMemoryStore.get().append(villager.getUUID(), vName, cName, Speaker.VILLAGER, line, now);

        Exchange ex = exchange(companion, villager);
        ex.turns++;
        ex.lastActivityTick = now;
        // Feed the villager's words to the companion's LLM as an incoming message.
        CompanionAdapter.deliverSpeech(companion, vName + " (MCA villager)", line);
    }

    static String buildCompanionContext(VillagerProfile p, List<String> history, boolean initiatedByCompanion) {
        StringBuilder sb = new StringBuilder();
        sb.append("[MCA villager nearby] ").append(p.describe());
        sb.append(initiatedByCompanion ? " You walked over to talk to " : " You are close to ")
                .append(p.name()).append(". Address them by name, speak to them directly in your normal chat, ")
                .append("and keep it short and natural; they can hear you and will answer. ");
        if (p.hearts() != null) {
            sb.append("Your owner's relationship with ").append(p.name()).append(" is ")
                    .append(VillagerProfile.heartsLabel(p.hearts())).append(", so act accordingly. ");
        }
        if (history.isEmpty()) {
            sb.append("You have never spoken with ").append(p.name()).append(" before.");
        } else {
            sb.append("What you remember from earlier conversations with ").append(p.name()).append(":\n")
                    .append(String.join("\n", history));
        }
        return sb.toString();
    }

    private Exchange exchange(AgentConversationData companion, VillagerEntityMCA v) {
        Exchange ex = exchanges.computeIfAbsent(new PairKey(CompanionAdapter.id(companion), v.getUUID()), k -> new Exchange());
        ex.villager = v;
        return ex;
    }

    private static void runOnServer(MinecraftServer server, Runnable r) {
        if (server == null) return;
        server.execute(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                McaPlayer2Bridge.LOGGER.error("Bridge task failed", t);
            }
        });
    }

    private record PairKey(UUID companion, UUID villager) {
        PairKey {
            Objects.requireNonNull(companion);
            Objects.requireNonNull(villager);
        }
    }

    private static final class Exchange {
        long lastGreetTick = Long.MIN_VALUE;
        long lastActivityTick = Long.MIN_VALUE;
        int turns;
        VillagerEntityMCA villager;

        boolean isActive(long now, BridgeConfig cfg) {
            return lastActivityTick != Long.MIN_VALUE && now - lastActivityTick < cfg.conversationResetTicks;
        }
    }
}
