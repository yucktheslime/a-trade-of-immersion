# MCA-Player2 Bridge

A Fabric compatibility mod for **Minecraft 1.21.1** that lets **Player2 AI companions** (Player2NPC / PlayerEngine) detect, approach, and talk with **Minecraft Comes Alive (MCA) Reborn** villagers. Villagers talk back, and both sides remember past conversations.

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
| Loop guard | Each companion/villager pair is limited to `maxTurnsPerConversation` villager lines per conversation, and greetings have a cooldown, so two AIs can't chat forever. | `bridge/ConversationBridge` |

## Requirements

1. **The Player2 desktop app must be installed, running, and signed in** on the machine running Minecraft (https://player2.game). Player2NPC/PlayerEngine use it for the companion's LLM and TTS. This bridge also calls its local API (`http://127.0.0.1:4315/v1/chat/completions`) to write villager replies.
2. Mods (Fabric, 1.21.1). These are the exact versions the bridge is built against:

| Mod | Version | Link |
|---|---|---|
| Fabric Loader | 0.17.2 or newer (use the latest) | https://fabricmc.net/use/ |
| Fabric API | 0.116.18+1.21.1 | https://modrinth.com/mod/fabric-api |
| Architectury API | 13.0.8 or newer (required by PlayerEngine) | https://modrinth.com/mod/architectury-api |
| PlayerEngine | 1.21.1-1.4.0 (Fabric) | https://modrinth.com/mod/playerengine |
| Player2NPC | 1.21.1-1.4.0 (Fabric) | https://modrinth.com/mod/player2npc |
| MCA Reborn | 7.7.36+1.21.1 (Fabric) | https://modrinth.com/mod/minecraft-comes-alive-reborn |
| MCA-Player2 Bridge | this mod | GitHub Actions artifact / `build/libs` |

## Setup

1. Install the Fabric loader for 1.21.1 and drop all of the mods above into `mods/`.
2. Start the Player2 app and sign in.
3. Launch the game, spawn a Player2NPC companion, and walk into an MCA village.
4. Try either of these:
   - Walk near a villager. Within `detectionRadius` (8 blocks by default), the villager greets your companion and the companion answers.
   - Tell your companion something like *"go talk to Alice"*. Its LLM should call `talk_to_villager Alice`.
5. Conversation logs are saved to `<world>/data/mcaplayer2/villagers/<villager-uuid>.json`.

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
| `useLlmForVillagerReplies` | `true` | Use the Player2 app for villager lines. When off or unreachable, canned lines are used. |
| `player2ApiUrl` | `http://127.0.0.1:4315` | Player2 app local API |
| `player2GameKey` | `mca-player2-bridge` | Value of the `player2-game-key` header |
| `llmTimeoutSeconds` | `20` | HTTP timeout for villager replies |
| `villagerReplyMaxWords` | `30` | Length limit for villager replies |
| `maxLogEntries` | `100` | Entries kept per villager log |
| `historyLinesInPrompt` | `10` | Past lines injected into prompts |

## How it works (code map)

```
src/main/java/dev/yucktheslime/mcaplayer2/
  McaPlayer2Bridge.java          Fabric entry point; registers lifecycle/tick listeners
  goal/TalkToMcaVillagerGoal     PlayerEngine Task: path to villager, stay in range, face it, converse
  command/TalkToVillagerCommand  LLM command `talk_to_villager <name|uuid>`; runs the goal
  command/NearbyVillagersCommand LLM command `nearby_villagers`; lists villagers and their profiles
  util/McaVillagerUtil           Every MCA API call (find villagers, read profile, speak, look)
  util/VillagerProfile           MCA data snapshot plus its prompt description
  player2/CompanionAdapter       Every PlayerEngine conversation call (list companions, inject info/speech)
  bridge/ConversationBridge      Two-way orchestration: greetings, replies, turn limits, look-at
  bridge/VillagerReplyGenerator  Villager lines through the Player2 local API, with canned-line fallback
  memory/ConversationMemoryStore Per-villager JSON logs in the world save
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

The jar is written to `build/libs/mca-player2-bridge-<version>.jar`. CI (`.github/workflows/build.yml`) builds on every push and pull request and uploads that jar as an artifact.

The build uses Gradle 9.5.0 (wrapper), Fabric Loom 1.17.21, and Mojang mappings. Loom 1.17 or newer is required because MCA 7.7.36 was built with Loom 1.17. Dependencies come from the Modrinth Maven (`maven.modrinth:<slug>:<version-id>`), the Fabric Maven, and the Architectury Maven.

## Known limitations

- **Not play-tested yet.** It compiles against the real APIs, but runtime behaviour such as timing, prompt quality, and PlayerEngine's turn gating is unverified.
- **Hearts are per player, not per companion.** MCA stores hearts per player UUID, and companions aren't players. The bridge reports the villager's hearts toward the **companion's owner**. It does not change MCA hearts.
- **Villager replies need the Player2 app on the server machine.** Villager lines are generated server-side through `127.0.0.1:4315`. That works in singleplayer or on a server hosted on the same PC. On a dedicated server without the Player2 app, villagers fall back to short canned lines. Companions themselves still work, because PlayerEngine handles them.
- **Villagers speak through MCA's chat pipeline, not its dialogue trees.** Lines go through MCA's `sendChatToAllAround` (MCA chat prefix, speech sound, players within 20 blocks). They are not added to MCA's interaction/dialogue GUI, and MCA's own chatAI module is not involved.
- **Command registration timing.** Commands are added at the end of `PlayerEngineCommands.init`. If PlayerEngine builds a companion's system prompt before then, the new commands may only appear in that prompt after the companion is respawned or reloaded. The mixins use `require = 0`, so if a future PlayerEngine renames the hooks, the bridge degrades silently instead of crashing. Check the log.
- **Companion speech reaches villagers through a mixin** into `ConversationManager.onAICharacterMessage`, an internal PlayerEngine method, not a public API. It may break on PlayerEngine updates.
- **Villager lines arrive as chat messages.** They are fed to the companion as `Event.UserMessage` with the speaker set to `"<Name> (MCA villager)"`. PlayerEngine may treat that speaker as a non-owner user, so blacklist/whitelist settings in Player2NPC can affect it.
- **Companion identity in memory** is keyed by the Player2 character name, because companion entity UUIDs can change on respawn. Two companions with the same name share history.
- **Fabric only.** MCA Reborn, PlayerEngine, and Player2NPC all publish NeoForge builds for 1.21.1, so a NeoForge port through Architectury (common/fabric/neoforge) is feasible. That's future work. The code keeps MCA and PlayerEngine calls inside `util/` and `player2/` to make a port easier.
- MCA's Fabric jar ships a `classTweaker v1` access widener. If the game fails to load MCA, update Fabric Loader to the latest version.

## License

LGPL-3.0-or-later, to stay compatible with PlayerEngine / Automatone. See `LICENSE` (LGPL v3 text followed by the GPL v3 text it builds on).
