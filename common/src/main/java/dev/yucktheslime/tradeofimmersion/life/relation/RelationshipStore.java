package dev.yucktheslime.tradeofimmersion.life.relation;

import dev.yucktheslime.tradeofimmersion.life.JsonStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bonds (who feels what about whom), pending proposals and per-pair bookkeeping, saved in
 * {@code <world>/data/tradeofimmersion/relationships.json}. Courtship stages themselves live in MCA's family tree
 * (partner + relationship state), so MCA and this mod always agree on who is with whom.
 */
public final class RelationshipStore {
    public static final class Data {
        public int version = 1;
        /** from -> to -> bond (from's feelings about to). */
        public Map<String, Map<String, Bond>> bonds = new HashMap<>();
        /** pair key -> last in-game day the two spent time together. */
        public Map<String, Long> togetherDay = new HashMap<>();
        /** pair key -> last in-game day their stage changed (one step per day at most). */
        public Map<String, Long> stageDay = new HashMap<>();
        /** asked person -> proposal. */
        public Map<String, Proposal> proposals = new HashMap<>();
        /** asker|asked|stage -> game time of the last refusal (cool-down before asking again). */
        public Map<String, Long> refusals = new HashMap<>();
    }

    public static final class Proposal {
        public String from;
        public String fromName;
        public String stage;
        public long time;
    }

    private static final RelationshipStore INSTANCE = new RelationshipStore();
    public final JsonStore<Data> store = new JsonStore<>("relationships.json", Data.class, Data::new);

    public static RelationshipStore get() {
        return INSTANCE;
    }

    public static String pair(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    public Bond bond(UUID from, UUID to) {
        store.dirty();
        return store.data().bonds.computeIfAbsent(from.toString(), k -> new HashMap<>()).computeIfAbsent(to.toString(), k -> new Bond());
    }

    public Bond find(UUID from, UUID to) {
        Map<String, Bond> m = store.data().bonds.get(from.toString());
        return m == null ? null : m.get(to.toString());
    }

    public Map<String, Bond> from(UUID from) {
        Map<String, Bond> m = store.data().bonds.get(from.toString());
        return m == null ? Map.of() : m;
    }

    public void forget(UUID id) {
        store.data().bonds.remove(id.toString());
        for (Map<String, Bond> m : store.data().bonds.values()) m.remove(id.toString());
        store.data().proposals.remove(id.toString());
        store.dirty();
    }
}
