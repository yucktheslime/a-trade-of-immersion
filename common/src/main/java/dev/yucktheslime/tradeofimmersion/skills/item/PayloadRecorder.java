package dev.yucktheslime.tradeofimmersion.skills.item;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.skills.SkillsConfig;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps each player's last few seconds of serverbound mod packets (fed from a mixin on the network thread), so a
 * reported key press can be matched to the packet it caused. Vanilla, loader and our own channels are ignored.
 */
public final class PayloadRecorder {
    public record Seen(String channel, long timeMs, CustomPacketPayload payload) {
    }

    private static final Map<UUID, Deque<Seen>> RECENT = new ConcurrentHashMap<>();
    private static final Set<String> IGNORED_NS = Set.of("minecraft", "c", "fabric", "neoforge", "forge", "architectury", "fml",
            TradeOfImmersion.MOD_ID, "mca", "voicechat", "jei", "rei", "emi", "xaero", "journeymap");
    private static final int KEEP = 48;
    private static final long KEEP_MS = 4000;

    private PayloadRecorder() {
    }

    /** Network thread. */
    public static void record(UUID player, CustomPacketPayload payload) {
        if (!SkillsConfig.get().keybindObservation || payload == null) return;
        String ch;
        try {
            ch = payload.type().id().toString();
        } catch (Throwable t) {
            return;
        }
        String ns = ch.substring(0, ch.indexOf(':'));
        if (IGNORED_NS.contains(ns)) return;
        long now = System.currentTimeMillis();
        Deque<Seen> q = RECENT.computeIfAbsent(player, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(new Seen(ch, now, payload));
            while (q.size() > KEEP || !q.isEmpty() && now - q.peekFirst().timeMs() > KEEP_MS) q.pollFirst();
        }
    }

    public static List<Seen> recent(UUID player) {
        Deque<Seen> q = RECENT.get(player);
        if (q == null) return List.of();
        synchronized (q) {
            return new ArrayList<>(q);
        }
    }

    public static void forget(UUID player) {
        RECENT.remove(player);
    }
}
