package dev.yucktheslime.tradeofimmersion.life.mixin;

import dev.yucktheslime.tradeofimmersion.life.Life;
import dev.yucktheslime.tradeofimmersion.life.hooks.LifeHooks;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BreedableRelationship;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Relationship;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player gives an MCA villager a gift: remembered (with whether it was liked) and can make a partner jealous. */
@Mixin(value = BreedableRelationship.class, remap = false)
public abstract class GiftMixin {
    @Unique
    private static int tradeofimmersion$hearts;
    @Unique
    private static String tradeofimmersion$item = "";

    @Inject(method = "giveGift", at = @At("HEAD"), remap = false)
    private void tradeofimmersion$before(ServerPlayer player, Memories memory, CallbackInfo ci) {
        try {
            ItemStack s = player.getMainHandItem();
            tradeofimmersion$item = s.isEmpty() ? "" : (s.getCount() > 1 ? s.getCount() + "x " : "a ") + s.getHoverName().getString();
            tradeofimmersion$hearts = memory.getHearts();
        } catch (Throwable t) {
            tradeofimmersion$item = "";
        }
    }

    @Inject(method = "giveGift", at = @At("RETURN"), remap = false)
    private void tradeofimmersion$after(ServerPlayer player, Memories memory, CallbackInfo ci) {
        try {
            if (tradeofimmersion$item.isEmpty()) return;
            Relationship<?> self = (Relationship<?>) (Object) this;
            if (self.getWorld().getEntity(self.getUUID()) instanceof VillagerEntityMCA v) {
                LifeHooks.onVillagerGift(player, v, tradeofimmersion$item, tradeofimmersion$hearts, memory.getHearts());
            }
        } catch (Throwable t) {
            Life.warnOnce("gift-mixin", t);
        } finally {
            tradeofimmersion$item = "";
        }
    }
}
