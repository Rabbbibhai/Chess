# Saucyfy

Track movies, shows and anime: save what you finished, rate it (1-10), and keep your place (season / episode) for what you're watching or plan to watch.

- Kotlin + Jetpack Compose (Material 3, dark theme)
- Email + password login via Supabase Auth (no third-party app registration)
- Library stored in Supabase Postgres (row level security, per user)
- Profile name + photo (photo stored in Supabase Storage, max 1 MB, auto-compressed)
- Metadata, no API keys needed: iTunes (movies), TVMaze (shows), Jikan / MyAnimeList (anime)
- Real season / episode limits per title (TVMaze + Jikan), so counters can't go past what exists
- Speed: library cached on disk (opens instantly), image memory+disk cache, HTTP cache, optimistic updates
- Rate limiting: per-host request spacing, write throttling, login lockout, DB write limit (60/min)
- One account per network (IP): Postgres auth hook `public.hook_before_user_created`

## Supabase dashboard settings (one-time)

1. Authentication -> Sign In / Providers -> **Confirm email OFF** (built-in mailer only sends a couple of emails/hour).
2. Authentication -> Auth Hooks -> **Before User Created** -> Postgres function -> `public.hook_before_user_created`. (Turns on the one-account-per-IP rule.)

The database (tables, grants, storage bucket, rate limit, IP hook) is already applied to the And-app project.

## Build the APK

Actions -> **Build Saucyfy APK** -> Run workflow. Download `Saucyfy.apk` from the repo's Releases page.

## App icon

Upload your image anywhere inside `saucyfy/assets/` (png / jpg / jpeg / webp, any file name). The build uses the largest image in that folder, center-crops it and trims the edges a little. Uploading triggers a new build automatically. The icon is baked into the APK, so to change it for everyone: replace the image, let the build finish, and share the new APK.
