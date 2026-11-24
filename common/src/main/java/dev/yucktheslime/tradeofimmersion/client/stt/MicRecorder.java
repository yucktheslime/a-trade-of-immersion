package dev.yucktheslime.tradeofimmersion.client.stt;

import dev.yucktheslime.tradeofimmersion.stt.WavUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Records the microphone as 16 kHz, 16-bit, mono PCM on its own thread (javax.sound.sampled). */
public final class MicRecorder {
    public static final AudioFormat FORMAT = new AudioFormat(WavUtil.SAMPLE_RATE, 16, 1, true, false);
    private final TargetDataLine line;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    private final int maxBytes;
    private final Thread thread;
    private volatile boolean running = true;
    private volatile float level;
    private volatile float peak;

    private MicRecorder(TargetDataLine line, int maxSeconds) {
        this.line = line;
        this.maxBytes = maxSeconds * WavUtil.SAMPLE_RATE * 2;
        this.thread = new Thread(this::run, "TOI microphone");
        this.thread.setDaemon(true);
    }

    /** Opens the microphone (the system default, or the first whose name contains {@code device}) and starts recording. */
    public static MicRecorder start(String device, int maxSeconds) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        TargetDataLine line = null;
        if (device != null && !device.isBlank()) {
            String want = device.toLowerCase(Locale.ROOT);
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (!mi.getName().toLowerCase(Locale.ROOT).contains(want)) continue;
                Mixer m = AudioSystem.getMixer(mi);
                if (m.isLineSupported(info)) {
                    line = (TargetDataLine) m.getLine(info);
                    break;
                }
            }
            if (line == null) throw new LineUnavailableException("no microphone named like \"" + device + "\" (available: " + devices() + ")");
        } else {
            if (!AudioSystem.isLineSupported(info)) throw new LineUnavailableException("no microphone found");
            line = (TargetDataLine) AudioSystem.getLine(info);
        }
        line.open(FORMAT, WavUtil.SAMPLE_RATE / 5 * 2); // ~200 ms buffer
        line.start();
        MicRecorder r = new MicRecorder(line, Math.max(1, Math.min(30, maxSeconds)));
        r.thread.start();
        return r;
    }

    /** Names of the devices that can record in our format. */
    public static List<String> devices() {
        List<String> out = new ArrayList<>();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        try {
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (AudioSystem.getMixer(mi).isLineSupported(info)) out.add(mi.getName());
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private void run() {
        byte[] buf = new byte[1600]; // 50 ms
        try {
            while (running) {
                int n = line.read(buf, 0, buf.length);
                if (n <= 0) continue;
                float lv = WavUtil.peak(buf, 0, n);
                level = lv;
                if (lv > peak) peak = lv;
                synchronized (pcm) {
                    pcm.write(buf, 0, Math.min(n, maxBytes - pcm.size()));
                    if (pcm.size() >= maxBytes) running = false;
                }
            }
        } finally {
            try {
                line.stop();
                line.close();
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** False once stopped or once the maximum length was reached. */
    public boolean running() {
        return running;
    }

    public float level() {
        return level;
    }

    public float peak() {
        return peak;
    }

    public double seconds() {
        synchronized (pcm) {
            return pcm.size() / (double) (WavUtil.SAMPLE_RATE * 2);
        }
    }

    /** Stops and returns the recording as a WAV. */
    public byte[] stop() {
        running = false;
        try {
            thread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (pcm) {
            return WavUtil.pcmToWav(pcm.toByteArray(), WavUtil.SAMPLE_RATE, 1, 16);
        }
    }
}
