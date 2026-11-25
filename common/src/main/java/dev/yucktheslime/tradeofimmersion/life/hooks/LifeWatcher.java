package dev.yucktheslime.tradeofimmersion.life.hooks;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.LifeConfig;
import dev.yucktheslime.tradeofimmersion.life.LifeEvents;
import dev.yucktheslime.tradeofimmersion.life.LifeMca;
import dev.yucktheslime.tradeofimmersion.life.marriage.Romance;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MCA Reborn fires no events for its villagers' lives, so this compares snapshots: every {@code lifeScanSeconds}
 * the MCA villagers near players are read (partner and relationship state, pregnancy, children, mood, hearts with
 * online players) and changes become life events ({@link LifeEvents}): marriages, engagements, break-ups,
 * widowhood, pregnancies, births, big mood changes and heart changes. Gifts are caught directly (GiftMixin).
 */
public final class LifeWatcher {
    record Snap(UUID partner, RelationshipState state, boolean pregnant, int children, int mood, String moodName, Map<UUID, Integer> hearts, long seen) {
    }

    private static final Map<UUID, Snap> SNAPS = new HashMap<>();

    private LifeWatcher() {
    }

    public static void reset() {
        SNAPS.clear();
    }

    public static void scan(MinecraftServer server) {
        LifeConfig cfg = LifeConfig.get();
        if (!cfg.on(cfg.lifeHooks)) return;
        long now = LifeMca.now(server);
        Set<UUID> done = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            for (VillagerEntityMCA v : p.level().getEntitiesOfClass(VillagerEntityMCA.class, p.getBoundingBox().inflate(cfg.lifeScanRadius), Entity::isAlive)) {
                if (!done.add(v.getUUID())) continue;
                try {
                    Snap s = snap(v, server, now);
                    Snap old = SNAPS.put(v.getUUID(), s);
                    if (old != null) diff(server, v, old, s, cfg);
                } catch (Throwable ignored) {
                    // a broken villager (or an MCA change) never stops the scan
                }
            }
        }
        // forget villagers not seen for 5 minutes (unloaded or gone)
        SNAPS.values().removeIf(s -> now - s.seen() > 6000);
    }

    private static Snap snap(VillagerEntityMCA v, MinecraftServer server, long now) {
        FamilyTreeNode n = v.getRelationships().getFamilyEntry();
        Map<UUID, Integer> hearts = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Memories m = v.getVillagerBrain().getMemories().get(p.getUUID());
            if (m != null) hearts.put(p.getUUID(), m.getHearts());
        }
        return new Snap(n.partner(), n.getRelationshipState(), v.getRelationships().getPregnancy().isPregnant(), n.children().size(),
                v.getVillagerBrain().getMoodValue(), v.getVillagerBrain().getMood().getName(), hearts, now);
    }

    private static void diff(MinecraftServer server, VillagerEntityMCA v, Snap a, Snap b, LifeConfig cfg) {
        String name = LifeMca.name(v);
        boolean companionPair = LifeMca.find(server, b.partner()) instanceof CompanionEntity || LifeMca.find(server, a.partner()) instanceof CompanionEntity;
        if (!companionPair && (a.state() != b.state() || !a.partner().equals(b.partner()))) {
            // Romance.setStage records companion pairs itself
            String partner = LifeMca.nameOf(server, b.partner());
            String oldPartner = LifeMca.nameOf(server, a.partner());
            String text = switch (b.state()) {
                case PROMISED -> name + " promised themselves to " + partner;
                case ENGAGED -> name + " got engaged to " + partner;
                case MARRIED_TO_PLAYER, MARRIED_TO_VILLAGER -> name + " married " + partner;
                case WIDOW -> name + " lost their spouse " + oldPartner + " and is now widowed";
                case SINGLE -> a.state().isMarried() ? name + " and " + oldPartner + " divorced" : name + " and " + oldPartner + " split up";
            };
            LifeEvents.record(server, v, "romance", text, v.getUUID(), FamilyTreeNode.isValid(b.partner()) ? b.partner() : a.partner());
        }
        if (!a.pregnant() && b.pregnant() && !dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies.pregnant(v.getUUID())) {
            LifeEvents.record(server, v, "pregnancy", name + " is expecting a baby", v.getUUID(), b.partner());
        }
        if (b.children() > a.children()) {
            LifeEvents.record(server, v, "birth", name + " has a new child (" + b.children() + " children now)", v.getUUID(), b.partner());
        }
        if (Math.abs(b.mood() - a.mood()) >= cfg.moodChangeThreshold && !a.moodName().equals(b.moodName())) {
            LifeEvents.record(server, v, "mood", name + "'s mood changed from " + a.moodName() + " to " + b.moodName(), v.getUUID());
        }
        for (Map.Entry<UUID, Integer> e : b.hearts().entrySet()) {
            Integer before = a.hearts().get(e.getKey());
            if (before == null || Math.abs(e.getValue() - before) < 5) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            int d = e.getValue() - before;
            LifeEvents.remember(v.getUUID(), "hearts", "Your feelings for " + p.getGameProfile().getName() + (d > 0 ? " grew" : " cooled")
                    + " (" + (d > 0 ? "+" : "") + d + " hearts, now " + e.getValue() + ")");
            // a player charming a villager makes their companion partner jealous (if it sees it)
            if (d > 0 && Romance.partner(server, p.getUUID()).filter(x -> !x.equals(v.getUUID())).isPresent()) {
                Romance.rivalry(p, v, 0.4, "getting close to");
            }
        }
    }
}
