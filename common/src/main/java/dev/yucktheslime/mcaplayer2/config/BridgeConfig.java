package dev.yucktheslime.mcaplayer2.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.yucktheslime.mcaplayer2.McaPlayer2Bridge;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple JSON config stored at {@code config/mcaplayer2.json}. Missing keys fall back to
 * the defaults below, and the file is re-written on load so new keys show up for the user.
 */
public final class BridgeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BridgeConfig instance = new BridgeConfig();

    /** Master switch. */
    public boolean enabled = true;
    /** Radius (blocks) in which a villager notices a companion and greets it / hears it. */
    public double detectionRadius = 8.0;
    /** How often (ticks) to scan for companion/villager proximity. */
    public int scanIntervalTicks = 40;
    /** Minimum ticks between two greetings for the same companion/villager pair (12000 = half a day). */
    public int greetCooldownTicks = 12000;
    /** Max villager lines in one back-and-forth before the villager stops replying. Prevents endless AI loops. */
    public int maxTurnsPerConversation = 4;
    /** Ticks of silence after which a conversation is considered over and the turn counter resets. */
    public int conversationResetTicks = 2400;
    /** Distance at which the talk_to_villager task considers itself "arrived". */
    public double talkArrivalDistance = 3.0;
    /** Distance the companion keeps from the villager while talking; it re-paths if the villager walks further away. */
    public double talkStayInRangeDistance = 5.0;
    /** The talk goal ends after this many ticks without a new line in the conversation (600 = 30 s). */
    public int talkIdleTimeoutTicks = 600;
    /** Hard cap on how long the talk goal keeps the companion near a villager (2400 = 2 min). */
    public int talkMaxDurationTicks = 2400;
    /** Make the villager look at the companion it is talking to. */
    public boolean villagerLooksAtCompanion = true;

    /** Generate villager lines with the Player2 app's local LLM endpoint; otherwise use canned lines. */
    public boolean useLlmForVillagerReplies = true;
    /**
     * Where villager lines are generated: {@code "playerengine"} (default; PlayerEngine's own Player2 client,
     * billed to the companion's owner like the companion itself), {@code "local_app"} (direct call to the Player2
     * desktop app at {@link #player2ApiUrl}) or {@code "canned"} (no AI, built-in lines).
     */
    public String villagerReplyBackend = "playerengine";
    /** After a billing/auth/quota failure, pause all bridge-triggered AI requests for this long (seconds). */
    public int billingFailureCooldownSeconds = 300;
    /** Global cap on companion LLM turns the bridge may trigger (villager lines + greetings) per minute. 0 = unlimited. */
    public int maxBridgeCompanionTurnsPerMinute = 6;
    /** Base URL of the Player2 desktop app's local API (only for villagerReplyBackend = "local_app"). */
    public String player2ApiUrl = "http://127.0.0.1:4315";
    /** Value of the {@code player2-game-key} header sent to the Player2 app. */
    public String player2GameKey = "mca-player2-bridge";
    /** HTTP timeout for villager reply generation. */
    public int llmTimeoutSeconds = 20;
    /** Max words requested for a villager line. */
    public int villagerReplyMaxWords = 30;

    /** Detect tamed companions / owned animals near the companion and tell it who owns them. */
    public boolean detectOwnedCompanions = true;
    /** Radius (blocks) scanned for owned entities when a conversation opens. */
    public double ownedCompanionScanRadius = 12.0;
    /** Max owned entities described in one prompt. */
    public int maxOwnedEntitiesInPrompt = 6;
    /** Use the (slower) saved-NBT fallback to find owner UUIDs on entities no resolver understands. */
    public boolean ownerNbtFallback = true;
    /** NBT keys checked by the fallback (top level and one compound deep). */
    public java.util.List<String> ownerNbtKeys = java.util.List.of("Owner", "OwnerUUID", "owner", "ownerUUID", "OwnerUuid", "ownerUuid");
    /**
     * Optional no-arg accessor methods on an entity that return a separate data object holding the owner
     * (that object is then checked for getOwnerUUID/getOwnerUuid/getOwnerId/getOwner). Empty by default.
     */
    public java.util.List<String> ownerAccessorMethods = java.util.List.of();
    /** If an owner UUID matches no loaded villager or online player, attribute it to the nearest villager (marked as a guess). */
    public boolean guessOwnerFromNearestVillager = false;
    /** Mention owned animals whose owner could not be resolved. */
    public boolean mentionUnknownOwners = true;

    /** Max entries kept per villager conversation log. */
    public int maxLogEntries = 100;
    /** How many past log lines are injected into prompts. */
    public int historyLinesInPrompt = 10;

    public static BridgeConfig get() {
        return instance;
    }

    public static Path path() {
        return Platform.getConfigFolder().resolve(McaPlayer2Bridge.MOD_ID + ".json");
    }

    public static void load() {
        Path path = path();
        BridgeConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(r, BridgeConfig.class);
            } catch (Exception e) {
                McaPlayer2Bridge.LOGGER.error("Failed to read {}, using defaults", path, e);
            }
        }
        instance = loaded != null ? loaded : new BridgeConfig();
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            McaPlayer2Bridge.LOGGER.warn("Could not write config {}", path, e);
        }
    }
}
