# MCA-Player2 Bridge

A **NeoForge** (primary) and Fabric compatibility mod for **Minecraft 1.21.1** that lets **Player2 AI companions** (Player2NPC / PlayerEngine) detect, approach, and talk with **Minecraft Comes Alive (MCA) Reborn** villagers. Villagers talk back, and both sides remember past conversations.

> Status: **early alpha (0.1.3)**. It compiles against the real dependency jars (local build + CI), but it has **not been play-tested in game yet**. See [Known limitations](#known-limitations).

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
| `companionMcaBody` | `true` | (Client, NeoForge) Render Player2 companions with MCA's body model, including the female shape and breasts. See [Companion body](#companion-body-mca-model-neoforge-client) |
| `companionAppearance` | `player_skin` | `player_skin`: keep the companion's own Player2 skin. `mca_generated`: MCA villager look (skin, face, hair and clothes generated from genes) |
| `companionGender` | `auto` | `auto` guesses from the Player2 character's description and greeting pronouns. `female`, `male` and `neutral` force a gender for all companions |
| `companionGenderFallback` | `male` | Used when `auto` can't tell |
| `companionGenderOverrides` | `{}` | Per character name, e.g. `{"Ellie": "female"}` |
| `companionBreastSize` | `-1.0` | 0..1 for female companions. Negative uses the default `0.5`. `/mcaplayer2 body <c> breast` overrides it per companion |
| `companionFlatWhenChestArmor` | `true` | Use the flat vanilla body while a chestplate is worn, so the breasts don't clip through armor |
| `companionMcaBodyDisableIfModsLoaded` | `[]` | Mod ids that switch the feature off when loaded (e.g. `wildfire_gender`) |
| `maxLogEntries` | `100` | Entries kept per villager log |
| `historyLinesInPrompt` | `10` | Past lines injected into prompts |

## Companion body (MCA model, NeoForge client)

On NeoForge clients, Player2NPC companions render with **MCA Reborn's own body model** instead of the flat player model. The bridge does what MCA does for players with its villager player model (`MixinPlayerRenderer`, "PLAYER" mode): it uses `PlayerEntityExtendedModel`, which is the vanilla player geometry plus MCA's breast parts. Because it keeps the player-skin UV layout, the companion's **own Player2 skin is used automatically** and has no UV mismatch.

How it works:
- A client-only mixin targets Player2NPC's `RenderAutomaton`. It is registered in the `client` section of `mcaplayer2.neoforge.mixins.json`, so dedicated servers never load it.
- MCA's model reads gender and genes through `CommonVillagerModel.getVillager(entity)`, which looks up `MCAClient.playerData[uuid]`. The bridge puts a client-side `VillagerEntityMCA` there for each companion UUID, holding its gender and genes.
- Companion gender is decided in this order:
  1. `/mcaplayer2 gender`;
  2. `companionGenderOverrides`;
  3. `companionGender`;
  4. a pronoun guess from the Player2 character's description and greeting (Player2 characters have **no gender field**);
  5. `companionGenderFallback`.
- With `player_skin`, male and neutral companions keep the vanilla model. MCA's male body is the same shape as the vanilla one.
- With `mca_generated`, the base skin is drawn transparent and MCA's Skin, Face, Clothing and Hair layers draw a gene-generated villager instead.

### Commands (permission level 2)

```
/mcaplayer2 gender <companion> <male|female|neutral|auto>
/mcaplayer2 body <companion>                      # show the saved overrides
/mcaplayer2 body <companion> <trait> <0.0-1.0>
/mcaplayer2 body <companion> reset                # clear traits (gender is kept; use "gender ... auto" to clear it)
```

- `<companion>` is a loaded companion's name, which tab-completes, or its entity UUID. Put names with spaces in quotes.
- Values are saved per companion UUID in `<world>/data/mcaplayer2/companion_bodies.json`.
- They are synced to every client when they change and when a player joins.
- They override the config and the character-description guess.

Traits map to MCA Reborn 7.7.36 `Genetics` genes (exact names):

| Trait | MCA gene | Visible in `player_skin` | Visible in `mca_generated` |
|---|---|---|---|
| `breast` | `Breast` | yes (female only) | yes (female only) |
| `size` / `height` | `Size` | yes (render scale `0.75 + Size/2`, like MCA) | yes |
| `width` / `weight` | `Width` | yes (render scale `0.75 + Width/2`) | yes |
| `melanin` / `skin_tone`, `hemoglobin` | `Melanin`, `Hemoglobin` | no (the Player2 skin is used) | skin color |
| `eumelanin`, `pheomelanin` | `Eumelanin`, `Pheomelanin` | no | hair color |
| `skin` | `Skin` | no | base skin texture |
| `face` | `Face` | no | face texture |
| `eye_brightness` | `EyeBrightness` | no | eyes |

With **MCA: Female Gender Physics** installed (mod id `mcabp`), these traits are also available. They are that addon's own genes:

| Trait | Addon gene | Effect |
|---|---|---|
| `separation` | `BreastSeparation` | Horizontal spacing |
| `breast_height` | `BreastHeight` | Vertical position |
| `depth` | `BreastDepth` | Forward offset |
| `rotation` | `BreastRotation` | Outward angle (cleavage) |
| `growth` | `BreastGrowth` | Size multiplier (0.5 = baseline) |

Height and width are only applied once you set them, so unedited companions keep Player2NPC's size. The scale is visual only; the server hitbox does not change.

### Compat: MCA: Female Gender Physics (optional)

[MCA: Female Gender Physics](https://www.curseforge.com/minecraft/mc-mods/mca-female-gender-physics) (mod id `mcabp`) is supported. The bridge was checked against its NeoForge 1.21.1 V0.1.8 jar. The addon is optional: the bridge works with or without it.

What the addon does (verified by decompiling it):
- It turns MCA's own breast cubes off (`Config.enableBoobs = false`).
- It adds its own `GenderLayer`, with spring physics, to MCA villager renderers. It also adds it to players when Wildfire's Female Gender Mod isn't installed.
- It reads gender and genes through `EntityConfig.resolveVillagerLike`, which only knows villagers and players.
- Because Player2 companions are neither, companions with the addon installed got no breasts at all: MCA's cubes were off and the addon skipped them.

What the bridge does when `mcabp` is loaded:
- Adds the addon's `GenderLayer` to Player2NPC's renderer.
- Adds client mixins, applied only when the addon is present, that make the addon accept companions and resolve them to the companion's MCA record. Physics then ticks from the companion's own movement.
- Textures the addon's breasts with the companion's skin (`player_skin`) or MCA's stitched villager skin (`mca_generated`).
- Gives the addon's genes neutral defaults.

There is no double drawing: the addon has already disabled MCA's cubes, so only the addon's breasts render. The addon's client config (`mcabp-client.toml`) controls physics, bounce, and armor behavior for companions too.

**MCA's own editor does not work on companions** (verified in MCA 7.7.36):
- `/mca editor` only opens the editor for yourself (`OpenGuiRequest(VILLAGER_EDITOR, player)`).
- `VillagerEditorSyncRequest` only applies edits to a `ServerPlayer` or a `VillagerLike` entity.
- A Player2NPC companion (`AutomatoneEntity extends LivingEntity`) is neither, so the commands above replace the editor for companions.

### Limitations

- **Armor:** while a chestplate is worn, the flat vanilla body is used (`companionFlatWhenChestArmor`). MCA's breast-shaped armor model (`PlayerArmorExtendedModel`) is not applied to companions.
- **Capes and elytra:** these stay vanilla.
- **First person:** not relevant, since companions are only seen in third person.
- **Breast physics:** only with MCA: Female Gender Physics (see above). MCA's own breasts are static. Wildfire's original Female Gender Mod (`wildfire_gender`) renders players only, so companions aren't affected by it.
- **Fixed in 0.1.3:** in 0.1.2, companions looked like scaled-up MCA children and had no breasts. The client-side MCA record was created without MCA's spawn initialization, so its age state stayed `UNASSIGNED` and MCA interpolated its dimensions toward `BABY`: head 1.5x, breasts 0. Records are now created as adults (`setAge(0)`, which sets `AgeState.ADULT` and adult dimensions). Height now comes only from the `Size` gene.
- **MCA player-model setting:** MCA's `enableVillagerPlayerModel` and `playerRendererBlacklist` only gate MCA's player renderer. They don't affect companions; use `companionMcaBody`.
- **Untested in game:** this has only been tested in the dev environment. The mixin applies, and the client reaches the menu without errors.
- **Fabric:** the feature is NeoForge-only. The Fabric build has no renderer mixin, but the commands and sync are there.

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
