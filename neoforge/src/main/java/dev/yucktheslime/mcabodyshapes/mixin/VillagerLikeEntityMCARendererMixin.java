package dev.yucktheslime.mcabodyshapes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yucktheslime.mcabodyshapes.body.BodyShape;
import dev.yucktheslime.mcabodyshapes.client.BodyShapeApplier;
import net.conczin.mca.client.render.VillagerLikeEntityMCARenderer;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerLikeEntityMCARenderer.class)
public abstract class VillagerLikeEntityMCARendererMixin<T extends Mob & VillagerLike<T>> {
    @Inject(method = "scale", at = @At("TAIL"))
    private void mcabodyshapes$applyHeight(T villager, PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (!BodyShapeApplier.shouldShape(villager)) {
            return;
        }
        BodyShape shape = BodyShape.get(villager);
        if (shape.height() == BodyShape.DEFAULT) {
            return;
        }
        float height = BodyShapeApplier.heightMultiplier(shape);
        matrices.scale(1.0f, height, 1.0f);
    }
}
