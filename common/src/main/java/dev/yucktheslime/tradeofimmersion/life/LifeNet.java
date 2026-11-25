package dev.yucktheslime.tradeofimmersion.life;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.life.pregnancy.Pregnancies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;

/** Server -> client: belly sizes of pregnant carriers (UUID -> 0..1), sent on join and when they change. */
public final class LifeNet {
    public record Bellies(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Bellies> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "life_bellies"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Bellies> CODEC = ByteBufCodecs.COMPOUND_TAG.map(Bellies::new, Bellies::data).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client side copy (read by the belly render layer). */
    public static volatile Map<UUID, Float> clientBellies = Map.of();
    private static CompoundTag last = new CompoundTag();

    private LifeNet() {
    }

    public static void init() {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), Bellies.TYPE, Bellies.CODEC, (payload, ctx) -> ctx.queue(() -> clientBellies = decode(payload.data())));
        } else {
            NetworkManager.registerS2CPayloadType(Bellies.TYPE, Bellies.CODEC);
        }
        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (Life.server() != null) NetworkManager.sendToPlayer(player, new Bellies(encode(Pregnancies.bellies(Life.server()))));
        });
    }

    static CompoundTag encode(Map<UUID, Float> m) {
        CompoundTag t = new CompoundTag();
        m.forEach((k, v) -> t.putFloat(k.toString(), v));
        return t;
    }

    static Map<UUID, Float> decode(CompoundTag t) {
        java.util.HashMap<UUID, Float> m = new java.util.HashMap<>();
        for (String k : t.getAllKeys()) {
            try {
                m.put(UUID.fromString(k), t.getFloat(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Map.copyOf(m);
    }

    public static void sync(MinecraftServer server, boolean force) {
        CompoundTag now = encode(Pregnancies.bellies(server));
        if (!force && now.equals(last)) return;
        last = now;
        NetworkManager.sendToPlayers(server.getPlayerList().getPlayers(), new Bellies(now));
    }

    public static void reset() {
        last = new CompoundTag();
    }
}
