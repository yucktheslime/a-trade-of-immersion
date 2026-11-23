package dev.yucktheslime.tradeofimmersion.client.voice;

import net.minecraft.client.sounds.AudioStream;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A WAV file (PCM 8/16/24/32-bit or 32-bit float, any channel count) as a 16-bit mono stream, so OpenAL can
 * place it in 3D (only mono sources are positional).
 */
public final class WavAudioStream implements AudioStream {
    private final AudioFormat format;
    private final byte[] pcm; // 16-bit little-endian mono
    private int pos;

    private WavAudioStream(AudioFormat format, byte[] pcm) {
        this.format = format;
        this.pcm = pcm;
    }

    public double seconds() {
        return pcm.length / 2.0 / format.getSampleRate();
    }

    /** Parses the WAV bytes. Throws IllegalArgumentException for anything it cannot play. */
    public static WavAudioStream parse(byte[] wav) {
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (wav.length < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) throw new IllegalArgumentException("not RIFF/WAVE");
        int p = 12, fmtTag = -1, channels = 0, rate = 0, bits = 0, dataStart = -1, dataLen = 0;
        while (p + 8 <= wav.length) {
            int id = b.getInt(p), len = b.getInt(p + 4);
            int body = p + 8;
            if (id == 0x20746d66) { // "fmt "
                fmtTag = b.getShort(body) & 0xFFFF;
                channels = b.getShort(body + 2) & 0xFFFF;
                rate = b.getInt(body + 4);
                bits = b.getShort(body + 14) & 0xFFFF;
                if (fmtTag == 0xFFFE && len >= 40) fmtTag = b.getShort(body + 24) & 0xFFFF; // WAVE_FORMAT_EXTENSIBLE subformat
            } else if (id == 0x61746164) { // "data"
                dataStart = body;
                dataLen = len < 0 || body + (long) len > wav.length ? wav.length - body : len; // streamed WAVs may say 0xFFFFFFFF
                break;
            }
            if (len < 0) break;
            p = body + len + (len & 1);
        }
        if (dataStart < 0 || channels < 1 || channels > 8 || rate < 4000 || rate > 192000) throw new IllegalArgumentException("unsupported WAV header");
        boolean isFloat = fmtTag == 3;
        if (!(fmtTag == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32)) && !(isFloat && bits == 32)) {
            throw new IllegalArgumentException("unsupported WAV encoding " + fmtTag + "/" + bits);
        }
        int frameBytes = bits / 8 * channels;
        int frames = dataLen / frameBytes;
        byte[] out = new byte[frames * 2];
        for (int f = 0; f < frames; f++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) {
                int o = dataStart + f * frameBytes + c * (bits / 8);
                double v;
                if (isFloat) v = b.getFloat(o);
                else if (bits == 8) v = ((wav[o] & 0xFF) - 128) / 128.0;
                else if (bits == 16) v = b.getShort(o) / 32768.0;
                else if (bits == 24) v = (((wav[o] & 0xFF) | (wav[o + 1] & 0xFF) << 8 | wav[o + 2] << 16)) / 8388608.0;
                else v = b.getInt(o) / 2147483648.0;
                sum += v;
            }
            int s = (int) Math.round(Math.max(-1, Math.min(1, sum / channels)) * 32767);
            out[f * 2] = (byte) s;
            out[f * 2 + 1] = (byte) (s >> 8);
        }
        return new WavAudioStream(new AudioFormat(rate, 16, 1, true, false), out);
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        if (pos >= pcm.length) return null;
        int n = Math.min(Math.max(2, size & ~1), pcm.length - pos);
        ByteBuffer out = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder());
        // OpenAL expects host byte order for 16-bit samples; ours are little-endian
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            out.put(pcm, pos, n);
        } else {
            for (int i = 0; i < n; i += 2) {
                out.put(pcm[pos + i + 1]);
                out.put(pcm[pos + i]);
            }
        }
        pos += n;
        out.flip();
        return out;
    }

    @Override
    public void close() {
    }
}
