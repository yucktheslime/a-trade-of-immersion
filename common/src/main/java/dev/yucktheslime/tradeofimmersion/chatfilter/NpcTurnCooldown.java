package dev.yucktheslime.tradeofimmersion.chatfilter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-NPC cooldown for chat- and dialogue-box-triggered AI turns. */
public final class NpcTurnCooldown {
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    public boolean allowed(UUID id, long nowMillis, long cooldownMs) {
        if (id == null || cooldownMs <= 0) return true;
        Long t = last.get(id);
        return t == null || nowMillis - t >= cooldownMs;
    }

    public long remainingMs(UUID id, long nowMillis, long cooldownMs) {
        if (id == null || cooldownMs <= 0) return 0;
        Long t = last.get(id);
        if (t == null) return 0;
        return Math.max(0, cooldownMs - (nowMillis - t));
    }

    public void mark(UUID id, long nowMillis) {
        if (id == null) return;
        last.put(id, nowMillis);
        if (last.size() > 2000) evict(nowMillis, 3_600_000L);
    }

    public void evict(long nowMillis, long maxAgeMs) {
        last.entrySet().removeIf(e -> nowMillis - e.getValue() > maxAgeMs);
    }

    public void clear() {
        last.clear();
    }
}
