package dev.yucktheslime.mcaplayer2.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.architectury.platform.Platform;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.yucktheslime.mcaplayer2.entity.CompanionEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a companion's skin spec (synced from the server):
 * <ul>
 *   <li>{@code ""} - a default player skin picked from the companion's UUID</li>
 *   <li>{@code player:<name>} or just {@code <name>} - that Minecraft account's skin (Mojang lookup, cached)</li>
 *   <li>{@code url:<https://...png>} - a 64x64 skin PNG from the web</li>
 *   <li>{@code file:<name.png>} - {@code config/mcaplayer2/skins/<name.png>} on this client</li>
 * </ul>
 * Lookups are async; the default skin is used until they finish. Failed lookups fall back to the default.
 */
public final class CompanionSkins {
    private static final Map<String, Result> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private CompanionSkins() {
    }

    /** texture + whether the skin itself says slim (null = unknown, use the entity's flag). */
    private record Result(ResourceLocation texture, Boolean slim) {
    }

    public static PlayerSkin.Model model(CompanionEntity e) {
        Result r = resolve(e);
        boolean slim = r != null && r.slim != null ? r.slim : e.isSlim();
        if (r == null && e.getSkin().isBlank()) return DefaultPlayerSkin.get(e.getUUID()).model();
        return slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
    }

    public static ResourceLocation texture(CompanionEntity e) {
        Result r = resolve(e);
        return r != null && r.texture != null ? r.texture : DefaultPlayerSkin.get(e.getUUID()).texture();
    }

    private static Result resolve(CompanionEntity e) {
        String spec = e.getSkin();
        if (spec == null || spec.isBlank()) return null;
        Result r = CACHE.get(spec);
        if (r != null) return r.texture == null ? null : r;
        CACHE.put(spec, new Result(null, null)); // loading marker
        load(spec).whenComplete((res, err) -> {
            if (err != null || res == null) {
                McaPlayer2Bridge.LOGGER.warn("[mcaplayer2] Could not load companion skin '{}': {}", spec, err == null ? "not found" : err.toString());
                return; // keep marker: default skin, no retry storm
            }
            CACHE.put(spec, res);
        });
        return null;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static CompletableFuture<Result> load(String spec) {
        String lower = spec.toLowerCase(Locale.ROOT);
        if (lower.startsWith("url:") || lower.startsWith("http://") || lower.startsWith("https://")) {
            String url = lower.startsWith("url:") ? spec.substring(4).trim() : spec.trim();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", "mcaplayer2").GET().build();
            return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).thenCompose(resp -> {
                if (resp.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + resp.statusCode());
                return register("url_" + Integer.toHexString(spec.hashCode()), resp.body());
            });
        }
        if (lower.startsWith("file:")) {
            Path p = Platform.getConfigFolder().resolve(McaPlayer2Bridge.MOD_ID).resolve("skins").resolve(spec.substring(5).trim()).normalize();
            if (!p.startsWith(Platform.getConfigFolder())) return CompletableFuture.failedFuture(new IllegalArgumentException("bad path"));
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return Files.newInputStream(p);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).thenCompose(in -> register("file_" + Integer.toHexString(spec.hashCode()), in));
        }
        String name = lower.startsWith("player:") ? spec.substring(7).trim() : spec.trim();
        return SkullBlockEntity.fetchGameProfile(name).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(null);
            GameProfile profile = opt.get();
            return Minecraft.getInstance().getSkinManager().getOrLoad(profile)
                    .thenApply(skin -> new Result(skin.texture(), skin.model() == PlayerSkin.Model.SLIM));
        });
    }

    private static CompletableFuture<Result> register(String key, InputStream in) {
        NativeImage img;
        try (in) {
            img = NativeImage.read(in);
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
        if (img.getWidth() != 64 || img.getHeight() != 64) {
            img.close();
            return CompletableFuture.failedFuture(new IllegalArgumentException("skin must be 64x64 (got " + img.getWidth() + "x" + img.getHeight() + ")"));
        }
        CompletableFuture<Result> f = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(McaPlayer2Bridge.MOD_ID, "skins/" + key);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
            f.complete(new Result(id, null));
        });
        return f;
    }
}
