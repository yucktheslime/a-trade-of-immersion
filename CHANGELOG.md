# Changelog

## Unreleased
- **Chat filter / routing.** Public chat only starts an AI turn when a player addresses an NPC: by name
  (whole word, accent-insensitive, nicknames count), a group vocative at the start of the line
  (`everyone`, `everybody`, `guys`, `team`, `all of you`), looking at them (`chatLookDegrees` /
  `chatLookRange`), a recent conversation with that NPC (`chatContinueSeconds`), or the dialogue box.
  Nearby companions still overhear the line as memory. The old "owned companion answers every nearby
  line" behaviour is off by default (`ownerChatWithoutAddress`).
- Public chat can also address MCA villagers by name or look (`aiVillagerPublicChat`). If MCA Reborn's
  own villager ChatAI is on (`enableVillagerChatAI`), a startup warning is logged and those public-chat
  lines are left to MCA (`aiSkipVillagersWhenMcaChatAi`) so both AIs do not answer the same line.
  Dialogue-box talk still uses this mod's AI. MCA's interaction-screen dialogues are unchanged.
- Per-NPC cooldown for chat and dialogue-box turns (`aiNpcChatCooldownSeconds`, default 2s), on top of
  the existing per-companion gap and the global per-minute cap. The public-chat filter can be switched
  off with `chatFilterEnabled`.
- Unit tests for the line filter, name matching and the routing rules.
- **Phase 4 AI budget.** Only NPCs within `aiProximityRange` of a player (default 64 blocks) may use
  the AI (LLM calls and heavy AI ticking). At most `aiMaxActiveNpcs` (default 8) are active at once,
  chosen by: the player is looking at them, a recent conversation or interaction, companion ownership,
  then closer distance. The rest stay in the world and keep ordinary Minecraft AI; their AI turns wait
  until a slot opens. LLM requests themselves are limited to `aiMaxConcurrentRequests` (default 2) in
  flight, with a FIFO queue. NPC memory is saved to disk and dropped from RAM when the NPC unloads,
  goes idle (`aiMemoryIdleSeconds`, default 120) or the LRU cache (`aiMemoryCacheSize`, default 32)
  is full; it reloads on the next access. Other phases should call `AiBudget.isActive(entity)` before
  an AI turn. All keys are 0 to disable that limit. `/npc ai status` shows the budget. Unit tests
  cover the cap, priority, LLM queue and LRU, including a 2000-NPC stress case.
- **Phase 3 jobs, mounts and item learning.** Companions can farm (harvest and replant), chop natural
  trees, mine exposed blocks they can walk to, dig a straight 1x2 tunnel or staircase, bridge a gap
  with blocks from the bag, and fetch items from the ground or a nearby chest. Vanilla pathfinding
  still cannot plan a mine; buried ore is not dug out automatically, and tunnels are straight lines
  that stop at lava, chests or unbreakable blocks. Mounts: they steer horses, donkeys, camels, pigs
  and striders (with the stick in the bag), boats and minecarts, and never ride a player-owned
  dragon (wild/unowned dragons are allowed; player-owned tamed animals, bikes and cars may be
  ridden). Trips longer than `longTravelDistance` (default 150 blocks) look nearby for an allowed
  ride first. Item tooltips are parsed into use plans ("right-click to…", "press V to…"); right-click
  goes through the fake player, and keybinds are learned by watching a real player's key press and
  replaying the serverbound packet (or a per-mod adapter). Ways that keep failing are marked
  unusable. Habit learning watches what you farm/chop/mine and idle following companions may join in.
  Automatic habit help and auto-mounting when you ride off are gated by `AiBudget.isActive`. All of
  it is toggleable in `config/tradeofimmersion-skills.json`. `/npcskills habits|items|mounts|reload`.
  Vehicle mods (bikes/cars/planes) have generic stub adapters; a real per-mod adapter can replace
  them. Unit tests cover mount rules, the distance trigger, tooltip parsing, action planning, key
  packet correlation and habit scores.

- **Phase 2 MCA life, pregnancy, marriage and relationships.** MCA fires no life-event hooks, so a
  snapshot watcher plus mixins detect marriages, engagements, births, pregnancies, deaths, gifts,
  mood and heart changes on nearby villagers; those become NPC memory and prompt context (and a
  Kagrenac `relationship` / `world_note` log). AI companions join MCA's family tree, so they can
  date, get engaged and marry in all four directions (companion-player, companion-companion,
  companion-villager, villager-companion), reusing MCA's marriage records. MCA's bouquet, engagement
  ring, wedding ring and divorce papers work on companions; `/npc romance ...` covers the rest.
  Affection, jealousy (flirting or gifting others) and drift from neglect drive proposals, break-ups
  and divorce. Close relatives can't pair (siblings, parents/children, grandparents, aunts/uncles,
  first cousins by default; `kinBlockDepth` is configurable). Pregnancy starts from MCA's normal
  procreate step (and companion procreation) but goes through phases (default 3 in-game days each)
  instead of handing over the baby item immediately; the item (or MCA's spawned child, for
  villager-villager gestation) arrives at birth. A persistent Pregnant effect, growing belly on the
  MCA/player model, slowness/hunger/nausea/mood swings, and a miscarriage toggle are all
  configurable in `config/tradeofimmersion-life.json`. Unit tests cover the kinship check,
  relationship math and the pregnancy state machine.

## 0.3.9
- **Architectury API is bundled** (jar-in-jar) in both the NeoForge and Fabric jars, so you no longer need
  to install Architectury separately. MCA Reborn is the only required content mod. If you already have
  Architectury API 13.0.8 or newer in `mods/`, the loader uses that copy. Architectury API 13.0.8 is
  LGPL-3.0; the bundled copy can be replaced by installing it yourself. Source:
  https://github.com/architectury/architectury-api
- Fabric still requires Fabric API (a standard Fabric dependency; MCA Reborn and Architectury both need it).
  It is not bundled, to avoid clashing with the copy you already have.

## 0.3.8
- **NPC dialogue box.** Companion and MCA villager lines (including MCA's own villager lines) show in a box on the
  screen instead of the main chat: an MCA-style dark panel with a gold frame, the speaker's name on a tab, names in
  colour (companions blue, villagers gold, you grey), and lines that fade out after `dialogueBoxLineSeconds`.
  Position, size, scale, opacity and timing are configurable; `mirrorToChat` also copies lines into chat. The
  client tells the server it has the box (`dialogue_hello`), and only then does the server send companion lines as
  `dialogue_line` payloads instead of chat; players without the mod or with `dialogueBox: false` still get chat.
  MCA villager lines are moved into the box on the client by an optional mixin on MCA's
  `ClientHandlerImpl.handleVillagerMessage` (MCA's own villager text-to-speech still runs).
- **Talk field.** **V** (or **Enter** while looking at an NPC) opens a talk field for the NPC under the crosshair,
  the one you look towards, or the last one that spoke; **Tab** cycles nearby NPCs, the wheel and PageUp/PageDown
  scroll back, **H** opens the scroll-back. MCA's interaction screen gets a **Talk (AI)** button. The companion
  menu's talk line uses the same path. Lines are sent with a `dialogue_talk` payload and never touch the main chat.
- **Talking to MCA villagers directly:** a typed or spoken line to a villager is answered by this mod's AI backend
  with its MCA profile (personality, mood, profession, hearts with you) and saved conversation.
- **Push-to-talk speech to text.** Hold **B** (rebindable) to record 16 kHz mono WAV with `javax.sound.sampled`
  (`sttMicDevice`, at most `sttMaxSeconds`). The recording goes to the Minecraft server in 30 KB `stt_chunk`
  payloads (1 MB cap, one at a time per player), and the server sends it to Kagrenac's `listen.php` (or to an
  OpenAI-compatible `sttUrl`). The transcript is said to the NPC (`sttAutoSend`) or put in the talk field.
  "Listening..." with a level meter and "Transcribing..." show in the box; no microphone, speech input off, a
  server without the mod and service errors are reported there, with typing as the fallback. `sttMode: "direct"`
  lets a single-player game call an STT URL itself. `/npc stt` shows the setup and the last result.
- **Kagrenac server 0.3.8-toi:** `listen.php` uses a new `lib/stt.php` with the providers `parakeet` (DwemerDistro,
  port 8022, `/v1/audio/transcriptions`), `fasterwhisper` (port 9876), `openai` (any OpenAI-compatible
  transcription API, optional key on **API keys**), `whispercpp` (`/inference`) and `auto` (finds a running
  DwemerDistro service). The **Voice** page lists the new providers, shows which services are running and can
  record 5 seconds from the browser's microphone for a test. New CI test with a Parakeet / faster-whisper /
  whisper.cpp stub (`tests/stt_test.php`).
- Unit tests (JUnit) for WAV building, chunk assembly, the STT HTTP client against a stub server, and the box
  position; a dev client run checked the box, the talk screen, MCA's Talk button, a typed villager talk, a fake
  push-to-talk recording through the server, and the chat fallback.

## 0.3.7
- **Companions use MCA Reborn's villager model by default.** Every companion, new or existing, renders like an MCA
  villager: MCA's model with its skin, face, clothes and hair layers, its genes (size, width, skin and hair colour,
  breasts) and traits. The server picks the look the way MCA does when a villager spawns (genes from the biome,
  traits, a body skin, clothes and hair for the companion's gender, sometimes dyed hair), saves it in
  `companion_bodies.json` and syncs it, so every player sees the same companion. Existing companions get a look the
  first time they load; a look made with MCA's editor is kept (only blank clothes or hair are filled in).
  Changing the gender (`/npc gender`) picks new clothes and hair for it.
- Sized like MCA villagers (gene size/width, no player 0.9375 scale). Armor uses MCA's armor model (covers the
  breasts); held items, shields and bows work as before. The name tag now shows like on MCA villagers (within MCA's
  `nameTagDistance`, `showNameTags`), not only when you look straight at the companion.
- **MCA Body Shapes** (optional): its sliders already shape companions (it hooks MCA's
  `PlayerEntityExtendedModel`); companions now also get a random body shape like villagers do, and its height slider
  is applied. Reflection only, never required.
- `/npc skin <npc> player` (or any player skin) opts a companion out; `/npc skin <npc> mca` switches back;
  `/npc skin <npc> random` rolls a new MCA look. `/npc spawn <name> <gender> <skin>` with a skin spawns with a player
  skin. `companionAppearance` defaults to `mca_generated`; older configs are moved to it once
  (`companionAppearanceDefaultsVersion`). The client no longer randomizes hair or clothes itself (that made players
  see different companions).
- MCA: Female Gender Physics compat textures the breasts from the per-companion look, not the client config.
- No server (Kagrenac) changes.

## 0.3.6
- **Fix: gifting a Poké Ball to an MCA villager crashed the game** when "MCA: Cobblemon" 1.0.1 (`mca_cobblemon`)
  is installed. That addon was built when MCA's `Personality` was an enum; MCA 7.7.36 made it a registry class, so
  its dialogue code hit `NoSuchMethodError: Personality.values()` the first time a villager caught or sent out a
  Pokémon (or picked a Cobblemon chat line), and the server tick crashed. A small optional mixin (only applied when
  `mca_cobblemon` is present) replaces that lookup with the same personality mapping keyed on the personality id.
  NeoForge only; no server changes.

## 0.3.5
- **Companion menu on right-click** (MCA-style): Follow / Stay / Wander, Inventory, Give held item, Edit (MCA
  editor), Clothes (needle and thread), Hair (comb), Voice, and a line to talk to the companion. Shift + right-click
  still opens the bag. `companionMenuOnRightClick: false` restores the old clicks. MCA's own interaction screen
  needs an MCA villager entity (its brain, memories and family tree), so companions get this menu instead.
- **MCA's villager editor, needle and thread, and comb work on companions.** MCA's real screens open on the
  companion. Two small mixins answer MCA's editor requests for companions and apply what it saves: name, gender,
  genes, personality, traits, and the skin, face, clothes, hair and colors. These are saved in the world
  (`companion_bodies.json`) and synced to all players. Editing the look switches that companion to the MCA
  villager look; `/npc skin <npc> player|mca` switches back and forth. New config keys: `mcaEditorItemForEveryone`
  (default off).
- **A voice profile for every NPC** (companions and MCA villagers), kept on the Kagrenac server (migration
  `011_npc_voices`). Each NPC automatically gets a distinct PocketTTS speaker on its first line (female or male
  when the speaker names say so) and keeps it. Change it with the menu's Voice picker, `/npc voice
  list|show|set|auto|pitch|language`, or the dashboard. Pitch is applied by the game. New config key:
  `villagerVoiceForEveryone`.
- **Dashboard: Configuration > NPC voices.** Lists the NPCs with their voice, language and pitch, lets you upload a
  voice sample (WAV; MP3/OGG/FLAC when ffmpeg is installed), or a **zip of samples** that are all added at once,
  with a per-file result table. You can optionally give each sample to the NPC with the same name (`Steve.wav` goes
  to Steve). Samples are stored in `config/voices/` and sent to PocketTTS `POST /upload_sample`. Changes need the
  server token. Zips are checked for path traversal, links, encryption and size. PHP's upload limit is shown on
  the page.
- Server 0.3.5-toi: `npc_voice.php` (game endpoint for `/npc voice`), `speak.php` accepts `npc_name` and
  answers `X-Voice-Pitch`, capability `npc_voices`. Includes the plugins work below (it had not been released).

## Unreleased (folded into 0.3.5)
- **Server plugins:** a Plugins page on the dashboard (Configuration) where you can enable, disable,
  configure, install (zip or GitHub URL) and remove server plugins.
  - Each plugin is a folder with `plugin.json` and `plugin.php`. Hooks: prompt or context injectors,
    extra allowlisted NPC actions, and `game_event` handlers.
  - Enable state and settings are stored in the database (migration `010_plugins`).
  - User-installed plugins live in `config/plugins/`, next to the private config, so a DwemerDistro
    launcher update keeps them and Core's web protection (which already denies `config/` and `lib/`)
    does not serve the PHP. Bundled examples live in `lib/plugins/bundled/`.
  - Install and other changes need the server token. The page warns that plugins run code on the
    server. Zips are checked for a valid manifest, path traversal, symlinks and unexpected files.
  - Shipped examples: **Mod item knowledge** (configurable facts in the prompt) and **Sample wave
    action** (`[ACTION:wave]`). The Minecraft client offers `plugin_actions` from `health.php` and
    treats unknown plugin actions as a simple gesture.
  - Authoring guide: [kagrenac-server/docs/PLUGINS.md](kagrenac-server/docs/PLUGINS.md).

## 0.3.4
- **Minime and TXT2VEC** (optional, off by default): Kagrenac can use DwemerDistro's local text helpers on port 8082, the same way CHIM does.
  - Minime (`GET /extract?text=...`, optional `GET /command`) decides whether a line needs a memory lookup and can hint an action. The language model is the fallback if Minime is off or misses.
  - TXT2VEC (`POST /embed` with `{"text":"..."}`) embeds diary notes and game events so a turn can recall the most relevant ones, not only the newest. It is also a fact-embeddings provider.
  - Vectors are stored in Postgres (a pgvector column when the extension is available, otherwise a JSON float array with cosine similarity in PHP). Migration `009_memory_vectors` is safe to run more than once.
  - Dashboard: **Configuration > Minime & TXT2VEC** has toggles, a status probe and test buttons. Start the service in the launcher, then turn each one on and press Reindex.
- Server 0.3.3-toi. No mod code changes; the version moves with the server release.

## 0.3.3
- **Voice works with the DwemerDistro launcher's PocketTTS (port 8024).** The server used to send speech to
  `http://127.0.0.1:8086/v1/audio/speech` (the audio.cpp build), which the launcher's PocketTTS does not have
  (HTTP 404). It now speaks its XTTS-style API the way CHIM does: `POST /tts_to_audio` with
  `{"text", "speaker_wav", "language"}`, a WAV back, and speakers from `GET /speakers_list`.
  - New `tts.provider` values: `auto` (new default; finds PocketTTS on `tts.url`, default
    `http://127.0.0.1:8024`, then ports 8020 and 8086 of that host) and `xtts` (also `dwemer-pockettts`).
  - An old `pockettts` setting on 8086 falls over to the PocketTTS it finds, once per line.
  - A voice PocketTTS does not have falls back to `tts.voice`, then to its first speaker. New `tts.language`
    (default `en`; blank keeps the model PocketTTS has loaded).
  - Voice hints pick among PocketTTS's real speakers (CHIM names such as `femalenord`/`malebrute` too).
- Dashboard **Voice** page: provider choice, a **Speaker** list from PocketTTS, language, what each voice hint
  picks from, and a voice hint in the **Speak** test.
- Server 0.3.2-toi. No mod code changes; the version moves with the server release.


## 0.3.2
- **DwemerDistro launcher listing:** the server is now its own repository,
  [yucktheslime/toi-kagrenac-server](https://github.com/yucktheslime/toi-kagrenac-server), published from
  `kagrenac-server/`. It has a manifest version 2, the GitHub topic `kagrenac-mod` and stable releases.
  - It shows in **Mods > Custom mods > Browse mods** (search `Trade of Immersion`).
  - **Install server** installs it; **Install** also puts the NeoForge jar into the modpack's `mods/` folder.
  - Once installed, it is listed under **Custom mods** with update, backup and dashboard links.
- Launcher installs live at `http://127.0.0.1:19000/TradeOfImmersionServer` (database `custom_trade_of_immersion`).
  This is the new default `kagrenacBaseUrl`.
- The mod reads the launcher's private pairing file `mods/tradeofimmersion-kagrenac.json` (URL and token).
  - It is used when `kagrenacToken` and the env var are empty and `kagrenacBaseUrl` is the default.
  - On a first start with a pairing file, `aiBackend` is set to `kagrenac`.
- **Fix (security):** the 0.3.0 `deploy_toi.sh` protected `/custom-mods/trade-of-immersion` but copied the server to
  `/custom-mods/TradeOfImmersionServer`, so `config/config.php` answered HTTP 200 there.
  - The script now installs to the protected `/custom-mods/trade-of-immersion` and moves an old copy there.
  - It fails unless `config/`, `lib/`, `scripts/` and `sql/` answer 403.
- New `scripts/move_to_launcher.sh` moves a manual install's database, settings and token into the launcher install.
- Server 0.3.1-toi.

## 0.3.1
- **Optional hosted server:** Kagrenac still runs on your PC by default. A Minecraft server elsewhere (Bisect or
  any host) can now use it through an https tunnel: `kagrenacBaseUrl` accepts any http/https URL, and
  `kagrenacToken` can be set in the config (the `TOI_KAGRENAC_TOKEN` env var overrides it).
  - A remote URL requires a token (only the health check is sent without one). Plain http over the internet is
    warned about, and the token is never logged. The dashboard refuses requests that come through a tunnel.
  - Guide: [Optional: connect a hosted server](docs/KAGRENAC_SETUP.md#10-optional-connect-a-hosted-server-bisect-etc).
- **Event forwarding:** player, companion and MCA villager deaths (with the death message and place), boss kills,
  kills by companions and fights are sent to Kagrenac. Optionally ordinary mob kills too (`kagrenacLogMobKills`).
  - Sent in the background through a bounded queue (`kagrenacEventQueueSize`). They are retried with backoff
    (`kagrenacRetryMaxSeconds`) while Kagrenac is unreachable and marked "(N min ago)" when they arrive late.
  - Warnings are rate-limited to once a minute.
- **Fallback while Kagrenac is unreachable:** `kagrenacFallback` = `line` (default: a short in-character line
  now and then), `none`, `openai` (OpenRouter from the server) or `mock`.
- **Clean chat:**
  - Events never go to chat. NPC lines only reach nearby players (`npcChatRadius`) and the owner (`npcChatOwnerRadius`).
  - AI errors stay in the console (`chatShowAiErrors`), and command results are no longer echoed to other ops
    (`chatOpFeedback`).
  - The companion death notice goes to the owner only (`chatCompanionDeathNotice`).
  - `/npc ai status` is for ops (`statusForEveryone`).
- **Voice through the server:** the Minecraft server fetches the speech from Kagrenac and streams it to nearby
  players. Clients no longer need the URL or the token. `voicePlay: false` mutes it per player.

## 0.3.0
- **Kagrenac is the CHIM-style brain:** a full protocol v1 client (`aiBackend: kagrenac`, `/npc ai preset kagrenac`).
  - All 13 actions go through the allowlisted action mechanism: the server allowlist, then client validation
    (4 per reply, `target_id` must be the NPC, argument pattern), with `result.php` reports.
  - `turn.php` carries game context: location, coordinates, dimension, weather, nearby creatures, things and villagers.
  - `event.php` gets deaths, attacks and damage, gifts, villager conversations, MCA family changes and action outcomes.
  - `decision.php` picks who answers. `cancel.php` runs on interruption, and stale replies are dropped.
  - Companion and MCA villager bios (personality, profession, family) are pushed to the dashboard (`npc_bio.php`).
- **Bundled server** `kagrenac-server/`: the Kagrenac template (GPL-3.0-only) adapted for Minecraft.
  - 13 actions with arguments, extra event types, recent events in the prompt, game bios, voice hints.
  - Configured for OpenRouter Gemma; `scripts/deploy_toi.sh` installs it into DwemerDistro.
  - `scripts/smoke_minecraft.sh` tests it.
- **Voice** (off by default, `voiceEnabled`): `speak.php`/PocketTTS replies streamed positionally from the NPC on the
  Voice/Speech channel. Each NPC keeps a stable per-gender voice. Villagers are voiced too (`voiceVillagers`).
- New commands: `/npc ai preset`, `/npc ai cancel`, `/npc ai bios`. `/npc ai status` shows the Kagrenac health, the
  token state and the session.
- Guide: [docs/KAGRENAC_SETUP.md](docs/KAGRENAC_SETUP.md) (Windows/CurseForge).
- The default `kagrenacBaseUrl` is now `.../custom-mods/TradeOfImmersionServer`. Set it back if you run the plain template.
- Fix: eating reported "ate Air".

## 0.2.1
- Renamed everything to **A Trade of Immersion**: mod id `mcaplayer2` -> `tradeofimmersion`, Java package
  `dev.yucktheslime.mcaplayer2` -> `dev.yucktheslime.tradeofimmersion`, command `/mcaplayer2` -> `/toi`,
  config `config/mcaplayer2.json` -> `config/tradeofimmersion.json`, world data `data/mcaplayer2/` ->
  `data/tradeofimmersion/`, env var `MCAPLAYER2_API_KEY` -> `TOI_API_KEY`, jars `a-trade-of-immersion-<loader>-<ver>.jar`,
  repository `mca-player2-bridge` -> `a-trade-of-immersion`.
- One-time migration (copy) of the old config, skins folder and per-world data; old env var still read.
- NeoForge registry aliases `mcaplayer2:companion` / `mcaplayer2:companion_spawn_egg` -> new ids.

## 0.2.0
- Own AI companion entity (inventory, item use, riding/steering, combat, memory, item learning, villager talk);
  OpenAI-compatible / Kagrenac / mock backends. Dropped the Player2, PlayerEngine and Player2NPC dependencies.
- Display name "A Trade of Immersion".

## 0.1.0 - 0.1.3 (as "MCA-Player2 Bridge")
- Bridge between Player2NPC/PlayerEngine companions and MCA Reborn villagers: villager conversations with memory,
  owned-pet context, MCA body for companions, MCA: Female Gender Physics compat.
