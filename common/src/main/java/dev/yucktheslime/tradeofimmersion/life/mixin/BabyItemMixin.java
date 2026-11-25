package dev.yucktheslime.tradeofimmersion.life.mixin;

import dev.yucktheslime.tradeofimmersion.life.Life;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import net.conczin.mca.item.BabyItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MCA hands out the baby item right after "procreate" (villager + player, and {@code /mca procreate} for two
 * players). With pregnancyPhases on, that becomes the start of a pregnancy and the item comes at birth. MCA's own
 * method name, so remap=false works on both loaders.
 */
@Mixin(value = BabyItem.class, remap = false)
public abstract class BabyItemMixin {
    @Inject(method = "createItem", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tradeofimmersion$pregnancyInstead(Entity mother, Entity father, long seed, CallbackInfoReturnable<ItemStack> cir) {
        try {
            if (Pregnancies.interceptBaby(mother, father, seed)) cir.setReturnValue(ItemStack.EMPTY);
        } catch (Throwable t) {
            Life.warnOnce("baby-item", t); // fall back to MCA's instant baby
        }
    }
}
