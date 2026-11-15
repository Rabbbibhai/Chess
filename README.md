# Word Wars (Roblox)

Two-player word-guessing game: **Warmer or Colder**. Sit at a table in the lobby; when two players are seated a match starts. One player secretly picks a word, the other guesses and gets FREEZING / COLD / COOL / WARM / HOT / BURNING feedback based on semantic similarity.

## Layout
- `src/Main.server.lua` - bootstraps remotes, tables, leaderboard
- `src/Modules/TableController.lua` - seating, match flow, scoring, validation (server authoritative)
- `src/Modules/WordData.lua` - curated vocabulary with category + feature tags (server only)
- `src/Modules/Similarity.lua` - similarity scoring and labels
- `src/Client/GameUI.client.lua` + `UIKit.lua` - responsive UI
- `build.py` - generates `WordWars.rbxlx` (lobby, tables, scripts)
- `.github/workflows/publish.yml` - builds and publishes to Roblox on every push to `main`

## Setup
Add repo secrets: `ROBLOX_API_KEY`, `ROBLOX_UNIVERSE_ID`, `ROBLOX_PLACE_ID`.

## Adding words
Edit `src/Modules/WordData.lua`. Each word is `word = "feature tags"` inside a category/subcategory. Words sharing a subcategory and tags score as closer.
