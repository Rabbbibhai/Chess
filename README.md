# Arena Shooter (Roblox)

Free-for-all shooter. Everyone spawns with a pistol, and weapons (SMG, Shotgun, Sniper, Rocket) are scattered in floors, walls and rooftops of 9 buildings. Infinite respawns, kills and deaths on the leaderboard.

## How it works
- Scripts live in `src/`.
- `build.py` turns them into a Roblox place file with the arena.
- GitHub Actions (`.github/workflows/publish.yml`) builds it and publishes to Roblox on every push to `main`.

## One-time setup
Add these repo secrets (Settings > Secrets and variables > Actions): `ROBLOX_API_KEY`, `ROBLOX_UNIVERSE_ID`, `ROBLOX_PLACE_ID`.

## Admin commands (chat)
`!fall`, `!kill`, `!gun`, `!slap`, `!tp`, `!speed`, `!rocket`, `!fling`, `!giant`, `!tiny`, `!normal`, `!event`.
Add friends' UserIds to `ADMINS` in `src/Admin.server.lua` and `src/Chaos.server.lua`.
