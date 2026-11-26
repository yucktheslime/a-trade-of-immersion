package dev.yucktheslime.mcabodyshapes.body;

import dev.yucktheslime.tradeofimmersion.life.LifeNet;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pregnancy progress (0..1 over the whole pregnancy) that drives the belly.
 *
 * <p>Bundled in A Trade of Immersion, the belly follows TOI's Phase 2 pregnancy phases
 * ({@code Pregnancies} / the Pregnant effect) instead of MCA's own {@code HasBaby} / {@code BabyAge}:
 * TOI takes over MCA's gestation and pauses MCA's timer, so MCA's values no longer describe the pregnancy.
 * <ul>
 *   <li>Server: read from TOI's saved pregnancy record.</li>
 *   <li>Client: read from TOI's synced progress map ({@code LifeNet.clientProgress}), which is empty when
 *   TOI's {@code bellyVisual} option is off.</li>
 *   <li>Client preview entities (the editor's villager copies) and the editor's instant local preview use a
 *   local override set by {@link PregnancyWrites}; for real world entities it expires after a few seconds,
 *   once the server's own value has been synced.</li>
 * </ul>
 * The adult-only rule is enforced by the callers ({@link BodyShapeAge#isShapeableAdult}).
 */
public final class PregnancyProgress {
    private static final long LOCAL_OVERRIDE_MS = 3000L;
    private static final Map<Entity, float[]> LOCAL = new WeakHashMap<>();
    private static final Map<Entity, Long> LOCAL_TIME = new WeakHashMap<>();

    private PregnancyProgress() {
    }

    public static float of(LivingEntity entity) {
        if (entity == null) {
            return 0.0f;
        }
        try {
            if (entity.level() instanceof ServerLevel level) {
                return Mth.clamp(Pregnancies.progressOf(level.getServer(), entity.getUUID()), 0.0f, 1.0f);
            }
            float[] local = local(entity);
            if (local != null) {
                return local[0];
            }
            Float synced = LifeNet.clientProgress.get(entity.getUUID());
            return synced == null ? 0.0f : Mth.clamp(synced, 0.0f, 1.0f);
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }

    /** Client only: a local preview value for this entity (see {@link PregnancyWrites}). */
    static void setLocal(Entity entity, float progress) {
        synchronized (LOCAL) {
            LOCAL.put(entity, new float[]{Mth.clamp(progress, 0.0f, 1.0f)});
            LOCAL_TIME.put(entity, System.currentTimeMillis());
        }
    }

    private static float[] local(Entity entity) {
        synchronized (LOCAL) {
            float[] v = LOCAL.get(entity);
            if (v == null) {
                return null;
            }
            // Editor copies are never added to a level: keep their value. World entities follow the sync again.
            if (entity.isAddedToLevel()) {
                Long at = LOCAL_TIME.get(entity);
                if (at == null || System.currentTimeMillis() - at > LOCAL_OVERRIDE_MS) {
                    LOCAL.remove(entity);
                    LOCAL_TIME.remove(entity);
                    return null;
                }
            }
            return v;
        }
    }
}
