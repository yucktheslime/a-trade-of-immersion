package dev.yucktheslime.tradeofimmersion.life;

import dev.yucktheslime.tradeofimmersion.ai.TurnRequest;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.marriage.Romance;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import dev.yucktheslime.tradeofimmersion.life.relation.Bond;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipMath;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipStore;
import dev.yucktheslime.tradeofimmersion.life.relation.Stage;
import dev.yucktheslime.tradeofimmersion.memory.NpcMemoryStore;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adds a companion's life (family, partner, feelings, jealousy, pregnancy and mood swings) to its turn prompt
 * (PromptMixin on PromptBuilder.build) and a villager's (pregnancy, recent life events) to MCA villager
 * descriptions (VillagerProfileMixin on VillagerProfile.describe).
 */
public final class LifePrompt {
    private static final Set<String> LIFE_TYPES = Set.of("romance", "birth", "pregnancy", "loss", "gift", "jealousy", "death");

    private LifePrompt() {
    }

    public static TurnRequest augment(CompanionEntity c, TurnRequest t) {
        MinecraftServer server = Life.server();
        if (t == null || server == null || !LifeConfig.get().enabled) return t;
        try {
            String section = companionSection(server, c, t.speaker());
            if (section.isEmpty()) return t;
            String user = t.user();
            int at = user.indexOf("== Now ==");
            user = at >= 0 ? user.substring(0, at) + section + user.substring(at) : section + user;
            Map<String, String> ctx = t.context() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(t.context());
            String fam = LifeMca.familyLine(server, c.getUUID());
            if (!fam.isEmpty()) ctx.put("family", fam);
            String preg = Pregnancies.describe(server, c.getUUID(), false);
            if (!preg.isEmpty()) ctx.put("pregnancy", preg);
            String feel = feelings(server, c, t.speaker(), false);
            if (!feel.isEmpty()) ctx.put("relationships", feel);
            return new TurnRequest(t.npcId(), t.npcName(), t.ownerName(), t.system(), user, t.trigger(), t.speaker(), t.eventType(), ctx);
        } catch (Throwable e) {
            Life.warnOnce("prompt", e);
            return t;
        }
    }

    static String companionSection(MinecraftServer server, CompanionEntity c, String speaker) {
        StringBuilder sb = new StringBuilder();
        String fam = LifeMca.familyLine(server, c.getUUID());
        if (!fam.isEmpty()) sb.append("Family: ").append(fam).append(".\n");
        String feel = feelings(server, c, speaker, true);
        if (!feel.isEmpty()) sb.append(feel).append('\n');
        RelationshipStore.Proposal waiting = null;
        for (RelationshipStore.Proposal p : RelationshipStore.get().store.data().proposals.values()) {
            if (p.from.equals(c.getUUID().toString())) waiting = p;
        }
        if (waiting != null) sb.append("You asked someone to ").append(Stage.valueOf(waiting.stage) == Stage.DATING ? "date you" : "marry you")
                .append(" and are waiting for an answer.\n");
        String preg = Pregnancies.describe(server, c.getUUID(), true);
        if (!preg.isEmpty()) sb.append(preg).append(" Let it show in how you talk.\n");
        if (sb.isEmpty()) return "";
        return "== Your life ==\n" + sb;
    }

    /** Feelings about the owner, the speaker, the partner and the strongest other bonds. */
    static String feelings(MinecraftServer server, CompanionEntity c, String speaker, boolean secondPerson) {
        LifeConfig cfg = LifeConfig.get();
        if (!cfg.on(cfg.relationships)) return "";
        long now = LifeMca.now(server);
        RelationshipMath.Params p = cfg.relationParams();
        List<UUID> order = new ArrayList<>();
        Optional<UUID> partner = Romance.partner(server, c.getUUID());
        partner.ifPresent(order::add);
        c.getOwnerId().ifPresent(order::add);
        if (speaker != null) {
            var sp = server.getPlayerList().getPlayerByName(speaker);
            if (sp != null) order.add(sp.getUUID());
        }
        Map<String, Bond> all = RelationshipStore.get().from(c.getUUID());
        all.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<String, Bond> e) -> -Math.abs(e.getValue().affection))).limit(3)
                .forEach(e -> order.add(UUID.fromString(e.getKey())));
        List<String> out = new ArrayList<>();
        for (UUID id : order.stream().distinct().limit(5).toList()) {
            Bond b = RelationshipStore.get().find(c.getUUID(), id);
            Stage st = Romance.stage(server, c.getUUID(), id);
            if (b == null && !st.romantic()) continue;
            if (b != null) RelationshipMath.decay(b, now, st.romantic(), p);
            String name = LifeMca.nameOf(server, id);
            StringBuilder s = new StringBuilder();
            if (st.romantic()) s.append(st == Stage.MARRIED ? "married to " : st == Stage.ENGAGED ? "engaged to " : "dating ").append(name);
            if (b != null) {
                s.append(s.isEmpty() ? "" : " and ").append(RelationshipMath.feeling(b.affection)).append(s.isEmpty() ? " " + name : " them")
                        .append(" (").append(Math.round(b.affection)).append("/100)");
                String j = RelationshipMath.jealousyWords(b.jealousy);
                if (!j.isEmpty()) s.append(", ").append(j).append(b.jealousOf.isEmpty() ? "" : " about " + b.jealousOf);
            }
            out.add(s.toString());
        }
        if (out.isEmpty()) return "";
        return (secondPerson ? "Your relationships: you are " : c.name() + " is ") + String.join("; ", out) + ".";
    }

    /** Extra sentences for an MCA villager's description (pregnancy, recent life events). */
    public static String villagerExtra(UUID id) {
        MinecraftServer server = Life.server();
        if (server == null || id == null || !LifeConfig.get().enabled) return "";
        try {
            StringBuilder sb = new StringBuilder();
            String preg = Pregnancies.describe(server, id, false);
            if (!preg.isEmpty()) sb.append(' ').append(preg);
            List<String> recent = NpcMemoryStore.get().recentEvents(id, 12).stream().filter(l -> LIFE_TYPES.contains(l.who()))
                    .map(NpcMemoryStore.Line::text).toList();
            if (!recent.isEmpty()) {
                sb.append(" Recently: ").append(String.join("; ", recent.subList(Math.max(0, recent.size() - 3), recent.size()))).append('.');
            }
            return sb.toString();
        } catch (Throwable e) {
            Life.warnOnce("villager-prompt", e);
            return "";
        }
    }
}
