package dev.yucktheslime.tradeofimmersion.client.voice;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.ai.KagrenacBackend;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.voice.VoiceSync;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client side of NPC voice: on a {@link VoiceSync.SpeakPayload} it asks Kagrenac's speak.php for the WAV (with
 * the NPC id for its profile voice and a gender voice hint) and plays it from the NPC. Off unless
 * {@code voiceEnabled} is true in this client's config. Text always shows first; any voice error just logs.
 */
public final class VoiceClient {
    private static final Map<Integer, NpcVoiceInstance> PLAYING = new HashMap<>();
    private static final AtomicInteger PENDING = new AtomicInteger();
    private static KagrenacBackend backend;
    private static String backendUrl = "";
    private static volatile String lastStatus = "no line voiced yet";

    private VoiceClient() {
    }

    public static String status() {
        return lastStatus;
    }

    private static synchronized KagrenacBackend backend() {
        String url = ToiConfig.get().kagrenacBaseUrl;
        if (backend == null || !url.equals(backendUrl)) {
            backend = new KagrenacBackend();
            backendUrl = url;
        }
        return backend;
    }

    public static void onSpeak(VoiceSync.SpeakPayload p) {
        ToiConfig cfg = ToiConfig.get();
        if (!cfg.voiceEnabled || p.text().isBlank()) return;
        if (p.npcId().startsWith("v-") && !cfg.voiceVillagers) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        if (e == null || e.distanceTo(mc.player) > cfg.voiceRange) return;
        if (PENDING.get() >= 4) {
            lastStatus = "skipped a line (4 voice requests already waiting)";
            return;
        }
        PENDING.incrementAndGet();
        long started = System.currentTimeMillis();
        backend().speak(p.text(), p.npcId(), p.parentRequestId(), p.voiceHint()).whenComplete((wav, err) -> {
            PENDING.decrementAndGet();
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                lastStatus = "voice failed: " + c.getMessage();
                TradeOfImmersion.LOGGER.warn("NPC voice failed: {}", c.toString());
                return;
            }
            mc.execute(() -> play(p.entityId(), wav, System.currentTimeMillis() - started));
        });
    }

    private static void play(int entityId, byte[] wav, long fetchMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return;
        WavAudioStream stream;
        try {
            stream = WavAudioStream.parse(wav);
        } catch (IllegalArgumentException ex) {
            lastStatus = "voice not playable: " + ex.getMessage();
            TradeOfImmersion.LOGGER.warn("NPC voice not playable: {}", ex.getMessage());
            return;
        }
        NpcVoiceInstance old = PLAYING.remove(entityId);
        if (old != null) mc.getSoundManager().stop(old); // a newer line interrupts the old one
        NpcVoiceInstance inst = new NpcVoiceInstance(e, stream, ToiConfig.get().voiceVolume, ToiConfig.get().voiceRange);
        PLAYING.put(entityId, inst);
        PLAYING.values().removeIf(i -> i.isStopped() || i.speaker().isRemoved());
        mc.getSoundManager().play(inst);
        lastStatus = String.format("voiced %s: %.1fs of audio (%d bytes, fetched in %d ms)", e.getName().getString(), stream.seconds(), wav.length, fetchMs);
        TradeOfImmersion.LOGGER.info("NPC voice: {}", lastStatus);
    }
}
