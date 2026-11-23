package dev.yucktheslime.tradeofimmersion.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * kagrenacFallback "line": while Kagrenac is unreachable a companion answers a player's line with one short,
 * in-character sentence at most every two minutes, and otherwise stays quiet. Events get no reaction and
 * villagers say nothing, so nothing piles up in chat.
 */
final class QuietFallbackBackend implements AiBackend {
    private static final List<String> LINES = List.of(
            "Hm? Sorry, my mind was somewhere else.",
            "Give me a moment... I can't think straight right now.",
            "Not now, my head is foggy.",
            "I... lost my train of thought. Later?");
    private static final long GAP_MS = 120_000;
    private final Map<String, Long> lastLine = new ConcurrentHashMap<>();
    private final boolean silent;

    QuietFallbackBackend(boolean silent) {
        this.silent = silent;
    }

    @Override
    public String name() {
        return silent ? "fallback(none)" : "fallback(line)";
    }

    @Override
    public CompletableFuture<AiReply> companionTurn(TurnRequest req) {
        if (silent || req.eventType() != null) return CompletableFuture.completedFuture(AiReply.text(""));
        long now = System.currentTimeMillis();
        Long last = lastLine.get(req.npcId());
        if (last != null && now - last < GAP_MS) return CompletableFuture.completedFuture(AiReply.text(""));
        lastLine.put(req.npcId(), now);
        return CompletableFuture.completedFuture(AiReply.text(LINES.get(Math.floorMod((req.npcId() + now / GAP_MS).hashCode(), LINES.size()))));
    }

    @Override
    public CompletableFuture<String> villagerLine(VillagerRequest req) {
        return CompletableFuture.completedFuture("");
    }
}
