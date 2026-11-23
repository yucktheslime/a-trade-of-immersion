package dev.yucktheslime.tradeofimmersion.item;

import dev.architectury.platform.Platform;
import dev.yucktheslime.tradeofimmersion.memory.ItemKnowledgeStore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.stream.Collectors;

/** Builds a plain-text description of any item (vanilla or modded) for the AI and caches it. */
public final class ItemInspector {
    private ItemInspector() {
    }

    public static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static String modName(String namespace) {
        if ("minecraft".equals(namespace)) return "Minecraft";
        try {
            return Platform.getMod(namespace).getName();
        } catch (Throwable t) {
            return namespace;
        }
    }

    /** Inspects the stack, stores the facts in {@link ItemKnowledgeStore} and returns them. */
    public static ItemKnowledgeStore.Knowledge inspect(ItemStack stack, Level level) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ItemKnowledgeStore.Knowledge k = new ItemKnowledgeStore.Knowledge();
        k.name = stack.getHoverName().getString();
        k.mod = modName(key.getNamespace());
        StringBuilder f = new StringBuilder();
        f.append(k.name).append(" (").append(key).append(", from ").append(k.mod).append(")");
        try {
            List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL);
            String tip = lines.stream().skip(1).map(Component::getString).filter(s -> !s.isBlank()).limit(8).collect(Collectors.joining("; "));
            if (!tip.isBlank()) f.append(". Tooltip: ").append(tip);
        } catch (Throwable ignored) {
            // some mods' tooltips are client-only
        }
        var food = stack.get(DataComponents.FOOD);
        if (food != null) f.append(". Food: ").append(food.nutrition()).append(" nutrition");
        if (stack.isDamageableItem()) f.append(". Durability ").append(stack.getMaxDamage() - stack.getDamageValue()).append("/").append(stack.getMaxDamage());
        if (stack.getItem() instanceof ArmorItem armor) f.append(". Armor for ").append(armor.getEquipmentSlot().getName()).append(", defense ").append(armor.getDefense());
        var mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null && !mods.modifiers().isEmpty()) {
            f.append(". Attributes: ").append(mods.modifiers().stream().limit(4)
                    .map(e -> e.attribute().value().getDescriptionId().replace("attribute.name.", "") + " " + e.modifier().amount())
                    .collect(Collectors.joining(", ")));
        }
        if (stack.getUseAnimation() != net.minecraft.world.item.UseAnim.NONE) f.append(". Use animation: ").append(stack.getUseAnimation().name().toLowerCase());
        String tags = stack.getTags().limit(6).map(t -> t.location().toString()).collect(Collectors.joining(", "));
        if (!tags.isBlank()) f.append(". Tags: ").append(tags);
        f.append(". Stack size ").append(stack.getMaxStackSize());
        k.facts = f.toString();
        ItemKnowledgeStore.get().put(key.toString(), k);
        return k;
    }

    /** Short line for prompts: known notes if any, else name + mod. */
    public static String brief(ItemStack stack) {
        String id = id(stack);
        ItemKnowledgeStore.Knowledge k = ItemKnowledgeStore.get().get(id);
        String base = stack.getCount() + "x " + stack.getHoverName().getString() + " [" + id + "]";
        if (k != null && k.notes != null && !k.notes.isBlank()) return base + " - " + k.notes;
        return base;
    }

    public static boolean isEquipSlot(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }
}
