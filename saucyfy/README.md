# Saucyfy

Track movies, shows and anime: save what you watched, rate it (1-10), and keep your place (season / episode) for what you're watching or plan to watch.

- Kotlin + Jetpack Compose (Material 3, dark theme)
- Google login via Supabase Auth
- Library stored in Supabase Postgres (row level security, per user)
- Metadata, no API keys needed: iTunes (movies), TVMaze (shows), Jikan / MyAnimeList (anime)

## One-time setup

1. **Supabase**: schema is in `supabase/schema.sql` (already applied to the And-app project).
2. **Google login**: Google Cloud Console -> OAuth client (type *Web*). Add redirect URI `https://lnxybreoxrvbwlbepjsp.supabase.co/auth/v1/callback`. Put the client id/secret in Supabase -> Authentication -> Providers -> Google.
3. **Redirect allow-list**: Supabase -> Authentication -> URL Configuration -> Redirect URLs, add `saucyfy://login-callback`.

The build falls back to the And-app project's public URL and anon key. To point at another project, set the `SUPABASE_URL` and `SUPABASE_ANON_KEY` environment variables / secrets.

## Build the APK

Actions -> **Build Saucyfy APK** -> Run workflow. When it finishes, download `Saucyfy.apk` from the run's artifacts or from the repo's Releases page.

## Icon

The icon is generated in CI from `assets/icon.b64` (center-cropped). To use a full-resolution version, upload your image as `saucyfy/assets/icon.png` (or `.jpg`) - it will be preferred automatically.
