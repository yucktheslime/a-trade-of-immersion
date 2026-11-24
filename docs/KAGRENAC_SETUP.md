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

The update gives you the `ddistro_custom_mod` tool with release-package support (needed by **Browse mods**
and **Install server** in step 3) and a patched PHP. Both install routes in step 3 refuse to run without them.

## 3. Install the A Trade of Immersion server from the launcher

1. In the DwemerDistro launcher, open **Mods > Custom mods > Browse mods**.
2. Search for `Trade of Immersion` and select **A Trade of Immersion** (by yucktheslime, Minecraft 1.21.1).
3. Choose one of these:
   - **Install server.** This installs only the server. You add the mod jar to your modpack yourself and
     set the token in step 5.
   - **Install.** This installs the server and also the mod jar. The launcher asks for your modpack's folder.
     In CurseForge, right-click the profile and choose **Open Folder**. That folder must contain `options.txt`,
     so start the modpack once before you do this.
     - Close Minecraft first.
     - The launcher puts `a-trade-of-immersion-neoforge-<version>.jar` into `mods/` and writes a private pairing
       file next to it, `mods/tradeofimmersion-kagrenac.json`. The mod reads that file, so you can skip the
       token in steps 5 and 6.
     - Remove any other `a-trade-of-immersion` jar from `mods/`.
4. Wait for the health check. **A Trade of Immersion** then shows under **Mods > Custom mods** as ready, with a
   **Dashboard** link.

The launcher installs the server in `/var/www/html/TradeOfImmersionServer`, with its own database
`custom_trade_of_immersion` and a new random token. It turns on DwemerDistro's web protection before it copies
anything, so the private config and source folders are never served. The server answers at:

```
http://127.0.0.1:19000/TradeOfImmersionServer
```

That is the mod's default `kagrenacBaseUrl`, so the mod config needs no change.

**Updating later:** when a new version is out, select the mod under **Mods > Custom mods** and choose
**Update**. Your settings, key, token and history are kept.

### Manual install (fallback)

Use this only if the launcher route does not work for you (for example, an old launcher without
**Browse mods**).

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
4. Paste these two lines. They download the server and install it:

   ```bash
   git clone https://github.com/yucktheslime/toi-kagrenac-server.git ~/toi-kagrenac-server
   bash ~/toi-kagrenac-server/scripts/deploy_toi.sh
   ```

   - If it asks for a password, type the `dwemer` user's password. The DwemerDistro default is `dwemer`.
   - Nothing appears on screen while you type the password. That is normal.

   The script does four things:
   - turns on DwemerDistro's web protection for `/custom-mods/trade-of-immersion` before it copies anything;
   - copies the server to `/var/www/html/custom-mods/trade-of-immersion`;
   - creates a private config with a new random token, and the database `trade_of_immersion_ai`;
   - runs a health check, and stops with an error unless `config/config.php` answers **403**.

   Success ends like this:

   ```
   OK: config/config.php is not served (HTTP 403).
   ...
   Mod config: "kagrenacBaseUrl": "http://127.0.0.1:19000/custom-mods/trade-of-immersion"
   ```

- A manual install answers at `http://127.0.0.1:19000/custom-mods/trade-of-immersion`. Set that as
  `kagrenacBaseUrl` in step 6. It is not the mod's default.
- A manual install does not show under **Custom mods** in the launcher. Only launcher installs do.
- **Updating later:**

  ```bash
  git -C ~/toi-kagrenac-server pull && bash ~/toi-kagrenac-server/scripts/deploy_toi.sh
  ```

### Already installed by hand with version 0.3.0?

The 0.3.0 script copied the server to `/var/www/html/custom-mods/TradeOfImmersionServer`, which DwemerDistro's
web protection does not cover. Its `config/config.php` answers HTTP 200 instead of 403. Fix it now, in the
distro shell, with one of these two options.

**Option A: move to the launcher install (recommended).** Your conversations, memories, bios, dashboard
settings and token are kept.

1. Install the server from the launcher (step 3, **Install server** or **Install**).
2. In the distro shell, run:

   ```bash
   git clone https://github.com/yucktheslime/toi-kagrenac-server.git ~/toi-kagrenac-server
   bash ~/toi-kagrenac-server/scripts/move_to_launcher.sh
   ```

   The script:
   - backs up both installs;
   - copies the old database `trade_of_immersion_ai` into the launcher's `custom_trade_of_immersion`;
   - copies the old config (so the OpenRouter key and the **token stay the same**);
   - checks health and that `config/config.php` answers 403;
   - moves the old folder out of the web server into `~/toi-move-backup-<time>/`.

   The old database stays as it was. Once everything works, you can drop it with
   `sudo -u postgres dropdb trade_of_immersion_ai`.
3. In `config/tradeofimmersion.json`, set `kagrenacBaseUrl` back to the default,
   `http://127.0.0.1:19000/TradeOfImmersionServer`, or delete the line. Then run `/npc ai reload` in game.

**Option B: stay manual.** Run the new manual install (the four lines in "Manual install" above). It moves
`custom-mods/TradeOfImmersionServer` into the protected folder `custom-mods/trade-of-immersion`, keeping the
config, token and database, and checks for 403. Then set
`"kagrenacBaseUrl": "http://127.0.0.1:19000/custom-mods/trade-of-immersion"` and run `/npc ai reload`.

Check the result from the distro shell. The line for the route you use must print `403`
(the first line is the launcher install, the second the manual one):

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:19000/TradeOfImmersionServer/config/config.php
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:19000/custom-mods/trade-of-immersion/config/config.php
```

The old URL `.../custom-mods/TradeOfImmersionServer/...` should now
answer 404.

## 4. Add your OpenRouter key (on the dashboard only)

1. On Windows, open the dashboard in your browser. Use the **Dashboard** link under **Mods > Custom mods**, or:
   - launcher install: <http://127.0.0.1:19000/TradeOfImmersionServer/ui/>
   - manual install: <http://127.0.0.1:19000/custom-mods/trade-of-immersion/>
2. Go to **Configuration → API keys**. In **LLM API key**, paste your OpenRouter key (it starts
   with `sk-or-`), then click **Save keys**.
   - To make a key: <https://openrouter.ai/keys>.
3. Go to **Configuration → LLM**. The server comes set to `openrouter` with the model
   `google/gemma-4-31b-it`. You can change the model there; any OpenRouter model id works.
4. Under **Test**, click **Send test to openrouter**. A real reply means the AI works.
   - If you get an error instead, check the key and that your OpenRouter account has credit or
     uses a `:free` model.

## 5. Copy the server token

**Skip this step if the launcher's Install put the mod jar in your modpack.** The pairing file already holds the
token.

Otherwise, in the distro shell (step 3, manual install, points 1 to 3), run the line for your install. It
prints a long random token, which you need in the next step.

- Launcher install:

  ```bash
  sudo php -r '$c = require "/var/www/html/TradeOfImmersionServer/config/config.php"; echo $c["token"], PHP_EOL;'
  ```

- Manual install:

  ```bash
  sudo php -r '$c = require "/var/www/html/custom-mods/trade-of-immersion/config/config.php"; echo $c["token"], PHP_EOL;'
  ```

## 6. Point the mod at the server

**If the launcher's Install put the mod jar in your modpack**, nothing is needed. On the first start without a
config file, the mod finds the pairing file and switches to Kagrenac by itself. With an older config file, run
`/npc ai preset kagrenac` once in game.

**Otherwise, store the token in a Windows environment variable** (it stays out of all files):

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
   "kagrenacBaseUrl": "http://127.0.0.1:19000/TradeOfImmersionServer",
   "kagrenacToken": "",
   ```

   - Manual install: use `http://127.0.0.1:19000/custom-mods/trade-of-immersion` instead.
   - Leave `kagrenacToken` empty when you use the environment variable or the launcher's pairing file.
   - You can put the token there instead. It is then stored in plain text, so never share that file.
3. Save the file and run `/npc ai reload` in game.

Where the token comes from, first match wins:

1. the `TOI_KAGRENAC_TOKEN` (or `KAGRENAC_TOKEN`) environment variable;
2. `kagrenacToken`;
3. the launcher's pairing file `mods/tradeofimmersion-kagrenac.json`.

The pairing file's URL is used only while `kagrenacBaseUrl` is left at the default.

## 7. Test it

In game, type:

```
/npc ai status
```

Good output contains:

```
Kagrenac http://127.0.0.1:19000/TradeOfImmersionServer: ready, server 0.3.1-toi, schema 008_game_bios, Minecraft actions (13 offered); token set; ...
```

With the launcher's pairing file it says `token set (launcher pairing)`.

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
| `token NOT SET` | Redo steps 5 and 6 and restart CurseForge completely. |
| `last error: ... HTTP 401 unauthorized` (after you talked to a companion) | The token is wrong. Copy it again (step 5). |
| `unreachable (ConnectException)` | Start the DwemerDistro launcher. Check that the dashboard from step 4 opens in your browser, and that `kagrenacBaseUrl` matches your install (launcher or manual). |
| `HTTP 404` | `kagrenacBaseUrl` points at the wrong route. Launcher: `/TradeOfImmersionServer`. Manual: `/custom-mods/trade-of-immersion`. |
| `template actions only` | The server is the plain Kagrenac template, or out of date. Update it (step 3). |
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
- On a multiplayer server, only the Minecraft server talks to Kagrenac. It fetches each spoken line and
  streams the audio to the players near the NPC. Players need nothing but the mod: no URL, no token.
  Each player can mute NPC voices for themselves with `"voicePlay": false` in their own config.

Speech input (talking to NPCs with a microphone) is not in this version. It is on the [roadmap](ROADMAP.md).

## 9. Optional: connect a hosted server (Bisect etc.)

Kagrenac keeps running **on your PC**, exactly as in steps 1 to 8. This step is only needed when the Minecraft
server runs somewhere else, such as Bisect Hosting or another server host, and should use the Kagrenac on your PC.
Single player and a server on your own PC do not need it.

What it looks like:

```
players --> Minecraft server (Bisect) --> https tunnel --> your PC: DwemerDistro, port 19000 --> Kagrenac
```

- The PC must be **on**, with the **DwemerDistro launcher running**, the whole time people play. When it is
  off, the server keeps working: NPCs say a short in-character line now and then (or stay silent, see
  `kagrenacFallback`), and game events wait in a queue that is sent when your PC is back.
- Only the Minecraft server talks to your PC. Players install only the mod.
- The tunnel gives your PC a public **https** address without opening ports on your router.
  Pick **one** of the two tunnels below.

### Option A: Cloudflare quick tunnel (no account needed)

1. Open the distro shell (step 3).
2. Install `cloudflared` once:

   ```bash
   curl -L -o /tmp/cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
   sudo dpkg -i /tmp/cloudflared.deb
   ```

3. Start the tunnel to the DwemerDistro custom-mods port, 19000:

   ```bash
   cloudflared tunnel --url http://localhost:19000
   ```

   After a few seconds it prints an address such as `https://some-random-words.trycloudflare.com`.
   Keep this window open while people play.
4. Your Kagrenac URL is that address plus the server path:

   ```
   https://some-random-words.trycloudflare.com/TradeOfImmersionServer
   ```

   For a manual install, the path is `/custom-mods/trade-of-immersion` instead.

- A quick tunnel gets a **new address every time you start it**. Then update `kagrenacBaseUrl` on the
  host (step "Edit the config on the host" below) each time. For a fixed address, create a free
  Cloudflare account and a named tunnel: <https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/>.

### Option B: Tailscale Funnel (fixed address, free account)

1. Make a free account at <https://tailscale.com>.
2. In the distro shell, install Tailscale and sign in (it prints a link to open in your browser):

   ```bash
   curl -fsSL https://tailscale.com/install.sh | sh
   sudo tailscale up
   ```

   If it says the daemon is not running, start it with `sudo tailscaled >/dev/null 2>&1 &` and run
   `sudo tailscale up` again.
3. Share port 19000 on the internet:

   ```bash
   sudo tailscale funnel --bg 19000
   ```

   - The first time, it prints a link to turn on HTTPS and Funnel for your account. Open it, allow it, and
     run the command again.
   - It prints your address, such as `https://my-pc.tail1234.ts.net`. That address stays the same.
   - Stop sharing with `sudo tailscale funnel --bg 19000 off`.
4. Your Kagrenac URL:

   ```
   https://my-pc.tail1234.ts.net/TradeOfImmersionServer
   ```

### Check the tunnel

Open `<your Kagrenac URL>/health.php` in a browser. You should see `{"protocol":1,"ok":true,...}`.

The **dashboard does not open through the tunnel** (it answers "The dashboard is only available on the PC
that runs it"). That is on purpose: the dashboard has no password. Keep using it on your PC at
<http://127.0.0.1:19000/TradeOfImmersionServer/ui/> (manual install:
<http://127.0.0.1:19000/custom-mods/trade-of-immersion/>).

### Edit the config on the host

1. Stop the Minecraft server in the host's control panel.
2. Open the host's **File Manager** and edit `config/tradeofimmersion.json`. Upload the mod first and
   start the server once if the file is not there yet.
3. Set these values, using your URL and the token from step 5:

   ```json
   "aiBackend": "kagrenac",
   "kagrenacBaseUrl": "https://some-random-words.trycloudflare.com/TradeOfImmersionServer",
   "kagrenacToken": "paste-the-token-here",
   ```

   - With a remote URL the token is **required**. Without it, the mod sends nothing but the health check and
     logs a warning.
   - If your host lets you set environment variables, you can put the token in `TOI_KAGRENAC_TOKEN` instead.
     It overrides `kagrenacToken`.
   - Use the `https://` address. With plain `http://` over the internet, the server log warns that the token
     and the conversations travel unencrypted.
4. Start the server. In the console you should see:

   ```
   AI backend: kagrenac(https://some-random-words.trycloudflare.com/TradeOfImmersionServer, remote)
   ```

5. As an op, run `/npc ai status` in game. It should say `(remote): ready ... token set`.

### What players see

- Game events (deaths, kills, fights) go only to Kagrenac, never into chat. NPCs can react to them in their
  own words.
- Companion and villager replies only reach players near the NPC (`npcChatRadius`, 24 blocks); the owner
  hears their companion from further away (`npcChatOwnerRadius`, 64 blocks).
- Mod status and errors stay in the server console. `/npc ai status` is for ops only.

### Good to know

- The tunnel shares the whole DwemerDistro custom-mods port, so other custom mods you run on port 19000
  can be reached too. Stop the tunnel when you do not need it.
- Events are sent in the background and never slow down the server. While your PC is off they wait in a
  queue (`kagrenacEventQueueSize`, 200; the oldest go first when it is full). They are retried with a growing pause of up
  to `kagrenacRetryMaxSeconds` (300), and are marked "(N min ago)" when they arrive late. The console
  warns at most once a minute.
- `kagrenacFallback` sets what NPCs do while Kagrenac is unreachable:
  `line` (default: a short in-character line, at most every 2 minutes per companion), `none` (silent),
  `openai` (answer with OpenRouter straight from the host: also set `"aiPreset": "openrouter"`, and the key in
  `aiApiKey` or the `TOI_API_KEY` environment variable; `aiModel` picks the model) or `mock`.

## Mod config keys for Kagrenac

| Key | Default | Meaning |
|---|---|---|
| `aiBackend` | `openai` | set to `kagrenac` (or run `/npc ai preset kagrenac`) |
| `kagrenacBaseUrl` | `http://127.0.0.1:19000/TradeOfImmersionServer` | the server: the launcher route by default; manual install `.../custom-mods/trade-of-immersion`; any http/https URL, such as a tunnel (step 9) |
| `kagrenacToken` | empty | the server token; the `TOI_KAGRENAC_TOKEN` (or `KAGRENAC_TOKEN`) environment variable overrides it; when empty, the launcher pairing file is used. Required for a remote URL |
| `kagrenacSessionId` | empty | history is kept per session; empty = `mc-<world name>` |
| `kagrenacAllowedActions` | all 13 | actions the game will run; remove any you do not want |
| `kagrenacLogEvents` | true | send deaths, damage, gifts, villager talk and family changes |
| `kagrenacSyncBios` | true | push companion and villager bios to the dashboard |
| `kagrenacUseDecision` | true | let the server pick which companion answers |
| `kagrenacCancelOnInterrupt` | true | a new line cancels the reply still being written |
| `voiceEnabled`, `voiceVillagers`, `voiceVolume`, `voiceRange` | false, true, 1.0, 24 | voice: `voiceEnabled` on the server fetches and streams it; the others are per player |
| `voicePlay` | true | per player: `false` mutes NPC voices on your own game |
| `kagrenacFallback` | `line` | while Kagrenac is unreachable: `line`, `none`, `openai` or `mock` |
| `kagrenacEventQueueSize` | 200 | events kept while Kagrenac is unreachable |
| `kagrenacRetryMaxSeconds` | 300 | longest pause between retries |
| `kagrenacLogMobKills` | false | also send ordinary mobs killed by players |
| `npcChatRadius`, `npcChatOwnerRadius` | 24, 64 | who sees NPC lines: nearby players, and the owner from further away |
| `chatShowAiErrors` | false | show "can't think" error lines in chat instead of the quiet fallback |
| `chatCompanionDeathNotice` | true | tell the owner (only) when their companion dies |
| `chatOpFeedback` | false | echo mod command results to other ops |
| `statusForEveryone` | false | let non-ops run `/npc ai status` |

Switching back is one command: `/npc ai preset openrouter`, `/npc ai preset ollama` or `/npc ai preset mock`.

## Privacy and safety notes

- Everything runs on your own PC. The only data that leaves it is what the server sends to OpenRouter
  to generate replies, plus, if you set up step 9, the traffic between your PC and your Minecraft server
  through the tunnel.
- The mod never writes the token to its log.
- The game only runs actions on its own allowlist, and only on the NPC that was asked. Each action is
  checked twice: by the server, then again by the game before it runs.
- The server code is in [`kagrenac-server/`](../kagrenac-server/). It is based on the Kagrenac template
  by Dwemer Dynamics (GPL-3.0-only). See [kagrenac-server/MINECRAFT.md](../kagrenac-server/MINECRAFT.md).
