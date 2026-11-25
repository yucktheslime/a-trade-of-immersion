package dev.yucktheslime.tradeofimmersion.budget;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.memory.ConversationMemoryStore;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 4 AI budget: proximity culling, an active-NPC cap, an LLM concurrency queue, and
 * RAM release for idle NPC memory.
 *
 * <p><b>Hook for other phases:</b> call {@link #isActive(Entity)} (or {@link #isActive(UUID)})
 * before starting an AI / LLM turn or a heavy per-NPC tick. Inactive NPCs still walk, follow
 * and fight with ordinary Minecraft AI; their AI turns stay queued until a slot opens.
 * Mark a conversation or interaction with {@link #noteInteraction(UUID)} so that NPC is
 * preferred when the cap has to choose.
 *
 * <p>An NPC is active when it is within {@code aiProximityRange} of a player (or the range is
 * 0 = no culling) and it is among the {@code aiMaxActiveNpcs} highest-priority candidates
 * (0 = no cap). Priority: the player is looking at it, it was recently spoken to or
 * interacted with, it is an owned companion, then closer distance. LLM calls themselves are
 * also gated by {@code aiMaxConcurrentRequests} via {@link LlmQueue}.
 */
public final class AiBudget {
    private static final int TICK_PERIOD = 10;
    private static final double LOOK_MAX = 12.0;
    private static final double LOOK_COS = Math.cos(Math.toRadians(10));

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> RECENT = new ConcurrentHashMap<>();
    private static final MemoryLru COMPANION_MEM = new MemoryLru();
    private static final MemoryLru VILLAGER_MEM = new MemoryLru();
    private static volatile MinecraftServer server;
    private static volatile int lastActive, lastCandidates;

    private AiBudget() {
    }

    public static void start(MinecraftServer srv) {
        server = srv;
        ACTIVE.clear();
        RECENT.clear();
        COMPANION_MEM.clear();
        VILLAGER_MEM.clear();
        ToiConfig cfg = ToiConfig.get();
        LlmQueue.get().setLimit(cfg.aiMaxConcurrentRequests);
        NpcMemoryStore.onAccess = AiBudget::touchCompanionMemory;
        ConversationMemoryStore.onAccess = AiBudget::touchVillagerMemory;
        TradeOfImmersion.LOGGER.info(
                "AI budget: proximity {} blocks, max {} active NPCs, max {} concurrent LLM requests, memory cache {} (idle {}s)",
                cfg.aiProximityRange, cfg.aiMaxActiveNpcs, cfg.aiMaxConcurrentRequests,
                cfg.aiMemoryCacheSize, cfg.aiMemoryIdleSeconds);
        refresh(srv);
    }

    public static void stop() {
        evictAllMemory();
        server = null;
        ACTIVE.clear();
        RECENT.clear();
        COMPANION_MEM.clear();
        VILLAGER_MEM.clear();
        LlmQueue.get().reset();
        NpcMemoryStore.onAccess = id -> { };
        ConversationMemoryStore.onAccess = id -> { };
    }

    /** Server tick: refresh the active set, evict idle memory, apply the LLM concurrency cap. */
    public static void tick(MinecraftServer srv) {
        if (srv.getTickCount() % TICK_PERIOD != 0) return;
        LlmQueue.get().setLimit(ToiConfig.get().aiMaxConcurrentRequests);
        refresh(srv);
        evictMemory(srv, System.currentTimeMillis());
    }

    /**
     * Whether this NPC may use the AI (LLM calls and heavy AI ticking) right now.
     * Other phases should gate work with this. Server-side only; returns {@code false} on the
     * client. When both {@code aiProximityRange} and {@code aiMaxActiveNpcs} are 0, every NPC
     * is treated as active (legacy behaviour).
     */
    public static boolean isActive(Entity entity) {
        if (entity == null || entity.level().isClientSide) return false;
        if (!entity.isAlive()) return false;
        if (uncapped()) return inRange(entity, ToiConfig.get().aiProximityRange);
        return ACTIVE.contains(entity.getUUID());
    }

    /**
     * UUID form of {@link #isActive(Entity)} for callers that only have an id. If the entity is
     * not in the current snapshot this returns {@code false} (unless the budget is uncapped,
     * in which case it returns {@code true}).
     */
    public static boolean isActive(UUID id) {
        if (id == null) return false;
        if (uncapped()) return true;
        return ACTIVE.contains(id);
    }

    /**
     * Record that a player (or another NPC) just interacted with this NPC, so it jumps the
     * queue for the active cap. Safe to call often; a refresh runs if we are on the server thread.
     */
    public static void noteInteraction(UUID id) {
        if (id == null) return;
        RECENT.put(id, System.currentTimeMillis());
        MinecraftServer srv = server;
        if (srv != null && srv.isSameThread()) refresh(srv);
    }

    /** Persist and drop this NPC's memory from RAM (chunk unload, death, dismiss). Reloads on the next access. */
    public static void onNpcUnloaded(UUID id) {
        if (id == null) return;
        ACTIVE.remove(id);
        unloadCompanionMemory(id);
        unloadVillagerMemory(id);
    }

    public static void touchCompanionMemory(UUID id) {
        if (id != null) COMPANION_MEM.touch(id, System.currentTimeMillis());
    }

    public static void touchVillagerMemory(UUID id) {
        if (id != null) VILLAGER_MEM.touch(id, System.currentTimeMillis());
    }

    public static String status() {
        ToiConfig cfg = ToiConfig.get();
        LlmQueue q = LlmQueue.get();
        return "budget " + lastActive + "/" + (cfg.aiMaxActiveNpcs <= 0 ? "unlim" : cfg.aiMaxActiveNpcs)
                + " active (" + lastCandidates + " in range, proximity " + cfg.aiProximityRange + ")"
                + ", llm " + q.inFlight() + "/" + (cfg.aiMaxConcurrentRequests <= 0 ? "unlim" : cfg.aiMaxConcurrentRequests)
                + " in flight (" + q.queued() + " queued)"
                + ", memory " + COMPANION_MEM.size() + "+" + VILLAGER_MEM.size()
                + "/" + (cfg.aiMemoryCacheSize <= 0 ? "unlim" : cfg.aiMemoryCacheSize);
    }

    public static Set<UUID> activeSnapshot() {
        return Set.copyOf(ACTIVE);
    }

    public static MemoryLru companionMemoryLru() {
        return COMPANION_MEM;
    }

    public static MemoryLru villagerMemoryLru() {
        return VILLAGER_MEM;
    }

    // ------------------------------------------------------------------ refresh

    static void refresh(MinecraftServer srv) {
        ToiConfig cfg = ToiConfig.get();
        List<AiActiveSet.Candidate> raw = collect(srv, cfg);
        Set<UUID> next = AiActiveSet.select(raw, cfg.aiMaxActiveNpcs);
        ACTIVE.clear();
        ACTIVE.addAll(next);
        lastCandidates = AiActiveSet.merge(raw).size();
        lastActive = next.size();
    }

    private static boolean uncapped() {
        ToiConfig cfg = ToiConfig.get();
        return cfg.aiProximityRange <= 0 && cfg.aiMaxActiveNpcs <= 0;
    }

    private static List<AiActiveSet.Candidate> collect(MinecraftServer srv, ToiConfig cfg) {
        List<AiActiveSet.Candidate> raw = new ArrayList<>();
        long now = System.currentTimeMillis();
        long recentMs = Math.max(0, cfg.aiRecentInteractionSeconds) * 1000L;
        double range = cfg.aiProximityRange;
        List<ServerPlayer> players = srv.getPlayerList().getPlayers();

        if (range <= 0) {
            for (ServerLevel level : srv.getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (!isBudgetNpc(e) || !e.isAlive()) continue;
                    double best = Double.POSITIVE_INFINITY;
                    boolean looked = false;
                    boolean owned = false;
                    for (ServerPlayer p : players) {
                        if (p.level() != level) continue;
                        double d = e.distanceToSqr(p);
                        if (d < best) best = d;
                        if (lookingAt(p, e)) looked = true;
                        if (ownedBy(e, p)) owned = true;
                    }
                    if (best == Double.POSITIVE_INFINITY) best = Double.MAX_VALUE;
                    if (!owned) owned = isOwnedCompanion(e);
                    raw.add(new AiActiveSet.Candidate(e.getUUID(),
                            AiActiveSet.rank(looked, isRecent(e.getUUID(), now, recentMs), owned), best));
                }
            }
            return raw;
        }

        if (players.isEmpty()) return raw;
        for (ServerPlayer p : players) {
            if (!p.isAlive()) continue;
            AABB box = p.getBoundingBox().inflate(range);
            for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, AiBudget::isBudgetNpc)) {
                if (!e.isAlive()) continue;
                double d = e.distanceToSqr(p);
                if (d > range * range) continue;
                raw.add(new AiActiveSet.Candidate(e.getUUID(),
                        AiActiveSet.rank(lookingAt(p, e), isRecent(e.getUUID(), now, recentMs), ownedBy(e, p) || isOwnedCompanion(e)),
                        d));
            }
        }
        return raw;
    }

    private static boolean inRange(Entity e, double range) {
        if (range <= 0) return true;
        if (!(e.level() instanceof ServerLevel level)) return false;
        double r2 = range * range;
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && e.distanceToSqr(p) <= r2) return true;
        }
        return false;
    }

    private static boolean isBudgetNpc(Entity e) {
        return e instanceof CompanionEntity || e instanceof VillagerEntityMCA;
    }

    private static boolean isOwnedCompanion(Entity e) {
        return e instanceof CompanionEntity c && c.getOwnerId().isPresent();
    }

    private static boolean ownedBy(Entity e, ServerPlayer p) {
        return e instanceof CompanionEntity c && c.isOwner(p);
    }

    private static boolean isRecent(UUID id, long now, long windowMs) {
        if (windowMs <= 0) return false;
        Long t = RECENT.get(id);
        return t != null && now - t <= windowMs;
    }

    private static boolean lookingAt(ServerPlayer player, Entity e) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1f);
        Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
        double len = to.length();
        if (len < 0.5 || len > LOOK_MAX) return false;
        if (to.scale(1 / len).dot(look) <= LOOK_COS) return false;
        return player.hasLineOfSight(e);
    }

    // ------------------------------------------------------------------ memory

    private static void evictMemory(MinecraftServer srv, long now) {
        ToiConfig cfg = ToiConfig.get();
        int cap = cfg.aiMemoryCacheSize;
        long idleMs = Math.max(0, cfg.aiMemoryIdleSeconds) * 1000L;
        Set<UUID> pinned = Set.copyOf(ACTIVE);
        for (UUID id : COMPANION_MEM.victims(cap, now, idleMs, pinned)) {
            if (entityLoaded(srv, id) && pinned.contains(id)) continue;
            unloadCompanionMemory(id);
        }
        for (UUID id : VILLAGER_MEM.victims(cap, now, idleMs, pinned)) {
            if (entityLoaded(srv, id) && pinned.contains(id)) continue;
            unloadVillagerMemory(id);
        }
        // Also drop RAM for NPCs that have left the world entirely (chunk unload already calls
        // onNpcUnloaded for companions; villagers and missed unloads are caught here).
        for (UUID id : List.copyOf(COMPANION_MEM.snapshotIds())) {
            if (!entityLoaded(srv, id)) unloadCompanionMemory(id);
        }
        for (UUID id : List.copyOf(VILLAGER_MEM.snapshotIds())) {
            if (!entityLoaded(srv, id)) unloadVillagerMemory(id);
        }
    }

    private static boolean entityLoaded(MinecraftServer srv, UUID id) {
        for (ServerLevel level : srv.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) return true;
        }
        return false;
    }

    private static void unloadCompanionMemory(UUID id) {
        NpcMemoryStore.get().unload(id);
        COMPANION_MEM.remove(id);
    }

    private static void unloadVillagerMemory(UUID id) {
        ConversationMemoryStore.get().unload(id);
        VILLAGER_MEM.remove(id);
    }

    private static void evictAllMemory() {
        for (UUID id : List.copyOf(COMPANION_MEM.snapshotIds())) unloadCompanionMemory(id);
        for (UUID id : List.copyOf(VILLAGER_MEM.snapshotIds())) unloadVillagerMemory(id);
    }
}
