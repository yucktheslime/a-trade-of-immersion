# Kagrenac setup (the CHIM-style brain), step by step

This guide is for Windows players who use the **CurseForge app**. No programming is needed.
You copy a few commands and paste them.

With Kagrenac, the AI runs in a small local server instead of inside Minecraft. It works the way CHIM works for Skyrim:

- the server writes the prompts and keeps each NPC's history and memory;
- it has a web **dashboard** where you can read conversations, edit NPC bios and change the AI model;
- it can speak the replies aloud (PocketTTS). The voice plays from the NPC's position in the world;
- companions can do all 13 game actions: follow, stay, attack, go somewhere, ride, get off,
  equip, use or eat items, give items, pick up, inspect items, and talk to villagers;
- deaths, attacks, damage, gifts, villager conversations and MCA family changes are sent to the
  server, so NPCs know what happened;
- with several companions around, the server picks which one answers.

You need about 30 minutes, plus download time.

> **Keep these private:** your OpenRouter key and your server token. Never paste them into a chat,
> a screenshot, a bug report or a GitHub repository. The key goes only into the dashboard. The
> token goes only into your own Windows settings or your own config file.

---

## 1. Install DwemerDistro

DwemerDistro is the free local server package that CHIM also uses. If you already use CHIM, you
already have it: skip to step 2.

1. Follow the official guide:
   <https://dwemerdynamics.com/custom-mods/installation.html#InstallDistro>
2. Start the **DwemerDistro launcher** once and wait until it reports that the server is running.

## 2. Update DwemerDistro

In the DwemerDistro launcher, click **Update Distro** and wait for it to finish.

The update gives you the `ddistro_custom_mod` tool and a patched PHP. The install script in step 3
refuses to run without them.

## 3. Install the A Trade of Immersion server

1. Open **PowerShell**: press the Windows key, type `PowerShell`, then press Enter.
2. Find your distro's name:

   ```powershell
   wsl -l -v
   ```

   You will see a name such as `DwemerAI4Skyrim3`. Use that name in the next command.
3. Open a shell inside the distro:

   ```powershell
   wsl -d DwemerAI4Skyrim3 -u dwemer
   ```

   The prompt changes to something like `dwemer@...:~$`.
4. Paste these two lines. They download this mod's repository and install the server:

   ```bash
   git clone https://github.com/yucktheslime/a-trade-of-immersion.git ~/a-trade-of-immersion
   bash ~/a-trade-of-immersion/kagrenac-server/scripts/deploy_toi.sh
   ```

   - If it asks for a password, type the `dwemer` user's password. The DwemerDistro default is `dwemer`.
   - Nothing appears on screen while you type the password. That is normal.

   The script does four things:
   - protects the server's private files from the web;
   - copies the server to `/var/www/html/custom-mods/TradeOfImmersionServer`;
   - creates a private config with a new random token;
   - creates the database `trade_of_immersion_ai`.

   It ends with a health check. Success looks like this:

   ```
   {"protocol":1,"ok":true,...,"server_version":"0.3.0-toi"}
   OK: config is not served (HTTP 403).
   ```

**Updating later:** run this in the same distro shell. Your settings, key and history are kept.

```bash
git -C ~/a-trade-of-immersion pull && bash ~/a-trade-of-immersion/kagrenac-server/scripts/deploy_toi.sh
```

## 4. Add your OpenRouter key (on the dashboard only)

1. On Windows, open your browser at
   <http://127.0.0.1:19000/custom-mods/TradeOfImmersionServer/>
2. Go to **Configuration → API keys**. In **LLM API key**, paste your OpenRouter key (it starts
   with `sk-or-`), then click **Save keys**.
   - To make a key: <https://openrouter.ai/keys>.
3. Go to **Configuration → LLM**. The server comes set to `openrouter` with the model
   `google/gemma-4-31b-it`. You can change the model there; any OpenRouter model id works.
4. Under **Test**, click **Send test to openrouter**. A real reply means the AI works.
   - If you get an error instead, check the key and that your OpenRouter account has credit or
     uses a `:free` model.

## 5. Copy the server token

Back in the distro shell (step 3), run:

```bash
sudo php -r '$c = require "/var/www/html/custom-mods/TradeOfImmersionServer/config/config.php"; echo $c["token"], PHP_EOL;'
```

It prints a long random token. You need it in the next step.

## 6. Point the mod at the server

**Recommended: store the token in a Windows environment variable** (it stays out of all files)

1. In a normal PowerShell window (not the distro shell), run the line below, using your own token:

   ```powershell
   setx TOI_KAGRENAC_TOKEN "paste-the-token-here"
   ```

2. **Fully close the CurseForge app**, including from the tray icon, and start it again. It only sees
   new environment variables after a restart.

**Switch the mod to Kagrenac**

1. In CurseForge, start your modpack profile.
2. In a world where you have cheats or op, type `/npc ai preset kagrenac` in chat.
   - This sets `aiBackend` to `kagrenac` and saves the config.

**Or edit the config file by hand**

1. In CurseForge, right-click the profile, choose **Open Folder**, then open
   `config/tradeofimmersion.json` with Notepad.
2. Set these values:

   ```json
   "aiBackend": "kagrenac",
   "kagrenacBaseUrl": "http://127.0.0.1:19000/custom-mods/TradeOfImmersionServer",
   "kagrenacToken": "",
   ```

   - Leave `kagrenacToken` empty when you use the environment variable.
   - You can put the token there instead. It is then stored in plain text, so never share that file.
3. Save the file and run `/npc ai reload` in game.

## 7. Test it

In game, type:

```
/npc ai status
```

Good output contains:

```
Kagrenac http://127.0.0.1:19000/custom-mods/TradeOfImmersionServer: ready, server 0.3.0-toi, schema 008_game_bios, Minecraft actions (13 offered); token set; ...
```

Then spawn a companion (`/npc spawn Elara female`) and talk to it in normal chat:

- `Elara, follow me`
- `Elara, attack the zombie`
- `Elara, what happened?`

On the dashboard you can now see:

- the conversation under **Roleplay → Conversation / History**;
- the NPC bios the game sent under **Configuration → NPC bios**, for your companions and the MCA
  villagers near you, with personality, profession and family. You can edit a bio there, and the
  game never overwrites your edits;
- each action's result under **Control Panel → Logs**.

| Status says | Fix |
|---|---|
| `token NOT SET` | Redo step 6 and restart CurseForge completely. |
| `last error: ... HTTP 401 unauthorized` (after you talked to a companion) | The token is wrong. Copy it again (step 5). |
| `unreachable (ConnectException)` | Start the DwemerDistro launcher. Check that the dashboard URL from step 4 opens in your browser. |
| `template actions only` | The server is the plain Kagrenac template, or out of date. Run the update command from step 3. |
| NPC answers "...(I can't think straight right now)" | The LLM failed. Use **Send test** on the dashboard (step 4) to see why. |

## 8. Voice (optional, off by default)

1. In the DwemerDistro launcher, start **PocketTTS**.
2. On the dashboard, open **Configuration → Voice**. Tick **TTS enabled**, keep the provider
   `pockettts` and the URL `http://127.0.0.1:8086/v1/audio/speech`, and save.
   - Use the **Speak** test on that page to check that you hear a voice.
3. In `config/tradeofimmersion.json`, set `"voiceEnabled": true`, then run `/npc ai reload`.
4. In Minecraft, go to **Options → Music & Sounds** and turn up **Voice/Speech**.

How the voice works:

- Replies are spoken from the NPC's position, so they get quieter with distance (`voiceRange`, 24 blocks).
- Female and male NPCs get different PocketTTS voices, and each NPC always keeps the same voice. You can
  choose a fixed voice per NPC on the dashboard's **Profiles** page.
- `voiceVillagers` (default `true`) also voices MCA villagers' lines. `voiceVolume` is between 0 and 1.
- On a multiplayer server, the voice is fetched by each player's own game. Every player who wants voice
  needs the same `kagrenacBaseUrl` and token in their own client config, and must be able to reach the
  server.

Speech input (talking to NPCs with a microphone) is not in this version. It is on the [roadmap](ROADMAP.md).

## Mod config keys for Kagrenac

| Key | Default | Meaning |
|---|---|---|
| `aiBackend` | `openai` | set to `kagrenac` (or run `/npc ai preset kagrenac`) |
| `kagrenacBaseUrl` | `http://127.0.0.1:19000/custom-mods/TradeOfImmersionServer` | the server |
| `kagrenacToken` | empty | empty = read the `TOI_KAGRENAC_TOKEN` (or `KAGRENAC_TOKEN`) environment variable |
| `kagrenacSessionId` | empty | history is kept per session; empty = `mc-<world name>` |
| `kagrenacAllowedActions` | all 13 | actions the game will run; remove any you do not want |
| `kagrenacLogEvents` | true | send deaths, damage, gifts, villager talk and family changes |
| `kagrenacSyncBios` | true | push companion and villager bios to the dashboard |
| `kagrenacUseDecision` | true | let the server pick which companion answers |
| `kagrenacCancelOnInterrupt` | true | a new line cancels the reply still being written |
| `voiceEnabled`, `voiceVillagers`, `voiceVolume`, `voiceRange` | false, true, 1.0, 24 | voice (client side) |

Switching back is one command: `/npc ai preset openrouter`, `/npc ai preset ollama` or `/npc ai preset mock`.

## Privacy and safety notes

- Everything runs on your own PC. The only data that leaves it is what the server sends to OpenRouter
  to generate replies.
- The game only runs actions on its own allowlist, and only on the NPC that was asked. Each action is
  checked twice: by the server, then again by the game before it runs.
- The server code is in [`kagrenac-server/`](../kagrenac-server/). It is based on the Kagrenac template
  by Dwemer Dynamics (GPL-3.0-only). See [kagrenac-server/MINECRAFT.md](../kagrenac-server/MINECRAFT.md).
