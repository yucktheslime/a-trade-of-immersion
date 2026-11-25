package dev.yucktheslime.tradeofimmersion.life.marriage;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.entity.Companions;
import dev.yucktheslime.tradeofimmersion.life.LifeConfig;
import dev.yucktheslime.tradeofimmersion.life.LifeMca;
import dev.yucktheslime.tradeofimmersion.life.relation.Bond;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipMath;
import dev.yucktheslime.tradeofimmersion.life.relation.RelationshipStore;
import dev.yucktheslime.tradeofimmersion.life.relation.Stage;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs every 30 seconds: time spent together builds affection, neglect drifts it, and NPC pairs with a companion move
 * through courtship on their own (date, engage, marry, break up, divorce), at most one step per in-game day. A
 * companion may ask its player out or propose, and married NPC couples can conceive.
 */
public final class RomanceTicker {
    public static final int PERIOD = 600;

    private RomanceTicker() {
    }

    public static void tick(MinecraftServer server) {
        LifeConfig cfg = LifeConfig.get();
        if (!cfg.on(cfg.relationships)) return;
        long now = LifeMca.now(server), today = now / RelationshipMath.DAY;
        RelationshipMath.Params p = cfg.relationParams();
        RelationshipStore st = RelationshipStore.get();
        List<String> pairsDone = new ArrayList<>();
        for (CompanionEntity c : Companions.all(server)) {
            if (!c.isAlive()) continue;
            List<Entity> near = c.level().getEntities(c, c.getBoundingBox().inflate(cfg.togetherRadius),
                    e -> e.isAlive() && (e instanceof CompanionEntity || e instanceof VillagerEntityMCA v && LifeMca.adult(v)));
            for (Entity n : near) {
                String key = RelationshipStore.pair(c.getUUID(), n.getUUID());
                if (pairsDone.contains(key)) continue;
                pairsDone.add(key);
                Long day = st.store.data().togetherDay.get(key);
                if (day == null || day != today) {
                    st.store.data().togetherDay.put(key, today);
                    Romance.interact(c, n, RelationshipMath.Kind.TIME_TOGETHER, 1);
                    Romance.interact(n, c, RelationshipMath.Kind.TIME_TOGETHER, 1);
                    st.store.dirty();
                }
                if (cfg.on(cfg.autonomousRomance)) autonomous(server, c, n, now, today, p, cfg);
            }
            ownerRomance(server, c, now, today, p, cfg);
        }
    }

    private static void autonomous(MinecraftServer server, Entity a, Entity b, long now, long today, RelationshipMath.Params p, LifeConfig cfg) {
        RelationshipStore st = RelationshipStore.get();
        String key = RelationshipStore.pair(a.getUUID(), b.getUUID());
        Long changed = st.store.data().stageDay.get(key);
        Stage stage = Romance.stage(server, a.getUUID(), b.getUUID());
        Bond ab = st.bond(a.getUUID(), b.getUUID()), ba = st.bond(b.getUUID(), a.getUUID());
        RelationshipMath.decay(ab, now, stage.romantic(), p);
        RelationshipMath.decay(ba, now, stage.romantic(), p);
        double mutual = RelationshipMath.mutual(ab, ba), jeal = Math.max(ab.jealousy, ba.jealousy);
        if (stage == Stage.MARRIED && cfg.npcProcreation) maybeConceive(a, b, cfg);
        if (changed != null && changed == today) return;
        if (RelationshipMath.shouldEnd(stage, mutual, jeal, p)) {
            if (stage == Stage.MARRIED && !cfg.divorce) return;
            Romance.end(a, b.getUUID(), true);
            st.store.data().stageDay.put(key, today);
            return;
        }
        Stage next = cfg.courtshipStages ? RelationshipMath.advance(stage, mutual, jeal, p)
                : stage == Stage.NONE && mutual >= p.marryAt && jeal < 70 ? Stage.MARRIED : stage;
        if (next == stage || Romance.blockReason(a, b, next).isPresent()) return;
        // the one who cares more asks (so "villager -> companion" and "companion -> villager" both happen)
        if (ab.affection >= ba.affection) Romance.setStage(a, b, next);
        else Romance.setStage(b, a, next);
    }

    private static void maybeConceive(Entity a, Entity b, LifeConfig cfg) {
        double perDay = Math.max(0, Math.min(1, cfg.npcProcreationChancePerDay));
        double perCheck = 1 - Math.pow(1 - perDay, PERIOD / (double) RelationshipMath.DAY);
        if (a.level().getRandom().nextDouble() >= perCheck) return;
        if (Procreation.carrier(a, b) == null || LifeMca.gender(a) == LifeMca.gender(b)) return; // NPCs need a mother and a father
        if (Procreation.blockReason(a, b).isEmpty() && dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies.phasesOn()) Procreation.conceive(a, b);
    }

    /** A companion's romance with its own player: proposing when it's ready, leaving when love is gone. */
    private static void ownerRomance(MinecraftServer server, CompanionEntity c, long now, long today, RelationshipMath.Params p, LifeConfig cfg) {
        Optional<UUID> ownerId = c.getOwnerId();
        if (ownerId.isEmpty()) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId.get());
        RelationshipStore st = RelationshipStore.get();
        Stage stage = Romance.stage(server, c.getUUID(), ownerId.get());
        Bond b = st.find(c.getUUID(), ownerId.get());
        if (b == null) return;
        RelationshipMath.decay(b, now, stage.romantic(), p);
        String key = RelationshipStore.pair(c.getUUID(), ownerId.get());
        Long changed = st.store.data().stageDay.get(key);
        if (changed != null && changed == today) return;
        if (stage.romantic() && RelationshipMath.shouldEnd(stage, b.affection, b.jealousy, p) && (stage != Stage.MARRIED || cfg.divorce) && owner != null) {
            Romance.end(c, ownerId.get(), true);
            st.store.data().stageDay.put(key, today);
            c.say(stage == Stage.MARRIED ? "I can't do this anymore. I want a divorce." : "I think we should stop seeing each other.");
            owner.sendSystemMessage(Component.literal(c.name() + (stage == Stage.MARRIED ? " divorced you." : " broke up with you."))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        if (!cfg.companionProposesToPlayer || owner == null || owner.level() != c.level() || owner.distanceToSqr(c) > 16 * 16) return;
        Stage next = RelationshipMath.advance(stage, b.affection, b.jealousy, p);
        if (!cfg.courtshipStages && stage == Stage.NONE && b.affection >= p.proposeAt) next = Stage.ENGAGED;
        if (next == stage) return;
        RelationshipStore.Proposal pending = st.store.data().proposals.get(owner.getUUID().toString());
        if (pending != null && now - pending.time < RelationshipMath.DAY) return;
        Long refused = st.store.data().refusals.get(c.getUUID() + "|" + owner.getUUID() + "|" + next);
        if (refused != null && now - refused < 2 * RelationshipMath.DAY) return;
        if (Romance.blockReason(c, owner, next).isPresent()) return;
        if (c.getRandom().nextDouble() < 0.2) {
            Romance.propose(c, owner, next);
            st.store.data().stageDay.put(key, today);
        }
    }
}
