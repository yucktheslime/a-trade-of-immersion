package dev.yucktheslime.tradeofimmersion.stt;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class WavUtilTest {
    @Test
    void headerDescribes16kMonoPcm() {
        byte[] pcm = new byte[3200];
        byte[] wav = WavUtil.pcmToWav(pcm, 16000, 1, 16);
        assertEquals(44 + pcm.length, wav.length);
        assertTrue(WavUtil.isWav(wav));
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(36 + pcm.length, b.getInt(4));
        assertEquals(1, b.getShort(20)); // PCM
        assertEquals(1, b.getShort(22)); // mono
        assertEquals(16000, b.getInt(24));
        assertEquals(32000, b.getInt(28));
        assertEquals(16, b.getShort(34));
        assertEquals(pcm.length, b.getInt(40));
    }

    @Test
    void rejectsNonWav() {
        assertFalse(WavUtil.isWav(null));
        assertFalse(WavUtil.isWav(new byte[10]));
        assertFalse(WavUtil.isWav("not a wav at all, definitely not a wav file....".getBytes()));
    }

    @Test
    void peakOfLittleEndianSamples() {
        byte[] pcm = {0, 0, (byte) 0xFF, 0x3F, 0x01, (byte) 0x80}; // 0, 16383, -32767
        assertEquals(1.0f, WavUtil.peak(pcm, 0, pcm.length), 0.001f);
        assertEquals(0.5f, WavUtil.peak(pcm, 0, 4), 0.01f);
        assertEquals(0f, WavUtil.peak(new byte[100], 0, 100));
    }
}
