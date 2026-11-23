package dev.yucktheslime.tradeofimmersion.client;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.yucktheslime.tradeofimmersion.registry.ModRegistry;

/** Client setup shared by both loaders. */
public final class TradeOfImmersionClient {
    private TradeOfImmersionClient() {
    }

    public static void init() {
        EntityRendererRegistry.register(ModRegistry.COMPANION, CompanionRenderer::new);
        dev.architectury.registry.client.rendering.ColorHandlerRegistry.registerItemColors(
                (stack, tint) -> tint == 0 ? 0xFF3A6EA5 : 0xFFE8C07D, ModRegistry.COMPANION_SPAWN_EGG);
    }
}
