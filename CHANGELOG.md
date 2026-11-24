# Changelog

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
  - Guide: [Optional: connect a hosted server](docs/KAGRENAC_SETUP.md#9-optional-connect-a-hosted-server-bisect-etc).
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
