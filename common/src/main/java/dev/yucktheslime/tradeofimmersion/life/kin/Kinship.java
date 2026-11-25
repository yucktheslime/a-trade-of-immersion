package dev.yucktheslime.tradeofimmersion.life.kin;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pure kinship math over any family graph (MCA's family tree, extended with AI companions). No Minecraft classes, so
 * it is unit tested on its own.
 *
 * <p>Two people are related when they share an ancestor. With {@code up(a)} and {@code up(b)} the number of
 * generations from each person up to their closest shared ancestor (0 = that person is the ancestor):
 * <ul>
 *   <li>(0, n) or (n, 0): direct line, parent/child (1), grandparent/grandchild (2), great-... (3+)</li>
 *   <li>(1, 1): siblings (full or half)</li>
 *   <li>(1, 2) / (2, 1): aunt or uncle / niece or nephew</li>
 *   <li>(2, 2): first cousins; (2, 3): first cousins once removed; (3, 3): second cousins; ...</li>
 * </ul>
 */
public final class Kinship {
    /** Deepest ancestor search; also guards against cycles in a broken tree. */
    public static final int MAX_DEPTH = 16;

    public enum Kind {
        SELF, UNRELATED, PARENT_OR_CHILD, GRANDPARENT_OR_GRANDCHILD, GREAT_ANCESTOR_OR_DESCENDANT,
        SIBLING, AUNT_UNCLE_OR_NIECE_NEPHEW, GREAT_AUNT_UNCLE, FIRST_COUSIN, COUSIN_REMOVED, SECOND_COUSIN, DISTANT
    }

    /** The closest shared-ancestor relation between two people. {@code upA}/{@code upB} are -1 when unrelated. */
    public record Relation(Kind kind, int upA, int upB, UUID ancestor) {
        public boolean related() {
            return kind != Kind.UNRELATED;
        }

        public boolean directLine() {
            return related() && kind != Kind.SELF && (upA == 0 || upB == 0);
        }

        /** Generations to the shared ancestor from the farther side (0 for self). */
        public int depth() {
            return Math.max(upA, upB);
        }

        public String describe() {
            return switch (kind) {
                case SELF -> "the same person";
                case UNRELATED -> "not related";
                case PARENT_OR_CHILD -> "parent and child";
                case GRANDPARENT_OR_GRANDCHILD -> "grandparent and grandchild";
                case GREAT_ANCESTOR_OR_DESCENDANT -> "direct ancestor and descendant";
                case SIBLING -> "siblings";
                case AUNT_UNCLE_OR_NIECE_NEPHEW -> "aunt/uncle and niece/nephew";
                case GREAT_AUNT_UNCLE -> "great-aunt/uncle and grand-niece/nephew";
                case FIRST_COUSIN -> "first cousins";
                case COUSIN_REMOVED -> "cousins (removed)";
                case SECOND_COUSIN -> "second cousins";
                case DISTANT -> "distant relatives";
            };
        }
    }

    private Kinship() {
    }

    /**
     * @param parents a person's parents (may be empty or null; unknown/NIL ids should already be filtered out)
     */
    public static Relation relation(UUID a, UUID b, Function<UUID, ? extends Collection<UUID>> parents) {
        if (a == null || b == null) return new Relation(Kind.UNRELATED, -1, -1, null);
        if (a.equals(b)) return new Relation(Kind.SELF, 0, 0, a);
        Map<UUID, Integer> upA = ancestors(a, parents), upB = ancestors(b, parents);
        UUID best = null;
        int bestA = -1, bestB = -1;
        for (Map.Entry<UUID, Integer> e : upA.entrySet()) {
            Integer db = upB.get(e.getKey());
            if (db == null) continue;
            int da = e.getValue();
            // closest relation: smallest farther side, then smallest sum
            if (best == null || Math.max(da, db) < Math.max(bestA, bestB)
                    || Math.max(da, db) == Math.max(bestA, bestB) && da + db < bestA + bestB) {
                best = e.getKey();
                bestA = da;
                bestB = db;
            }
        }
        if (best == null) return new Relation(Kind.UNRELATED, -1, -1, null);
        return new Relation(classify(bestA, bestB), bestA, bestB, best);
    }

    static Kind classify(int da, int db) {
        int lo = Math.min(da, db), hi = Math.max(da, db);
        if (hi == 0) return Kind.SELF;
        if (lo == 0) return hi == 1 ? Kind.PARENT_OR_CHILD : hi == 2 ? Kind.GRANDPARENT_OR_GRANDCHILD : Kind.GREAT_ANCESTOR_OR_DESCENDANT;
        if (lo == 1 && hi == 1) return Kind.SIBLING;
        if (lo == 1 && hi == 2) return Kind.AUNT_UNCLE_OR_NIECE_NEPHEW;
        if (lo == 1) return Kind.GREAT_AUNT_UNCLE;
        if (lo == 2 && hi == 2) return Kind.FIRST_COUSIN;
        if (lo == 3 && hi == 3) return Kind.SECOND_COUSIN;
        if (hi - lo >= 1 && lo <= 3) return Kind.COUSIN_REMOVED;
        return Kind.DISTANT;
    }

    /** Every ancestor (and the person, at 0) with the fewest generations up to it. */
    static Map<UUID, Integer> ancestors(UUID start, Function<UUID, ? extends Collection<UUID>> parents) {
        Map<UUID, Integer> seen = new HashMap<>();
        ArrayDeque<UUID> todo = new ArrayDeque<>();
        seen.put(start, 0);
        todo.add(start);
        while (!todo.isEmpty()) {
            UUID cur = todo.poll();
            int d = seen.get(cur);
            if (d >= MAX_DEPTH) continue;
            Collection<UUID> ps = parents.apply(cur);
            if (ps == null) continue;
            for (UUID p : ps) {
                if (p == null || seen.containsKey(p)) continue;
                seen.put(p, d + 1);
                todo.add(p);
            }
        }
        return seen;
    }

    /**
     * Whether a pairing is forbidden. Direct lines (parents, grandparents, ... and children, grandchildren, ...)
     * are always forbidden; other relatives when their shared ancestor is within {@code depth} generations of both:
     * 1 = siblings, 2 = + aunts/uncles, nieces/nephews and first cousins (default), 3 = + second cousins and
     * great-aunts/uncles, ... 0 = only the direct line.
     */
    public static boolean forbidden(Relation r, int depth) {
        if (!r.related()) return false;
        if (r.kind() == Kind.SELF || r.directLine()) return true;
        return r.depth() <= depth;
    }

    /** Convenience for tests: a parents function from a map. */
    public static Function<UUID, List<UUID>> fromMap(Map<UUID, List<UUID>> m) {
        return id -> m.getOrDefault(id, List.of());
    }
}
