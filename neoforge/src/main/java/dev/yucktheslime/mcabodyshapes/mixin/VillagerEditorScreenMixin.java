package dev.yucktheslime.mcabodyshapes.mixin;

import dev.yucktheslime.mcabodyshapes.client.BodyShapeEditorPage;
import dev.yucktheslime.mcabodyshapes.client.VillagerEditorAccess;
import net.conczin.mca.client.gui.DestinyScreen;
import net.conczin.mca.client.gui.LimitedVillagerEditorScreen;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Softly adds a Shape tab to MCA's villager editor. The existing "body" tab is
 * left alone (it's MCA's skin/size page).
 */
@Mixin(VillagerEditorScreen.class)
public abstract class VillagerEditorScreenMixin implements VillagerEditorAccess {
    @Shadow
    @Final
    protected VillagerEntityMCA villager;

    @Shadow
    @Final
    protected VillagerEntityMCA villagerVisualization;

    @Shadow
    @Final
    UUID villagerUUID;

    @Shadow
    @Final
    UUID playerUUID;

    @Shadow
    protected abstract void setPage(String page);

    @Inject(method = "getPages", at = @At("RETURN"), cancellable = true)
    private void mcabodyshapes$addShapePage(CallbackInfoReturnable<String[]> cir) {
        if (!mcabodyshapes$shouldShowShapePage()) {
            return;
        }
        String[] original = cir.getReturnValue();
        if (original == null) {
            return;
        }
        for (String page : original) {
            if ("shape".equals(page)) {
                return;
            }
        }
        String[] expanded = new String[original.length + 1];
        int write = 0;
        boolean inserted = false;
        for (String page : original) {
            expanded[write++] = page;
            if ("body".equals(page)) {
                expanded[write++] = "shape";
                inserted = true;
            }
        }
        if (!inserted) {
            expanded[expanded.length - 1] = "shape";
        }
        cir.setReturnValue(expanded);
    }

    @Inject(method = "setPage", at = @At("HEAD"))
    private void mcabodyshapes$copyShape(String page, CallbackInfo ci) {
        BodyShapeEditorPage.copyWorldShapeToPreview(this.villagerUUID, this.villager, this.villagerVisualization);
    }

    @Inject(method = "setPage", at = @At("TAIL"))
    private void mcabodyshapes$addShapeWidgets(String page, CallbackInfo ci) {
        if (!"shape".equals(page) || !mcabodyshapes$shouldShowShapePage()) {
            return;
        }
        BodyShapeEditorPage.populate(
                (VillagerEditorScreen) (Object) this,
                this.villager,
                this.villagerVisualization,
                this.villagerUUID
        );
    }

    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void mcabodyshapes$onEditorData(CompoundTag villagerData, CallbackInfo ci) {
        BodyShapeEditorPage.copyWorldShapeToPreview(this.villagerUUID, this.villager, this.villagerVisualization);
    }

    @Unique
    private boolean mcabodyshapes$shouldShowShapePage() {
        VillagerEditorScreen screen = (VillagerEditorScreen) (Object) this;
        if (screen instanceof LimitedVillagerEditorScreen || screen instanceof DestinyScreen) {
            return false;
        }
        if (BodyShapeEditorPage.isToiCompanion(this.villagerUUID)) {
            return false;
        }
        AgeState age = this.villager.getAgeState();
        if (age == AgeState.ADULT) {
            return true;
        }
        // The player's own editor can load before an age is assigned.
        return this.villagerUUID.equals(this.playerUUID) && (age == AgeState.UNASSIGNED);
    }

    /**
     * Used by {@link BodyShapeEditorPage} to rebuild the tab after Randomize/Reset.
     */
    @Override
    public void mcabodyshapes$rebuildShapePage() {
        this.setPage("shape");
    }
}
