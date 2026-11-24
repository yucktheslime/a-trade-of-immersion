package dev.yucktheslime.tradeofimmersion.stt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Speech to text over HTTP without Kagrenac: an OpenAI-compatible {@code /v1/audio/transcriptions} endpoint
 * (DwemerDistro Parakeet on port 8022, faster-whisper-server, LocalAI, OpenAI itself) or whisper.cpp's server
 * ({@code /inference}). The WAV is sent as multipart form field {@code file}, with {@code model} and
 * {@code response_format=json}. Replies are {@code {"text": "..."}} (plain text is accepted too).
 */
public final class SttHttp {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public static final int MAX_TEXT = 1000;

    private SttHttp() {
    }

    /** A multipart/form-data body; returns the body and sets {@code boundaryOut[0]}. */
    public static byte[] multipart(Map<String, String> fields, String fileField, String fileName, byte[] file, String[] boundaryOut) {
        String boundary = "----toi" + UUID.randomUUID().toString().replace("-", "");
        boundaryOut[0] = boundary;
        ByteArrayOutputStream out = new ByteArrayOutputStream(file.length + 512);
        for (Map.Entry<String, String> f : fields.entrySet()) {
            out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey() + "\"\r\n\r\n"
                    + f.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + fileName
                + "\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(file);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** The transcript in a reply body: JSON {@code text} (also {@code transcript}/{@code result}) or plain text. Null if none. */
    public static String parseTranscript(String body) {
        if (body == null) return null;
        String t = body.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("{")) {
            try {
                JsonObject o = JsonParser.parseString(t).getAsJsonObject();
                for (String k : new String[]{"text", "transcript", "result"}) {
                    JsonElement e = o.get(k);
                    if (e != null && e.isJsonPrimitive()) return clean(e.getAsString());
                }
                return null;
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (t.startsWith("<") || t.startsWith("[")) return null; // HTML error page or JSON array
        return clean(t);
    }

    /** One spoken line: collapse whitespace, drop whisper's "[BLANK_AUDIO]"-style tags; null when nothing is left. */
    public static String clean(String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\[[A-Z_ ]{3,30}]|\\([a-z ]{3,30}\\)", " ").replaceAll("\\s+", " ").trim();
        if (t.length() > MAX_TEXT) t = t.substring(0, MAX_TEXT);
        return t.chars().anyMatch(Character::isLetterOrDigit) ? t : null;
    }

    /** POST the WAV to an OpenAI-compatible (or whisper.cpp) transcription URL. Completes with the text or fails. */
    public static CompletableFuture<String> transcribe(String url, String model, String apiKey, byte[] wav, int timeoutSeconds) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("model", model == null || model.isBlank() ? "whisper-1" : model.trim());
        fields.put("response_format", "json");
        String[] boundary = new String[1];
        byte[] body = multipart(fields, "file", "speech.wav", wav, boundary);
        HttpRequest.Builder rb;
        try {
            rb = HttpRequest.newBuilder(URI.create(url.trim())).timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary[0])
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("bad STT URL: " + url));
        }
        if (apiKey != null && !apiKey.isBlank()) rb.header("Authorization", "Bearer " + apiKey.trim());
        return HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new IllegalStateException("STT HTTP " + resp.statusCode() + (resp.statusCode() == 404 ? " (wrong URL?)" : ""));
            String text = parseTranscript(resp.body());
            if (text == null) throw new IllegalStateException("no speech recognised");
            return text;
        });
    }
}
