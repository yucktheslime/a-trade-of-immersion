package dev.yucktheslime.tradeofimmersion.life;

import dev.yucktheslime.tradeofimmersion.body.CompanionBodyStore;
import dev.yucktheslime.tradeofimmersion.body.CompanionLooks;
import dev.yucktheslime.tradeofimmersion.body.CompanionMcaData;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.kin.Kinship;
import dev.yucktheslime.tradeofimmersion.util.McaVillagerUtil;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every Phase 2 call into MCA Reborn's family tree and villager data, so an MCA change breaks one file. AI companions
 * are not MCA villagers; they get their own node in MCA's family tree ({@code FamilyTree.getOrCreate(uuid, name,
 * gender, false)}), so MCA's marriage records, family tree screen and relative checks include them.
 */
public final class LifeMca {
    private LifeMca() {
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static FamilyTree tree(MinecraftServer server) {
        return FamilyTree.get(server.overworld());
    }

    public enum Kind { PLAYER, VILLAGER, COMPANION, OTHER }

    public static Kind kind(Entity e) {
        if (e instanceof Player) return Kind.PLAYER;
        if (e instanceof VillagerEntityMCA) return Kind.VILLAGER;
        if (e instanceof CompanionEntity) return Kind.COMPANION;
        return Kind.OTHER;
    }

    public static boolean social(Entity e) {
        return kind(e) != Kind.OTHER;
    }

    public static String name(Entity e) {
        if (e instanceof Player p) return p.getGameProfile().getName();
        if (e instanceof VillagerEntityMCA v) return McaVillagerUtil.name(v);
        return e.getName().getString();
    }

    public static Gender gender(Entity e) {
        try {
            if (e instanceof CompanionEntity c) {
                return CompanionMcaData.parseGender(CompanionLooks.genderOf(c, CompanionBodyStore.get().find(c.getUUID())), Gender.MALE);
            }
            if (e instanceof VillagerEntityMCA v) return v.getGenetics().getGender();
            if (e instanceof ServerPlayer p) {
                Gender g = PlayerSaveData.get(p).getGender();
                return g == null ? Gender.UNASSIGNED : g;
            }
        } catch (Throwable ignored) {
        }
        return Gender.UNASSIGNED;
    }

    public static String genderWord(Entity e) {
        Gender g = gender(e);
        return g == Gender.FEMALE ? "female" : g == Gender.MALE ? "male" : "unknown";
    }

    public static boolean adult(Entity e) {
        if (e instanceof VillagerEntityMCA v) return v.getAgeState() == AgeState.ADULT;
        if (e instanceof CompanionEntity c) return !c.isBaby();
        return e instanceof Player;
    }

    /** The MCA family-tree node of a player, villager or companion (created when missing). Server side only. */
    public static FamilyTreeNode node(Entity e) {
        FamilyTree tree = FamilyTree.get(((ServerLevel) e.level()).getServer().overworld());
        if (e instanceof CompanionEntity c) {
            FamilyTreeNode n = tree.getOrCreate(c.getUUID(), c.name(), gender(c), false);
            if (!n.getName().equals(c.name())) n.setName(c.name());
            Gender g = gender(c);
            if (g != Gender.UNASSIGNED && n.gender() != g) n.setGender(g);
            return n;
        }
        if (e instanceof ServerPlayer p) return PlayerSaveData.get(p).getFamilyEntry();
        return tree.getOrCreate(e);
    }

    public static Optional<FamilyTreeNode> node(MinecraftServer server, UUID id) {
        return tree(server).getOrEmpty(id);
    }

    /** Parents in MCA's family tree, for {@link Kinship}. */
    public static List<UUID> parents(MinecraftServer server, UUID id) {
        return tree(server).getOrEmpty(id).map(n -> n.streamParents().toList()).orElse(List.of());
    }

    public static Kinship.Relation kin(MinecraftServer server, UUID a, UUID b) {
        FamilyTree t = tree(server);
        return Kinship.relation(a, b, id -> t.getOrEmpty(id).map(n -> n.streamParents().toList()).orElse(List.of()));
    }

    /** A loaded player, villager or companion by UUID (any dimension). */
    public static Entity find(MinecraftServer server, UUID id) {
        if (id == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    public static String nameOf(MinecraftServer server, UUID id) {
        Entity e = find(server, id);
        if (e != null) return name(e);
        return tree(server).getOrEmpty(id).map(FamilyTreeNode::getName).orElse("someone");
    }

    /**
     * MCA's view of an entity as a villager (genes and traits), for baby genes: villagers themselves, players through
     * MCA's player data, companions through the same villager record that gives them their MCA look.
     */
    public static VillagerLike<?> asVillager(Entity e) {
        if (e instanceof VillagerLike<?> v) return v;
        if (e instanceof CompanionEntity c) {
            VillagerEntityMCA v = CompanionMcaData.create(c.level(), c.getUUID(), gender(c),
                    CompanionBodyStore.get().find(c.getUUID()), ToiConfig.get().companionBreastSize, null, null);
            if (v != null) {
                v.setUUID(c.getUUID());
                v.setCustomName(c.getName());
                v.setPos(c.position());
            }
            return v;
        }
        return VillagerLike.toVillager(e);
    }

    /** Short family summary for prompts: spouse/partner, children, parents. */
    public static String familyLine(MinecraftServer server, UUID id) {
        Optional<FamilyTreeNode> on = node(server, id);
        if (on.isEmpty()) return "";
        FamilyTreeNode n = on.get();
        StringBuilder sb = new StringBuilder();
        if (FamilyTreeNode.isValid(n.partner())) {
            String who = tree(server).getOrEmpty(n.partner()).map(FamilyTreeNode::getName).orElse("someone");
            String st = switch (n.getRelationshipState()) {
                case PROMISED -> "dating";
                case ENGAGED -> "engaged to";
                case MARRIED_TO_PLAYER, MARRIED_TO_VILLAGER -> "married to";
                default -> "with";
            };
            sb.append(st).append(' ').append(who);
        } else if (n.getRelationshipState() == net.conczin.mca.entity.ai.relationship.RelationshipState.WIDOW) {
            sb.append("widowed");
        }
        List<String> kids = n.streamChildren().map(c -> tree(server).getOrEmpty(c).map(FamilyTreeNode::getName).orElse(null))
                .filter(x -> x != null).limit(8).toList();
        if (!kids.isEmpty()) sb.append(sb.isEmpty() ? "" : "; ").append("children: ").append(String.join(", ", kids));
        List<String> ps = n.getParents().map(FamilyTreeNode::getName).toList();
        if (!ps.isEmpty()) sb.append(sb.isEmpty() ? "" : "; ").append("parents: ").append(String.join(" and ", ps));
        return sb.toString();
    }
}
