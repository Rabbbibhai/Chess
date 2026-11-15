# Parkour Game (Roblox)

Obby/parkour game with checkpoints, lava, and admin tools (slap, fall gun, chat commands).

## How it works
- Scripts live in `src/`.
- `build.py` turns them into `ParkourGame.rbxlx` (a Roblox place file) with a starter course.
- GitHub Actions (`.github/workflows/publish.yml`) builds it and publishes it to Roblox on every push to `main`.

## One-time setup
1. Create an experience on https://create.roblox.com and publish it once so it has a Universe ID and Place ID.
2. Create an Open Cloud API key with the **Place Publishing** (universe-places write) permission for that experience.
3. In this repo go to Settings > Secrets and variables > Actions and add:
   - `ROBLOX_API_KEY`
   - `ROBLOX_UNIVERSE_ID`
   - `ROBLOX_PLACE_ID`
4. Push a change, or run the workflow by hand from the Actions tab.

## Admin commands (chat)
`!fall`, `!kill`, `!gun`, `!slap`, `!tp`, `!speed <name> <n>`, `!stage <name> <n>`. Use `me`, `all` or part of a name.
Add friends' UserIds to the `ADMINS` list in `src/Admin.server.lua`.
