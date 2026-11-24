# Changelog

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
  server token. Zips are checked for path traversal, links, encryption and size.
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
