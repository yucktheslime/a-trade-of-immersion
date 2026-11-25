package dev.yucktheslime.tradeofimmersion.fabric;

import dev.yucktheslime.tradeofimmersion.skills.item.PayloadBridge;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Fabric {@link PayloadBridge}: encode/decode with the play C2S payload registry, and replay by calling the
 * registered {@link ServerPlayNetworking.PlayPayloadHandler} with the companion's fake player as
 * {@code context.player()}. Mods that need a real connection may still refuse the packet; those items get marked
 * unusable.
 */
public final class FabricPayloadBridge implements PayloadBridge {
    private static final PacketSender NOOP = new PacketSender() {
        @Override
        public Packet<?> createPacket(CustomPacketPayload payload) {
            return null;
        }

        @Override
        public void sendPacket(Packet<?> packet, @Nullable PacketSendListener callback) {
        }

        @Override
        public void disconnect(Component disconnectReason) {
        }
    };

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Nullable
    public byte[] encode(CustomPacketPayload payload, RegistryAccess access) {
        if (payload == null) return null;
        CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, ?> tac = registry().get(payload.type().id());
        if (tac == null) return null;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            ((StreamCodec) tac.codec()).encode(buf, payload);
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
        CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, ?> tac = registry().get(channel);
        if (tac == null) return null;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), access);
        try {
            return (CustomPacketPayload) tac.codec().decode(buf);
        } catch (Throwable t) {
            return null;
        } finally {
            buf.release();
        }
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean replay(ServerPlayer sender, CustomPacketPayload payload) {
        if (sender == null || payload == null) return false;
        ServerPlayNetworking.PlayPayloadHandler handler = ServerNetworkingImpl.PLAY.getHandler(payload.type().id());
        if (handler == null) return false;
        handler.receive(payload, new ServerPlayNetworking.Context() {
            @Override
            public MinecraftServer server() {
                return sender.getServer();
            }

            @Override
            public ServerPlayer player() {
                return sender;
            }

            @Override
            public PacketSender responseSender() {
                return NOOP;
            }
        });
        return true;
    }

    @SuppressWarnings("unchecked")
    private static PayloadTypeRegistryImpl<RegistryFriendlyByteBuf> registry() {
        return (PayloadTypeRegistryImpl<RegistryFriendlyByteBuf>) PayloadTypeRegistry.playC2S();
    }
}
