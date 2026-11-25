package dev.yucktheslime.tradeofimmersion.neoforge;

import dev.yucktheslime.tradeofimmersion.skills.item.PayloadBridge;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge {@link PayloadBridge}: encode/decode with the payload registry's serverbound codec, and replay by
 * handing the packet to {@link NetworkRegistry#handleModdedPayload} as if {@code sender} (the companion's fake
 * player) had sent it. The fake player's dummy connection is marked compatible first so the registry accepts it.
 */
public final class NeoForgePayloadBridge implements PayloadBridge {
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nullable
    public byte[] encode(CustomPacketPayload payload, RegistryAccess access) {
        if (payload == null) return null;
        StreamCodec codec = NetworkRegistry.getCodec(payload.type().id(), ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND);
        if (codec == null) return null;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            codec.encode(buf, payload);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return data;
        } catch (Throwable t) {
            return null;
        } finally {
            buf.release();
        }
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nullable
    public CustomPacketPayload decode(ResourceLocation channel, byte[] data, RegistryAccess access) {
        if (channel == null || data == null) return null;
        StreamCodec codec = NetworkRegistry.getCodec(channel, ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND);
        if (codec == null) return null;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), access);
        try {
            return (CustomPacketPayload) codec.decode(buf);
        } catch (Throwable t) {
            return null;
        } finally {
            buf.release();
        }
    }

    @Override
    public boolean replay(ServerPlayer sender, CustomPacketPayload payload) {
        if (sender == null || sender.connection == null || payload == null) return false;
        try {
            NetworkRegistry.configureMockConnection(sender.connection.getConnection());
        } catch (Throwable ignored) {
            // dummy connections sometimes already look configured
        }
        NetworkRegistry.handleModdedPayload(sender.connection, new ServerboundCustomPayloadPacket(payload));
        return true;
    }
}
