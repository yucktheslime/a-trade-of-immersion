package dev.yucktheslime.tradeofimmersion.client;

import dev.architectury.platform.Platform;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;

/**
 * Optional MCA Body Shapes (mod id mcabodyshapes, NeoForge). Its body sliders already apply to companions because it
 * hooks MCA's {@code PlayerEntityExtendedModel.setupAnim}, the model companions render with; only its height slider
 * lives in MCA's villager renderer, so it is applied here the same way ({@code BodyShapeApplier.heightMultiplier}).
 * Reflection only: the addon is never required.
 */
public final class BodyShapesClient {
    private static Boolean off;
    private static Method get, height;

    private BodyShapesClient() {
    }

    public static float heightMultiplier(Entity e) {
        if (Boolean.TRUE.equals(off)) return 1.0f;
        try {
            if (off == null) {
                off = !Platform.isModLoaded("mcabodyshapes");
                if (off) return 1.0f;
                Class<?> shape = Class.forName("dev.yucktheslime.mcabodyshapes.body.BodyShape");
                get = shape.getMethod("get", Entity.class);
                height = Class.forName("dev.yucktheslime.mcabodyshapes.client.BodyShapeApplier").getMethod("heightMultiplier", shape);
            }
            float h = (Float) height.invoke(null, get.invoke(null, e));
            return h > 0.1f && h < 3f ? h : 1.0f;
        } catch (Throwable t) {
            off = true;
            TradeOfImmersion.LOGGER.warn("[tradeofimmersion] MCA Body Shapes height unavailable for companions", t);
            return 1.0f;
        }
    }
}
