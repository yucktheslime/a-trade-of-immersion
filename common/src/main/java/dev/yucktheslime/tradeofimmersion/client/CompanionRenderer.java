package dev.yucktheslime.tradeofimmersion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.ClothingLayer;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.HairLayer;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Player-shaped renderer for companions with MCA Reborn's body: the vanilla player geometry (wide or slim, by
 * skin) plus MCA's breast parts, like MCA's own player renderer; or (config {@code companionAppearance:
 * mca_generated}) an MCA villager look from genes. Armor, held items, heads and elytra render like on a player.
 */
public class CompanionRenderer extends LivingEntityRenderer<CompanionEntity, PlayerModel<CompanionEntity>> {
    /** Extra layers from loader compat (e.g. MCA: Female Gender Physics). */
    public static final List<BiConsumer<CompanionRenderer, EntityRendererProvider.Context>> LAYER_HOOKS = new ArrayList<>();

    private final PlayerModel<CompanionEntity> wide, slim;
    private PlayerModel<CompanionEntity> mcaWide, mcaSlim;
    private final boolean generated;

    public CompanionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wide = this.model;
        this.slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        this.generated = CompanionBodyClient.featureEnabled() && CompanionBodyClient.generatedAppearance();
        try {
            if (CompanionBodyClient.featureEnabled()) {
                if (generated) {
                    mcaWide = mcaSlim = new PlayerEntityExtendedModel<>(ctx.bakeLayer(ModelLayers.PLAYER));
                    this.addLayer(new SkinLayer<>(this, visible(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE))));
                    this.addLayer(new FaceLayer<>(this, visible(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.01f))), "normal"));
                    this.addLayer(new ClothingLayer<>(this, visible(VillagerEntityModelMCA.bodyData(new CubeDeformation(0.0625f))), "normal"));
                    this.addLayer(new HairLayer<>(this, visible(VillagerEntityModelMCA.hairData(new CubeDeformation(0.125f)))));
                } else {
                    mcaWide = new PlayerEntityExtendedModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false,
                            LayerDefinition.create(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE), 64, 64).bakeRoot());
                    mcaSlim = new PlayerEntityExtendedModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true,
                            LayerDefinition.create(VillagerEntityModelMCA.bodyData(CubeDeformation.NONE), 64, 64).bakeRoot());
                }
            }
        } catch (Throwable t) {
            mcaWide = mcaSlim = null;
            TradeOfImmersion.LOGGER.warn("[tradeofimmersion] MCA companion body unavailable; using the vanilla player model", t);
        }
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        this.addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, ctx.getModelSet()));
        for (var hook : LAYER_HOOKS) {
            try {
                hook.accept(this, ctx);
            } catch (Throwable t) {
                TradeOfImmersion.LOGGER.warn("[tradeofimmersion] companion layer hook failed", t);
            }
        }
    }

    private static PlayerEntityExtendedModel<CompanionEntity> visible(MeshDefinition data) {
        return new PlayerEntityExtendedModel<>(LayerDefinition.create(data, 64, 64).bakeRoot());
    }

    @Override
    public void render(CompanionEntity e, float yaw, float tickDelta, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean slimSkin = !generated && CompanionSkins.model(e) == PlayerSkin.Model.SLIM;
        boolean mca = false;
        if (mcaWide != null) {
            try {
                mca = CompanionBodyClient.useMcaBody(e) && CompanionBodyClient.ensure(e) != null;
            } catch (Throwable t) {
                mca = false;
            }
        }
        this.model = mca ? (slimSkin ? mcaSlim : mcaWide) : (slimSkin ? slim : wide);
        setPoses(e);
        super.render(e, yaw, tickDelta, pose, buffers, light);
    }

    private void setPoses(CompanionEntity e) {
        PlayerModel<CompanionEntity> m = this.model;
        m.setAllVisible(true);
        m.crouching = e.isCrouching();
        HumanoidModel.ArmPose main = armPose(e, InteractionHand.MAIN_HAND), off = armPose(e, InteractionHand.OFF_HAND);
        if (main.isTwoHanded()) off = e.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (e.getMainArm() == HumanoidArm.RIGHT) {
            m.rightArmPose = main;
            m.leftArmPose = off;
        } else {
            m.rightArmPose = off;
            m.leftArmPose = main;
        }
    }

    private static HumanoidModel.ArmPose armPose(CompanionEntity e, InteractionHand hand) {
        ItemStack s = e.getItemInHand(hand);
        if (s.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if (e.getUsedItemHand() == hand && e.getUseItemRemainingTicks() > 0) {
            UseAnim a = s.getUseAnimation();
            if (a == UseAnim.BLOCK) return HumanoidModel.ArmPose.BLOCK;
            if (a == UseAnim.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
            if (a == UseAnim.SPEAR) return HumanoidModel.ArmPose.THROW_SPEAR;
            if (a == UseAnim.CROSSBOW) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
        } else if (s.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(s)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    @Override
    protected void scale(CompanionEntity e, PoseStack pose, float tickDelta) {
        pose.scale(0.9375f, 0.9375f, 0.9375f);
        try {
            CompanionBodyClient.applyScale(e, pose);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionEntity e) {
        if (generated && (this.model == mcaWide)) return CompanionBodyClient.TRANSPARENT;
        return CompanionSkins.texture(e);
    }

    @Override
    protected boolean shouldShowName(CompanionEntity e) {
        return super.shouldShowName(e) && (e.hasCustomName());
    }
}
