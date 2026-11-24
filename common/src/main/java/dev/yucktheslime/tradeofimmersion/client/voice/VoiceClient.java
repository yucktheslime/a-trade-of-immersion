package dev.yucktheslime.tradeofimmersion.client.voice;

import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.voice.VoiceSync;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client side of NPC voice: collects the WAV chunks the server streams ({@link VoiceSync.VoiceChunkPayload}) and
 * plays the clip from the NPC's position. The client never talks to Kagrenac itself. {@code voicePlay} false mutes it.
 */
public final class VoiceClient {
    private static final Map<Integer, NpcVoiceInstance> PLAYING = new HashMap<>();
    private static final Map<Integer, Clip> CLIPS = new HashMap<>();
    private static volatile String lastStatus = "no line voiced yet";

    private static final class Clip {
        final byte[][] parts;
        final long started = System.currentTimeMillis();
        int have;

        Clip(int total) {
            parts = new byte[total][];
        }
    }

    private VoiceClient() {
    }

    public static String status() {
        return lastStatus;
    }

    public static void onChunk(VoiceSync.VoiceChunkPayload p) {
        ToiConfig cfg = ToiConfig.get();
        if (!cfg.voicePlay || p.villager() && !cfg.voiceVillagers) return;
        if (p.total() < 1 || p.total() > 200 || p.index() < 0 || p.index() >= p.total()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Clip> it = CLIPS.values().iterator(); it.hasNext(); ) if (now - it.next().started > 30_000) it.remove();
        Clip c = CLIPS.computeIfAbsent(p.clipId(), k -> new Clip(p.total()));
        if (c.parts.length != p.total() || c.parts[p.index()] != null) return;
        c.parts[p.index()] = p.data();
        if (++c.have < p.total()) return;
        CLIPS.remove(p.clipId());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] b : c.parts) out.writeBytes(b);
        play(p.entityId(), out.toByteArray(), p.pitch());
    }

    private static void play(int entityId, byte[] wav, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity e = mc.level.getEntity(entityId);
        ToiConfig cfg = ToiConfig.get();
        if (e == null || e.distanceTo(mc.player) > cfg.voiceRange + 8) return;
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
        NpcVoiceInstance inst = new NpcVoiceInstance(e, stream, cfg.voiceVolume, cfg.voiceRange, pitch);
        PLAYING.put(entityId, inst);
        PLAYING.values().removeIf(i -> i.isStopped() || i.speaker().isRemoved());
        mc.getSoundManager().play(inst);
        lastStatus = String.format("voiced %s: %.1fs of audio (%d bytes)", e.getName().getString(), stream.seconds(), wav.length);
        TradeOfImmersion.LOGGER.info("NPC voice: {}", lastStatus);
    }
}
