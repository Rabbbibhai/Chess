# Saucyfy

Track movies, shows and anime: save what you watched, rate it (1-10), and keep your place (season / episode) for what you're watching or plan to watch.

- Kotlin + Jetpack Compose (Material 3, dark theme)
- Google login via Supabase Auth
- Library stored in Supabase Postgres (row level security, per user)
- Metadata: TMDB (movies + shows) and Jikan / MyAnimeList (anime)

## One-time setup

1. **Supabase**: create a project, open SQL editor, run `supabase/schema.sql`.
2. **Google login**: Google Cloud Console -> OAuth client (type *Web*). Add redirect URI `https://YOUR-REF.supabase.co/auth/v1/callback`. Put the client id/secret in Supabase -> Authentication -> Providers -> Google.
3. **Redirect allow-list**: Supabase -> Authentication -> URL Configuration -> Redirect URLs, add `saucyfy://login-callback`.
4. **TMDB**: free key at themoviedb.org (Settings -> API) - v3 API key.
5. **GitHub secrets** (repo -> Settings -> Secrets and variables -> Actions):
   - `SUPABASE_URL` (https://YOUR-REF.supabase.co)
   - `SUPABASE_ANON_KEY` (anon / publishable key)
   - `TMDB_API_KEY`

## Build the APK

Actions -> **Build Saucyfy APK** -> Run workflow. When it finishes, download `Saucyfy.apk` from the run's artifacts or from the repo's Releases page.

## Icon

The icon is generated in CI from `assets/icon.b64` (center-cropped). To use a full-resolution version, upload your image as `saucyfy/assets/icon.png` (or `.jpg`) - it will be preferred automatically.
