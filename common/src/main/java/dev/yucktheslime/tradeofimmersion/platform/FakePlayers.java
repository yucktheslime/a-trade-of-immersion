package dev.yucktheslime.tradeofimmersion.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Loader-provided fake players (NeoForge {@code FakePlayerFactory}, Fabric API {@code FakePlayer}). Companions use
 * one to call other mods' item code exactly like a player would ({@code useItem}/{@code useItemOn}/
 * {@code interactOn}), so modded items work without per-mod compat.
 */
public final class FakePlayers {
    private static BiFunction<ServerLevel, GameProfile, ServerPlayer> provider;

    private FakePlayers() {
    }

    public static void setProvider(BiFunction<ServerLevel, GameProfile, ServerPlayer> p) {
        provider = p;
    }

    public static ServerPlayer get(ServerLevel level, UUID companionId, String name) {
        if (provider == null) throw new IllegalStateException("No fake player provider registered by the loader");
        String n = name.replaceAll("[^A-Za-z0-9_]", "");
        if (n.isEmpty()) n = "Companion";
        if (n.length() > 16) n = n.substring(0, 16);
        UUID id = UUID.nameUUIDFromBytes(("tradeofimmersion:" + companionId).getBytes(StandardCharsets.UTF_8));
        return provider.apply(level, new GameProfile(id, n));
    }
}
