# MCA-Player2 Bridge

A **NeoForge** (primary) and Fabric compatibility mod for **Minecraft 1.21.1** that lets **Player2 AI companions** (Player2NPC / PlayerEngine) detect, approach, and talk with **Minecraft Comes Alive (MCA) Reborn** villagers. Villagers talk back, and both sides remember past conversations.

> Status: **early alpha (0.1.0)**. It compiles against the real dependency jars (local build + CI), but it has **not been play-tested in game yet**. See [Known limitations](#known-limitations).

## Why

- Player2 companions only respond to the human player's chat. They don't notice or address other entities.
- MCA villagers use template-based dialogue and only track relationship (hearts) scores, with no per-NPC conversation memory.

This bridge connects the two in both directions.

## Features

| | What it does | Where |
|---|---|---|
| Companion → villager goal | `talk_to_villager <name\|uuid>` is a PlayerEngine command the companion's LLM can call. It runs `TalkToMcaVillagerGoal`, a PlayerEngine `Task` that paths to the villager with PlayerEngine's `GetToEntityTask` (the Automatone/Baritone pathfinder), stays in range, faces it, and opens the conversation. | `goal/`, `command/` |
| Prompt injection | When a conversation opens, the villager's MCA data (name, gender, age stage, profession, personality, mood, traits, hearts with the companion's owner) and the remembered exchanges are sent into the companion's Player2 conversation as a PlayerEngine `Event.InfoMessage`. | `bridge/ConversationBridge`, `player2/CompanionAdapter` |
| `nearby_villagers` | A second LLM-callable command that lists nearby MCA villagers with their profiles, so the companion knows who it can talk to. | `command/` |
| Villagers start conversations | A server-tick listener notices companions within `detectionRadius` of an MCA villager. The villager greets the companion through MCA's own villager chat (`Messenger#sendChatToAllAround`), then turns its head to face the companion. | `bridge/ConversationBridge` |
| Villagers answer | A mixin captures every line a companion speaks. If a nearby villager is addressed by name, or is already talking with that companion, it answers. The answer is generated from the companion's words, the villager's MCA persona, and the shared memory. It goes out through MCA chat and comes back to the companion as an incoming message, so the companion's LLM replies. | `mixin/`, `bridge/VillagerReplyGenerator` |
| Memory | Each villager gets a JSON conversation log in the world save, which both sides read across sessions. | `memory/ConversationMemoryStore` |
| Tamed companions / owned animals | When a conversation opens, nearby living entities are checked for an owner UUID. Each owner is resolved to an MCA villager (or a player), and the companion is told things like *This wolf "Rex" is a tamed companion belonging to the villager Alice.* Villagers are told about their own tamed companions nearby. This works with vanilla tameables and with modded pets through pluggable resolvers. | `util/OwnedEntityUtil`, `util/OwnerUuidResolver`, `context/` |
| Loop guard | Each companion/villager pair is limited to `maxTurnsPerConversation` villager lines per conversation, and greetings have a cooldown, so two AIs can't chat forever. | `bridge/ConversationBridge` |

## Requirements

1. **The Player2 desktop app must be installed, running, and signed in** on the machine running Minecraft (https://player2.game). Your account needs available credit. Player2NPC/PlayerEngine use it for the companion's LLM and TTS. By default, villager replies are also generated through PlayerEngine's Player2 client and billed to the companion's owner, so each villager line costs one extra LLM request.
2. Mods for **NeoForge 1.21.1**. These are the exact versions the bridge is built against:

| Mod | Version | Link |
|---|---|---|
| NeoForge | 21.1.267 or newer 21.1.x | https://neoforged.net/ |
| Architectury API (NeoForge) | 13.0.8 or newer | https://modrinth.com/mod/architectury-api |
| PlayerEngine (NeoForge) | 1.21.1-1.4.0-NeoForge | https://modrinth.com/mod/playerengine/version/9cN9nlTk |
| Player2NPC (NeoForge) | 1.21.1-1.4.0-NeoForge | https://modrinth.com/mod/player2npc/version/6hUQg8ap |
| MCA Reborn (NeoForge) | 7.7.36+1.21.1 | https://modrinth.com/mod/minecraft-comes-alive-reborn/version/S2Ln2tIn |
| MCA-Player2 Bridge | `mca-player2-bridge-neoforge-<version>.jar` | GitHub Actions artifact `mca-player2-bridge-neoforge-1.21.1` / `neoforge/build/libs` |

<details><summary>Fabric (secondary build)</summary>

| Mod | Version | Link |
|---|---|---|
| Fabric Loader | 0.17.2 or newer (latest recommended) | https://fabricmc.net/use/ |
| Fabric API | 0.116.18+1.21.1 | https://modrinth.com/mod/fabric-api |
| Architectury API (Fabric) | 13.0.8 or newer | https://modrinth.com/mod/architectury-api |
| PlayerEngine (Fabric) | 1.21.1-1.4.0-Fabric | https://modrinth.com/mod/playerengine/version/msqQbeg3 |
| Player2NPC (Fabric) | 1.21.1-1.4.0-Fabric | https://modrinth.com/mod/player2npc/version/QcQZkwnO |
| MCA Reborn (Fabric) | 7.7.36+1.21.1 | https://modrinth.com/mod/minecraft-comes-alive-reborn/version/eUYBilCG |
| MCA-Player2 Bridge | `mca-player2-bridge-fabric-<version>.jar` | artifact `mca-player2-bridge-fabric-1.21.1` |

</details>

## Setup (NeoForge)

1. Install NeoForge for 1.21.1 (21.1.267 or newer) with the NeoForge installer, then start the game once so the `mods/` folder is created.
2. Put these jars into `mods/`: Architectury API, PlayerEngine, Player2NPC, MCA Reborn (all NeoForge builds from the table above), and `mca-player2-bridge-neoforge-<version>.jar`. Use the plain jar, not `-dev-shadow` or `-sources`.
3. Start the Player2 app and sign in.
4. Launch the game, spawn a Player2NPC companion, and walk into an MCA village.
5. Try either of these:
   - Walk near a villager. Within `detectionRadius` (8 blocks by default), the villager greets your companion and the companion answers.
   - Tell your companion something like *"go talk to Alice"*. Its LLM should call `talk_to_villager Alice`.
6. Conversation logs are saved to `<world>/data/mcaplayer2/villagers/<villager-uuid>.json`.

Fabric setup is the same, using the Fabric jars and Fabric API.

## Config

The config is at `config/mcaplayer2.json` and is created on first launch.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `detectionRadius` | `8.0` | Blocks within which villagers notice and hear companions |
| `scanIntervalTicks` | `40` | How often proximity is checked |
| `greetCooldownTicks` | `12000` | Minimum time between greetings for the same pair |
| `maxTurnsPerConversation` | `4` | Villager lines per conversation before it stops answering |
| `conversationResetTicks` | `2400` | Silence after which a conversation ends and the turn counter resets |
| `talkArrivalDistance` | `3.0` | How close the talk goal walks to the villager |
| `talkStayInRangeDistance` | `5.0` | The goal re-paths if the villager moves farther away than this |
| `talkIdleTimeoutTicks` | `600` | The talk goal ends after this much silence |
| `talkMaxDurationTicks` | `2400` | Hard cap on the talk goal |
| `villagerLooksAtCompanion` | `true` | Villager turns its head to face the companion during a conversation |
| `villagerReplyBackend` | `playerengine` | Where villager lines come from. `playerengine` goes through PlayerEngine's own Player2 client and is billed to the companion's owner, the same way as the companion. `local_app` calls the Player2 desktop app directly at `player2ApiUrl`. `canned` uses built-in lines and no AI. |
| `billingFailureCooldownSeconds` | `300` | After a billing, auth or quota failure, pause all bridge-triggered AI requests for this long |
| `maxBridgeCompanionTurnsPerMinute` | `6` | Global cap on companion AI turns the bridge triggers (0 = unlimited) |
| `useLlmForVillagerReplies` | `true` | Use the Player2 app for villager lines. When off or unreachable, canned lines are used. |
| `player2ApiUrl` | `http://127.0.0.1:4315` | Player2 app local API (only used with `local_app`) |
| `player2GameKey` | `mca-player2-bridge` | Value of the `player2-game-key` header |
| `llmTimeoutSeconds` | `20` | HTTP timeout for villager replies |
| `villagerReplyMaxWords` | `30` | Length limit for villager replies |
| `detectOwnedCompanions` | `true` | Detect tamed companions / owned animals and describe their owners |
| `ownedCompanionScanRadius` | `12.0` | Scan radius for owned entities |
| `maxOwnedEntitiesInPrompt` | `6` | Max owned entities described per prompt |
| `ownerNbtFallback` | `true` | Check saved NBT for owner UUIDs when no resolver matches |
| `ownerNbtKeys` | `Owner, OwnerUUID, owner, ownerUUID, OwnerUuid, ownerUuid` | NBT keys checked, at the top level and one compound deep |
| `ownerAccessorMethods` | `[]` | No-arg methods returning a separate pet-data object that holds the owner (for mods that store ownership outside the entity) |
| `guessOwnerFromNearestVillager` | `false` | Attribute an unresolved owner to the nearest villager, marked as a guess |
| `mentionUnknownOwners` | `true` | Mention owned animals whose owner can't be found |
| `maxLogEntries` | `100` | Entries kept per villager log |
| `historyLinesInPrompt` | `10` | Past lines injected into prompts |

## Tamed companions and owned animals

`OwnedEntityUtil.ownerOf(entity)` finds an owner UUID without referencing any specific mod. It tries these in order and caches each result for 10 seconds:

1. **Vanilla `OwnableEntity#getOwnerUUID()`.** Covers wolves, cats, parrots, horses, and any modded mob that implements it.
2. **Reflection.** Looks for a public no-arg method returning `UUID` named `getOwnerUUID`, `getOwnerUuid`, `getOwnerId` or `getOwner`, on the entity or on an object returned by one of the `ownerAccessorMethods` in the config.
3. **Extra resolvers registered by other code.** Use `OwnedEntityUtil.registerResolver(OwnerUuidResolver)`.
4. **NBT fallback.** Reads the entity's saved data and looks for an int-array or string UUID under `ownerNbtKeys`.

Owners are then resolved in this order:

1. An exact UUID match against loaded MCA villagers.
2. An online player.
3. Optionally, the nearest villager, worded as a guess.
4. Otherwise, "owner not around".

The resulting lines go through `context/PromptContextProvider`, the same injection layer that adds MCA villager context when `TalkToMcaVillagerGoal` or a proximity greeting opens a conversation. You can register more providers with `PromptContextProvider.register(...)`.

Prompts only use generic wording: *tamed companion*, *owned animal*, *companion*. Note that MCA villagers don't tame animals by themselves, so a villager owner usually comes from another mod or a command.

## How it works (code map)

Architectury multi-loader layout:

```
common/    all logic (Mojang mappings; events via Architectury API)
neoforge/  @Mod entry class + META-INF/neoforge.mods.toml (deps + mixin config)
fabric/    ModInitializer entry class + fabric.mod.json

common/src/main/java/dev/yucktheslime/mcaplayer2/
  McaPlayer2Bridge.java          Common init; registers Architectury lifecycle/tick events
  neoforge/McaPlayer2BridgeNeoForge (neoforge module)  NeoForge @Mod entry
  fabric/McaPlayer2BridgeFabric     (fabric module)    Fabric entry
  goal/TalkToMcaVillagerGoal     PlayerEngine Task: path to villager, stay in range, face it, converse
  command/TalkToVillagerCommand  LLM command `talk_to_villager <name|uuid>`; runs the goal
  command/NearbyVillagersCommand LLM command `nearby_villagers`; lists villagers and their profiles
  util/McaVillagerUtil           Every MCA API call (find villagers, read profile, speak, look)
  util/VillagerProfile           MCA data snapshot plus its prompt description
  player2/CompanionAdapter       Every PlayerEngine conversation call (list companions, inject info/speech)
  bridge/ConversationBridge      Two-way orchestration: greetings, replies, turn limits, look-at
  bridge/VillagerReplyGenerator  Villager lines through the Player2 local API, with canned-line fallback
  memory/ConversationMemoryStore Per-villager JSON logs in the world save
  util/OwnedEntityUtil           Generic owner-UUID detection (vanilla / reflection / NBT) and owner resolution
  util/OwnerUuidResolver         Plug-in interface for extra owner resolvers
  context/PromptContextProvider  Registry of extra prompt-context sources
  context/OwnedCompanionContextProvider  "This X is a tamed companion belonging to <villager>" lines
  config/BridgeConfig            JSON config
  mixin/PlayerEngineCommandsMixin            Registers the commands in PlayerEngineCommands.init
  mixin/PlayerEngineConversationManagerMixin Captures companion speech at ConversationManager.onAICharacterMessage
```

### How the APIs were checked

PlayerEngine and MCA Reborn don't publish API docs, so every class and method this mod calls was checked against the actual 1.21.1 jars from Modrinth, using `javap` and the CFR decompiler:

- **PlayerEngine 1.21.1-1.4.0:** `tasks.base.Task`, `tasks.movement.GetToEntityTask(Entity, double)`, `util.helpers.LookHelper.lookAt(controller, Vec3)`, `commands.base.Command/Arg/ArgParser`, `PlayerEngineCommands.init(PlayerEngineController)`, `PlayerEngineController#runUserTask/getEntity/getOwner/getCommandExecutor`, `player2api.manager.ConversationManager.queueData/getOrCreateEventQueueData/onAICharacterMessage`, `AgentConversationData#onEvent/getEntity/getName/getUUID/getMod`, `Event.InfoMessage`, `Event.UserMessage`, `Event.CharacterMessage`.
- **MCA Reborn 7.7.36:** `net.conczin.mca.entity.VillagerEntityMCA`, `VillagerLike#getAgeState/getProfessionId/getTraits/isHostile`, `Messenger#sendChatToAllAround/isToYoungToSpeak/isSpeechImpaired`, `VillagerBrain#getPersonality/getMood/getMoodValue/getMemories`, `Memories#getHearts`, `Relationship#getGender`, `Traits.Trait#getId`.

## Building

```bash
./gradlew build      # Java 21 required
```

The build produces:

- `neoforge/build/libs/mca-player2-bridge-neoforge-<version>.jar`: the runnable NeoForge mod.
- `fabric/build/libs/mca-player2-bridge-fabric-<version>.jar`: the runnable Fabric mod.

Ignore the `-dev-shadow` and `-sources` jars; they're intermediate build outputs. CI (`.github/workflows/build.yml`) builds on every push and pull request and uploads the runnable jars as `mca-player2-bridge-neoforge-1.21.1` and `mca-player2-bridge-fabric-1.21.1`.

Build tooling:

- Gradle 9.5.0 (wrapper)
- Architectury Loom 1.17.493. Loom 1.17 or newer is required because MCA 7.7.36 was built with Loom 1.17.
- Architectury Plugin 3.5.170
- Shadow 9.6.1
- Mojang mappings

Dependencies come from the Modrinth Maven (`maven.modrinth:<slug>:<version-id>`), maven.neoforged.net, the Fabric Maven, and the Architectury Maven. You can run a dev server with `./gradlew :neoforge:runServer`.

## Billing and the circuit breaker

Every AI request the bridge causes is billed to the **companion's owner**:

- Villager lines are delivered to the companion as PlayerEngine info events, not as chat from a player. PlayerEngine bills the last *player* who spoke to a companion, so a villager name would match no one and the request would fail.
- Villager replies go through PlayerEngine's own Player2 transport, billed to the owner.

If a billing, auth or quota problem shows up, the bridge stops making requests for `billingFailureCooldownSeconds`. That covers PlayerEngine's "no billing player/token" error, HTTP 401, 402, 403 or 429, budget limits, and PlayerEngine reporting that no one can pay. Meanwhile villagers use canned lines and nothing is forwarded to companions. One warning goes to the server log, and nothing is sent to chat. Bridge-triggered companion turns are also capped by `maxBridgeCompanionTurnsPerMinute`.

PlayerEngine's own messages are never hidden. The bridge only observes `AgentSideEffects.onError` to know when to back off.

## Known limitations

- **Not play-tested yet.** On NeoForge, a dedicated dev server (`:neoforge:runServer`) with MCA, PlayerEngine, and Player2NPC loads the bridge without errors. On startup the bridge writes its config and opens the world memory directory. Companion/villager conversations have not been tested in game: timing, prompt quality, and PlayerEngine's turn gating are unverified.
- **Hearts are per player, not per companion.** MCA stores hearts per player UUID, and companions aren't players. The bridge reports the villager's hearts toward the **companion's owner**. It does not change MCA hearts.
- **Villager replies cost Player2 credit.** With the default `playerengine` backend they use the owner's Player2 credit through PlayerEngine's transport. That bypasses PlayerEngine's per-call budget counters, but the bridge's own rate limit and circuit breaker still apply. To spend no extra credit, set `villagerReplyBackend` to `canned`.
- **Villagers speak through MCA's chat pipeline, not its dialogue trees.** Lines go through MCA's `sendChatToAllAround` (MCA chat prefix, speech sound, players within 20 blocks). They are not added to MCA's interaction/dialogue GUI, and MCA's own chatAI module is not involved.
- **Command registration timing.** Commands are added at the end of `PlayerEngineCommands.init`. If PlayerEngine builds a companion's system prompt before then, the new commands may only appear in that prompt after the companion is respawned or reloaded. The mixins use `require = 0`, so if a future PlayerEngine renames the hooks, the bridge degrades silently instead of crashing. Check the log.
- **Companion speech reaches villagers through a mixin** into `ConversationManager.onAICharacterMessage`, an internal PlayerEngine method, not a public API. It may break on PlayerEngine updates.
- **Villager lines arrive as info events** (`Info: Alice (MCA villager) says to you: "..."`), not as chat from a user. The companion's reply prompt therefore has no "last message was from …" hint.
- **Companion identity in memory** is keyed by the Player2 character name, because companion entity UUIDs can change on respawn. Two companions with the same name share history.
- **Owned-entity detection is best effort.** The NBT fallback calls `saveWithoutId` on nearby entities when a conversation opens. That costs some performance in mob-dense areas; turn it off with `ownerNbtFallback: false`. Pet mods that store ownership under an unusual key or a deeper object need an `ownerNbtKeys` or `ownerAccessorMethods` entry, or a registered `OwnerUuidResolver`.
- **Common code is compiled against the Fabric builds** of PlayerEngine and MCA. That's the normal Architectury setup, and `javap` shows the same class and method names in the NeoForge jars. The NeoForge module then compiles against the NeoForge jars. Keep the two loaders' dependency versions in step.
- (Fabric) MCA's Fabric jar ships a `classTweaker v1` access widener. If the game fails to load MCA, update Fabric Loader to the latest version.

## License

LGPL-3.0-or-later, to stay compatible with PlayerEngine / Automatone. See `LICENSE` (LGPL v3 text followed by the GPL v3 text it builds on).
