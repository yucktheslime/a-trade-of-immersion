package dev.yucktheslime.tradeofimmersion.life.marriage;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.LifeConfig;
import dev.yucktheslime.tradeofimmersion.life.LifeEvents;
import dev.yucktheslime.tradeofimmersion.life.LifeMca;
import dev.yucktheslime.tradeofimmersion.life.kin.Kinship;
import dev.yucktheslime.tradeofimmersion.life.relation.Bond;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipMath;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipStore;
import dev.yucktheslime.tradeofimmersion.life.relation.Stage;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Courtship and marriage for AI companions, in all four directions (companion-player, companion-companion,
 * companion-MCA villager, MCA villager-companion), stored in MCA's own family tree so spouses, the family tree
 * screen, MCA's relative checks and procreation all see it. Pairs without a companion (player-villager,
 * villager-villager, player-player) stay MCA's business; the kin check still applies to MCA's rings through
 * MCA's own blood-relative test.
 */
public final class Romance {
    private Romance() {
    }

    public static boolean involvesCompanion(Entity a, Entity b) {
        return a instanceof CompanionEntity || b instanceof CompanionEntity;
    }

    /** The pair's stage from MCA's family tree (a's node pointing at b). */
    public static Stage stage(MinecraftServer server, UUID a, UUID b) {
        Optional<FamilyTreeNode> n = LifeMca.node(server, a);
        if (n.isEmpty() || !b.equals(n.get().partner())) return Stage.NONE;
        return switch (n.get().getRelationshipState()) {
            case PROMISED -> Stage.DATING;
            case ENGAGED -> Stage.ENGAGED;
            case MARRIED_TO_PLAYER, MARRIED_TO_VILLAGER -> Stage.MARRIED;
            default -> Stage.NONE;
        };
    }

    /** The partner UUID in MCA's tree (dating, engaged or married), if any. */
    public static Optional<UUID> partner(MinecraftServer server, UUID id) {
        return LifeMca.node(server, id).filter(n -> FamilyTreeNode.isValid(n.partner())
                && n.getRelationshipState() != RelationshipState.SINGLE && n.getRelationshipState() != RelationshipState.WIDOW).map(FamilyTreeNode::partner);
    }

    /** Why {@code a} and {@code b} can't move to {@code to}, or empty when they can. */
    public static Optional<String> blockReason(Entity a, Entity b, Stage to) {
        LifeConfig cfg = LifeConfig.get();
        if (!cfg.on(cfg.companionMarriage)) return Optional.of("companion romance is turned off (companionMarriage)");
        if (a == b || a.getUUID().equals(b.getUUID())) return Optional.of("that's the same person");
        if (!LifeMca.social(a) || !LifeMca.social(b)) return Optional.of("only players, companions and MCA villagers can marry");
        if (!LifeMca.adult(a) || !LifeMca.adult(b)) return Optional.of("both have to be adults");
        MinecraftServer server = ((ServerLevel) a.level()).getServer();
        Kinship.Relation rel = LifeMca.kin(server, a.getUUID(), b.getUUID());
        if (Kinship.forbidden(rel, cfg.kinBlockDepth)) {
            return Optional.of(LifeMca.name(a) + " and " + LifeMca.name(b) + " are " + rel.describe() + " - family can't marry");
        }
        for (Entity e : new Entity[]{a, b}) {
            Entity other = e == a ? b : a;
            Optional<UUID> p = partner(server, e.getUUID());
            if (p.isPresent() && !p.get().equals(other.getUUID())) {
                return Optional.of(LifeMca.name(e) + " is already with " + LifeMca.nameOf(server, p.get()));
            }
        }
        Stage cur = stage(server, a.getUUID(), b.getUUID());
        if (cur == to) return Optional.of("they already are " + words(to));
        if (cfg.courtshipStages && to.ordinal() > cur.ordinal() + 1) {
            return Optional.of("they need to be " + words(Stage.values()[to.ordinal() - 1]) + " first");
        }
        return Optional.empty();
    }

    public static String words(Stage s) {
        return switch (s) {
            case NONE -> "single";
            case DATING -> "dating";
            case ENGAGED -> "engaged";
            case MARRIED -> "married";
        };
    }

    private static RelationshipState mcaState(Stage s, Entity partner) {
        return switch (s) {
            case NONE -> RelationshipState.SINGLE;
            case DATING -> RelationshipState.PROMISED;
            case ENGAGED -> RelationshipState.ENGAGED;
            case MARRIED -> partner instanceof Player ? RelationshipState.MARRIED_TO_PLAYER : RelationshipState.MARRIED_TO_VILLAGER;
        };
    }

    /** Writes the stage for both into MCA's family tree. No checks (callers use {@link #blockReason}). */
    public static void setStage(Entity a, Entity b, Stage s) {
        MinecraftServer server = ((ServerLevel) a.level()).getServer();
        Stage before = stage(server, a.getUUID(), b.getUUID());
        if (s == Stage.NONE) {
            end(a, b.getUUID(), false);
            return;
        }
        FamilyTreeNode na = LifeMca.node(a), nb = LifeMca.node(b);
        na.updatePartner(b, mcaState(s, b));
        nb.updatePartner(a, mcaState(s, a));
        if (a instanceof ServerPlayer p) PlayerSaveData.get(p).setDirty();
        if (b instanceof ServerPlayer p) PlayerSaveData.get(p).setDirty();
        for (Entity e : new Entity[]{a, b}) {
            if (e instanceof VillagerEntityMCA v) v.getVillagerBrain().modifyMoodValue(s == Stage.MARRIED ? 15 : 8);
        }
        RelationshipStore.get().store.data().stageDay.put(RelationshipStore.pair(a.getUUID(), b.getUUID()), LifeMca.now(server) / RelationshipMath.DAY);
        RelationshipStore.get().store.data().proposals.remove(a.getUUID().toString());
        RelationshipStore.get().store.data().proposals.remove(b.getUUID().toString());
        RelationshipStore.get().store.dirty();
        String text = switch (s) {
            case DATING -> LifeMca.name(a) + " and " + LifeMca.name(b) + " started dating";
            case ENGAGED -> LifeMca.name(a) + " and " + LifeMca.name(b) + " got engaged";
            case MARRIED -> LifeMca.name(a) + " and " + LifeMca.name(b) + " got married";
            default -> "";
        };
        if (s != before) LifeEvents.record(server, a, "romance", text, a.getUUID(), b.getUUID());
        TradeOfImmersion.LOGGER.info("[tradeofimmersion] {}", text);
    }

    /** Ends a's romance with b (break-up, or divorce when married). */
    public static void end(Entity a, UUID b, boolean byChoice) {
        MinecraftServer server = ((ServerLevel) a.level()).getServer();
        Stage before = stage(server, a.getUUID(), b);
        if (before == Stage.NONE) return;
        FamilyTreeNode na = LifeMca.node(a);
        na.updatePartner(null, RelationshipState.SINGLE); // MCA clears the partner's side too
        Entity other = LifeMca.find(server, b);
        if (a instanceof ServerPlayer p) PlayerSaveData.get(p).setDirty();
        if (other instanceof ServerPlayer p) PlayerSaveData.get(p).setDirty();
        if (other instanceof VillagerEntityMCA v) v.getVillagerBrain().modifyMoodValue(-15);
        if (a instanceof VillagerEntityMCA v) v.getVillagerBrain().modifyMoodValue(-15);
        RelationshipMath.Params p = LifeConfig.get().relationParams();
        long now = LifeMca.now(server);
        for (Bond bd : new Bond[]{RelationshipStore.get().bond(b, a.getUUID()), RelationshipStore.get().bond(a.getUUID(), b)}) {
            RelationshipMath.decay(bd, now, false, p);
            bd.affection = RelationshipMath.clampAffection(bd.affection - (before == Stage.MARRIED ? 25 : 12));
        }
        String what = before == Stage.MARRIED ? "divorced" : "broke up";
        LifeEvents.record(server, a, "romance", LifeMca.name(a) + " and " + LifeMca.nameOf(server, b) + " " + what, a.getUUID(), b);
    }

    // ---------------------------------------------------------------- interactions

    /** {@code receiver}'s feelings about {@code actor} change after an interaction. */
    public static double interact(Entity receiver, Entity actor, RelationshipMath.Kind kind, double weight) {
        LifeConfig cfg = LifeConfig.get();
        if (!cfg.on(cfg.relationships) || receiver == null || actor == null || receiver == actor) return 0;
        if (!LifeMca.social(receiver) || !LifeMca.social(actor) || receiver instanceof Player) return 0;
        MinecraftServer server = ((ServerLevel) receiver.level()).getServer();
        Bond b = RelationshipStore.get().bond(receiver.getUUID(), actor.getUUID());
        RelationshipMath.decay(b, LifeMca.now(server), stage(server, receiver.getUUID(), actor.getUUID()).romantic(), cfg.relationParams());
        return RelationshipMath.interact(b, kind, weight, LifeMca.now(server), cfg.relationParams());
    }

    /**
     * {@code actor} flirted with / gifted {@code rival}. Their partner (and anyone fond of them nearby) gets jealous.
     * {@code intensity}: flirt 1, gift 0.7, chat 0.2.
     */
    public static void rivalry(Entity actor, Entity rival, double intensity, String what) {
        LifeConfig cfg = LifeConfig.get();
        if (!cfg.on(cfg.relationships) || actor == null || rival == null) return;
        MinecraftServer server = ((ServerLevel) actor.level()).getServer();
        long now = LifeMca.now(server);
        RelationshipMath.Params p = cfg.relationParams();
        Optional<UUID> partner = partner(server, actor.getUUID());
        for (Entity w : actor.level().getEntities(actor, actor.getBoundingBox().inflate(cfg.lifeWitnessRadius),
                e -> e != rival && (e instanceof CompanionEntity || e instanceof VillagerEntityMCA))) {
            boolean isPartner = partner.isPresent() && partner.get().equals(w.getUUID());
            Bond b = RelationshipStore.get().find(w.getUUID(), actor.getUUID());
            if (!isPartner && (b == null || b.affection < 40)) continue;
            if (b == null) b = RelationshipStore.get().bond(w.getUUID(), actor.getUUID());
            double before = b.jealousy;
            RelationshipMath.makeJealous(b, isPartner ? stage(server, w.getUUID(), actor.getUUID()) : Stage.NONE, intensity, now, p);
            RelationshipStore.get().store.dirty();
            if (b.jealousy - before >= 3) {
                b.jealousOf = LifeMca.name(rival);
                LifeEvents.remember(w.getUUID(), "jealousy", "You saw " + LifeMca.name(actor) + " " + what + " " + LifeMca.name(rival)
                        + " and felt " + RelationshipMath.jealousyWords(b.jealousy) + ".");
                if (w instanceof CompanionEntity c && b.jealousy >= 45) {
                    dev.yucktheslime.tradeofimmersion.ai.CompanionBrain.onEvent(c, "jealousy",
                            "You just saw " + LifeMca.name(actor) + " " + what + " " + LifeMca.name(rival) + ". You feel "
                                    + RelationshipMath.jealousyWords(b.jealousy) + ". React in character (one line).");
                }
            }
        }
    }

    // ---------------------------------------------------------------- proposals (player <-> companion)

    /** Asks {@code asked} (a companion) to move to {@code to} with {@code asker}. Returns a line to show. */
    public static String ask(Entity asker, CompanionEntity asked, Stage to) {
        Optional<String> block = blockReason(asker, asked, to);
        if (block.isPresent()) return block.get() + ".";
        MinecraftServer server = ((ServerLevel) asked.level()).getServer();
        LifeConfig cfg = LifeConfig.get();
        RelationshipStore.Proposal pending = RelationshipStore.get().store.data().proposals.get(asker.getUUID().toString());
        boolean theyAsked = pending != null && pending.from.equals(asked.getUUID().toString()) && Stage.valueOf(pending.stage) == to;
        Bond b = RelationshipStore.get().bond(asked.getUUID(), asker.getUUID());
        long now = LifeMca.now(server);
        RelationshipMath.decay(b, now, stage(server, asked.getUUID(), asker.getUUID()).romantic(), cfg.relationParams());
        String key = asker.getUUID() + "|" + asked.getUUID() + "|" + to;
        Long refused = RelationshipStore.get().store.data().refusals.get(key);
        if (!theyAsked && refused != null && now - refused < RelationshipMath.DAY / 2) {
            return asked.name() + " already gave you an answer. Give it some time.";
        }
        double chance = theyAsked ? 1.0 : RelationshipMath.acceptChance(to, b.affection, b.jealousy, cfg.relationParams());
        if (asked.getRandom().nextDouble() < chance) {
            setStage(asker, asked, to);
            asked.say(switch (to) {
                case DATING -> "Yes... I'd love to.";
                case ENGAGED -> "Yes! Yes, I'll marry you!";
                case MARRIED -> "I do.";
                default -> "Okay.";
            });
            return LifeMca.name(asker) + " and " + asked.name() + " are now " + words(to) + ".";
        }
        RelationshipStore.get().store.data().refusals.put(key, now);
        RelationshipStore.get().store.dirty();
        asked.say(b.affection < 15 ? "I... don't know you well enough for that." : b.jealousy > 45 ? "Not while you keep looking at others."
                : "I'm not ready yet. Ask me again some time.");
        return asked.name() + " said no (affection " + Math.round(b.affection) + ").";
    }

    /** A companion asks a player (stored until the player answers with a ring, a bouquet or /npc romance accept). */
    public static void propose(CompanionEntity c, ServerPlayer player, Stage to) {
        RelationshipStore.Proposal p = new RelationshipStore.Proposal();
        p.from = c.getUUID().toString();
        p.fromName = c.name();
        p.stage = to.name();
        p.time = LifeMca.now(player.server);
        RelationshipStore.get().store.data().proposals.put(player.getUUID().toString(), p);
        RelationshipStore.get().store.dirty();
        String q = switch (to) {
            case DATING -> "go out with me";
            case ENGAGED -> "marry me";
            case MARRIED -> "make it official - let's have the wedding";
            default -> "stay with me";
        };
        dev.yucktheslime.tradeofimmersion.ai.CompanionBrain.onEvent(c, "romance",
                "You have decided to ask " + player.getGameProfile().getName() + " to " + q + ". Ask them now, sincerely, in one or two lines.");
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(c.name() + " wants to " + words(to).replace("dating", "date you")
                .replace("engaged", "get engaged").replace("married", "marry you") + ". Answer with "
                + (to == Stage.DATING ? "a bouquet" : to == Stage.ENGAGED ? "an engagement ring" : "a wedding ring")
                + " or /npc romance accept " + c.name()).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
    }

    public static String accept(ServerPlayer player, CompanionEntity c) {
        RelationshipStore.Proposal p = RelationshipStore.get().store.data().proposals.get(player.getUUID().toString());
        if (p == null || !p.from.equals(c.getUUID().toString())) return c.name() + " hasn't asked you anything.";
        return ask(player, c, Stage.valueOf(p.stage));
    }
}
