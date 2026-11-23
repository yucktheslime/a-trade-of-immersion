package dev.yucktheslime.tradeofimmersion.entity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 45-slot view of a companion for a 9x5 chest screen: rows 1-4 = 36 inventory slots, row 5 = 3 extra slots,
 * head, chest, legs, feet, main hand, offhand.
 */
public class CompanionContainer implements Container {
    private static final EquipmentSlot[] EQUIPMENT = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private final CompanionEntity npc;

    public CompanionContainer(CompanionEntity npc) {
        this.npc = npc;
    }

    @Override
    public int getContainerSize() {
        return 45;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < CompanionEntity.INVENTORY_SIZE) return npc.inventory.getItem(slot);
        return npc.getItemBySlot(EQUIPMENT[slot - CompanionEntity.INVENTORY_SIZE]);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack s = getItem(slot);
        if (s.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack out = s.split(amount);
        setItem(slot, s.isEmpty() ? ItemStack.EMPTY : s);
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack s = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return s;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < CompanionEntity.INVENTORY_SIZE) npc.inventory.setItem(slot, stack);
        else npc.setItemSlot(EQUIPMENT[slot - CompanionEntity.INVENTORY_SIZE], stack);
    }

    @Override
    public void setChanged() {
        npc.inventory.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return npc.isAlive() && player.distanceToSqr(npc) < 64;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < getContainerSize(); i++) setItem(i, ItemStack.EMPTY);
    }
}
