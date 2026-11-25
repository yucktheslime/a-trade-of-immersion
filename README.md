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
- a [Kagrenac](https://github.com/Dwemer-Dynamics/Kagrenac) server in DwemerDistro: the **CHIM-style** setup, with a
  dashboard, server-side memory and history, NPC bios, NPC selection, and spoken replies. The server is included in
  [`kagrenac-server/`](kagrenac-server/MINECRAFT.md). Setup guide: **[docs/KAGRENAC_SETUP.md](docs/KAGRENAC_SETUP.md)**.


> Status: **alpha (0.3.0)**. Tested in-game on NeoForge with the offline mock AI; 0.3.0 was also tested against the bundled Kagrenac server (mock model). See [what was verified](#verified-in-game) and the [roadmap](docs/ROADMAP.md).
> Mod id `tradeofimmersion`. Upgrading from 0.2.0? See [Upgrading](#upgrading-from-020). History: [CHANGELOG](CHANGELOG.md).

## Requirements (NeoForge 1.21.1)

| Mod | Version | Link |
|---|---|---|
| NeoForge | 21.1.267 or newer 21.1.x | https://neoforged.net/ |
| MCA Reborn | 7.7.36+1.21.1 | https://modrinth.com/mod/minecraft-comes-alive-reborn/version/S2Ln2tIn |
| *(optional)* MCA: Female Gender Physics | 0.1.8 | https://www.curseforge.com/minecraft/mc-mods/mca-female-gender-physics |
| A Trade of Immersion | `a-trade-of-immersion-neoforge-0.3.9.jar` | GitHub Actions artifact `a-trade-of-immersion-neoforge-1.21.1` |

MCA Reborn is the only other mod you have to install. Architectury API 13.0.8 is bundled inside this jar (LGPL-3.0), so you do not need a separate Architectury download. If you already have Architectury API 13.0.8 or newer in `mods/`, that is fine: the loader uses that copy and ignores the bundled one.

Use the plain jar, not `-dev-shadow` or `-sources`.

The Fabric build (`a-trade-of-immersion-fabric-0.3.9.jar`) compiles from the same code, but it is less tested. On Fabric you still need [Fabric API](https://modrinth.com/mod/fabric-api) and the Fabric MCA build. Architectury API is bundled there too; Fabric API is not, because MCA Reborn and Architectury both require it and nearly every Fabric player already has it.

## Setting up the AI

The config file is `config/tradeofimmersion.json`. It is created on first launch.
Phase 2 (marriage, pregnancy, relationships) has its own file, `config/tradeofimmersion-life.json`,
also created on first launch; `/npc life reload` rereads it.
Phase 3 job, mount, travel and item-learning switches live in `config/tradeofimmersion-skills.json` (also created on first launch).

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

### Option D: Kagrenac, the CHIM-style brain (recommended for immersion)

**Step-by-step guide for Windows and CurseForge: [docs/KAGRENAC_SETUP.md](docs/KAGRENAC_SETUP.md).**

You do not load anything extra in CurseForge. The server runs in DwemerDistro, the same local server
package CHIM uses, and is listed in the DwemerDistro launcher. In short:

1. Install DwemerDistro and run **Update Distro**.
2. In the launcher, open **Mods > Custom mods > Browse mods**, search `Trade of Immersion` and choose
   **Install server**.
   - Or choose **Install**: this also puts the mod jar in your modpack's `mods/` folder and pairs it, so no token
     setup is needed.
   - The server repository is [yucktheslime/toi-kagrenac-server](https://github.com/yucktheslime/toi-kagrenac-server).
3. On the dashboard (**Custom mods > Dashboard**, or `http://127.0.0.1:19000/TradeOfImmersionServer/ui/`), paste
   your OpenRouter key under **API keys**. The server comes set to Gemma.
4. Unless the launcher paired the mod, put the server token in the `TOI_KAGRENAC_TOKEN` environment variable.
5. In game, run `/npc ai preset kagrenac`, then `/npc ai status`.

- Manual install (fallback): `scripts/deploy_toi.sh` from the server repository. It serves the server at
  `http://127.0.0.1:19000/custom-mods/trade-of-immersion`.
- Installed by hand with 0.3.0? That copy's config was not protected. See
  [Already installed by hand](docs/KAGRENAC_SETUP.md#already-installed-by-hand-with-version-030).

What you get with the bundled server ([kagrenac-server/](kagrenac-server/MINECRAFT.md)):

- **Prompts, history and memory live on the server.** You can read and edit them on the dashboard.
- **All 13 actions:** follow, stay, attack the nearest hostile, attack a named target, go to, mount, dismount,
  equip, use/eat, give, pick up, inspect, talk to a villager.
  - The server checks every action against its allowlist, then the mod checks it again against its own
    allowlist (`kagrenacAllowedActions`).
  - The mod reports each outcome back to `result.php`.
- **Events:** deaths, attacks and damage, gifts, villager conversations, MCA family changes and action outcomes
  go to `event.php`, so NPCs can tell you "what happened".
- **NPC bios:** companion and MCA villager bios (personality, profession, family) appear on the dashboard's
  **NPC bios** page. The mod never overwrites a bio you edit there.
- **NPC selection:** when several of your companions are near and you name none, `decision.php` picks who answers.
- **Interruptions:** a new line while a reply is still coming cancels it (`cancel.php`).
- **Voice (off by default):** PocketTTS replies played from the NPC's position. Set `voiceEnabled`.
- **Plugins:** the dashboard **Plugins** page can enable bundled examples (mod item knowledge, a sample wave
  action) or install a zip / GitHub plugin. Plugins run code on the server; install only ones you trust, and
  expect to enter the server token. See [kagrenac-server/docs/PLUGINS.md](kagrenac-server/docs/PLUGINS.md).

A plain upstream Kagrenac template server also works, but only with its 3 actions and 4 event types.

**Hosted server (Bisect etc.), optional:** Kagrenac stays on your PC, and a Minecraft server at a host connects to it
through an https tunnel (Cloudflare quick tunnel or Tailscale Funnel). Set `kagrenacBaseUrl` to the tunnel URL plus
`/TradeOfImmersionServer` and `kagrenacToken` in the host's config file. Events are queued while your PC is
off, and players' chat stays clean. See
[step 10 of the guide](docs/KAGRENAC_SETUP.md#10-optional-connect-a-hosted-server-bisect-etc).

### Offline mock

`"aiBackend": "mock"` uses keyword rules, with no network. Examples: "follow me", "stay", "attack the zombie", "ride the horse", "get off", "eat something", "give me the bread", "what is this", "talk to Anna about the weather".

If the real backend fails, the mock also answers during the cooldown (`aiFallbackToMock`).

## Playing

- **Get a companion:**
  - `/npc spawn [name] [male|female|neutral] [skin]` (op);
  - or the **Companion Spawn Egg** from the Spawn Eggs tab (rename the egg in an anvil to name the companion);
  - or right-click an unowned companion to claim it.
- **Talk with the dialogue box** (0.3.8): NPC lines (companions and MCA villagers, MCA's own lines too) show in a
  box at the bottom right instead of the main chat, with the speaker's name and a fade-out.
  - **V** (or **Enter** while looking at an NPC) opens a talk field for the NPC you look at (or the last one that
    spoke). **Tab** picks the next NPC nearby, **Enter** sends, the mouse wheel scrolls back. **H** opens the
    scroll-back. In MCA's interaction screen a **Talk (AI)** button (or V) opens it for that villager.
  - **Hold B** to speak: the mod records your microphone, turns it into text with your speech-to-text service
    (Parakeet in DwemerDistro, through Kagrenac) and says it to the NPC you look at. "Listening..." and
    "Transcribing..." show in the box. All keys can be changed in **Options → Controls → A Trade of Immersion**.
  - MCA villagers answer you through this mod's AI (with their MCA personality, mood and hearts).
  - Players without the mod, or with `dialogueBox` off, keep getting NPC lines in chat.
- **Talk in chat:** normal chat still works.
  - A companion answers when you use its name ("Elara, follow me"), or when you are its owner and within 10 blocks.
  - `/npc say <npc> <text>` works from anywhere.
  - Only real dialogue reaches companions. Command feedback, advancements, death messages, typed commands and error codes are ignored.
- **Inventory:**
  - **Shift + right-click** opens their bag: 39 slots, plus a bottom row of head, chest, legs, feet, main hand, off hand.
  - **Give held item** in the menu (below) hands over what you hold. They notice the gift and may inspect it.
- **Right-click** opens the companion's menu, laid out like MCA's: **Follow / Stay / Wander**, **Inventory**,
  **Give held item**, **Edit (MCA editor)**, **Clothes**, **Hair**, **Voice**, and a line to talk to it.
  Set `companionMenuOnRightClick` to `false` for the old clicks (right-click toggles follow / stay or hands over the held item).
- **MCA's villager editor, needle and thread, and comb** work on companions. Right-click with the item (or use the
  menu buttons). Name, gender, genes, personality, traits, skin, face, clothes, hair and colors are saved for the
  companion and shown to everyone.
- **Companions look like MCA villagers** (since 0.3.7): MCA Reborn's villager model with its own genes, skin, face,
  clothes and hair, picked for its gender the way MCA does for a new villager, saved in the world and the same for
  every player. Existing companions get one the first time they load. Armor and held items render on the MCA body;
  with MCA Body Shapes installed they also get a random body shape like villagers.
  `/npc skin <npc> player` (or naming a skin) opts one out to a player skin, `/npc skin <npc> mca` switches back, and
  `/npc skin <npc> random` rolls a new MCA look.
  The owner or an operator can edit (anyone with the editor item if `mcaEditorItemForEveryone` is on).
- **Voice:** every companion and MCA villager has its own voice. Change it in the menu's **Voice** button, with
  `/npc voice ...`, or on the Kagrenac dashboard, where you can also upload voice samples one by one or as a zip
  ([Kagrenac setup, section 8](docs/KAGRENAC_SETUP.md#voices-for-each-npc-server-035-toi-mod-035)).
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
  - **Marriage and family (Phase 2).** AI companions can date, get engaged and marry players, each
    other and MCA villagers (MCA's bouquet, engagement ring, wedding ring and divorce papers work on
    them; `/npc romance` does the same). Close relatives can't pair. Affection, jealousy and neglect
    change over time and can lead to a proposal, a break-up or a divorce. Procreating starts a
    pregnancy that goes through phases (default about nine in-game days) before the baby item
    arrives; the parent gets a Pregnant effect, a slowly growing belly and configurable side effects.
    Settings live in `config/tradeofimmersion-life.json` (every feature has a switch); `/npc life
    reload` rereads it. `/npc kin`, `/npc romance status` and `/npc pregnancy status` inspect it.
- **They learn items:**
  - `inspect_item` reads the registry id, the mod name, the tooltip, food/armor/attribute stats and tags;
  - the AI can save a one-line note per item, shared by all companions in that world.
- **Jobs, mounts and learned items** (Phase 3): ask a companion to farm, chop trees, mine exposed
  blocks, dig a tunnel or stairs, bridge a gap, or fetch an item. On a long trip they look for an
  allowed horse, boat or vehicle first. They will not ride a dragon a player owns. They can learn
  how an item is used from its tooltip, and (if you press a mod key while holding it) from that
  key's packet. Settings: `config/tradeofimmersion-skills.json`. Commands: `/npcskills habits`,
  `/npcskills items`, `/npcskills mounts <npc>`, `/npcskills reload`.

### Commands

| Command | Who | What |
|---|---|---|
| `/npc spawn [name] [gender] [skin]` | op | spawn one owned by you |
| `/npc list` | all | loaded companions, owner, mode, task |
| `/npc say <npc> <text>` | owner/op | talk from anywhere |
| `/npc follow\|stay\|wander <npc>` | owner/op | movement mode |
| `/npc inventory <npc>` | owner/op | open the bag |
| `/npc skin <npc> <spec>` | owner/op | `mca` (MCA villager look, the default), `random` (new MCA look), or a player skin: `player` / `default`, `player:<name>`, `url:<https://...png>`, `file:<name.png>` (in `config/tradeofimmersion/skins/`), `slim`, `wide` |
| `/npc gender <npc> <male\|female\|neutral>` | owner/op | also drives the MCA body |
| `/npc persona <npc> <text>` | owner/op | personality given to the AI |
| `/npc memory <npc>` | owner/op | recent memories |
| `/npc do <npc> <json>` | op | run actions directly, e.g. `{"action":"mount","target":"horse"}` |
| `/npc dismiss <npc>` | owner/op | drop their items and remove them |
| `/npc ai status` / `/npc ai reload` | all / op | backend status and errors; reload the config |
| `/npc ai preset <kagrenac\|openrouter\|ollama\|mock>` | op | switch the AI backend and save the config |
| `/npc ai cancel <npc>` | owner/op | cancel the companion's pending reply (Kagrenac: `cancel.php`) |
| `/npc ai bios` | op | push companion and villager bios to the Kagrenac dashboard now |
| `/npc romance status [who]` | all | family, partner, affection and jealousy |
| `/npc romance date\|engage\|marry\|accept\|breakup <npc>` | player | courtship with an AI companion (MCA rings and a bouquet also work) |
| `/npc romance procreate <npc>` | player | start a pregnancy (must be married and close) |
| `/npc romance pair <a> <b> dating\|engaged\|married\|single` | op | set a companion pair's stage |
| `/npc romance affection <a> <b> <value>` | op | set affection (-100 to 100) |
| `/npc kin <a> <b>` | all | are these two close relatives? |
| `/npc pregnancy status [who]` | all | pregnancy phase and due time |
| `/npc pregnancy list` / `start` / `advance` / `end` | op | list or debug pregnancies |
| `/npc life reload` | op | reload `config/tradeofimmersion-life.json` |
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
| `aiProximityRange` | 64 | only NPCs this close to a player use the AI; 0 = no culling |
| `aiMaxActiveNpcs` | 8 | cap on AI-active NPCs (looked-at, recent interaction, owned companion, then closer); 0 = no cap |
| `aiMaxConcurrentRequests` | 2 | max LLM requests in flight; extras wait in a queue; 0 = no cap |
| `aiMemoryCacheSize`, `aiMemoryIdleSeconds` | 32, 120 | RAM cap for NPC memory (LRU, saved to disk) and idle unload; 0 = off |
| `aiRecentInteractionSeconds` | 45 | a conversation keeps that NPC preferred for the cap this long |
| `kagrenacBaseUrl` | `http://127.0.0.1:19000/TradeOfImmersionServer` | Kagrenac server (the launcher route; manual install: `.../custom-mods/trade-of-immersion`); any http/https URL, e.g. a tunnel to your PC for a hosted server |
| `kagrenacToken` | empty | server token; env `TOI_KAGRENAC_TOKEN` (or `KAGRENAC_TOKEN`) overrides it; when empty, the launcher pairing file `mods/tradeofimmersion-kagrenac.json` is used; required for a remote URL |
| `kagrenacSessionId` | empty | defaults to `mc-<world name>` |
| `kagrenacAllowedActions` | all 13 | the client-side action allowlist |
| `kagrenacLogEvents`, `kagrenacSyncBios`, `kagrenacUseDecision`, `kagrenacCancelOnInterrupt` | true | events, bios, NPC selection, cancel on interruption |
| `kagrenacFallback` | `line` | while Kagrenac is unreachable: `line` (short in-character line), `none`, `openai` or `mock` |
| `kagrenacEventQueueSize`, `kagrenacRetryMaxSeconds` | 200, 300 | offline event queue and longest retry pause |
| `kagrenacLogMobKills` | false | also send ordinary mobs killed by players |

**Voice (Kagrenac)**

| Key | Default | Meaning |
|---|---|---|
| `voiceEnabled` | false | server side: fetch spoken replies from Kagrenac and stream them to nearby players (needs TTS on the Kagrenac server) |
| `voicePlay` | true | per player: `false` mutes NPC voices |
| `voiceVillagers` | true | voice MCA villagers' lines too |
| `voiceVolume`, `voiceRange` | 1.0, 24 | volume and hearing distance in blocks (Voice/Speech slider applies) |

**Dialogue box and speech input (0.3.8)**

| Key | Default | Meaning |
|---|---|---|
| `dialogueBox` | true | per player: NPC lines in the dialogue box instead of chat (the server keeps using chat for players without it) |
| `mirrorToChat` | false | per player: also copy NPC lines into chat |
| `dialogueBoxAnchor`, `dialogueBoxOffsetX`, `dialogueBoxOffsetY` | `bottom_right`, 4, 56 | where the box sits: `top_left`, `top_right`, `bottom_left`, `bottom_right`, `top_center`, `bottom_center` |
| `dialogueBoxWidth`, `dialogueBoxMaxLines`, `dialogueBoxScale`, `dialogueBoxOpacity` | 260, 8, 1.0, 0.6 | size, text scale (0.5-2) and background opacity (0-1) |
| `dialogueBoxLineSeconds`, `dialogueBoxFadeSeconds`, `dialogueHistorySize` | 12, 2, 200 | how long lines stay, fade time, lines kept for scroll-back |
| `sttMode` | `server` | per player: `server` (the Minecraft server transcribes, through Kagrenac), `direct` (this PC calls `sttDirectUrl`), `off` |
| `sttDirectUrl`, `sttModel` | `http://127.0.0.1:8022/v1/audio/transcriptions`, `whisper-1` | for `direct`: any OpenAI-compatible transcription URL (Parakeet, faster-whisper-server, whisper.cpp `/inference`) |
| `sttMicDevice`, `sttMaxSeconds`, `sttAutoSend` | empty, 15, true | microphone (part of its name; empty = default), longest recording, send the transcript right away (false: put it in the talk field first) |
| `sttEnabled`, `sttUrl` | true, empty | server: accept recordings; empty `sttUrl` = Kagrenac's listen.php, or set an OpenAI-compatible URL to use without Kagrenac (key in the `TOI_STT_API_KEY` environment variable) |

`/npc stt` shows where the server sends speech and the last result. The microphone names the mod can use are
written to the log the first time you press push-to-talk.

**Chat and memory**

| Key | Default | Meaning |
|---|---|---|
| `chatHearingRadius`, `ownerChatRadius` | 16, 10 | who hears chat; `ownerChatRadius` is only used for unaddressed owner chat when `ownerChatWithoutAddress` is on |
| `chatFilterEnabled` | true | public chat only starts an AI turn when a player addresses an NPC (name, group vocative, look, recent conversation, or the dialogue box). Off: public chat never starts a turn |
| `ownerChatWithoutAddress` | false | on: an owned companion also answers the owner's nearby chat with no name or look (the old behaviour). Off: nearby chat with other players does not start a turn |
| `chatContinueSeconds` | 60 | after an AI turn, the same player can keep talking to that NPC in public chat without repeating the name, within hearing range. 0 = off |
| `aiVillagerPublicChat` | true | public chat can start an AI turn with an MCA villager addressed by name or look. The dialogue box always can |
| `aiSkipVillagersWhenMcaChatAi` | true | if MCA Reborn's own villager ChatAI is on, leave public-chat villager lines to MCA so both AIs do not answer. Dialogue-box talk still uses this mod |
| `aiNpcChatCooldownSeconds` | 2.0 | extra per-NPC cooldown for chat and dialogue-box turns (on top of `aiMinSecondsBetweenTurns` and the global per-minute cap) |
| `chatLookDegrees`, `chatLookRange` | 10, 12 | how close the crosshair must be, and how far, for a look to count as addressing an NPC |
| `npcChatRadius`, `npcChatOwnerRadius` | 24, 64 | who sees NPC lines: nearby players, and the owner from further away |
| `chatShowAiErrors`, `chatCompanionDeathNotice`, `chatOpFeedback`, `statusForEveryone` | false, true, false, false | error lines in chat, owner-only companion death notice, command echo to ops, `/npc ai status` for non-ops |
| `eventAwarenessRadius`, `reactToEvents` | 32, true | which deaths and attacks companions notice and react to |
| `maxMemoryEvents`, `memoryEventsInPrompt`, `chatLinesInPrompt` | 200, 15, 12 | memory size and how much goes into prompts |
| `maxVillagerExchanges`, `villagerReplyMaxWords` | 3, 30 | companion ↔ villager conversations |

**Behavior and looks**

| Key | Default | Meaning |
|---|---|---|
| `fleeHealthFraction`, `defendOwner`, `followTeleportDistance` | 0.3, true, 24 | behavior |
| `defaultSkin`, `companionGender` | empty, `male` | defaults for new companions |
| `companionMcaBody`, `companionAppearance` | true, `mca_generated` | MCA body on; companions use the MCA villager look (`player_skin`: new companions use a player skin instead). Server-side default; the look is saved per companion |

Saved data lives in `<world>/data/tradeofimmersion/`:

- `npcs/<uuid>.json`: memory;
- `item_knowledge.json`;
- `villagers/<uuid>.json`: villager conversation logs;
- `companion_bodies.json`.

## Verified in-game

### 0.3.0: Kagrenac

Tested with the NeoForge dev client against the bundled `kagrenac-server/`, which ran on PHP 8.4 and
PostgreSQL 17 with the server's **mock model**.

- `/npc ai status` reports the server, its schema and the 13 offered actions.
- Each of these ran, and its outcome reached `result.php`: follow, stay, mount, dismount, go_to (a failure,
  reported), pick up, equip, eat, give (a correct failure), inspect, attack a zombie, and talk to an MCA villager.
- Deaths and damage were logged, and "what happened?" was answered from them.
- Bios for the companions and an MCA villager, including family, showed up on the dashboard table.
- A second line cancelled the pending turn. NPC selection ran for an unnamed line.
- Voice came from real pocket-tts, through a small wrapper rather than DwemerDistro's PocketTTS, and played
  positionally. The audio was captured to a file, not listened to.

Not yet tested:

- a real OpenRouter model through the server;
- the real DwemerDistro/Apache install (`deploy_toi.sh`);
- `attack_hostile` on its own;
- Fabric;
- multiplayer voice.

Details: [kagrenac-server/ports/minecraft/README.md](kagrenac-server/ports/minecraft/README.md).

### 0.2.0

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
- Phase 2 marriage, pregnancy, the growing belly, MCA rings on companions, autonomous courtship and
  family-tree checks with real MCA trees need testing in a real game (and in multiplayer). The
  kinship, relationship-math and pregnancy-phase logic is unit tested.

- Mojang skin lookups can be rate-limited. The companion shows a default skin until the lookup works. URL and file skins must be 64x64.
- Minecarts and modded seats are ride-only; companions do not drive them.
- Items moved through the inventory screen are not reported as gifts. Only right-click hand-overs are.
- Charged items (bows, tridents, modded staffs) are released after a fixed 1 second charge.
- Fabric is compiled, but it is not play-tested.
- Kagrenac voice is fetched by each player's own game. In multiplayer, each player who wants voice needs the
  Kagrenac URL and token in their own client config.
- No speech input yet (typing only); it is on the roadmap.

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

This mod bundles [Architectury API](https://github.com/architectury/architectury-api) 13.0.8, also licensed under LGPL-3.0. The bundled copy is a separate nested jar, so you can replace it by installing a compatible Architectury API jar yourself. Source: https://github.com/architectury/architectury-api
