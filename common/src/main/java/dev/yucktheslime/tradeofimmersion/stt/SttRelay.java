package dev.yucktheslime.tradeofimmersion.stt;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend;
import dev.yucktheslime.tradeofimmersion.ai.KagrenacWorld;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.util.QuietLog;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of push-to-talk: the client sends its 16 kHz mono WAV in {@link Chunk}s (custom payloads from the
 * client are limited to 32 KiB each), the server puts them together and asks Kagrenac's listen.php (its STT
 * settings: Parakeet in DwemerDistro, faster-whisper, whisper.cpp or an OpenAI-compatible service) or, when
 * {@code sttUrl} is set, that OpenAI-compatible URL. The transcript goes back in {@link Result}; the client then
 * sends it to the NPC like a typed line. One recording per player at a time.
 */
public final class SttRelay {
    public static final int CHUNK = 30_000;
    /** 30 s of 16 kHz 16-bit mono is 960 KB; leave room for the header. */
    public static final int MAX_BYTES = 1_000_000;
    private static final Map<UUID, ChunkAssembler> UPLOADS = new ConcurrentHashMap<>();
    private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();
    private static volatile String lastStatus = "no speech yet";

    public record Chunk(int clipId, int index, int total, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "stt_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Chunk> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.clipId);
            b.writeVarInt(p.index);
            b.writeVarInt(p.total);
            b.writeByteArray(p.data);
        }, b -> new Chunk(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readByteArray(CHUNK)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Result(int clipId, boolean ok, String text) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TradeOfImmersion.MOD_ID, "stt_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Result> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.clipId);
            b.writeBoolean(p.ok);
            b.writeUtf(p.text, 1200);
        }, b -> new Result(b.readVarInt(), b.readBoolean(), b.readUtf(1200)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private SttRelay() {
    }

    public static void init() {
        NetworkManager.registerReceiver(NetworkManager.c2s(), Chunk.TYPE, Chunk.CODEC,
                (p, ctx) -> ctx.queue(() -> { if (ctx.getPlayer() instanceof ServerPlayer sp) onChunk(sp, p); }));
        if (Platform.getEnvironment() == Env.CLIENT) {
            NetworkManager.registerReceiver(NetworkManager.s2c(), Result.TYPE, Result.CODEC,
                    (p, ctx) -> ctx.queue(() -> dev.yucktheslime.tradeofimmersion.client.stt.SttClient.onResult(p)));
        } else {
            NetworkManager.registerS2CPayloadType(Result.TYPE, Result.CODEC);
        }
    }

    public static String status() {
        return lastStatus + "; service: " + describeService();
    }

    public static void forget(UUID player) {
        UPLOADS.remove(player);
    }

    /** Where the server sends speech: sttUrl, else Kagrenac's listen.php, else nothing. */
    public static String describeService() {
        ToiConfig cfg = ToiConfig.get();
        if (!cfg.sttEnabled) return "off (sttEnabled false)";
        if (cfg.sttUrl != null && !cfg.sttUrl.isBlank()) return "sttUrl " + cfg.sttUrl.trim();
        KagrenacBackend k = KagrenacWorld.kagrenac();
        if (k != null) return "Kagrenac listen.php" + (k.has("listen") ? "" : " (server did not report it yet)");
        return "none (use aiBackend kagrenac with speech to text on in its dashboard, or set sttUrl)";
    }

    private static void reply(ServerPlayer p, int clip, boolean ok, String text) {
        NetworkManager.sendToPlayer(p, new Result(clip, ok, text == null ? "" : text.length() > 1200 ? text.substring(0, 1200) : text));
    }

    private static void onChunk(ServerPlayer p, Chunk c) {
        ToiConfig cfg = ToiConfig.get();
        UUID id = p.getUUID();
        if (!cfg.sttEnabled) {
            if (c.index() == 0) reply(p, c.clipId(), false, "Speech input is turned off on this server.");
            return;
        }
        ChunkAssembler a = UPLOADS.get(id);
        long now = System.currentTimeMillis();
        if (a == null || a.id() != c.clipId() || now - a.started() > 60_000) {
            if (BUSY.contains(id)) {
                if (c.index() == 0) reply(p, c.clipId(), false, "Still transcribing your last line.");
                return;
            }
            try {
                a = new ChunkAssembler(c.clipId(), c.total(), MAX_BYTES, now);
            } catch (IllegalArgumentException e) {
                reply(p, c.clipId(), false, "Recording rejected: " + e.getMessage());
                return;
            }
            UPLOADS.put(id, a);
        }
        byte[] wav;
        try {
            wav = a.add(c.index(), c.total(), c.data());
        } catch (IllegalArgumentException e) {
            UPLOADS.remove(id);
            reply(p, c.clipId(), false, "Recording rejected: " + e.getMessage());
            return;
        }
        if (wav == null) return;
        UPLOADS.remove(id);
        if (!WavUtil.isWav(wav)) {
            reply(p, c.clipId(), false, "Recording rejected: not a WAV.");
            return;
        }
        CompletableFuture<String> f = transcribe(wav);
        if (f == null) {
            reply(p, c.clipId(), false, "No speech-to-text service on this server. " + describeService());
            return;
        }
        BUSY.add(id);
        MinecraftServer srv = p.server;
        long started = now;
        int clip = c.clipId();
        f.whenComplete((text, err) -> srv.execute(() -> {
            BUSY.remove(id);
            ServerPlayer pl = srv.getPlayerList().getPlayer(id);
            if (pl == null) return;
            if (err != null) {
                Throwable t = err.getCause() != null ? err.getCause() : err;
                String m = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                if (t instanceof java.net.ConnectException || t instanceof java.net.http.HttpConnectTimeoutException) m = "the speech service is not reachable";
                lastStatus = "failed: " + m;
                QuietLog.warn("stt", "Speech to text failed: {}", t.toString());
                reply(pl, clip, false, "Speech to text failed: " + m + ". Type instead.");
                return;
            }
            lastStatus = "heard " + text.length() + " characters from " + pl.getGameProfile().getName() + " in " + (System.currentTimeMillis() - started) + " ms";
            reply(pl, clip, true, text);
        }));
    }

    /** Starts the transcription with the configured service, or null when there is none. */
    static CompletableFuture<String> transcribe(byte[] wav) {
        ToiConfig cfg = ToiConfig.get();
        if (cfg.sttUrl != null && !cfg.sttUrl.isBlank()) {
            String key = System.getenv("TOI_STT_API_KEY");
            return SttHttp.transcribe(cfg.sttUrl, cfg.sttModel, key, wav, 60);
        }
        KagrenacBackend k = KagrenacWorld.kagrenac();
        return k != null ? k.listen(wav) : null;
    }
}
