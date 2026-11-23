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
     * {@code "kagrenac"}: a Kagrenac server (the CHIM-style setup: server-side prompts, memory, dashboard, voice).
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

    // ---- Kagrenac (aiBackend "kagrenac", see docs/KAGRENAC_SETUP.md) ----
    /**
     * Kagrenac server base URL: the kagrenac-server/ folder of this repo installed in DwemerDistro. Any http(s) URL
     * works, so a hosted Minecraft server (BisectHosting, Apex, ...) can reach Kagrenac on your PC through an https
     * tunnel, e.g. "https://name.trycloudflare.com/custom-mods/TradeOfImmersionServer". A URL that is not on this
     * machine or your local network needs a token.
     */
    public String kagrenacBaseUrl = "http://127.0.0.1:19000/custom-mods/TradeOfImmersionServer";
    /**
     * Kagrenac bearer token (the "token" in the server's config/config.php). Settable here for hosts that have no
     * environment variables; the environment variable TOI_KAGRENAC_TOKEN (or KAGRENAC_TOKEN) overrides it when set.
     * Never logged or shown in chat.
     */
    public String kagrenacToken = "";
    /** Kagrenac session id (history and memory are kept per session + NPC). Empty = "mc-" + world name. */
    public String kagrenacSessionId = "";
    /**
     * Actions this game may run for Kagrenac (the client-side allowlist; the server has its own allowed_actions).
     * Names: follow_player, hold_position, attack_hostile, attack_target, go_to, mount, dismount, equip, use_item,
     * give_item, pick_up, inspect_item, talk_to_villager. Remove a name to forbid it.
     */
    public java.util.List<String> kagrenacAllowedActions = new java.util.ArrayList<>(java.util.List.of(
            "follow_player", "hold_position", "attack_hostile", "attack_target", "go_to", "mount", "dismount", "equip",
            "use_item", "give_item", "pick_up", "inspect_item", "talk_to_villager"));
    /** Send world events (deaths, damage, gifts, villager talk, relationships) to the Kagrenac event log so NPCs know what happened. */
    public boolean kagrenacLogEvents = true;
    /** Push companion and MCA villager bios (name, personality, profession, family) to the Kagrenac dashboard. */
    public boolean kagrenacSyncBios = true;
    /** Ask Kagrenac's decision.php which companion should answer when several are near and none is named or looked at. */
    public boolean kagrenacUseDecision = true;
    /** A new line to a companion that is still thinking cancels the old turn (cancel.php) instead of waiting. */
    public boolean kagrenacCancelOnInterrupt = true;
    /**
     * What companions do while Kagrenac is unreachable (PC off, tunnel down): {@code "line"} = one short in-character
     * line now and then, otherwise quiet; {@code "openai"} = answer through the OpenAI-compatible settings above
     * (aiPreset/aiBaseUrl/aiModel/aiApiKey, e.g. OpenRouter); {@code "mock"} = the offline keyword brain;
     * {@code "none"} = silent.
     */
    public String kagrenacFallback = "line";
    /** World events waiting for Kagrenac (kept in memory while it is unreachable; the oldest are dropped beyond this). */
    public int kagrenacEventQueueSize = 200;
    /** Longest wait between two retries while Kagrenac is unreachable (the wait doubles from 2 s up to this). */
    public int kagrenacRetryMaxSeconds = 300;
    /** Also forward kills of ordinary hostile mobs by players (companion kills, bosses and named mobs are always sent). */
    public boolean kagrenacLogMobKills = false;

    // ---- voice (Kagrenac speak.php) ----
    /**
     * Server side: fetch NPC speech from Kagrenac's speak.php (through kagrenacBaseUrl, with the server's token) and
     * stream it to nearby players, who hear it from the NPC's position. Needs TTS enabled on the Kagrenac server.
     * Off by default. Players need nothing but the mod (no token, no URL).
     */
    public boolean voiceEnabled = false;
    /** Client side: play NPC voice the server streams (set false to mute it on this computer). */
    public boolean voicePlay = true;
    /** Also speak MCA villager lines. */
    public boolean voiceVillagers = true;
    /** Voice volume 0..1 (also scaled by the "Voice/Speech" volume slider). */
    public float voiceVolume = 1.0f;
    /** Voice is heard up to this many blocks from the NPC. */
    public int voiceRange = 24;

    // ---- chat (keep players' screens clean) ----
    /** NPC lines are shown to players within this many blocks of the NPC. */
    public double npcChatRadius = 24.0;
    /** ... and to the companion's owner within this many blocks (same dimension). */
    public double npcChatOwnerRadius = 64.0;
    /** Show "(I can't think straight: AI backend unreachable)" lines in chat when the AI fails. Off: failures only go to the log and /npc ai status. */
    public boolean chatShowAiErrors = false;
    /** Tell a companion's owner in chat when it dies. */
    public boolean chatCompanionDeathNotice = true;
    /** Also show /npc command results to other ops (vanilla "[Name: ...]" broadcast). Off: only whoever ran the command sees them. */
    public boolean chatOpFeedback = false;
    /** Let non-op players run /npc ai status (backend, errors, queue). Off: ops only. */
    public boolean statusForEveryone = false;

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

    /** The API key: the environment variable TOI_API_KEY (or OPENROUTER_API_KEY) when set, else aiApiKey. */
    public String effectiveApiKey() {
        String env = System.getenv("TOI_API_KEY");
        if (env == null || env.isBlank()) {
            env = System.getenv(Migration.LEGACY_ENV_KEY);
            if (env != null && !env.isBlank() && !warnedLegacyEnv) {
                warnedLegacyEnv = true;
                TradeOfImmersion.LOGGER.warn("Using the API key from the old environment variable {}; please rename it to TOI_API_KEY", Migration.LEGACY_ENV_KEY);
            }
        }
        if (env == null || env.isBlank()) env = System.getenv("OPENROUTER_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        return aiApiKey == null ? "" : aiApiKey.trim();
    }

    /** The Kagrenac token: the environment variable TOI_KAGRENAC_TOKEN (or KAGRENAC_TOKEN) when set, else kagrenacToken. */
    public String effectiveKagrenacToken() {
        String env = System.getenv("TOI_KAGRENAC_TOKEN");
        if (env == null || env.isBlank()) env = System.getenv("KAGRENAC_TOKEN");
        if (env != null && !env.isBlank()) return env.trim();
        return kagrenacToken == null ? "" : kagrenacToken.trim();
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

    /** Writes the current settings back to the file (used by /npc ai preset). */
    public static void save() {
        try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
            GSON.toJson(instance, w);
        } catch (IOException e) {
            TradeOfImmersion.LOGGER.warn("Could not write config {}", path(), e);
        }
    }
}
