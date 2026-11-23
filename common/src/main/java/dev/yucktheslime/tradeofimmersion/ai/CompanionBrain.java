package dev.yucktheslime.tradeofimmersion.ai;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.action.ActionExecutor;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.item.ItemInspector;
import dev.yucktheslime.tradeofimmersion.memory.ItemKnowledgeStore;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The AI loop: turns chat and world events into backend requests, then speaks the reply and runs its actions
 * on the server thread. One request in flight per companion (newer triggers wait, latest wins), a minimum gap
 * between turns, a global per-minute cap, and a cooldown after failures (during which the offline mock answers,
 * if {@code aiFallbackToMock}).
 */
public final class CompanionBrain {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Deque<Long> RECENT = new ArrayDeque<>();
    private static final MockBackend MOCK = new MockBackend();
    private static AiBackend backend = MOCK;
    private static MinecraftServer server;
    private static volatile long failureUntil;
    private static volatile String lastError = "";
    private static volatile int ok, failed;

    private CompanionBrain() {
    }

    private static final class State {
        boolean inFlight;
        long lastTurn;
        Pending pending;
    }

    private record Pending(String speaker, String trigger, String eventType, int depth) {
    }

    public static void reload() {
        String b = ToiConfig.get().aiBackend.toLowerCase(Locale.ROOT).trim();
        backend = switch (b) {
            case "mock", "offline" -> MOCK;
            case "kagrenac" -> new KagrenacBackend();
            default -> new OpenAiBackend();
        };
        failureUntil = 0;
        TradeOfImmersion.LOGGER.info("AI backend: {}", backend.name());
    }

    public static void start(MinecraftServer s) {
        server = s;
        STATES.clear();
        String world = s.getWorldData().getLevelName();
        KagrenacBackend.worldSession = KagrenacBackend.safeId("mc-" + world);
        reload();
    }

    public static void stop() {
        server = null;
        STATES.clear();
    }

    public static AiBackend backend() {
        return backend;
    }

    /** Backend to use right now: the configured one, or the mock while cooling down after a failure. */
    public static AiBackend active() {
        if (backend != MOCK && System.currentTimeMillis() < failureUntil && ToiConfig.get().aiFallbackToMock) return MOCK;
        return backend;
    }

    public static String status() {
        long cd = Math.max(0, failureUntil - System.currentTimeMillis()) / 1000;
        return "backend " + backend.name() + ", active " + active().name() + ", ok " + ok + ", failed " + failed
                + (cd > 0 ? ", cooling down " + cd + "s" : "") + (lastError.isBlank() ? "" : ", last error: " + lastError)
                + ", item knowledge " + ItemKnowledgeStore.get().size();
    }

    // ------------------------------------------------------------------ triggers

    public static void onChat(CompanionEntity c, String speaker, String text) {
        NpcMemoryStore.get().chat(c.getUUID(), speaker, text);
        request(c, new Pending(speaker, text, null, 0));
    }

    /** A world event the companion noticed and may react to (item_given, combat_started, death, ...). */
    public static void onEvent(CompanionEntity c, String type, String text) {
        if (!ToiConfig.get().reactToEvents) return;
        request(c, new Pending("", text, type, 0));
    }

    /** A villager answered this companion. */
    public static void onVillagerLine(CompanionEntity c, String villagerName, String line) {
        request(c, new Pending("villager:" + villagerName, villagerName + " (villager) says to you: " + line
                + "\nAnswer them with \"say\" (they hear you), or use talk_to_villager to continue, or end the conversation.", "villager_line", 0));
    }

    private static void request(CompanionEntity c, Pending p) {
        if (!ToiConfig.get().enabled || c.getServer() == null) return;
        State s = STATES.computeIfAbsent(c.getUUID(), k -> new State());
        long now = System.currentTimeMillis();
        if (s.inFlight || now - s.lastTurn < ToiConfig.get().aiMinSecondsBetweenTurns * 1000 || !takeSlot(now)) {
            // keep the most important pending trigger: chat beats events
            if (s.pending == null || p.eventType == null || s.pending.eventType != null) s.pending = p;
            return;
        }
        run(c, s, p);
    }

    public static boolean trySlot() {
        return takeSlot(System.currentTimeMillis());
    }

    private static synchronized boolean takeSlot(long now) {
        while (!RECENT.isEmpty() && now - RECENT.peekFirst() > 60_000) RECENT.pollFirst();
        if (RECENT.size() >= Math.max(1, ToiConfig.get().aiMaxRequestsPerMinute)) return false;
        RECENT.addLast(now);
        return true;
    }

    private static void run(CompanionEntity c, State s, Pending p) {
        TurnRequest req;
        try {
            req = PromptBuilder.build(c, p.speaker, p.trigger, p.eventType);
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.error("Prompt build failed", e);
            return;
        }
        s.inFlight = true;
        s.lastTurn = System.currentTimeMillis();
        AiBackend b = active();
        MinecraftServer srv = c.getServer();
        UUID id = c.getUUID();
        CompletableFuture<AiReply> f;
        try {
            f = b.companionTurn(req);
        } catch (Exception e) {
            f = CompletableFuture.failedFuture(e);
        }
        f.whenComplete((reply, err) -> srv.execute(() -> {
            s.inFlight = false;
            s.lastTurn = System.currentTimeMillis();
            CompanionEntity live = find(srv, id);
            if (live == null) return; // unloaded or dead: drop the reply
            AiReply r = reply;
            if (err != null) {
                failed++;
                Throwable cause = err.getCause() != null ? err.getCause() : err;
                lastError = cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
                if (lastError.length() > 200) lastError = lastError.substring(0, 200);
                TradeOfImmersion.LOGGER.warn("AI turn failed ({}): {}", b.name(), lastError);
                failureUntil = System.currentTimeMillis() + ToiConfig.get().aiFailureCooldownSeconds * 1000L;
                if (ToiConfig.get().aiFallbackToMock && b != MOCK) r = MockBackend.reply(req);
                else {
                    if (p.eventType == null) live.say("...(I can't think straight right now: AI backend unreachable)");
                    return;
                }
            } else {
                ok++;
            }
            apply(live, r, p);
        }));
    }

    private static void apply(CompanionEntity c, AiReply r, Pending p) {
        if (r.say() != null && !r.say().isBlank()) {
            c.say(r.say());
            if (p.speaker != null && p.speaker.startsWith("villager:")) {
                dev.yucktheslime.tradeofimmersion.villager.VillagerTalk.companionReplied(c, r.say(), r.actions().stream()
                        .anyMatch(a -> a.has("action") && a.get("action").getAsString().equals("talk_to_villager")));
            }
        }
        for (AiReply.Learn l : r.learn()) {
            String id = resolveItemId(c, l.item());
            if (id != null) ItemKnowledgeStore.get().addNote(id, l.notes());
        }
        if (r.actions().isEmpty()) return;
        List<ActionExecutor.Result> results = ActionExecutor.run(c, r.actions());
        boolean interesting = results.stream().anyMatch(x -> !x.ok() || x.action().equals("inspect_item"));
        if (interesting && p.depth == 0) {
            String text = "[result] " + results.stream().map(ActionExecutor.Result::toString).collect(Collectors.joining(" | "));
            State s = STATES.computeIfAbsent(c.getUUID(), k -> new State());
            Pending follow = new Pending("", text, "action_result", 1);
            // follow-ups run as soon as the gap allows (via tick)
            if (s.pending == null || s.pending.eventType != null) s.pending = follow;
        }
    }

    private static String resolveItemId(CompanionEntity c, String item) {
        if (item == null || item.isBlank()) return null;
        if (item.contains(":")) return item.trim();
        int slot = ActionExecutor.findItem(c, item);
        if (slot >= 0) return ItemInspector.id(c.inventory.getItem(slot));
        ItemStack hand = c.getMainHandItem();
        if (ActionExecutor.matches(hand, item)) return ItemInspector.id(hand);
        if (c.getOwner() != null && ActionExecutor.matches(c.getOwner().getMainHandItem(), item)) return ItemInspector.id(c.getOwner().getMainHandItem());
        return null;
    }

    /** Called every server tick: flushes pending triggers when allowed. */
    public static void tick(MinecraftServer srv) {
        if (srv.getTickCount() % 10 != 0 || STATES.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (var e : STATES.entrySet()) {
            State s = e.getValue();
            if (s.pending == null || s.inFlight || now - s.lastTurn < ToiConfig.get().aiMinSecondsBetweenTurns * 1000) continue;
            CompanionEntity c = find(srv, e.getKey());
            if (c == null) {
                s.pending = null;
                continue;
            }
            if (!takeSlot(now)) return;
            Pending p = s.pending;
            s.pending = null;
            run(c, s, p);
        }
    }

    public static CompanionEntity find(MinecraftServer srv, UUID id) {
        for (ServerLevel level : srv.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e instanceof CompanionEntity c && c.isAlive()) return c;
        }
        return null;
    }
}
