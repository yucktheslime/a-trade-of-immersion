package dev.yucktheslime.tradeofimmersion.chatfilter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * After a player addresses an NPC, later public-chat lines from the same player can keep going
 * to that NPC for a short window, without repeating the name (same idea as MCA's ChatAI
 * "open conversation").
 */
public final class OpenConversations {
    private final Map<UUID, Open> open = new ConcurrentHashMap<>();

    public record Open(UUID npcId, long atMillis) {
    }

    public void remember(UUID speaker, UUID npc, long nowMillis) {
        if (speaker == null || npc == null) return;
        open.put(speaker, new Open(npc, nowMillis));
        if (open.size() > 2000) evict(nowMillis, 24 * 3_600_000L);
    }

    /**
     * The NPC this speaker was last talking to, if the window has not expired and that NPC is
     * still in hearing range. {@code windowMs <= 0} disables this.
     */
    public UUID current(UUID speaker, long nowMillis, long windowMs, Set<UUID> hearingIds) {
        if (speaker == null || windowMs <= 0) return null;
        Open o = open.get(speaker);
        if (o == null || nowMillis - o.atMillis > windowMs) return null;
        if (hearingIds != null && !hearingIds.contains(o.npcId)) return null;
        return o.npcId;
    }

    public void evict(long nowMillis, long maxAgeMs) {
        open.entrySet().removeIf(e -> nowMillis - e.getValue().atMillis > maxAgeMs);
    }

    public void clear() {
        open.clear();
    }
}
