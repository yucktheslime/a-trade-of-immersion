package dev.yucktheslime.mcabodyshapes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.yucktheslime.mcabodyshapes.client.BodyShapeApplier;
import dev.yucktheslime.mcabodyshapes.client.BellyRenderer;
import dev.yucktheslime.mcabodyshapes.client.BustRenderer;
import dev.yucktheslime.mcabodyshapes.client.SeatRenderer;
import dev.yucktheslime.mcabodyshapes.client.ThighRenderer;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerEntityBaseModelMCAMixin<T extends LivingEntity & VillagerLike<T>> {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void mcabodyshapes$applyBodyShape(
            T villager,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        @SuppressWarnings("unchecked")
        HumanoidModel<T> model = (HumanoidModel<T>) (Object) this;
        @SuppressWarnings("unchecked")
        CommonVillagerModel<T> villagerModel = (CommonVillagerModel<T>) (Object) this;
        BodyShapeApplier.apply(villager, model, villagerModel);
    }

    @Inject(method = "renderToBuffer", at = @At("TAIL"))
    private void mcabodyshapes$renderSeat(
            PoseStack matrices,
            VertexConsumer vertices,
            int light,
            int overlay,
            int color,
            CallbackInfo ci
    ) {
        BustRenderer.render((HumanoidModel<?>) (Object) this, matrices, vertices, light, overlay, color);
        SeatRenderer.render((HumanoidModel<?>) (Object) this, matrices, vertices, light, overlay, color);
        ThighRenderer.render((HumanoidModel<?>) (Object) this, matrices, vertices, light, overlay, color);
        BellyRenderer.render((HumanoidModel<?>) (Object) this, matrices, vertices, light, overlay, color);
    }
}
