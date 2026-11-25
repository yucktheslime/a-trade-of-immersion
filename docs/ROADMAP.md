# A Trade of Immersion - Roadmap

The goal: an MCA Reborn addon NPC that can do what a player does and feels alive, in the spirit of
Mantella / CHIM for Skyrim. The AI runs on any OpenAI-compatible endpoint (free local Ollama, OpenRouter,
LM Studio, ...) or a [Kagrenac](https://github.com/Dwemer-Dynamics/Kagrenac) server. No paid service is required.

Status legend: **done** = in the current release and tested in-game, **partial** = works with known limits,
**planned** = not built yet.

## Phase 1 - a real companion (0.2.0)

| Feature | Status | Notes |
|---|---|---|
| Own companion entity (`tradeofimmersion:companion`), owner, follow/stay/wander, saved with the world | done | `/npc spawn`, spawn egg, right-click an unowned one to claim it |
| MCA body (breasts, genes) + MCA: Female Gender Physics compat | done | `/toi gender/body` still work |
| Skins: Mojang player name, URL, local file, default | done | `/npc skin <name> player:Notch \| url:https://... \| file:x.png \| slim \| wide` |
| Player-sized inventory + armor + both hands; shift-right-click to open | done | right-click with an item to hand it over |
| Equip / eat / use items, including other mods' items, via a fake player | done | `useItemOn`, `useItem`, `interactOn`, charged items released after 1 s |
| Mount / dismount any rideable entity (horse, boat, minecart, pig, modded dragons, seats) | done | forced seating when a vehicle refuses mob riders but has a free seat |
| Steer horses / mob mounts (navigation) and boats (server-side paddling) | done | minecarts and seats are ride-only |
| Combat: attack named target or nearest hostile, defend owner, flee on low health, auto-eat | done | |
| Memory: deaths, attacks, damage, gifts, heard chat, action results; per NPC, in the prompt | done | `<world>/data/tradeofimmersion/npcs/<uuid>.json`, `/npc memory` |
| Item learning: `inspect_item` (id, mod, tooltip, stats, tags) + AI notes, shared knowledge file | done | `<world>/data/tradeofimmersion/item_knowledge.json` |
| AI loop: chat by name or owner nearby, JSON actions from an allowlist, rate limits, failure cooldown + offline fallback | done | backends: `openai`, `kagrenac`, `mock` |
| Companion <-> MCA villager conversations through the same backend | done | uses MCA personality, mood, profession, hearts |
| Basic chat filter: only real player dialogue (no command output, advancements, error codes) | done | system messages never reach the chat hook; typed commands/codes are dropped |

## 0.3.0 - Kagrenac, the CHIM-style brain

| Feature | Status | Notes |
|---|---|---|
| Kagrenac protocol v1 client: turns with game context, events, results, cancel, decision | done | tested against the bundled server with its mock model |
| Bundled server `kagrenac-server/` (13 actions, game events, bios, voice hints) for DwemerDistro | partial | tested on php -S + PostgreSQL; the real DwemerDistro install is not tested yet |
| Companion + MCA villager bios (personality, profession, family) on the dashboard | done | game bios never overwrite operator edits |
| Positional NPC voice through speak.php / PocketTTS | done | off by default; tested with pocket-tts behind a wrapper, captured to a file |
| Speech input (push-to-talk) | done (0.3.8) | hold B: the microphone goes through the Minecraft server to Kagrenac `listen.php` (Parakeet); the transcript is said to the NPC, or put in the talk field (`sttAutoSend: false`) |
| Dedicated NPC dialogue box | done (0.3.8) | companion and MCA villager lines out of the main chat; talk field (V), scroll-back (H), Talk button in MCA's screen |
| Real-model tuning (OpenRouter Gemma prompts for the 13 actions) | planned | needs play-testing with a key |

## Phase 2 - MCA relationships and family (next)

- Hook MCA Reborn relationship/family events (courting, engagement, marriage, births, divorce, deaths of
  family) and log them as milestones in companion and villager memory, fed to the AI.
- Companions form relationships with MCA villagers and each other: hearts, gifts, dates; they can
  marry and have kids with other NPCs autonomously (through MCA's own marriage/procreation systems).
- Expanded MCA-style interaction UI for companions (talk, gift, ask, relationship view) instead of
  plain chat commands.
- Dedicated NPC chat box UI (MCA-style dialogue window) so conversations don't clutter the main chat.
- Smarter chat filtering on top of the phase 1 filter (per-player opt-out, "is this addressed to me" model check).

## Phase 3 - growing up: aging and best friends

- Aging for players and NPCs: child, teen and adult stages that advance over in-game days.
- "Start as a child" choice in MCA's opening (destiny) menu.
- Child debuffs: smaller size, less max health, weaker attacks, slower mining.
- Each age-up removes debuffs and grants a permanent perk (extra health, faster XP, better trades),
  so starting as a child ends up stronger than starting as an adult. Possible RPG perk tree on top.
- Kid friendships: child NPCs remember how you treat them; some dislike you for random
  personality reasons, and those who stick around long enough become **Best Friends**.
- Best Friends buff: being near a best friend gives a buff (e.g. Strength or Resistance); the bond
  carries over as both grow up.
- NPCs (companions and MCA villagers) age with the same system.

## Phase 4 - scale: proximity culling and the "social battery" cap

- Proximity culling: companions far from every player go dormant (no AI turns, active memory unloaded
  to disk) and wake up when a player is back in range. **partial** — `AiBudget` (`aiProximityRange`,
  default 64) gates LLM calls and heavy AI ticks; ordinary walk/follow/fight still runs. Memory is
  persisted and evicted on unload, idle (`aiMemoryIdleSeconds`) or LRU cap (`aiMemoryCacheSize`).
- Active AI NPC cap: at most `aiMaxActiveNpcs` (default 8) are AI-active, by look / recent interaction
  / ownership / distance; others wait. LLM requests are also capped by `aiMaxConcurrentRequests`
  (default 2) with a FIFO queue. Other phases should call `AiBudget.isActive(entity)`.
- Off-screen lives: while dormant, a cheap summary step advances their story (where they went, what
  happened) so they tell a consistent story on reunion. **planned** (not in this slice).
- Language-barrier flavour (canned/vanilla MCA lines for NPCs past the cap) **planned**.

## Phase 5 - jobs and modpack roles

- Mining missions: "get me 20 iron", ore finding, tunnelling, returning with the haul.
- Camp management: campfires, cooking, sleeping, gear repair and upkeep, restocking food.
- Tech/magic modpack roles: run automation (feed machines, empty outputs), tend mystical crops,
  use modded tools and staffs with learned item knowledge.
- Building from simple plans; farming; trading with villagers on the owner's behalf.

## Phase 6 - adaptive learning

- Learn from past mistakes: summarize failed actions and bad outcomes ("the creeper blew up my boat")
  into lessons stored per NPC and retrieved when a similar situation comes up, so failures are not repeated.
- Long-term memory compaction (daily diary summaries) and retrieval by relevance instead of only "last N events".

## Known limits (0.3.0)

- Mojang skin lookups are rate-limited; a companion shows a default skin until the lookup succeeds.
- Fabric builds and compiles but is less tested than NeoForge.
- Items moved through the inventory screen are not reported as "gifts" (only right-click hand-overs are).
- A plain upstream Kagrenac template only offers its three built-in actions; the bundled `kagrenac-server/`
  offers all 13.
