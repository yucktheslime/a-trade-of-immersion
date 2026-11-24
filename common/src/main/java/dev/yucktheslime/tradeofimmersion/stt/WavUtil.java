package dev.yucktheslime.tradeofimmersion.stt;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Small WAV helpers for speech input: wrap raw PCM in a RIFF header, and check that bytes are a WAV. */
public final class WavUtil {
    public static final int SAMPLE_RATE = 16_000;

    private WavUtil() {
    }

    /** A 44-byte-header PCM WAV of {@code pcm} (signed little-endian samples). */
    public static byte[] pcmToWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + pcm.length).put(new byte[]{'W', 'A', 'V', 'E'});
        h.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(sampleRate).putInt(byteRate).putShort((short) (channels * bitsPerSample / 8)).putShort((short) bitsPerSample);
        h.put(new byte[]{'d', 'a', 't', 'a'}).putInt(pcm.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        out.writeBytes(h.array());
        out.writeBytes(pcm);
        return out.toByteArray();
    }

    public static boolean isWav(byte[] b) {
        return b != null && b.length >= 44 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'A' && b[10] == 'V' && b[11] == 'E';
    }

    /** Peak level 0..1 of 16-bit little-endian PCM (for the "Listening" meter and to skip silent recordings). */
    public static float peak(byte[] pcm, int from, int len) {
        int max = 0;
        for (int i = from; i + 1 < from + len && i + 1 < pcm.length; i += 2) {
            int v = Math.abs((short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8)));
            if (v > max) max = v;
        }
        return Math.min(1f, max / 32767f);
    }
}
