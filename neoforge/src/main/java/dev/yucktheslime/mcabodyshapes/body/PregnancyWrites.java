package dev.yucktheslime.mcabodyshapes.body;

import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Writes pregnancy progress for the Shape tab slider and {@code /mcabody pregnancy}.
 *
 * <p>Server side this sets A Trade of Immersion's pregnancy ({@link Pregnancies#setProgress}): it starts one
 * (with the villager's MCA partner, if any) when there is none, moves it to the given progress, or ends it
 * silently at 0. Pregnancy phases must be on in TOI's life config. Client side (editor previews) it only sets
 * a local preview value read by {@link PregnancyProgress}.
 */
public final class PregnancyWrites {
    private PregnancyWrites() {
    }

    /** Returns true when something was written. */
    public static boolean apply(LivingEntity entity, float progress) {
        if (entity == null) {
            return false;
        }
        float clamped = Mth.clamp(progress, 0.0f, 1.0f);
        // Clearing is always allowed; growing a belly is adult-only, including editor previews of non-adults.
        if (clamped > 0.001f && !BodyShapeAge.isShapeableAdult(entity)) {
            return false;
        }
        if (entity.level().isClientSide) {
            PregnancyProgress.setLocal(entity, clamped);
            return true;
        }
        return Pregnancies.setProgress(entity, clamped);
    }
}
