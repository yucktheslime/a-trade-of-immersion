package dev.yucktheslime.tradeofimmersion.stt;

import java.io.ByteArrayOutputStream;

/**
 * Collects the pieces of one recording sent in order-independent chunks. Rejects inconsistent or oversized
 * uploads. Not thread safe: use it on one thread (the server thread).
 */
public final class ChunkAssembler {
    private final int id;
    private final byte[][] parts;
    private final int maxBytes;
    private final long started;
    private int have;
    private int bytes;

    public ChunkAssembler(int id, int total, int maxBytes, long now) {
        if (total < 1 || total > 1000) throw new IllegalArgumentException("bad chunk count " + total);
        this.id = id;
        this.parts = new byte[total][];
        this.maxBytes = maxBytes;
        this.started = now;
    }

    public int id() {
        return id;
    }

    public int total() {
        return parts.length;
    }

    public long started() {
        return started;
    }

    /** Adds one piece. Returns the whole recording once every piece arrived, else null. Throws on bad input. */
    public byte[] add(int index, int total, byte[] data) {
        if (total != parts.length || index < 0 || index >= parts.length) throw new IllegalArgumentException("chunk " + index + "/" + total + " does not fit");
        if (parts[index] != null) throw new IllegalArgumentException("chunk " + index + " sent twice");
        bytes += data.length;
        if (bytes > maxBytes) throw new IllegalArgumentException("recording larger than " + maxBytes + " bytes");
        parts[index] = data;
        if (++have < parts.length) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes);
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }
}
