package dev.yucktheslime.tradeofimmersion.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.level.entity.EntityAttributeRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.entity.CompanionEntity;
import dev.yucktheslime.tradeofimmersion.item.CompanionSpawnEggItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;

public final class ModRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(TradeOfImmersion.MOD_ID, Registries.ENTITY_TYPE);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(TradeOfImmersion.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<EntityType<CompanionEntity>> COMPANION = ENTITIES.register("companion",
            () -> EntityType.Builder.<CompanionEntity>of(CompanionEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("companion"));

    public static final RegistrySupplier<Item> COMPANION_SPAWN_EGG = ITEMS.register("companion_spawn_egg",
            () -> new CompanionSpawnEggItem(new Item.Properties().stacksTo(16)));

    private ModRegistry() {
    }

    public static void init() {
        ENTITIES.register();
        ITEMS.register();
        EntityAttributeRegistry.register(COMPANION, CompanionEntity::createAttributes);
        CreativeTabRegistry.append(net.minecraft.resources.ResourceKey.create(Registries.CREATIVE_MODE_TAB, net.minecraft.resources.ResourceLocation.withDefaultNamespace("spawn_eggs")), COMPANION_SPAWN_EGG);
    }
}
