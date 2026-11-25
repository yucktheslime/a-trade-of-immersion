package dev.yucktheslime.tradeofimmersion.chatfilter;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.yucktheslime.tradeofimmersion.ai.CompanionBrain;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.dialogue.DialogueNet;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import dev.yucktheslime.tradeofimmersion.util.McaVillagerUtil;
import dev.yucktheslime.tradeofimmersion.villager.VillagerTalk;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side wiring for {@link ChatRouter}: gathers nearby NPCs, records overhearing, and
 * starts AI turns (or leaves the line to MCA's ChatAI).
 */
public final class ChatFilterHooks {
    /** How far a named MCA villager can be and still be addressed (MCA's ChatAI uses 32). */
    public static final double VILLAGER_NAME_RADIUS = 32.0;
    /** An owned companion called by name can be a bit further than {@link ToiConfig#chatHearingRadius}. */
    public static final double OWNER_NAME_RADIUS = 64.0;

    private static final NpcTurnCooldown COOLDOWN = new NpcTurnCooldown();
    private static final OpenConversations CONVERSATIONS = new OpenConversations();

    private ChatFilterHooks() {
    }

    public static void init() {
        LifecycleEvent.SERVER_STARTED.register(s -> {
            COOLDOWN.clear();
            CONVERSATIONS.clear();
            McaChatAi.detectAndLog();
        });
    }

    public static NpcTurnCooldown cooldown() {
        return COOLDOWN;
    }

    public static OpenConversations conversations() {
        return CONVERSATIONS;
    }

    public static ChatFilterSettings settings(ToiConfig cfg) {
        ChatFilterSettings s = new ChatFilterSettings();
        s.enabled = cfg.enabled;
        s.chatFilterEnabled = cfg.chatFilterEnabled;
        s.ownerChatWithoutAddress = cfg.ownerChatWithoutAddress;
        s.chatContinueSeconds = cfg.chatContinueSeconds;
        s.aiVillagerPublicChat = cfg.aiVillagerPublicChat;
        s.aiSkipVillagersWhenMcaChatAi = cfg.aiSkipVillagersWhenMcaChatAi;
        s.aiNpcChatCooldownSeconds = cfg.aiNpcChatCooldownSeconds;
        s.chatHearingRadius = cfg.chatHearingRadius;
        s.ownerChatRadius = cfg.ownerChatRadius;
        return s;
    }

    /** Public signed player chat (Architectury {@code ChatEvent.RECEIVED}). Server thread. */
    public static void handlePublicChat(ServerPlayer player, String raw) {
        ToiConfig cfg = ToiConfig.get();
        if (!cfg.enabled || player == null) return;
        String text = raw == null ? "" : raw.trim();
        if (!ChatFilter.isDialogue(text)) return;

        List<ChatCandidate> nearby = collect(player, cfg);
        String speaker = player.getGameProfile().getName();
        for (ChatCandidate c : nearby) {
            if (c.kind() == ChatCandidate.Kind.COMPANION && c.inHearingRange()) {
                NpcMemoryStore.get().event(c.id(), "heard", speaker + " said: " + text);
            }
        }
        if (!cfg.chatFilterEnabled) return;

        ChatFilterSettings settings = settings(cfg);
        Set<UUID> hearing = new HashSet<>();
        for (ChatCandidate c : nearby) if (c.inHearingRange()) hearing.add(c.id());
        long now = System.currentTimeMillis();
        UUID continued = CONVERSATIONS.current(player.getUUID(), now, settings.continueMs(), hearing);
        RouteDecision d = ChatRouter.route(
                ChatRouter.Channel.PUBLIC_CHAT, text, nearby, null, continued,
                settings, COOLDOWN, now, McaChatAi.enabled());

        switch (d.action()) {
            case AI_TURN -> {
                for (ChatCandidate t : d.targets()) dispatch(player, t, text, now);
            }
            case DECIDE -> decide(player, d.targets(), text, now);
            case RATE_LIMITED, MCA_CHAT_AI, OVERHEAR_ONLY, SKIP, DISABLED -> {
                // overhearing already recorded; MCA ChatAI answers on its own hook
            }
        }
    }

    /**
     * A line from the dialogue box (typed or transcribed) to an already-chosen NPC.
     * Returns whether an AI turn was started. The caller still echoes the line and records overhearing.
     */
    public static boolean handleDialogueTalk(ServerPlayer player, Entity target, String text) {
        ToiConfig cfg = ToiConfig.get();
        ChatCandidate cand = candidateOf(player, target, cfg);
        if (cand == null) {
            DialogueNet.info(player, (target == null ? "Nobody" : target.getName().getString()) + " can't talk.");
            return false;
        }
        ChatFilterSettings settings = settings(cfg);
        long now = System.currentTimeMillis();
        RouteDecision d = ChatRouter.route(
                ChatRouter.Channel.DIALOGUE_BOX, text, List.of(cand), cand, null,
                settings, COOLDOWN, now, McaChatAi.enabled());
        if (d.action() == RouteDecision.Action.AI_TURN) {
            dispatch(player, cand, text, now);
            return true;
        }
        if (d.action() == RouteDecision.Action.RATE_LIMITED) {
            long wait = Math.max(1, (COOLDOWN.remainingMs(cand.id(), now, settings.cooldownMs()) + 999) / 1000);
            DialogueNet.info(player, cand.name() + " needs a moment (try again in " + wait + "s).");
            return false;
        }
        if (d.action() == RouteDecision.Action.SKIP || d.action() == RouteDecision.Action.DISABLED) {
            DialogueNet.info(player, "That doesn't look like something to say.");
            return false;
        }
        return false;
    }

    private static void dispatch(ServerPlayer player, ChatCandidate t, String text, long now) {
        String who = player.getGameProfile().getName();
        Entity e = player.level() instanceof ServerLevel sl ? sl.getEntity(t.id()) : null;
        if (t.kind() == ChatCandidate.Kind.COMPANION) {
            CompanionEntity c = e instanceof CompanionEntity ce ? ce : CompanionBrain.find(player.server, t.id());
            if (c == null) return;
            CompanionBrain.onChat(c, who, text);
        } else if (e instanceof VillagerEntityMCA v) {
            VillagerTalk.playerSpeaks(player, v, text);
        } else {
            return;
        }
        COOLDOWN.mark(t.id(), now);
        CONVERSATIONS.remember(player.getUUID(), t.id(), now);
    }

    /**
     * Several of this player's companions can hear an unaddressed line: let Kagrenac pick when
     * {@code kagrenacUseDecision} is on, otherwise the closest.
     */
    private static void decide(ServerPlayer player, List<ChatCandidate> mine, String text, long now) {
        if (mine.isEmpty()) return;
        ChatCandidate baseline = mine.get(0);
        var k = dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld.kagrenac();
        ToiConfig cfg = ToiConfig.get();
        if (k != null && cfg.kagrenacUseDecision && mine.size() >= 2 && k.has("decision")) {
            List<dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend.Candidate> cands = new ArrayList<>();
            Map<String, UUID> ids = new java.util.HashMap<>();
            for (ChatCandidate t : mine) {
                CompanionEntity c = CompanionBrain.find(player.server, t.id());
                if (c == null) continue;
                String id = dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend.companionId(c.getUUID().toString());
                ids.put(id, c.getUUID());
                String cues = c.getGender() + " companion, " + (c.getPersona().isBlank() ? "" : c.getPersona() + ", ")
                        + (int) Math.round(c.distanceTo(player)) + " blocks away" + (c.getTask() != null ? ", busy: " + c.getTask().describe() : "");
                cands.add(new dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend.Candidate(id, c.name(), cues));
            }
            if (cands.isEmpty()) {
                dispatch(player, baseline, text, now);
                return;
            }
            String baseId = dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend.companionId(baseline.id().toString());
            UUID fallback = baseline.id();
            String speaker = player.getGameProfile().getName();
            k.decide(text, baseId, cands).thenAccept(chosen -> player.server.execute(() -> {
                UUID id = ids.getOrDefault(chosen, fallback);
                ChatCandidate picked = null;
                for (ChatCandidate t : mine) if (t.id().equals(id)) { picked = t; break; }
                if (picked == null) picked = baseline;
                dispatch(player, picked, text, System.currentTimeMillis());
            }));
            return;
        }
        dispatch(player, baseline, text, now);
    }

    static List<ChatCandidate> collect(ServerPlayer player, ToiConfig cfg) {
        double hear = Math.max(1.0, cfg.chatHearingRadius);
        double villagerR = Math.max(hear, VILLAGER_NAME_RADIUS);
        double companionR = Math.max(villagerR, OWNER_NAME_RADIUS);
        double lookR = Math.max(1.0, cfg.chatLookRange);
        double degrees = Math.max(1.0, cfg.chatLookDegrees);

        List<CompanionEntity> companions = player.level().getEntitiesOfClass(
                CompanionEntity.class, player.getBoundingBox().inflate(companionR),
                c -> c.isAlive() && c.distanceTo(player) <= companionR);
        List<VillagerEntityMCA> villagers = McaVillagerUtil.villagersNear(player, villagerR);

        List<Entity> lookPool = new ArrayList<>();
        lookPool.addAll(companions);
        lookPool.addAll(villagers);
        Entity looked = lookedAt(player, lookPool, degrees, lookR);

        List<ChatCandidate> out = new ArrayList<>();
        for (CompanionEntity c : companions) {
            out.add(new ChatCandidate(
                    c.getUUID(), c.name(), List.of(), ChatCandidate.Kind.COMPANION,
                    c.isOwner(player), c.distanceTo(player),
                    c.distanceTo(player) <= hear, looked == c));
        }
        for (VillagerEntityMCA v : villagers) {
            List<String> aliases = new ArrayList<>();
            try {
                String nick = v.getNickname(player.getUUID());
                if (nick != null && !nick.isBlank()) aliases.add(nick);
            } catch (Throwable ignored) {
            }
            out.add(new ChatCandidate(
                    v.getUUID(), McaVillagerUtil.name(v), aliases, ChatCandidate.Kind.VILLAGER,
                    false, v.distanceTo(player),
                    v.distanceTo(player) <= hear, looked == v));
        }
        return out;
    }

    static ChatCandidate candidateOf(ServerPlayer player, Entity e, ToiConfig cfg) {
        if (e == null || !e.isAlive()) return null;
        double hear = Math.max(1.0, cfg.chatHearingRadius);
        if (e instanceof CompanionEntity c) {
            return new ChatCandidate(
                    c.getUUID(), c.name(), List.of(), ChatCandidate.Kind.COMPANION,
                    c.isOwner(player), c.distanceTo(player),
                    c.distanceTo(player) <= hear, true);
        }
        if (e instanceof VillagerEntityMCA v) {
            List<String> aliases = new ArrayList<>();
            try {
                String nick = v.getNickname(player.getUUID());
                if (nick != null && !nick.isBlank()) aliases.add(nick);
            } catch (Throwable ignored) {
            }
            return new ChatCandidate(
                    v.getUUID(), McaVillagerUtil.name(v), aliases, ChatCandidate.Kind.VILLAGER,
                    false, v.distanceTo(player),
                    v.distanceTo(player) <= hear, true);
        }
        return null;
    }

    /** The entity the player is looking straight at (within {@code degrees}, {@code range} blocks), if any. */
    static Entity lookedAt(ServerPlayer player, List<? extends Entity> near, double degrees, double range) {
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1f);
        Entity best = null;
        double bestDot = Math.cos(Math.toRadians(degrees));
        for (Entity e : near) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double len = to.length();
            if (len < 0.5 || len > range) continue;
            double dot = to.scale(1 / len).dot(look);
            if (dot > bestDot && player.hasLineOfSight(e)) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }
}
