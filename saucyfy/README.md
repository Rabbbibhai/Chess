# Saucyfy

Track movies, shows and anime: save what you watched, rate it (1-10), and keep your place (season / episode) for what you're watching or plan to watch.

- Kotlin + Jetpack Compose (Material 3, dark theme)
- Email + password login via Supabase Auth (no third-party app registration)
- Library stored in Supabase Postgres (row level security, per user)
- Metadata, no API keys needed: iTunes (movies), TVMaze (shows), Jikan / MyAnimeList (anime)
- Rate limiting: per-host request spacing + result caching for metadata APIs, write throttling, and a login attempt lockout (5 tries / minute). Optional server-side write limit in `supabase/rate_limit.sql`.

## One-time setup

1. **Supabase**: schema is in `supabase/schema.sql` (already applied to the And-app project).
2. **Email login**: Supabase -> Authentication -> Providers -> Email. Turn **Confirm email** OFF. (The built-in mailer only sends a couple of emails per hour, so confirmation emails would block sign-ups.)
3. Optional: run `supabase/rate_limit.sql` for the server-side write limit.

The build falls back to the And-app project's public URL and anon key. To point at another project, set the `SUPABASE_URL` and `SUPABASE_ANON_KEY` environment variables / secrets.

## Build the APK

Actions -> **Build Saucyfy APK** -> Run workflow. When it finishes, download `Saucyfy.apk` from the run's artifacts or from the repo's Releases page.

## Icon

The icon is generated in CI from `assets/icon.b64` (center-cropped). To use a full-resolution version, upload your image as `saucyfy/assets/icon.png` (or `.jpg`) - it will be preferred automatically.
