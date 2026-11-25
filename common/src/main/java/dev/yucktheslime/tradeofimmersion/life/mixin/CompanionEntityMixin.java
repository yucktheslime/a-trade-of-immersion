package dev.yucktheslime.tradeofimmersion.life.mixin;

import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.life.hooks.LifeHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into this mod's own companion (instead of editing the shared CompanionEntity file): MCA's bouquet, rings and
 * divorce papers on a companion, and gifts. mobInteract overrides a vanilla method, so both its Mojang name and its
 * Fabric intermediary name (method_5992) are listed with remap=false; giveFrom is this mod's own name.
 */
@Mixin(value = CompanionEntity.class, remap = false)
public abstract class CompanionEntityMixin {
    @Unique
    private static ItemStack tradeofimmersion$gift = ItemStack.EMPTY;

    @Inject(method = {"mobInteract", "method_5992"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void tradeofimmersion$romanceItems(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult r = LifeHooks.onCompanionInteract((CompanionEntity) (Object) this, player, hand);
        if (r != null) cir.setReturnValue(r);
    }

    @Inject(method = "giveFrom", at = @At("HEAD"), remap = false)
    private void tradeofimmersion$giftBefore(Player player, InteractionHand hand, CallbackInfo ci) {
        tradeofimmersion$gift = player.getItemInHand(hand).copy();
    }

    @Inject(method = "giveFrom", at = @At("RETURN"), remap = false)
    private void tradeofimmersion$giftAfter(Player player, InteractionHand hand, CallbackInfo ci) {
        ItemStack before = tradeofimmersion$gift;
        tradeofimmersion$gift = ItemStack.EMPTY;
        if (before.isEmpty()) return;
        ItemStack now = player.getItemInHand(hand);
        int moved = before.getCount() - (ItemStack.isSameItemSameComponents(before, now) ? now.getCount() : 0);
        LifeHooks.onGiftToCompanion((CompanionEntity) (Object) this, player, before, moved);
    }
}
