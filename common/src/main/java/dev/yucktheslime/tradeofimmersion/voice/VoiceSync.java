package dev.yucktheslime.tradeofimmersion.voice;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend;
import dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.util.QuietLog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NPC voice, fetched by the Minecraft server: when an NPC says a line and {@code voiceEnabled} is on, the server
 * asks Kagrenac's speak.php for the WAV (through kagrenacBaseUrl with the server's own token, so it also works for
 * a hosted server talking to Kagrenac through a tunnel) and streams it in chunks to the players near the NPC. The
 * client plays it from the NPC's position unless its own {@code voicePlay} is off. Players need no URL or token.
 */
public final class VoiceSync {
    private static final int CHUNK = 30_000;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int MAX_PENDING = 4;
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static final AtomicInteger CLIP_IDS = new AtomicInteger();
    private static volatile String lastStatus = "no line voiced yet";

    /** One piece of a WAV clip for the entity; the client plays the clip when all {@code total} pieces arrived. */
    public record VoiceChunkPayload(int entityId, int clipId, int index, int total, boolean villager, byte[] data)
            implements CustomPacketPayload {
        public static final Type<VoiceChunkPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "npc_voice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, VoiceChunkPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, VoiceChunkPayload::entityId,
                ByteBufCodecs.VAR_INT, VoiceChunkPayload::clipId,
                ByteBufCodecs.VAR_INT, VoiceChunkPayload::index,
                ByteBufCodecs.VAR_INT, VoiceChunkPayload::total,
                ByteBufCodecs.BOOL, VoiceChunkPayload::villager,
                ByteBufCodecs.byteArray(CHUNK), VoiceChunkPayload::data,
                VoiceChunkPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private VoiceSync() {
    }

    public static void init() {
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), VoiceChunkPayload.TYPE, VoiceChunkPayload.CODEC,
                    (payload, ctx) -> ctx.queue(() -> dev.yucktheslime.tradeofimmersion.client.voice.VoiceClient.onChunk(payload)));
        } else {
            NetworkManager.registerS2CPayloadType(VoiceChunkPayload.TYPE, VoiceChunkPayload.CODEC);
        }
    }

    public static String status() {
        return lastStatus;
    }

    /** {@code speaker} said {@code text}: voice it for nearby players if voice is on. Server thread; never blocks. */
    public static void spoke(Entity speaker, String npcId, String text, String parentRequestId, String voiceHint) {
        ToiConfig cfg = ToiConfig.get();
        if (!cfg.voiceEnabled || text == null || text.isBlank() || !(speaker.level() instanceof ServerLevel level)) return;
        boolean villager = npcId != null && npcId.startsWith("v-");
        if (villager && !cfg.voiceVillagers) return;
        KagrenacBackend k = KagrenacWorld.kagrenac();
        if (k == null || !k.has("speak")) return;
        if (nearby(level, speaker).isEmpty()) return;
        if (PENDING.get() >= MAX_PENDING) {
            lastStatus = "skipped a line (" + MAX_PENDING + " voice requests already waiting)";
            return;
        }
        String line = text.replaceAll("\\s+", " ").trim();
        if (line.length() > 1000) line = line.substring(0, 1000);
        PENDING.incrementAndGet();
        MinecraftServer srv = level.getServer();
        int entityId = speaker.getId();
        long started = System.currentTimeMillis();
        k.speak(line, npcId, parentRequestId, voiceHint).whenComplete((wav, err) -> {
            PENDING.decrementAndGet();
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                lastStatus = "voice failed: " + c.getMessage();
                QuietLog.warn("npc-voice", "NPC voice failed: {}", c.toString());
                return;
            }
            srv.execute(() -> send(level, entityId, villager, wav, System.currentTimeMillis() - started));
        });
    }

    private static List<ServerPlayer> nearby(ServerLevel level, Entity speaker) {
        double r = ToiConfig.get().voiceRange + 8;
        return level.players().stream().filter(pl -> pl.distanceToSqr(speaker) < r * r).toList();
    }

    private static void send(ServerLevel level, int entityId, boolean villager, byte[] wav, long fetchMs) {
        Entity e = level.getEntity(entityId);
        if (e == null || wav == null || wav.length > MAX_BYTES) return;
        List<ServerPlayer> near = nearby(level, e);
        if (near.isEmpty()) return;
        int clip = CLIP_IDS.incrementAndGet();
        int total = (wav.length + CHUNK - 1) / CHUNK;
        for (int i = 0; i < total; i++) {
            byte[] part = Arrays.copyOfRange(wav, i * CHUNK, Math.min(wav.length, (i + 1) * CHUNK));
            NetworkManager.sendToPlayers(near, new VoiceChunkPayload(entityId, clip, i, total, villager, part));
        }
        lastStatus = "voiced " + e.getName().getString() + ": " + wav.length + " bytes to " + near.size() + " player(s), fetched in " + fetchMs + " ms";
        TradeOfImmersion.LOGGER.debug("NPC voice: {}", lastStatus);
    }
}
