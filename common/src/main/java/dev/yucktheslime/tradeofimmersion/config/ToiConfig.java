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

    // ---- Phase 4 performance (AI budget) ----
    /**
     * Only NPCs within this many blocks of a player may use the AI (LLM calls and heavy AI ticking).
     * {@code 0} = no proximity culling. Ordinary Minecraft AI (walk, follow, fight) is not affected.
     */
    public int aiProximityRange = 64;
    /**
     * Max NPCs that may be AI-active at once. Priority: the player is looking at them, they were
     * recently interacted with, they are an owned companion, then closer distance. The rest wait
     * in the queue. {@code 0} = no cap.
     */
    public int aiMaxActiveNpcs = 8;
    /**
     * Max LLM / AI-backend requests in flight at once. Extra requests wait in a FIFO queue.
     * Separate from {@code aiMaxRequestsPerMinute}. {@code 0} = no concurrency cap.
     */
    public int aiMaxConcurrentRequests = 2;
    /**
     * Max NPC memories (companion event/chat logs, and villager conversation logs, each) kept in RAM.
     * Least-recently used ones are saved to disk and evicted; they reload on the next access.
     * {@code 0} = no size cap (legacy: only evict by idle timeout, if any).
     */
    public int aiMemoryCacheSize = 32;
    /**
     * Unload an NPC's memory from RAM after this many seconds without access, when it is also not
     * AI-active. {@code 0} = only evict by {@code aiMemoryCacheSize}.
     */
    public int aiMemoryIdleSeconds = 120;
    /** A conversation or interaction keeps that NPC preferred for the active cap for this many seconds. */
    public int aiRecentInteractionSeconds = 45;

    // ---- Kagrenac (aiBackend "kagrenac", see docs/KAGRENAC_SETUP.md) ----
    /**
     * Kagrenac server base URL: the kagrenac-server/ folder of this repo installed in DwemerDistro. Any http(s) URL
     * works, so a hosted Minecraft server (BisectHosting, Apex, ...) can reach Kagrenac on your PC through an https
     * tunnel, e.g. "https://name.trycloudflare.com/TradeOfImmersionServer". A URL that is not on this
     * machine or your local network needs a token.
     * The default is where the DwemerDistro launcher installs the server. When the launcher also installed the mod
     * jar, it writes mods/tradeofimmersion-kagrenac.json (URL and token); that file is used while this is left at
     * the default (or empty) and kagrenacToken is empty. A manual install (deploy_toi.sh) lives at
     * "http://127.0.0.1:19000/custom-mods/trade-of-immersion".
     */
    public String kagrenacBaseUrl = DEFAULT_KAGRENAC_URL;
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
     * give_item, pick_up, inspect_item, talk_to_villager, farm, chop, mine, dig, fetch, travel, learn_item,
     * use_ability, stop_job. Remove a name to forbid it.
     */
    public java.util.List<String> kagrenacAllowedActions = new java.util.ArrayList<>(java.util.List.of(
            "follow_player", "hold_position", "attack_hostile", "attack_target", "go_to", "mount", "dismount", "equip",
            "use_item", "give_item", "pick_up", "inspect_item", "talk_to_villager",
            "farm", "chop", "mine", "dig", "fetch", "travel", "learn_item", "use_ability", "stop_job"));
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
    /** Also speak MCA villager lines (this mod's AI replies, and MCA's own scripted lines when {@code mcaLinesUseToiVoice} is on). */
    public boolean voiceVillagers = true;
    /**
     * When voice is on, speak MCA Reborn's own scripted villager lines (greetings, interaction-screen replies, and
     * so on) through this mod's Kagrenac PocketTTS voice — the same per-NPC voice as AI lines — instead of MCA's
     * built-in text-to-speech. Off: those scripted lines use MCA's TTS again; AI lines still use this mod's voice.
     * Default true, so MCA's "Your game language is not supported by the text-to-speech" warning does not appear
     * while this mod is voicing the line.
     */
    public boolean mcaLinesUseToiVoice = true;
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

    // ---- NPC dialogue box (client side, 0.3.8) ----
    /**
     * Client: show AI NPC lines (companions, and MCA villagers' lines including MCA's own) in a dialogue box on the
     * screen instead of the main chat. The server only stops sending companion lines to chat for players whose
     * client has this on; players without the mod (or with this off) keep getting them in chat.
     */
    public boolean dialogueBox = true;
    /** Client: also copy NPC lines into the main chat while the dialogue box is on. */
    public boolean mirrorToChat = false;
    /** Client: where the box sits: top_left, top_right, bottom_left, bottom_right, top_center or bottom_center. */
    public String dialogueBoxAnchor = "bottom_right";
    /** Client: offset in GUI pixels from that corner (positive = towards the middle of the screen). */
    public int dialogueBoxOffsetX = 4;
    public int dialogueBoxOffsetY = 56;
    /** Client: box width in GUI pixels and how many text lines it shows at most. */
    public int dialogueBoxWidth = 260;
    public int dialogueBoxMaxLines = 8;
    /** Client: background opacity 0..1 and text scale (0.5..2). */
    public float dialogueBoxOpacity = 0.6f;
    public float dialogueBoxScale = 1.0f;
    /** Client: a line stays this many seconds, then fades out over dialogueBoxFadeSeconds. */
    public int dialogueBoxLineSeconds = 12;
    public int dialogueBoxFadeSeconds = 2;
    /** Client: lines kept for the scroll-back screen. */
    public int dialogueHistorySize = 200;

    // ---- speech to text (push-to-talk, 0.3.8) ----
    /**
     * Client: push-to-talk ({@code B} by default, rebind it in Controls) records the microphone and turns it into
     * a line to the NPC you look at. {@code "server"}: the recording goes to the Minecraft server, which asks
     * Kagrenac's listen.php (or sttUrl below); {@code "direct"}: this computer calls sttDirectUrl itself (single
     * player / your own PC); {@code "off"}: no speech input.
     */
    public String sttMode = "server";
    /** Client, for sttMode "direct": an OpenAI-compatible transcription URL (Parakeet, faster-whisper-server, whisper.cpp /inference). */
    public String sttDirectUrl = "http://127.0.0.1:8022/v1/audio/transcriptions";
    /** Model name sent with OpenAI-compatible requests (Parakeet ignores it; OpenAI needs whisper-1). */
    public String sttModel = "whisper-1";
    /** Client: microphone to use: empty = the system default, otherwise part of the device name (see /npc stt mics in the log). */
    public String sttMicDevice = "";
    /** Client: longest recording in seconds (1..30). */
    public int sttMaxSeconds = 15;
    /** Client: send the transcript to the NPC right away. Off: it is put in the talk box so you can fix it and press Enter. */
    public boolean sttAutoSend = true;
    /**
     * Server: speech from players goes to Kagrenac's listen.php when aiBackend is kagrenac. Set this to an
     * OpenAI-compatible transcription URL to use that instead (or when you do not use Kagrenac at all).
     */
    public String sttUrl = "";
    /** Server: let players send speech recordings at all. */
    public boolean sttEnabled = true;

    // ---- companion menu, MCA editor and NPC voice profiles ----
    /** Right-clicking a companion opens its menu (talk, follow/stay, inventory, gift, MCA editor, voice). Off: the old clicks (owner click toggles follow/stay, gives the held item). */
    public boolean companionMenuOnRightClick = true;
    /** Anyone holding MCA's villager editor item may edit any companion (like MCA villagers). Off: owner or operator only. */
    public boolean mcaEditorItemForEveryone = false;
    /** Let non-op players change MCA villagers' voices with /npc voice (companions: their owner always can). Single-player always can. */
    public boolean villagerVoiceForEveryone = false;

    // ---- companions ----
    /** Players within this many blocks of a companion are heard by it. */
    public double chatHearingRadius = 16.0;
    /** A companion answers chat that mentions its name; its owner's chat is also answered without the name when the owner is this close and {@link #ownerChatWithoutAddress} is on. */
    public double ownerChatRadius = 10.0;

    // ---- chat filter / routing (Phase 1) ----
    /**
     * Public chat is run through the address filter so the AI only answers when a player addresses an NPC
     * (by name, a group vocative, looking at them, a recent conversation, or the dialogue box). Off: public
     * chat never starts an AI turn; the dialogue box and world events still can.
     */
    public boolean chatFilterEnabled = true;
    /**
     * An owned companion answers the owner's public chat with no name or look, when the owner is within
     * {@link #ownerChatRadius}. Off (the default): the companion only answers when addressed, so nearby
     * chat with other players does not start a turn.
     */
    public boolean ownerChatWithoutAddress = false;
    /**
     * After an AI turn, the same player can keep talking to that NPC in public chat without repeating
     * the name, for this many seconds, within {@link #chatHearingRadius}. 0 = off.
     */
    public int chatContinueSeconds = 60;
    /** Public chat can start an AI turn with an MCA villager (when addressed by name or look). The dialogue box always can. */
    public boolean aiVillagerPublicChat = true;
    /**
     * When MCA Reborn's own villager ChatAI is on ({@code enableVillagerChatAI}), skip our AI for public-chat
     * lines to villagers so the two do not both answer. Dialogue-box talk still uses our AI.
     */
    public boolean aiSkipVillagersWhenMcaChatAi = true;
    /**
     * Extra seconds an NPC will not start another AI turn from chat or the dialogue box (on top of
     * {@link #aiMinSecondsBetweenTurns} and the global per-minute cap). 0 = only those other limits.
     */
    public double aiNpcChatCooldownSeconds = 2.0;
    /** Degrees off the crosshair that still count as looking at an NPC. */
    public double chatLookDegrees = 10.0;
    /** Max distance (blocks) for a look to count as addressing an NPC. */
    public double chatLookRange = 12.0;

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
    /**
     * Server default look of companions: {@code "mca_generated"} (the default since 0.3.7): MCA Reborn's villager look,
     * with genes, skin, clothes and hair picked like MCA does for a new villager, saved per companion and synced to
     * every player. {@code "player_skin"}: the companion's player skin on MCA's body. Per companion:
     * {@code /npc skin <npc> player|mca|random}.
     */
    public String companionAppearance = "mca_generated";
    /** Internal: 1 once companionAppearance was moved to the 0.3.7 default (mca_generated). */
    public int companionAppearanceDefaultsVersion = 0;
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

    /**
     * The Kagrenac token: the environment variable TOI_KAGRENAC_TOKEN (or KAGRENAC_TOKEN) when set, else
     * kagrenacToken, else the token in the launcher's pairing file (only while kagrenacBaseUrl is the default).
     */
    public String effectiveKagrenacToken() {
        String env = System.getenv("TOI_KAGRENAC_TOKEN");
        if (env == null || env.isBlank()) env = System.getenv("KAGRENAC_TOKEN");
        if (env != null && !env.isBlank()) return env.trim();
        if (kagrenacToken != null && !kagrenacToken.isBlank()) return kagrenacToken.trim();
        LauncherPairing p = urlIsDefault() ? LauncherPairing.get() : null;
        return p != null ? p.token() : "";
    }

    /** The Kagrenac base URL: kagrenacBaseUrl, or the launcher pairing file's URL while that is left at the default. */
    public String effectiveKagrenacUrl() {
        LauncherPairing p = urlIsDefault() && (kagrenacToken == null || kagrenacToken.isBlank()) ? LauncherPairing.get() : null;
        if (p != null) return p.serverUrl();
        return kagrenacBaseUrl == null || kagrenacBaseUrl.isBlank() ? DEFAULT_KAGRENAC_URL : kagrenacBaseUrl.trim();
    }

    private boolean urlIsDefault() {
        String u = kagrenacBaseUrl == null ? "" : kagrenacBaseUrl.trim().replaceAll("/+$", "");
        return u.isEmpty() || u.equals(DEFAULT_KAGRENAC_URL) || u.equals(OLD_DEFAULT_KAGRENAC_URL);
    }

    /** Where the DwemerDistro launcher (release install, server_folder TradeOfImmersionServer) serves the server. */
    public static final String DEFAULT_KAGRENAC_URL = "http://127.0.0.1:19000/TradeOfImmersionServer";
    /** The 0.3.0/0.3.1 default; treated like the default so a launcher pairing can take over. */
    static final String OLD_DEFAULT_KAGRENAC_URL = "http://127.0.0.1:19000/custom-mods/TradeOfImmersionServer";

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
        if (instance.companionAppearanceDefaultsVersion < 1) {
            // 0.3.7: companions use the MCA villager model by default, also in configs written by older versions.
            if (!"mca_generated".equalsIgnoreCase(instance.companionAppearance)) {
                TradeOfImmersion.LOGGER.info("[tradeofimmersion] companionAppearance {} -> mca_generated (new default; "
                        + "/npc skin <npc> player keeps a player skin)", instance.companionAppearance);
            }
            instance.companionAppearance = "mca_generated";
            instance.companionAppearanceDefaultsVersion = 1;
        }
        LauncherPairing.reload();
        if (loaded == null && LauncherPairing.get() != null) {
            // First start after the DwemerDistro launcher installed and paired the mod: use Kagrenac right away.
            instance.aiBackend = "kagrenac";
            TradeOfImmersion.LOGGER.info("Found the DwemerDistro launcher pairing ({}); using aiBackend kagrenac", LauncherPairing.FILE);
        }
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
