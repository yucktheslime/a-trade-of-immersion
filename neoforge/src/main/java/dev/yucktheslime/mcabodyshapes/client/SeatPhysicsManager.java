package dev.yucktheslime.mcabodyshapes.client;

import net.minecraft.world.entity.LivingEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only per-entity seat physics. Entries are dropped after a short
 * period without updates so unloaded villagers do not leak state.
 */
public final class SeatPhysicsManager {
    private static final int STALE_TICKS = 200;
    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private SeatPhysicsManager() {
    }

    public static Entry get(LivingEntity entity) {
        return ENTRIES.computeIfAbsent(entity.getUUID(), id -> new Entry());
    }

    public static void evictStale(int currentTick) {
        Iterator<Map.Entry<UUID, Entry>> iterator = ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (currentTick - entry.lastSeenTick > STALE_TICKS) {
                iterator.remove();
            }
        }
    }

    public static final class Entry {
        public final SeatPhysics main = new SeatPhysics(0.0f);
        public final SeatPhysics left = new SeatPhysics(0.11f);
        public final SeatPhysics right = new SeatPhysics(-0.09f);
        private int lastSeenTick = Integer.MIN_VALUE;

        public void touch(int tick) {
            lastSeenTick = tick;
        }
    }
}
