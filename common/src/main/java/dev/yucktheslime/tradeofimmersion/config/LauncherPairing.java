package dev.yucktheslime.tradeofimmersion.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;

import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The private pairing file the DwemerDistro launcher writes when it installs this mod from its community release
 * (Mods > Custom mods > Browse mods): {@code <game folder>/mods/tradeofimmersion-kagrenac.json} with
 * {@code server_url} and {@code token}. Read once per config load; the token is never logged.
 */
public record LauncherPairing(String serverUrl, String token) {
    public static final String FILE = "mods/tradeofimmersion-kagrenac.json";
    private static volatile LauncherPairing current;

    public static LauncherPairing get() {
        return current;
    }

    static void reload() {
        current = read(Platform.getGameFolder().resolve(FILE));
    }

    static LauncherPairing read(Path path) {
        if (!Files.isRegularFile(path)) return null;
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            String url = o.has("server_url") ? o.get("server_url").getAsString().trim().replaceAll("/+$", "") : "";
            String token = o.has("token") ? o.get("token").getAsString().trim() : "";
            URI u = URI.create(url);
            if (!("http".equals(u.getScheme()) || "https".equals(u.getScheme())) || u.getHost() == null || token.length() < 16) {
                TradeOfImmersion.LOGGER.warn("Ignoring {}: it has no usable server_url/token", FILE);
                return null;
            }
            return new LauncherPairing(url, token);
        } catch (Exception e) {
            TradeOfImmersion.LOGGER.warn("Could not read {} ({})", FILE, e.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public String toString() {
        return "LauncherPairing[" + serverUrl + "]";
    }
}
