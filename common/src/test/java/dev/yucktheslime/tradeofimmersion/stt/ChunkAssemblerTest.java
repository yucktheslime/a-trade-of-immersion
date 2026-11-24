package dev.yucktheslime.tradeofimmersion.stt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkAssemblerTest {
    @Test
    void joinsChunksInAnyOrder() {
        ChunkAssembler a = new ChunkAssembler(7, 3, 100, 0);
        assertNull(a.add(2, 3, new byte[]{5}));
        assertNull(a.add(0, 3, new byte[]{1, 2}));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, a.add(1, 3, new byte[]{3, 4}));
    }

    @Test
    void rejectsDuplicatesWrongCountsAndOversize() {
        ChunkAssembler a = new ChunkAssembler(1, 2, 4, 0);
        a.add(0, 2, new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> a.add(0, 2, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> a.add(1, 3, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> a.add(5, 2, new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> a.add(1, 2, new byte[]{1, 2, 3, 4}));
        assertThrows(IllegalArgumentException.class, () -> new ChunkAssembler(1, 0, 4, 0));
    }

    @Test
    void maxRecordingFitsTheChunkPlan() {
        // 30 s of 16 kHz 16-bit mono plus the header stays under the server limit, in < 40 chunks
        int bytes = 30 * 16000 * 2 + 44;
        assertTrue(bytes <= SttRelayLimits.MAX_BYTES);
        assertTrue((bytes + SttRelayLimits.CHUNK - 1) / SttRelayLimits.CHUNK < 40);
        assertTrue(SttRelayLimits.CHUNK + 64 < 32767, "a chunk must fit in one client custom payload");
    }

    /** Same numbers as SttRelay (which needs Minecraft classes to load). */
    static final class SttRelayLimits {
        static final int CHUNK = 30_000;
        static final int MAX_BYTES = 1_000_000;
    }
}
