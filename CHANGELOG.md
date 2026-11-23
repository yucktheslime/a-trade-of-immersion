# Changelog

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
