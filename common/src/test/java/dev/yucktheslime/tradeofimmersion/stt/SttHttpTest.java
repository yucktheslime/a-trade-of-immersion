package dev.yucktheslime.tradeofimmersion.stt;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** SttHttp against a stub OpenAI-compatible / Parakeet transcription server on a free local port. */
class SttHttpTest {
    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile String reply = "{\"text\":\"  follow   me please \"}";
    private volatile int status = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/audio/transcriptions", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream o = ex.getResponseBody()) {
                o.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/audio/transcriptions";
    }

    private static byte[] wav() {
        return WavUtil.pcmToWav(new byte[]{1, 2, 3, 4}, 16000, 1, 16);
    }

    @Test
    void sendsMultipartAndReadsText() throws Exception {
        String text = SttHttp.transcribe(url(), "whisper-1", null, wav(), 10).get(10, TimeUnit.SECONDS);
        assertEquals("follow me please", text);
        String body = lastBody.get();
        assertTrue(body.contains("name=\"model\"\r\n\r\nwhisper-1\r\n"), body);
        assertTrue(body.contains("name=\"response_format\"\r\n\r\njson\r\n"));
        assertTrue(body.contains("name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\nRIFF"));
        assertNull(lastAuth.get());
    }

    @Test
    void sendsBearerKeyWhenGiven() throws Exception {
        SttHttp.transcribe(url(), "", "sk-test", wav(), 10).get(10, TimeUnit.SECONDS);
        assertEquals("Bearer sk-test", lastAuth.get());
    }

    @Test
    void errorsAreFailures() {
        status = 500;
        ExecutionException e = assertThrows(ExecutionException.class, () -> SttHttp.transcribe(url(), "m", null, wav(), 10).get(10, TimeUnit.SECONDS));
        assertTrue(e.getCause().getMessage().contains("500"));
        status = 200;
        reply = "{\"text\":\"[BLANK_AUDIO]\"}";
        e = assertThrows(ExecutionException.class, () -> SttHttp.transcribe(url(), "m", null, wav(), 10).get(10, TimeUnit.SECONDS));
        assertTrue(e.getCause().getMessage().contains("no speech"));
    }

    @Test
    void unreachableServerFails() {
        String dead = "http://127.0.0.1:1/v1/audio/transcriptions";
        assertThrows(ExecutionException.class, () -> SttHttp.transcribe(dead, "m", null, wav(), 5).get(10, TimeUnit.SECONDS));
    }

    @Test
    void parsesReplyShapes() {
        assertEquals("hi", SttHttp.parseTranscript("{\"text\":\"hi\"}"));
        assertEquals("hi there", SttHttp.parseTranscript("{\"transcript\":\" hi  there\"}"));
        assertEquals("plain words", SttHttp.parseTranscript("plain words\n"));
        assertNull(SttHttp.parseTranscript("<html>bad gateway</html>"));
        assertNull(SttHttp.parseTranscript("{\"error\":\"x\"}"));
        assertNull(SttHttp.parseTranscript(""));
        assertNull(SttHttp.clean(" (silence) [BLANK_AUDIO] "));
        assertEquals("Hello", SttHttp.clean("Hello (laughs)"));
    }

    @Test
    void multipartHasBoundaryAndFields() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("model", "x");
        String[] b = new String[1];
        String body = new String(SttHttp.multipart(f, "file", "a.wav", new byte[]{'R'}, b), StandardCharsets.ISO_8859_1);
        assertTrue(body.startsWith("--" + b[0] + "\r\n"));
        assertTrue(body.endsWith("\r\n--" + b[0] + "--\r\n"));
    }
}
