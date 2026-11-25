package dev.yucktheslime.tradeofimmersion.budget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Access-ordered LRU of NPC memory keys, plus optional idle timeout. Pure Java, unit-tested.
 *
 * <p>{@code cap <= 0} means no size cap. {@code idleMs <= 0} means no idle eviction. Pinned IDs
 * (currently AI-active NPCs) are evicted only if the cap still cannot be met otherwise.
 */
public final class MemoryLru {
    private final LinkedHashMap<UUID, Long> lastAccess = new LinkedHashMap<>();

    public synchronized void touch(UUID id, long nowMs) {
        if (id == null) return;
        lastAccess.remove(id);
        lastAccess.put(id, nowMs);
    }

    public synchronized void remove(UUID id) {
        lastAccess.remove(id);
    }

    public synchronized void clear() {
        lastAccess.clear();
    }

    public synchronized int size() {
        return lastAccess.size();
    }

    public synchronized boolean contains(UUID id) {
        return lastAccess.containsKey(id);
    }

    public synchronized Long lastAccess(UUID id) {
        return lastAccess.get(id);
    }

    /** Current keys, oldest access first. */
    public synchronized java.util.List<UUID> snapshotIds() {
        return new ArrayList<>(lastAccess.keySet());
    }

    /**
     * IDs that should be persisted and dropped from RAM, in eviction order (idle first, then
     * least-recently used). The map is not mutated; call {@link #remove(UUID)} after a successful unload.
     */
    public synchronized List<UUID> victims(int cap, long nowMs, long idleMs, Set<UUID> pinned) {
        List<UUID> out = new ArrayList<>();
        if (idleMs > 0) {
            for (Map.Entry<UUID, Long> e : lastAccess.entrySet()) {
                if (nowMs - e.getValue() >= idleMs && !isPinned(pinned, e.getKey())) out.add(e.getKey());
            }
        }
        if (cap > 0) {
            int remaining = lastAccess.size() - out.size();
            if (remaining > cap) {
                int need = remaining - cap;
                need -= takeOldest(out, need, pinned, true);
                if (need > 0) takeOldest(out, need, pinned, false);
            }
        }
        return out;
    }

    private int takeOldest(List<UUID> out, int need, Set<UUID> pinned, boolean skipPinned) {
        int taken = 0;
        for (UUID id : lastAccess.keySet()) {
            if (taken >= need) break;
            if (out.contains(id)) continue;
            if (skipPinned && isPinned(pinned, id)) continue;
            out.add(id);
            taken++;
        }
        return taken;
    }

    private static boolean isPinned(Set<UUID> pinned, UUID id) {
        return pinned != null && pinned.contains(id);
    }
}
