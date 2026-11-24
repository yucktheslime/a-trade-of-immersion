package dev.yucktheslime.tradeofimmersion.client.stt;

import dev.architectury.networking.NetworkManager;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.yucktheslime.tradeofimmersion.client.dialogue.DialogueClient;
import dev.yucktheslime.tradeofimmersion.client.dialogue.DialogueScreen;
import dev.yucktheslime.tradeofimmersion.config.ToiConfig;
import dev.yucktheslime.tradeofimmersion.stt.SttHttp;
import dev.yucktheslime.tradeofimmersion.stt.SttRelay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Locale;

/**
 * Push-to-talk: records while the key (or the talk screen's Mic button) is held, then sends the WAV to the
 * Minecraft server in chunks ({@code sttMode "server"}, the server asks Kagrenac / sttUrl) or straight to
 * {@code sttDirectUrl} ({@code "direct"}). The transcript is said to the NPC the player was looking at
 * ({@code sttAutoSend}), or put in the talk field to check first.
 */
public final class SttClient {
    private enum State { IDLE, LISTENING, TRANSCRIBING }

    private static State state = State.IDLE;
    private static MicRecorder recorder;
    private static boolean heldByScreen;
    private static int targetId = -1;
    private static int clipId;
    private static long since;
    private static boolean loggedMics;

    private SttClient() {
    }

    public static boolean recording() {
        return state == State.LISTENING;
    }

    public static boolean heldByScreen() {
        return heldByScreen;
    }

    public static void startHeld(Entity target) {
        start(target);
        if (recording()) heldByScreen = true;
    }

    public static void start(Entity target) {
        ToiConfig cfg = ToiConfig.get();
        if (state != State.IDLE) return;
        String mode = cfg.sttMode == null ? "server" : cfg.sttMode.toLowerCase(Locale.ROOT);
        if (mode.equals("off")) {
            note("Speech input is off (sttMode \"off\" in tradeofimmersion.json).", true);
            return;
        }
        if (target == null) {
            note("Look at an NPC, then hold " + DialogueClient.PUSH_TO_TALK.getTranslatedKeyMessage().getString() + " to talk.", true);
            return;
        }
        if (mode.equals("server") && !NetworkManager.canServerReceive(SttRelay.Chunk.TYPE)) {
            note("This server does not have A Trade of Immersion, so it can't hear you. Type instead.", true);
            return;
        }
        if (!loggedMics) {
            loggedMics = true;
            TradeOfImmersion.LOGGER.info("Microphones that can record 16 kHz mono: {}", MicRecorder.devices());
        }
        try {
            recorder = MicRecorder.start(cfg.sttMicDevice, cfg.sttMaxSeconds);
        } catch (Exception | LinkageError e) {
            recorder = null;
            String m = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            TradeOfImmersion.LOGGER.warn("Microphone unavailable: {}", e.toString());
            note("Microphone unavailable: " + m + ". Type instead (" + DialogueClient.TALK.getTranslatedKeyMessage().getString() + ").", true);
            return;
        }
        targetId = target.getId();
        state = State.LISTENING;
        since = System.currentTimeMillis();
        heldByScreen = false;
    }

    public static void stop() {
        if (state != State.LISTENING || recorder == null) return;
        MicRecorder r = recorder;
        recorder = null;
        heldByScreen = false;
        double secs = r.seconds();
        byte[] wav = r.stop();
        if (secs < 0.4) {
            state = State.IDLE;
            note("Too short: hold the key while you speak.", true);
            return;
        }
        if (r.peak() < 0.01f) {
            state = State.IDLE;
            note("Didn't hear anything: check your microphone" + (ToiConfig.get().sttMicDevice.isBlank() ? "" : " (sttMicDevice)") + ".", true);
            return;
        }
        submit(targetId, wav);
    }

    /** Transcribes a finished recording for the NPC with this entity id (also used by the dev test without a microphone). */
    public static void submit(int npcEntityId, byte[] wav) {
        targetId = npcEntityId;
        state = State.TRANSCRIBING;
        since = System.currentTimeMillis();
        ToiConfig cfg = ToiConfig.get();
        if ("direct".equalsIgnoreCase(cfg.sttMode)) {
            int clip = ++clipId;
            SttHttp.transcribe(cfg.sttDirectUrl, cfg.sttModel, System.getenv("TOI_STT_API_KEY"), wav, 60)
                    .whenComplete((text, err) -> Minecraft.getInstance().execute(() -> {
                        if (clip != clipId || state != State.TRANSCRIBING) return;
                        if (err != null) {
                            Throwable t = err.getCause() != null ? err.getCause() : err;
                            String m = t instanceof java.net.ConnectException ? "nothing answers at " + cfg.sttDirectUrl : t.getMessage();
                            failed("Speech to text failed: " + m + ". Type instead.");
                        } else heard(text);
                    }));
            return;
        }
        int clip = ++clipId;
        int total = (wav.length + SttRelay.CHUNK - 1) / SttRelay.CHUNK;
        for (int i = 0; i < total; i++) {
            NetworkManager.sendToServer(new SttRelay.Chunk(clip, i, total, Arrays.copyOfRange(wav, i * SttRelay.CHUNK, Math.min(wav.length, (i + 1) * SttRelay.CHUNK))));
        }
    }

    public static void cancel() {
        if (recorder != null) recorder.stop();
        recorder = null;
        state = State.IDLE;
        heldByScreen = false;
        clipId++;
    }

    public static void onResult(SttRelay.Result r) {
        if (r.clipId() != clipId || state != State.TRANSCRIBING) return;
        if (r.ok()) heard(r.text());
        else failed(r.text());
    }

    private static void failed(String message) {
        state = State.IDLE;
        note(message, true);
    }

    private static void heard(String text) {
        state = State.IDLE;
        Minecraft mc = Minecraft.getInstance();
        Entity target = mc.level == null ? null : mc.level.getEntity(targetId);
        if (text == null || text.isBlank()) {
            note("No words recognised. Try again or type.", true);
            return;
        }
        if (ToiConfig.get().sttAutoSend && DialogueClient.talkable(target)) {
            DialogueClient.say(target, text, true);
            if (mc.screen instanceof DialogueScreen ds) ds.onClose();
            return;
        }
        if (mc.screen instanceof DialogueScreen ds) ds.setDraft(text);
        else if (mc.screen == null) DialogueScreen.open(DialogueClient.talkable(target) ? target : DialogueClient.target(), text, false);
    }

    private static void note(String text, boolean error) {
        DialogueClient.info(text);
    }

    public static void tick(Minecraft mc) {
        if (state == State.LISTENING && recorder != null) {
            if (!recorder.running()) stop(); // reached sttMaxSeconds
            else if (heldByScreen && GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) stop();
        }
        if (state == State.TRANSCRIBING && System.currentTimeMillis() - since > 75_000) failed("Speech to text timed out. Type instead.");
    }

    /** "Listening..." / "Transcribing..." for the box, or null when idle. */
    public static String statusLine() {
        long now = System.currentTimeMillis();
        if (state == State.LISTENING && recorder != null) {
            int bars = Math.round(recorder.level() * 8);
            return "\u25CF Listening... " + String.format(Locale.ROOT, "%.0fs", recorder.seconds()) + " " + "|".repeat(bars);
        }
        if (state == State.TRANSCRIBING) return "Transcribing" + ".".repeat((int) ((now - since) / 400 % 4));
        return null;
    }

    public static int statusColor() {
        return state == State.LISTENING ? 0xFFFF6060 : 0xFFE0C080;
    }
}
