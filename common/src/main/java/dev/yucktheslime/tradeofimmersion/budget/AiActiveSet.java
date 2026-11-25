package dev.yucktheslime.tradeofimmersion.budget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure priority + cap selection for the AI budget (no Minecraft types, so it is unit-tested).
 *
 * <p>Priority, highest first: looked-at, recent interaction, companion ownership, then closer
 * distance. When two NPCs tie, the UUID breaks the tie so the set is stable.
 *
 * <p>{@code maxActive <= 0} means no cap (every candidate is active).
 */
public final class AiActiveSet {
    public static final int LOOKED_AT = 1 << 2;
    public static final int RECENT_INTERACTION = 1 << 1;
    public static final int OWNED = 1 << 0;

    private AiActiveSet() {
    }

    public record Candidate(UUID id, int flags, double distanceSq) {
        public Candidate with(int extraFlags, double otherDistanceSq) {
            return new Candidate(id, flags | extraFlags, Math.min(distanceSq, otherDistanceSq));
        }
    }

    static final Comparator<Candidate> COMPARATOR = Comparator
            .comparingInt(Candidate::flags).reversed()
            .thenComparingDouble(Candidate::distanceSq)
            .thenComparing(Candidate::id);

    /** Merge duplicates of the same NPC (several players) by OR-ing flags and keeping the closest distance. */
    public static List<Candidate> merge(Collection<Candidate> raw) {
        Map<UUID, Candidate> best = new LinkedHashMap<>();
        for (Candidate c : raw) {
            if (c == null || c.id() == null) continue;
            best.merge(c.id(), c, (a, b) -> a.with(b.flags(), b.distanceSq()));
        }
        return new ArrayList<>(best.values());
    }

    /**
     * The UUIDs that may use the AI, already in priority order. {@code maxActive <= 0} or a candidate
     * list no larger than the cap returns everyone.
     */
    public static Set<UUID> select(Collection<Candidate> candidates, int maxActive) {
        List<Candidate> merged = merge(candidates);
        if (merged.isEmpty()) return Set.of();
        if (maxActive <= 0 || merged.size() <= maxActive) {
            Set<UUID> all = new LinkedHashSet<>();
            merged.sort(COMPARATOR);
            for (Candidate c : merged) all.add(c.id());
            return all;
        }
        merged.sort(COMPARATOR);
        Set<UUID> out = new LinkedHashSet<>();
        for (int i = 0; i < maxActive; i++) out.add(merged.get(i).id());
        return out;
    }

    public static int rank(boolean lookedAt, boolean recentInteraction, boolean owned) {
        return (lookedAt ? LOOKED_AT : 0)
                | (recentInteraction ? RECENT_INTERACTION : 0)
                | (owned ? OWNED : 0);
    }
}
