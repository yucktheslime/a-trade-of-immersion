package dev.yucktheslime.tradeofimmersion.budget;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MemoryLruTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @Test
    void evictsLeastRecentlyUsedOverCap() {
        MemoryLru lru = new MemoryLru();
        lru.touch(A, 1);
        lru.touch(B, 2);
        lru.touch(C, 3);
        lru.touch(A, 4); // A is now most recent
        List<UUID> victims = lru.victims(2, 4, 0, Set.of());
        assertEquals(List.of(B), victims); // C is newer than B; cap 2 => evict 1 (B)
        lru.remove(B);
        assertEquals(2, lru.size());
        assertFalse(lru.contains(B));
        assertTrue(lru.contains(A));
        assertTrue(lru.contains(C));
    }

    @Test
    void idleTimeoutEvictsUnpinned() {
        MemoryLru lru = new MemoryLru();
        lru.touch(A, 0);
        lru.touch(B, 50);
        lru.touch(C, 100);
        List<UUID> victims = lru.victims(10, 200, 100, Set.of(C));
        // idle threshold: last access <= 100 ago is kept? now-last >= 100 => A (200-0) and B (200-50=150) are idle
        // C is pinned, skipped even if idle (200-100=100, exactly idle)
        assertEquals(List.of(A, B), victims);
        assertFalse(victims.contains(C));
    }

    @Test
    void pinnedSkippedUntilCapCannotBeMet() {
        MemoryLru lru = new MemoryLru();
        lru.touch(A, 1);
        lru.touch(B, 2);
        lru.touch(C, 3);
        // all pinned, cap 1: must evict LRU pinned (A, then B)
        List<UUID> victims = lru.victims(1, 10, 0, Set.of(A, B, C));
        assertEquals(List.of(A, B), victims);
    }

    @Test
    void noCapAndNoIdleMeansNoVictims() {
        MemoryLru lru = new MemoryLru();
        lru.touch(A, 1);
        lru.touch(B, 2);
        assertTrue(lru.victims(0, 10_000, 0, Set.of()).isEmpty());
    }

    @Test
    void stressManyKeysHonorsCap() {
        MemoryLru lru = new MemoryLru();
        int n = 5000;
        for (int i = 0; i < n; i++) {
            lru.touch(new UUID(0, i), i);
        }
        assertEquals(n, lru.size());
        List<UUID> victims = lru.victims(32, n, 0, Set.of());
        assertEquals(n - 32, victims.size());
        // oldest first
        assertEquals(new UUID(0, 0), victims.get(0));
        assertEquals(new UUID(0, n - 33), victims.get(victims.size() - 1));
        for (UUID id : victims) lru.remove(id);
        assertEquals(32, lru.size());
        assertTrue(lru.contains(new UUID(0, n - 1)));
        assertFalse(lru.contains(new UUID(0, 0)));
    }
}
