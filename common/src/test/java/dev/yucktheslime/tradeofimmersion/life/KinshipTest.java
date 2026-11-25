package dev.yucktheslime.tradeofimmersion.life;

import dev.yucktheslime.tradeofimmersion.life.kin.Kinship;
import dev.yucktheslime.tradeofimmersion.life.kin.Kinship.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class KinshipTest {
    // Family:            gg1 + gg2
    //                        |
    //          g1 + g2      gA + gB  (gA child of gg1/gg2)
    //            |             |
    //     p1 (child of g1,g2) + p2 (child of gA,gB);  u1 = sibling of p1 (child of g1,g2)
    //     kids: c1, c2 (full siblings), h1 = half sibling of c1 (p1 + other)
    //     u1's kid: k1 (first cousin of c1); k1's kid: kk1 (first cousin once removed of c1)
    //     c1's kid: cc1 ; k1's kid kk1 vs cc1: second cousins
    final Map<UUID, List<UUID>> tree = new HashMap<>();
    final UUID gg1 = id(), gg2 = id(), g1 = id(), g2 = id(), gA = id(), gB = id(), p1 = id(), p2 = id(), u1 = id(),
            c1 = id(), c2 = id(), h1 = id(), other = id(), k1 = id(), kk1 = id(), cc1 = id(), stranger = id(), spouse = id();

    static UUID id() {
        return UUID.randomUUID();
    }

    void parents(UUID child, UUID... ps) {
        tree.put(child, new ArrayList<>(List.of(ps)));
    }

    KinshipTest() {
        parents(gA, gg1, gg2);
        parents(p1, g1, g2);
        parents(u1, g1, g2);
        parents(p2, gA, gB);
        parents(c1, p1, p2);
        parents(c2, p1, p2);
        parents(h1, p1, other);
        parents(k1, u1, spouse);
        parents(kk1, k1);
        parents(cc1, c1);
    }

    Kinship.Relation rel(UUID a, UUID b) {
        return Kinship.relation(a, b, Kinship.fromMap(tree));
    }

    @Test
    void classifiesRelatives() {
        assertEquals(Kind.SELF, rel(c1, c1).kind());
        assertEquals(Kind.PARENT_OR_CHILD, rel(c1, p1).kind());
        assertEquals(Kind.PARENT_OR_CHILD, rel(p2, c2).kind());
        assertEquals(Kind.GRANDPARENT_OR_GRANDCHILD, rel(c1, g1).kind());
        assertEquals(Kind.GREAT_ANCESTOR_OR_DESCENDANT, rel(c1, gg2).kind());
        assertEquals(Kind.SIBLING, rel(c1, c2).kind());
        assertEquals(Kind.SIBLING, rel(c1, h1).kind(), "half siblings are siblings");
        assertEquals(Kind.AUNT_UNCLE_OR_NIECE_NEPHEW, rel(c1, u1).kind());
        assertEquals(Kind.AUNT_UNCLE_OR_NIECE_NEPHEW, rel(u1, c2).kind());
        assertEquals(Kind.FIRST_COUSIN, rel(c1, k1).kind());
        assertEquals(Kind.COUSIN_REMOVED, rel(c1, kk1).kind());
        assertEquals(Kind.SECOND_COUSIN, rel(cc1, kk1).kind());
        assertEquals(Kind.UNRELATED, rel(c1, stranger).kind());
        assertEquals(Kind.UNRELATED, rel(p1, p2).kind(), "spouses from different families are unrelated");
        assertEquals(Kind.UNRELATED, rel(u1, spouse).kind());
    }

    @Test
    void defaultDepthBlocksTheUsersList() {
        int depth = 2;
        assertTrue(Kinship.forbidden(rel(c1, c2), depth), "siblings");
        assertTrue(Kinship.forbidden(rel(c1, p1), depth), "parent/child");
        assertTrue(Kinship.forbidden(rel(c1, g2), depth), "grandparents");
        assertTrue(Kinship.forbidden(rel(c1, gg1), depth), "direct line always");
        assertTrue(Kinship.forbidden(rel(c1, u1), depth), "aunt/uncle");
        assertTrue(Kinship.forbidden(rel(c1, k1), depth), "first cousins");
        assertFalse(Kinship.forbidden(rel(c1, kk1), depth), "once removed is allowed at depth 2");
        assertFalse(Kinship.forbidden(rel(cc1, kk1), depth), "second cousins allowed at depth 2");
        assertFalse(Kinship.forbidden(rel(c1, stranger), depth));
        assertFalse(Kinship.forbidden(rel(p1, p2), depth));
    }

    @Test
    void depthIsConfigurable() {
        assertTrue(Kinship.forbidden(rel(cc1, kk1), 3), "second cousins blocked at depth 3");
        assertFalse(Kinship.forbidden(rel(c1, k1), 1), "cousins allowed at depth 1");
        assertTrue(Kinship.forbidden(rel(c1, c2), 1), "siblings still blocked at depth 1");
        assertFalse(Kinship.forbidden(rel(c1, c2), 0), "depth 0 = only direct line");
        assertTrue(Kinship.forbidden(rel(c1, p1), 0));
    }

    @Test
    void survivesCyclesAndMissingData() {
        UUID x = id(), y = id();
        tree.put(x, List.of(y));
        tree.put(y, List.of(x)); // broken data: each other's parent
        Function<UUID, List<UUID>> f = Kinship.fromMap(tree);
        assertTrue(Kinship.relation(x, y, f).related());
        assertFalse(Kinship.relation(null, y, f).related());
        assertFalse(Kinship.relation(x, stranger, id2 -> null).related());
    }
}
