package dev.yucktheslime.tradeofimmersion.skills.item;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/**
 * Loader glue for key-packet replay: encode/decode a serverbound mod payload with its registered codec and hand a
 * payload to its registered server handler as if {@code sender} had sent it. NeoForge uses its payload registry,
 * Fabric its global play receivers. Set by the loader entry points.
 */
public interface PayloadBridge {
    /** @return null when the channel has no serverbound codec */
    @Nullable
    byte[] encode(CustomPacketPayload payload, RegistryAccess access);

    @Nullable
    CustomPacketPayload decode(ResourceLocation channel, byte[] data, RegistryAccess access);

    /** Runs the channel's server handler with {@code sender} as the player. @return false when there is no handler */
    boolean replay(ServerPlayer sender, CustomPacketPayload payload) throws Exception;

    final class Holder {
        private static PayloadBridge bridge;

        private Holder() {
        }

        public static void set(PayloadBridge b) {
            bridge = b;
        }

        @Nullable
        public static PayloadBridge get() {
            return bridge;
        }
    }
}
