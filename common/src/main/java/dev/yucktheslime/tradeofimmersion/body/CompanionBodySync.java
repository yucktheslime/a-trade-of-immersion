package dev.yucktheslime.tradeofimmersion.body;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Server -> client sync of {@link CompanionBodyStore} (full snapshot on join and after every change). */
public final class CompanionBodySync {
    public record Payload(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Payload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "companion_bodies"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC =
                ByteBufCodecs.COMPOUND_TAG.map(Payload::new, Payload::data).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CompanionBodySync() {
    }

    public static void init() {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), Payload.TYPE, Payload.CODEC,
                    (payload, ctx) -> ctx.queue(() -> ClientCompanionBodies.accept(payload.data())));
        } else {
            NetworkManager.registerS2CPayloadType(Payload.TYPE, Payload.CODEC);
        }
        PlayerEvent.PLAYER_JOIN.register(player -> NetworkManager.sendToPlayer(player, new Payload(CompanionBodyStore.get().snapshot())));
    }

    public static void broadcast(MinecraftServer server) {
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), new Payload(CompanionBodyStore.get().snapshot()));
    }
}
