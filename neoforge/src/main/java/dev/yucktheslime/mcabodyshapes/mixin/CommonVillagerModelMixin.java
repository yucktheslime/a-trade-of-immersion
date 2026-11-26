package dev.yucktheslime.mcabodyshapes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yucktheslime.mcabodyshapes.client.SeatRenderer;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws clothed seat parts at the end of MCA's shared model render, after the
 * body and bust, so skin, clothing, and armor layers all include them with
 * their own textures.
 */
@Mixin(CommonVillagerModel.class)
public interface CommonVillagerModelMixin {
    @Inject(method = "renderCommon", at = @At("TAIL"))
    default void mcabodyshapes$renderSeats(
            PoseStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            CallbackInfo ci
    ) {
        if ((Object) this instanceof HumanoidModel<?> model) {
            SeatRenderer.render(model, matrices, vertices, light, overlay, color);
        }
    }
}
