package dev.yucktheslime.tradeofimmersion.life.marriage;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.LifeConfig;
import dev.yucktheslime.tradeofimmersion.life.LifeMca;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.PregnancyPhases;
import dev.yucktheslime.tradeofimmersion.life.relation.Stage;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Procreation for couples that include a companion (MCA handles its own couples, then the phases take over through
 * the BabyItem/Pregnancy mixins). Starts a pregnancy in the female partner (or, like MCA, the companion when neither
 * is female); the baby item comes at birth.
 */
public final class Procreation {
    private Procreation() {
    }

    public static Optional<String> blockReason(Entity a, Entity b) {
        LifeConfig cfg = LifeConfig.get();
        if (!cfg.on(cfg.companionMarriage)) return Optional.of("companion romance is off");
        MinecraftServer server = ((ServerLevel) a.level()).getServer();
        if (Romance.stage(server, a.getUUID(), b.getUUID()) != Stage.MARRIED) return Optional.of(LifeMca.name(a) + " and " + LifeMca.name(b) + " aren't married");
        if (a.distanceToSqr(b) > 8 * 8) return Optional.of("you need to be close to each other");
        if (Pregnancies.pregnant(a.getUUID()) || Pregnancies.pregnant(b.getUUID())) return Optional.of("someone is already expecting");
        if (infertile(a) || infertile(b)) return Optional.of("one of you is infertile");
        Entity carrier = carrier(a, b);
        Long last = Pregnancies.STORE.data().lastStart.get(carrier.getUUID().toString());
        long cd = Math.round(cfg.procreationCooldownDays * PregnancyPhases.DAY);
        if (last != null && LifeMca.now(server) - last < cd + Math.round(cfg.pregnancyParams().phases * cfg.pregnancyParams().ticksPerPhase)) {
            return Optional.of("it's too soon after the last baby");
        }
        return Optional.empty();
    }

    static boolean infertile(Entity e) {
        try {
            return e instanceof VillagerEntityMCA v && v.getTraits().hasTrait(Traits.INFERTILE);
        } catch (Throwable t) {
            return false;
        }
    }

    public static Entity carrier(Entity a, Entity b) {
        boolean fa = LifeMca.gender(a) == Gender.FEMALE, fb = LifeMca.gender(b) == Gender.FEMALE;
        if (fa != fb) return fa ? a : b;
        if (a instanceof CompanionEntity) return a;
        if (b instanceof CompanionEntity) return b;
        return a;
    }

    /** Returns a message for the player who asked (or null on success). */
    public static String conceive(Entity a, Entity b) {
        Optional<String> block = blockReason(a, b);
        if (block.isPresent()) return block.get() + ".";
        if (!Pregnancies.phasesOn()) return "pregnancy phases are off (pregnancyPhases); use MCA's own procreation instead.";
        Entity carrier = carrier(a, b);
        Entity other = carrier == a ? b : a;
        Pregnancies.start(carrier, other, a.level().getRandom().nextLong(), false);
        return null;
    }
}
