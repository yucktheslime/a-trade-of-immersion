package dev.yucktheslime.tradeofimmersion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.yucktheslime.tradeofimmersion.TradeOfImmersion;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple JSON config stored at {@code config/tradeofimmersion.json}. Missing keys fall back to
 * the defaults below, and the file is re-written on load so new keys show up for the user.
 */
public final class ToiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ToiConfig instance = new ToiConfig();

    /** Master switch. */
    public boolean enabled = true;

    // ---- AI backend ----
    /**
     * {@code "openai"}: any OpenAI-compatible chat API (Ollama, OpenRouter, LM Studio, ...).
     * {@code "kagrenac"}: a Kagrenac server (PROTOCOL.md v1 turn/event endpoints).
     * {@code "mock"}: no AI, keyword rules (for testing / offline).
     */
    public String aiBackend = "openai";
    /** Fills baseUrl/model when they are left empty: {@code "ollama"} (free, local) or {@code "openrouter"}. */
    public String aiPreset = "ollama";
    /** OpenAI-compatible base URL (ends in /v1). Empty = from preset. */
    public String aiBaseUrl = "";
    /** Model name. Empty = from preset (ollama: gemma3:4b, openrouter: google/gemma-3-27b-it:free). */
    public String aiModel = "";
    /**
     * API key. Leave empty and set the environment variable TOI_API_KEY (or OPENROUTER_API_KEY) instead,
     * so the key never ends up in a shared modpack/config. Not needed for Ollama.
     */
    public String aiApiKey = "";
    public double aiTemperature = 0.7;
    public int aiMaxTokens = 350;
    public int aiTimeoutSeconds = 45;
    /** When the AI backend fails (offline, bad key), answer with the built-in rule-based "mock" brain instead of going silent. */
    public boolean aiFallbackToMock = true;
    /** After a backend failure, wait this long before calling it again (uses mock meanwhile). */
    public int aiFailureCooldownSeconds = 60;
    /** Global cap on AI requests per minute (companions + villagers). */
    public int aiMaxRequestsPerMinute = 20;
    /** Minimum seconds between two AI turns of the same companion. */
    public double aiMinSecondsBetweenTurns = 2.0;

    /** Kagrenac server base URL (manual DwemerDistro install default). */
    public String kagrenacBaseUrl = "http://127.0.0.1:19000/custom-mods/KagrenacServer";
    /** Kagrenac bearer token (from the server's config/config.php). Or env KAGRENAC_TOKEN. */
    public String kagrenacToken = "";
    /** Kagrenac session id (history is kept per session + NPC). Empty = world name. */
    public String kagrenacSessionId = "";

    // ---- companions ----
    /** Players within this many blocks of a companion are heard by it. */
    public double chatHearingRadius = 16.0;
    /** A companion answers chat that mentions its name; its owner's chat is also answered without the name when the owner is this close. */
    public double ownerChatRadius = 10.0;
    /** Blocks within which deaths/attacks are remembered by a companion. */
    public double eventAwarenessRadius = 32.0;
    /** Let companions react (with an AI turn) to important events: owner hurt/died, companion hurt by a player, item given. */
    public boolean reactToEvents = true;
    /** Max events kept per companion memory file. */
    public int maxMemoryEvents = 200;
    /** Events / chat lines included in prompts. */
    public int memoryEventsInPrompt = 15;
    public int chatLinesInPrompt = 12;
    /** Health fraction at which a companion flees. */
    public double fleeHealthFraction = 0.3;
    /** Companions defend their owner and attack hostile mobs that come close. */
    public boolean defendOwner = true;
    /** Teleport to the owner when further than this while following (0 = never). */
    public double followTeleportDistance = 24.0;
    /** Max villager <-> companion exchanges in one talk_to_villager conversation. */
    public int maxVillagerExchanges = 3;
    /** Default skin for new companions: "player:<name>", "url:<https://...png>", "file:<name>.png" (config/tradeofimmersion/skins), or "" (default skin). */
    public String defaultSkin = "";

    /** Max words for an MCA villager line. */
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

    // ---- client-side companion body (NeoForge only) ----
    /** Render companions with MCA Reborn's body (female shape/breasts) instead of the flat player model. */
    public boolean companionMcaBody = true;
    /** {@code "player_skin"}: keep the companion's own skin on MCA's player-compatible body. {@code "mca_generated"}: MCA villager look (skin, face, hair, clothes generated from genes). */
    public String companionAppearance = "player_skin";
    /** Fallback when a companion has no gender set: {@code "female"}, {@code "male"} or {@code "neutral"}. */
    public String companionGender = "male";
    /** Breast size 0..1 for female companions; negative = MCA gene, seeded from the companion's UUID. */
    public double companionBreastSize = -1.0;
    /** Use the flat vanilla body while a chestplate is worn (MCA's breast geometry would clip through armor). */
    public boolean companionFlatWhenChestArmor = true;
    /** Mod ids that, when loaded, disable the MCA companion body (e.g. a body/physics mod you want to win). Empty by default. */
    public java.util.List<String> companionMcaBodyDisableIfModsLoaded = java.util.List.of();

    /** Max entries kept per villager conversation log. */
    public int maxLogEntries = 100;
    /** How many past log lines are injected into prompts. */
    public int historyLinesInPrompt = 10;

    public String effectiveBaseUrl() {
        if (aiBaseUrl != null && !aiBaseUrl.isBlank()) return aiBaseUrl.replaceAll("/+$", "");
        return "openrouter".equalsIgnoreCase(aiPreset) ? "https://openrouter.ai/api/v1" : "http://localhost:11434/v1";
    }

    public String effectiveModel() {
        if (aiModel != null && !aiModel.isBlank()) return aiModel;
        return "openrouter".equalsIgnoreCase(aiPreset) ? "google/gemma-3-27b-it:free" : "gemma3:4b";
    }

    public String effectiveApiKey() {
        if (aiApiKey != null && !aiApiKey.isBlank()) return aiApiKey;
        String env = System.getenv("TOI_API_KEY");
        if (env == null || env.isBlank()) {
            env = System.getenv(Migration.LEGACY_ENV_KEY);
            if (env != null && !env.isBlank() && !warnedLegacyEnv) {
                warnedLegacyEnv = true;
                TradeOfImmersion.LOGGER.warn("Using the API key from the old environment variable {}; please rename it to TOI_API_KEY", Migration.LEGACY_ENV_KEY);
            }
        }
        if (env == null || env.isBlank()) env = System.getenv("OPENROUTER_API_KEY");
        return env == null ? "" : env;
    }

    public String effectiveKagrenacToken() {
        if (kagrenacToken != null && !kagrenacToken.isBlank()) return kagrenacToken;
        String env = System.getenv("KAGRENAC_TOKEN");
        return env == null ? "" : env;
    }

    private static transient boolean warnedLegacyEnv;

    public static ToiConfig get() {
        return instance;
    }

    public static Path path() {
        return Platform.getConfigFolder().resolve(TradeOfImmersion.MOD_ID + ".json");
    }

    public static void load() {
        Path path = path();
        Migration.migrateConfig(path);
        ToiConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(r, ToiConfig.class);
            } catch (Exception e) {
                TradeOfImmersion.LOGGER.error("Failed to read {}, using defaults", path, e);
            }
        }
        instance = loaded != null ? loaded : new ToiConfig();
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (IOException e) {
            TradeOfImmersion.LOGGER.warn("Could not write config {}", path, e);
        }
    }
}
