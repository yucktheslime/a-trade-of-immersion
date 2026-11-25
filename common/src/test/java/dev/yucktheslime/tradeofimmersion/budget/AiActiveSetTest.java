package dev.yucktheslime.tradeofimmersion.budget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AiActiveSetTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID D = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final UUID E = UUID.fromString("00000000-0000-0000-0000-00000000000e");

    @Test
    void uncappedReturnsEveryoneInPriorityOrder() {
        List<AiActiveSet.Candidate> all = List.of(
                new AiActiveSet.Candidate(C, 0, 4),
                new AiActiveSet.Candidate(A, AiActiveSet.LOOKED_AT, 100),
                new AiActiveSet.Candidate(B, AiActiveSet.OWNED, 1)
        );
        Set<UUID> selected = AiActiveSet.select(all, 0);
        assertEquals(Set.of(A, B, C), selected);
        assertEquals(List.of(A, B, C), List.copyOf(selected));
    }

    @Test
    void capKeepsHighestPriority() {
        List<AiActiveSet.Candidate> all = List.of(
                new AiActiveSet.Candidate(A, 0, 1), // closest but no flags
                new AiActiveSet.Candidate(B, AiActiveSet.OWNED, 50),
                new AiActiveSet.Candidate(C, AiActiveSet.RECENT_INTERACTION, 80),
                new AiActiveSet.Candidate(D, AiActiveSet.LOOKED_AT, 90),
                new AiActiveSet.Candidate(E, AiActiveSet.LOOKED_AT | AiActiveSet.OWNED, 200)
        );
        Set<UUID> selected = AiActiveSet.select(all, 3);
        // E (looked+owned), D (looked), C (recent) beat B (owned) and A (close only)
        assertEquals(List.of(E, D, C), List.copyOf(selected));
    }

    @Test
    void distanceBreaksTies() {
        List<AiActiveSet.Candidate> all = List.of(
                new AiActiveSet.Candidate(A, AiActiveSet.OWNED, 25),
                new AiActiveSet.Candidate(B, AiActiveSet.OWNED, 9),
                new AiActiveSet.Candidate(C, AiActiveSet.OWNED, 16)
        );
        assertEquals(List.of(B, C), List.copyOf(AiActiveSet.select(all, 2)));
    }

    @Test
    void mergeORsFlagsAndKeepsClosest() {
        List<AiActiveSet.Candidate> raw = List.of(
                new AiActiveSet.Candidate(A, AiActiveSet.OWNED, 100),
                new AiActiveSet.Candidate(A, AiActiveSet.LOOKED_AT, 4),
                new AiActiveSet.Candidate(B, 0, 9)
        );
        List<AiActiveSet.Candidate> merged = AiActiveSet.merge(raw);
        assertEquals(2, merged.size());
        AiActiveSet.Candidate a = merged.stream().filter(c -> c.id().equals(A)).findFirst().orElseThrow();
        assertEquals(AiActiveSet.OWNED | AiActiveSet.LOOKED_AT, a.flags());
        assertEquals(4, a.distanceSq());
        Set<UUID> selected = AiActiveSet.select(raw, 1);
        assertEquals(Set.of(A), selected);
    }

    @Test
    void emptyAndNullSafe() {
        assertTrue(AiActiveSet.select(List.of(), 8).isEmpty());
        List<AiActiveSet.Candidate> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(new AiActiveSet.Candidate(null, 0, 1));
        withNull.add(new AiActiveSet.Candidate(A, 0, 1));
        assertEquals(Set.of(A), AiActiveSet.select(withNull, 3));
    }

    @Test
    void rankBitsMatchConstants() {
        assertEquals(0, AiActiveSet.rank(false, false, false));
        assertEquals(AiActiveSet.LOOKED_AT | AiActiveSet.RECENT_INTERACTION | AiActiveSet.OWNED,
                AiActiveSet.rank(true, true, true));
        assertTrue(AiActiveSet.LOOKED_AT > AiActiveSet.RECENT_INTERACTION);
        assertTrue(AiActiveSet.RECENT_INTERACTION > AiActiveSet.OWNED);
    }
}
