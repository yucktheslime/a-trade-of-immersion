package dev.yucktheslime.tradeofimmersion.life.client;

import dev.yucktheslime.tradeofimmersion.life.LifeConfig;
import dev.yucktheslime.tradeofimmersion.life.LifeNet;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Grows a belly on the MCA / player humanoid model from the synced pregnancy progress (0..1). The same model
 * instance is reused for every entity of that type, so scales and the body's z are set absolutely every frame
 * (never added to). Breast parts are repositioned by MCA every frame, so those get a one-frame bump after that.
 */
public final class BellyVisual {
    private static final WeakHashMap<ModelPart, Float> ORIG_Z = new WeakHashMap<>();
    /**
     * Set on NeoForge by the bundled MCA Body Shapes: true for entities whose belly its mesh draws on MCA models
     * (adult MCA villagers and players). Those are skipped here so the belly is not drawn twice; everything else
     * (companions, vanilla-model players, and all of Fabric) keeps this simple belly.
     */
    public static volatile Predicate<LivingEntity> drawnElsewhere = e -> false;

    private BellyVisual() {
    }

    public static float size(LivingEntity e) {
        if (e == null) return 0;
        try {
            if (!LifeConfig.get().on(LifeConfig.get().bellyVisual)) return 0;
            Float v = LifeNet.clientBellies.get(e.getUUID());
            return v == null ? 0 : Math.max(0, Math.min(1, v));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** After {@code HumanoidModel.setupAnim}: body scale and forward shift. */
    public static void applyBody(LivingEntity e, HumanoidModel<?> model) {
        if (model == null || model.body == null) return;
        if (model instanceof CommonVillagerModel<?> && drawnElsewhere.test(e)) {
            // the model is shared: undo a shift left by an entity drawn here before (scales are reset by Body Shapes)
            Float orig = ORIG_Z.get(model.body);
            if (orig != null) model.body.z = orig;
            return;
        }
        float belly = size(e);
        float max = 3.5f;
        try {
            max = (float) LifeConfig.get().bellyMaxDepth;
        } catch (Throwable ignored) {
        }
        ModelPart body = model.body;
        body.xScale = 1f + 0.16f * belly;
        body.yScale = 1f + 0.05f * belly;
        body.zScale = 1f + 0.55f * belly;
        float orig = ORIG_Z.computeIfAbsent(body, p -> p.z);
        body.z = orig + belly * max * 0.2f;
    }

    /** After MCA's {@code applyVillagerDimensions}: move the breasts forward with the belly. */
    public static void applyBreasts(LivingEntity e, CommonVillagerModel<?> model) {
        float belly = size(e);
        if (belly <= 0 || model == null) return;
        if (drawnElsewhere.test(e)) return;
        float max = 3.5f;
        try {
            max = (float) LifeConfig.get().bellyMaxDepth;
        } catch (Throwable ignored) {
        }
        for (ModelPart p : model.getBreastParts()) {
            p.z += belly * max * 0.15f;
        }
    }
}
