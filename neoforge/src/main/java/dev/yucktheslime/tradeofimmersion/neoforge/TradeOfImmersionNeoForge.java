package dev.yucktheslime.tradeofimmersion.neoforge;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.platform.FakePlayers;
import dev.yucktheslime.tradeofimmersion.skills.item.PayloadBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** NeoForge entry point; all logic lives in the common module (events via Architectury API). */
@Mod(TradeOfImmersion.MOD_ID)
public final class TradeOfImmersionNeoForge {
    public TradeOfImmersionNeoForge() {
        FakePlayers.setProvider(FakePlayerFactory::get);
        PayloadBridge.Holder.set(new NeoForgePayloadBridge());
        TradeOfImmersion.init();
        addLegacyAliases();
        if (FMLEnvironment.dist == Dist.CLIENT) NeoForgeClient.init();
    }

    /** Companions and spawn eggs saved by 0.2.0 (old mod id) load as the new ids. */
    private static void addLegacyAliases() {
        String old = dev.yucktheslime.tradeofimmersion.config.Migration.LEGACY_ID;
        BuiltInRegistries.ENTITY_TYPE.addAlias(ResourceLocation.fromNamespaceAndPath(old, "companion"),
                ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "companion"));
        BuiltInRegistries.ITEM.addAlias(ResourceLocation.fromNamespaceAndPath(old, "companion_spawn_egg"),
                ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "companion_spawn_egg"));
    }
}
