package dev.yucktheslime.mcaplayer2.client;

import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.yucktheslime.mcaplayer2.registry.ModRegistry;

/** Client setup shared by both loaders. */
public final class McaPlayer2Client {
    private McaPlayer2Client() {
    }

    public static void init() {
        EntityRendererRegistry.register(ModRegistry.COMPANION, CompanionRenderer::new);
        dev.architectury.registry.client.rendering.ColorHandlerRegistry.registerItemColors(
                (stack, tint) -> tint == 0 ? 0xFF3A6EA5 : 0xFFE8C07D, ModRegistry.COMPANION_SPAWN_EGG);
    }
}
