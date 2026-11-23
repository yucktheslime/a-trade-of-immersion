package dev.yucktheslime.mcaplayer2.neoforge.mixin.compat;

import com.goodbird.player2npc.companion.AutomatoneEntity;
import dev.yucktheslime.mcaplayer2.neoforge.client.CompanionBodyClient;
import net.conczin.mca.client.render.DynamicSkinCache;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The addon textures breasts with the villager's stitched MCA skin or the player's skin; companions are neither,
 * so give it the companion's own Player2 skin (player_skin) or MCA's stitched skin of our record (mca_generated).
 */
@Pseudo
@Mixin(targets = "com.mcabp.wildfire.client.render.GenderLayer", remap = false)
public abstract class McabpGenderLayerMixin {
    @Inject(method = "getBreastTexture(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void mcaplayer2$texture(LivingEntity entity, CallbackInfoReturnable<ResourceLocation> cir) {
        if (!(entity instanceof AutomatoneEntity npc)) return;
        VillagerEntityMCA v = CompanionBodyClient.villagerFor(entity);
        if (v == null) return;
        if (CompanionBodyClient.generatedAppearance()) {
            cir.setReturnValue(DynamicSkinCache.getOrCreateStitchedSkin(v));
            return;
        }
        ResourceLocation skin = npc.textureLocation;
        if (skin == null) {
            try {
                skin = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(npc).getTextureLocation(npc);
            } catch (Throwable ignored) {
                skin = null;
            }
        }
        cir.setReturnValue(skin != null ? skin : DefaultPlayerSkin.getDefaultTexture());
    }
}
