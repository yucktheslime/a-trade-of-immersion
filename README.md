# A Trade of Immersion

A **NeoForge** (primary) and Fabric mod for **Minecraft 1.21.1** that adds AI companions to
**Minecraft Comes Alive (MCA) Reborn**. These are player-like NPCs that:

- carry a real inventory, wear armor, eat, and use items, including other mods' items;
- ride horses, boats, minecarts, pigs, modded mounts and seats, and steer horses and boats;
- fight, defend you, and flee when hurt;
- remember what happens around them (deaths, attacks, gifts, conversations);
- learn what items do;
- talk with you and with MCA villagers.

Think Mantella / CHIM, but for MCA.

The AI is **your choice and can be free**:

- a local model through [Ollama](https://ollama.com);
- any OpenAI-compatible API (OpenRouter, LM Studio, llama.cpp, vLLM, ...);
- a [Kagrenac](https://github.com/Dwemer-Dynamics/Kagrenac) server.


> Status: **alpha (0.2.0)**. Tested in-game on NeoForge with the offline mock AI. See [what was verified](#verified-in-game-020) and the [roadmap](docs/ROADMAP.md).
> Mod id `tradeofimmersion`. Upgrading from 0.2.0? See [Upgrading](#upgrading-from-020). History: [CHANGELOG](CHANGELOG.md).

## Requirements (NeoForge 1.21.1)

| Mod | Version | Link |
|---|---|---|
| NeoForge | 21.1.267 or newer 21.1.x | https://neoforged.net/ |
| Architectury API | 13.0.8 or newer | https://modrinth.com/mod/architectury-api |
| MCA Reborn | 7.7.36+1.21.1 | https://modrinth.com/mod/minecraft-comes-alive-reborn/version/S2Ln2tIn |
| *(optional)* MCA: Female Gender Physics | 0.1.8 | https://www.curseforge.com/minecraft/mc-mods/mca-female-gender-physics |
| A Trade of Immersion | `a-trade-of-immersion-neoforge-0.2.0.jar` | GitHub Actions artifact `a-trade-of-immersion-neoforge-1.21.1` |

Use the plain jar, not `-dev-shadow` or `-sources`.

The Fabric build (`a-trade-of-immersion-fabric-0.2.0.jar`, which needs Fabric API and the Fabric MCA build) compiles from the same code, but it is less tested.

## Setting up the AI

The config file is `config/tradeofimmersion.json`. It is created on first launch.

After editing it, run `/npc ai reload`, then `/npc ai status` to check it.

**Never commit or share your API key.** Put it in an environment variable rather than the config file if you can.

### Option A: free and local, with Ollama (default)

1. Install Ollama from https://ollama.com.
2. Run `ollama pull gemma3:4b`. That takes about 3 GB; use `gemma3:12b` instead if your GPU has room.
3. Keep Ollama running. The default config already points at `http://localhost:11434/v1` with model `gemma3:4b`:

```json
"aiBackend": "openai",
"aiPreset": "ollama"
```

### Option B: OpenRouter (cloud, has free models)

1. Make a key at https://openrouter.ai/keys.
2. Set it as an environment variable before launching the game or server:
   - Windows: `setx TOI_API_KEY "sk-or-..."`, then restart the launcher.
   - Linux / macOS: `export TOI_API_KEY=sk-or-...`
   - `OPENROUTER_API_KEY` also works.
3. In the config, set:

```json
"aiBackend": "openai",
"aiPreset": "openrouter",
"aiModel": "google/gemma-3-27b-it:free"
```

`aiApiKey` in the config also works, but it is stored in plain text.

### Option C: any OpenAI-compatible server

Set `aiBaseUrl` (for example `http://localhost:1234/v1` for LM Studio), `aiModel`, and, if the server needs one, a key.

### Option D: Kagrenac

Run a Kagrenac server (https://github.com/Dwemer-Dynamics/Kagrenac). Then set:

```json
"aiBackend": "kagrenac",
"kagrenacBaseUrl": "http://127.0.0.1:19000/custom-mods/KagrenacServer"
```

Put the token in the `KAGRENAC_TOKEN` environment variable, or in `kagrenacToken`.

- The mod uses protocol v1: `turn.php`, and `event.php` for gifts and combat.
- It offers Kagrenac's three actions (`follow_player`, `hold_position`, `attack_hostile`).
- The server keeps the NPC bios and history. The full action set needs Option A, B or C.

### Offline mock

`"aiBackend": "mock"` uses keyword rules, with no network. Examples: "follow me", "stay", "attack the zombie", "ride the horse", "get off", "eat something", "give me the bread", "what is this", "talk to Anna about the weather".

If the real backend fails, the mock also answers during the cooldown (`aiFallbackToMock`).

## Playing

- **Get a companion:**
  - `/npc spawn [name] [male|female|neutral] [skin]` (op);
  - or the **Companion Spawn Egg** from the Spawn Eggs tab (rename the egg in an anvil to name the companion);
  - or right-click an unowned companion to claim it.
- **Talk:** type in normal chat.
  - A companion answers when you use its name ("Elara, follow me"), or when you are its owner and within 10 blocks.
  - `/npc say <npc> <text>` works from anywhere.
  - Only real dialogue reaches companions. Command feedback, advancements, death messages, typed commands and error codes are ignored.
- **Inventory:**
  - **Shift + right-click** opens their bag: 39 slots, plus a bottom row of head, chest, legs, feet, main hand, off hand.
  - Right-click while holding an item to hand it over. They notice the gift and may inspect it.
- **Right-click with an empty hand** toggles follow / stay.
- **Ask them to do things.** The AI picks from these actions:
  - `follow`, `stay`
  - `goto`
  - `mount`, `dismount`
  - `attack`
  - `equip`, `use_item`, `give_item`
  - `pick_up`
  - `inspect_item`
  - `talk_to_villager`

  Every action is validated server-side, with at most 4 per reply.
- **They remember:**
  - nearby deaths, who attacked whom, damage they took, gifts, what was said, and how their own actions turned out;
  - `/npc memory <npc>` shows the most recent memories.
- **They learn items:**
  - `inspect_item` reads the registry id, the mod name, the tooltip, food/armor/attribute stats and tags;
  - the AI can save a one-line note per item, shared by all companions in that world.

### Commands

| Command | Who | What |
|---|---|---|
| `/npc spawn [name] [gender] [skin]` | op | spawn one owned by you |
| `/npc list` | all | loaded companions, owner, mode, task |
| `/npc say <npc> <text>` | owner/op | talk from anywhere |
| `/npc follow\|stay\|wander <npc>` | owner/op | movement mode |
| `/npc inventory <npc>` | owner/op | open the bag |
| `/npc skin <npc> <spec>` | owner/op | `player:<name>`, `url:<https://...png>`, `file:<name.png>` (in `config/tradeofimmersion/skins/`), `default`, `slim`, `wide` |
| `/npc gender <npc> <male\|female\|neutral>` | owner/op | also drives the MCA body |
| `/npc persona <npc> <text>` | owner/op | personality given to the AI |
| `/npc memory <npc>` | owner/op | recent memories |
| `/npc do <npc> <json>` | op | run actions directly, e.g. `{"action":"mount","target":"horse"}` |
| `/npc dismiss <npc>` | owner/op | drop their items and remove them |
| `/npc ai status` / `/npc ai reload` | all / op | backend status and errors; reload the config |
| `/toi gender\|body <npc> ...` | op | MCA body traits (size, width, breast, skin genes, ...); the mcabp traits too when installed |

## Config (main keys)

**AI backend**

| Key | Default | Meaning |
|---|---|---|
| `aiBackend` | `openai` | `openai`, `kagrenac` or `mock` |
| `aiPreset` | `ollama` | `ollama` or `openrouter`; fills in the URL and model when those are empty |
| `aiBaseUrl`, `aiModel`, `aiApiKey` | empty | override the preset (prefer the env var for the key) |
| `aiTemperature`, `aiMaxTokens`, `aiTimeoutSeconds` | 0.7, 350, 45 | |
| `aiMaxRequestsPerMinute` | 20 | global cap on AI calls |
| `aiMinSecondsBetweenTurns` | 2 | per-companion gap; newer triggers wait, latest wins |
| `aiFallbackToMock`, `aiFailureCooldownSeconds` | true, 60 | behavior when the backend is down |
| `kagrenacBaseUrl`, `kagrenacToken`, `kagrenacSessionId` | | for Kagrenac; the session defaults to `mc-<world name>` |

**Chat and memory**

| Key | Default | Meaning |
|---|---|---|
| `chatHearingRadius`, `ownerChatRadius` | 16, 10 | who hears chat |
| `eventAwarenessRadius`, `reactToEvents` | 32, true | which deaths and attacks companions notice and react to |
| `maxMemoryEvents`, `memoryEventsInPrompt`, `chatLinesInPrompt` | 200, 15, 12 | memory size and how much goes into prompts |
| `maxVillagerExchanges`, `villagerReplyMaxWords` | 3, 30 | companion ↔ villager conversations |

**Behavior and looks**

| Key | Default | Meaning |
|---|---|---|
| `fleeHealthFraction`, `defendOwner`, `followTeleportDistance` | 0.3, true, 24 | behavior |
| `defaultSkin`, `companionGender` | empty, `male` | defaults for new companions |
| `companionMcaBody`, `companionAppearance` | true, `player_skin` | MCA body on; `mca_generated` gives an MCA villager look instead of a skin |

Saved data lives in `<world>/data/tradeofimmersion/`:

- `npcs/<uuid>.json`: memory;
- `item_knowledge.json`;
- `villagers/<uuid>.json`: villager conversation logs;
- `companion_bodies.json`.

## Verified in-game (0.2.0)

Tested on NeoForge 21.1.267, MCA 7.7.36 and MCA: Female Gender Physics, using the mock backend. Screenshots are in the release folder.

- Spawn with a name and gender. Default skin, the MCA body, and the name tag render correctly. A companion persists and stays mounted across a world reload.
- Gifts: right-click hand-over makes the companion react ("Thank you") and run `inspect_item`. It learns the item, and the knowledge file is written.
- The shift-right-click inventory works: bag, armor and hands, with items moving both ways. `equip` puts the sword in hand and the chestplate on.
- Mount and steer:
  - mounting a saddled horse works, and the companion rides it to the owner;
  - mounting a boat works, and the companion paddles it across water to the owner;
  - `dismount` works.
- Combat: "attack the zombie" kills it. Damage taken and the kill are recorded in memory, and the companion auto-eats when hurt.
- Item use through the fake player works: shears on a sheep (sheared), flint and steel on a block (fire). `give_item` hands bread to the player.
- Talking to an MCA villager works: the villager answers through the backend, using its MCA profile, and the line goes into both memories.
- A dedicated server starts cleanly; client-only classes do not load on it.

Not tested in-game with a live LLM: real Ollama/OpenRouter output depends on the model. JSON parsing is lenient, falls back to plain speech, and is covered by the mock path. Kagrenac is implemented from PROTOCOL.md v1, but it was not tested against a running server.

## Known limitations

- Mojang skin lookups can be rate-limited. The companion shows a default skin until the lookup works. URL and file skins must be 64x64.
- Minecarts and modded seats are ride-only; companions do not drive them.
- Items moved through the inventory screen are not reported as gifts. Only right-click hand-overs are.
- Charged items (bows, tridents, modded staffs) are released after a fixed 1 second charge.
- Fabric is compiled, but it is not play-tested.

## Upgrading from 0.2.0

0.2.1 renamed every identifier: mod id `tradeofimmersion`, command `/toi` (was the old mod id), config
`config/tradeofimmersion.json`, world data `<world>/data/tradeofimmersion/`, env var `TOI_API_KEY`. On first start:

- the old config file and `config/<old id>/` skins folder are copied to the new names if the new ones don't exist;
- each world's old `data/<old id>/` folder (memories, item knowledge, villager logs, body edits) is copied the same way;
- the old API key environment variable is still read (with a warning) when `TOI_API_KEY` is not set;
- on **NeoForge**, registry aliases map the old entity and spawn egg ids to the new ones, so companions saved by 0.2.0 load normally.
  On **Fabric** there is no alias: companions saved by 0.2.0 are dropped when their chunk loads (re-spawn them with `/npc spawn`).

Old files are copied, never deleted. See [CHANGELOG](CHANGELOG.md) for the exact old names.

## Building

```
./gradlew build
```

Output:

- `neoforge/build/libs/a-trade-of-immersion-neoforge-<version>.jar`
- `fabric/build/libs/a-trade-of-immersion-fabric-<version>.jar`

Add `-PnoMcabp` to run without the optional addon in dev.

## License

LGPL-3.0-or-later
