package dev.yucktheslime.tradeofimmersion.budget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A cheap stand-in for "spawn many NPCs": 2000 candidates go through the same cap / priority /
 * LRU path the server uses, without booting Minecraft.
 */
class AiBudgetStressTest {
    @Test
    void twoThousandNpcsCapAndLru() {
        int n = 2000;
        List<AiActiveSet.Candidate> all = new ArrayList<>(n);
        MemoryLru lru = new MemoryLru();
        for (int i = 0; i < n; i++) {
            UUID id = new UUID(0, i);
            int flags = 0;
            if (i == 7) flags |= AiActiveSet.LOOKED_AT;
            if (i % 50 == 0) flags |= AiActiveSet.RECENT_INTERACTION;
            if (i % 10 == 0) flags |= AiActiveSet.OWNED;
            all.add(new AiActiveSet.Candidate(id, flags, (n - i) * 1.0)); // higher i is closer
            lru.touch(id, i);
        }
        Set<UUID> active = AiActiveSet.select(all, 8);
        assertEquals(8, active.size());
        assertTrue(active.contains(new UUID(0, 7)), "looked-at NPC must be in the cap");
        // evict everyone not active down to 32, pinning the live set
        List<UUID> victims = lru.victims(32, 2000, 0, active);
        assertEquals(n - 32, victims.size());
        for (UUID id : active) {
            assertFalse(victims.contains(id), "active NPC " + id + " should stay pinned under a 32 cap");
        }
        for (UUID id : victims) lru.remove(id);
        assertEquals(32, lru.size());
        for (UUID id : active) assertTrue(lru.contains(id));
    }
}
