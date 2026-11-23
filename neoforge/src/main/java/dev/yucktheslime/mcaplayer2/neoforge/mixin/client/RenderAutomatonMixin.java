package dev.yucktheslime.mcaplayer2.neoforge.mixin.client;

import com.goodbird.player2npc.client.render.RenderAutomaton;
import com.goodbird.player2npc.companion.AutomatoneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.yucktheslime.mcaplayer2.neoforge.client.CompanionBodyClient;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only: renders Player2NPC companions ({@code RenderAutomaton}, a
 * {@code LivingEntityRenderer<AutomatoneEntity, PlayerModel<AutomatoneEntity>>}) with MCA Reborn's body.
 *
 * <p>Mirrors what MCA 7.7.36's own {@code MixinPlayerRenderer} does for players (verified by decompiling):
 * <ul>
 *   <li>{@code player_skin}: MCA's "PLAYER" model -
 *       {@code new PlayerEntityExtendedModel(playerParts, slim, bodyData parts)}, i.e. the vanilla player
 *       geometry + MCA breast parts, textured with the companion's own Player2 skin.</li>
 *   <li>{@code mca_generated}: MCA's "VILLAGER" look - the base model is drawn with a transparent texture
 *       and MCA's Skin/Face/Clothing/Hair layers draw a gene-generated villager appearance.</li>
 * </ul>
 */
@Mixin(value = RenderAutomaton.class, remap = false)
public abstract class RenderAutomatonMixin extends LivingEntityRenderer<AutomatoneEntity, PlayerModel<AutomatoneEntity>> {
    @Unique
    private PlayerModel<AutomatoneEntity> mcaplayer2$vanillaModel;
    @Unique
    private PlayerEntityExtendedModel<AutomatoneEntity> mcaplayer2$mcaModel;
    @Unique
    private boolean mcaplayer2$generated;

    protected RenderAutomatonMixin(EntityRendererProvider.Context ctx, PlayerModel<AutomatoneEntity> model, float shadow) {
        super(ctx, model, shadow);
    }

    @Unique
    private static PlayerEntityExtendedModel<AutomatoneEntity> mcaplayer2$visible(MeshDefinition data) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void mcaplayer2$init(EntityRendererProvider.Context ctx, CallbackInfo ci) {
        try {
            if (!CompanionBodyClient.featureEnabled()) return;
            this.mcaplayer2$vanillaModel = this.model;
            this.mcaplayer2$generated = CompanionBodyClient.generatedAppearance();
            if (mcaplayer2$generated) {
                this.mcaplayer2$mcaModel = new PlayerEntityExtendedModel<>(ctx.bakeLayer(ModelLayers.PLAYER));
                this.layers.add(0, new SkinLayer<>(this, mcaplayer2$visible(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE))));
                this.addLayer(new FaceLayer<>(this, mcaplayer2$visible(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01f))), "normal"));
                this.addLayer(new ClothingLayer<>(this, mcaplayer2$visible(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0625f))), "normal"));
                this.addLayer(new HairLayer<>(this, mcaplayer2$visible(VillagerEntityModelMCA.hairData(new CubeDeformation(0.125f)))));
            } else {
                var mcaParts = LayerDefinition.create(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE), 64, 64).bakeRoot();
                this.mcaplayer2$mcaModel = new PlayerEntityExtendedModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false, mcaParts);
            }
            if (dev.yucktheslime.mcaplayer2.neoforge.compat.McabpPresence.loaded()) {
                dev.yucktheslime.mcaplayer2.neoforge.compat.FemaleGenderPhysicsCompat.addLayer(this, ctx);
            }
            dev.yucktheslime.mcaplayer2.McaPlayer2Bridge.LOGGER.info("[mcaplayer2] MCA body model ready for Player2 companions (appearance={})",
                    mcaplayer2$generated ? "mca_generated" : "player_skin");
        } catch (Throwable t) {
            this.mcaplayer2$mcaModel = null;
            dev.yucktheslime.mcaplayer2.McaPlayer2Bridge.LOGGER.warn("[mcaplayer2] MCA companion body unavailable; using vanilla model", t);
        }
    }

    @Inject(method = "render(Lcom/goodbird/player2npc/companion/AutomatoneEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), require = 0)
    private void mcaplayer2$selectModel(AutomatoneEntity entity, float yaw, float tickDelta, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (mcaplayer2$mcaModel == null || mcaplayer2$vanillaModel == null) return;
        boolean mca;
        try {
            mca = CompanionBodyClient.useMcaBody(entity) && CompanionBodyClient.ensure(entity) != null;
        } catch (Throwable t) {
            mca = false;
        }
        this.model = mca ? mcaplayer2$mcaModel : mcaplayer2$vanillaModel;
    }

    @Inject(method = "scale(Lcom/goodbird/player2npc/companion/AutomatoneEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("TAIL"), require = 0)
    private void mcaplayer2$scale(AutomatoneEntity entity, PoseStack pose, float tickDelta, CallbackInfo ci) {
        try {
            CompanionBodyClient.applyScale(entity, pose);
        } catch (Throwable ignored) {
            // keep Player2NPC's scale
        }
    }

    @Inject(method = "getTextureLocation(Lcom/goodbird/player2npc/companion/AutomatoneEntity;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void mcaplayer2$texture(AutomatoneEntity entity, CallbackInfoReturnable<ResourceLocation> cir) {
        if (mcaplayer2$generated && mcaplayer2$mcaModel != null && this.model == mcaplayer2$mcaModel) {
            cir.setReturnValue(CompanionBodyClient.TRANSPARENT);
        }
    }
}
