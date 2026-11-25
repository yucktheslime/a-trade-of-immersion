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
| Bundled server `kagrenac-server/` (Minecraft actions, game events, bios, voice hints) for DwemerDistro | partial | tested on php -S + PostgreSQL; the real DwemerDistro install is not tested yet |
| Companion + MCA villager bios (personality, profession, family) on the dashboard | done | game bios never overwrite operator edits |
| Positional NPC voice through speak.php / PocketTTS | done | off by default; tested with pocket-tts behind a wrapper, captured to a file |
| Speech input (push-to-talk) | done (0.3.8) | hold B: the microphone goes through the Minecraft server to Kagrenac `listen.php` (Parakeet); the transcript is said to the NPC, or put in the talk field (`sttAutoSend: false`) |
| Dedicated NPC dialogue box | done (0.3.8) | companion and MCA villager lines out of the main chat; talk field (V), scroll-back (H), Talk button in MCA's screen |
| Real-model tuning (OpenRouter Gemma prompts for the 13 actions) | planned | needs play-testing with a key |

## Phase 2 - MCA relationships and family

| Feature | Status | Notes |
|---|---|---|
| MCA life hooks and memory (marriage, engagement, birth, pregnancy, death, gifts, mood, hearts) | partial | MCA fires no events, so a snapshot watcher (villagers near players) plus mixins (`BabyItem`, `Pregnancy`, `giveGift`) detect changes; they go into NPC memory and prompts. Needs in-game testing. |
| Pregnancy with phases instead of an instant baby item | partial | Hooks MCA's procreate path; default 3 phases of 3 in-game days; baby item (or MCA child spawn) at birth; Pregnant effect, growing belly, side effects, miscarriage toggle. Config: `config/tradeofimmersion-life.json`. Unit-tested state machine. Needs in-game testing. |
| Companion marriage in all four directions, reusing MCA's family tree | partial | Bouquet / rings / divorce papers on companions; `/npc romance`; dating → engaged → married (`courtshipStages`). MCA already handles player-villager. Needs in-game testing. |
| Dynamic relationships (affection, jealousy, drift) | partial | Drives proposals, break-ups, divorce and dialogue. Unit-tested math. Autonomous NPC courtship is in; a dedicated relationship UI is still planned. |
| Family-tree restriction (siblings, parents, grandparents, aunts/uncles, cousins) | done (logic) | Reuses MCA's tree, extended to companions and cousins; `kinBlockDepth` (default 2). Unit tested. Ring-path still needs in-game testing. |
| Dedicated NPC chat box; smarter chat filter | done | Shipped in 0.3.8 and Phase 1 (`chatfilter`), not this slice. |
| Expanded MCA-style interaction UI for companions | planned | Commands and MCA rings work; no extra relationship screen yet. |

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

- Farming, chopping natural trees, fetching items (ground or a nearby chest), and bringing them to
  the player. **partial** — `farm` / `chop` / `fetch` jobs; auto-help when the player does this often
  (`habitLearning`, gated by `AiBudget.isActive`).
- Mining and tunnelling: mine exposed blocks of one kind; dig a straight 1x2 tunnel or staircase, or
  bridge a gap with blocks from the bag, stopping at lava, chests, machines or unbreakable blocks.
  **partial** — buried ore is not dug out; vanilla pathfinding still cannot plan a mine or build
  complex bridges. `miningEnabled` / `bridgingEnabled` switches.
- Mount and vehicle control with ownership rules (never a player-owned dragon; wild dragons and
  player-owned tamed animals / bikes / cars may be ridden). Long trips (default 150 blocks) look for
  an allowed ride first. **partial** — vanilla mounts, boats and minecarts steer; vehicle-mod adapters
  are generic stubs until a real per-mod adapter is registered.
- Item learning from tooltips ("right-click to…", "press V to…") and keybind observation/replay.
  **partial** — right-click via the fake player works generally; key replay depends on the other mod
  accepting a fake player as sender. Failed ways are marked unusable.
- Settings live in `config/tradeofimmersion-skills.json`. `/npcskills` inspects habits, learned
  items and mount decisions.
- Camp management: campfires, cooking, sleeping, gear repair and upkeep, restocking food. **planned**
- Tech/magic modpack roles: run automation (feed machines, empty outputs), tend mystical crops,
  use modded tools and staffs with learned item knowledge. **planned** (use_ability is the hook).
- Building from plans; trading with villagers on the owner's behalf. **planned**

## Phase 6 - adaptive learning

- Learn from past mistakes: summarize failed actions and bad outcomes ("the creeper blew up my boat")
  into lessons stored per NPC and retrieved when a similar situation comes up, so failures are not repeated.
- Long-term memory compaction (daily diary summaries) and retrieval by relevance instead of only "last N events".

## Known limits (0.3.0)

- Mojang skin lookups are rate-limited; a companion shows a default skin until the lookup succeeds.
- Fabric builds and compiles but is less tested than NeoForge.
- Items moved through the inventory screen are not reported as "gifts" (only right-click hand-overs are).
- A plain upstream Kagrenac template only offers its three built-in actions; the bundled `kagrenac-server/`
  offers the full Minecraft action catalog (jobs, travel and item abilities included).
