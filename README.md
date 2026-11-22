# MCA-Player2 Bridge

A compatibility mod that lets **Player2 AI companions** (Player2NPC / PlayerEngine) detect, approach, and hold conversations with **Minecraft Comes Alive (MCA) Reborn** villagers.

## Goal

Player2 companions currently only listen to the player. MCA villagers have rich personalities, relationships, and dialogue, but no AI-driven interaction with other NPCs. This bridge closes that gap.

## Planned Features

- Player2 companions can target nearby MCA villagers as conversation partners
- Companions read the villager's name, personality, relationship score, and recent context
- Feed that data into the Player2 prompt so the companion speaks *to* the villager naturally
- Optional MCA-side hook so villagers can initiate or respond
- Basic memory of past conversations with specific villagers

## Dependencies (1.21.1)

- Minecraft Comes Alive Reborn (MCA)
- Player2NPC + PlayerEngine
- Automatone (usually pulled by PlayerEngine)
- Fabric API or NeoForge (depending on loader)

## Status

Early scaffold. Source structure and core hooks are being added.

## Building

```bash
./gradlew build
```

Artifacts will appear in `build/libs/`.

## License

LGPL-3.0-or-later (to stay compatible with PlayerEngine / Automatone).
