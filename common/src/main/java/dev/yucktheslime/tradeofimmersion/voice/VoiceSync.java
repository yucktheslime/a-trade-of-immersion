package dev.yucktheslime.tradeofimmersion.voice;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Server -> client: "this entity just said this line". The client decides whether to voice it (its own
 * {@code voiceEnabled} config) and fetches the audio itself from Kagrenac's speak.php, then plays it from the
 * entity's position. Only text travels over the game connection; no audio is streamed through the server.
 */
public final class VoiceSync {
    /** Players further than this never get the packet (the client applies its own, smaller voiceRange). */
    private static final double SEND_RANGE = 64;

    public record SpeakPayload(int entityId, String npcId, String text, String parentRequestId, String voiceHint)
            implements CustomPacketPayload {
        public static final Type<SpeakPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "npc_speak"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SpeakPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SpeakPayload::entityId,
                ByteBufCodecs.stringUtf8(64), SpeakPayload::npcId,
                ByteBufCodecs.stringUtf8(1000), SpeakPayload::text,
                ByteBufCodecs.stringUtf8(64), SpeakPayload::parentRequestId,
                ByteBufCodecs.stringUtf8(16), SpeakPayload::voiceHint,
                SpeakPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private VoiceSync() {
    }

    public static void init() {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), SpeakPayload.TYPE, SpeakPayload.CODEC,
                    (payload, ctx) -> ctx.queue(() -> dev.yucktheslime.tradeofimmersion.client.voice.VoiceClient.onSpeak(payload)));
        } else {
            NetworkManager.registerS2CPayloadType(SpeakPayload.TYPE, SpeakPayload.CODEC);
        }
    }

    /** Tells nearby players that {@code speaker} said {@code text}. Server thread. */
    public static void spoke(Entity speaker, String npcId, String text, String parentRequestId, String voiceHint) {
        if (text == null || text.isBlank() || !(speaker.level() instanceof ServerLevel level)) return;
        String line = text.replaceAll("\\s+", " ").trim();
        if (line.length() > 1000) line = line.substring(0, 1000);
        SpeakPayload p = new SpeakPayload(speaker.getId(), cut(npcId, 64), line, cut(parentRequestId, 64), cut(voiceHint, 16));
        List<ServerPlayer> near = level.players().stream().filter(pl -> pl.distanceToSqr(speaker) < SEND_RANGE * SEND_RANGE).toList();
        if (!near.isEmpty()) NetworkManager.sendToPlayers(near, p);
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) : s;
    }
}
