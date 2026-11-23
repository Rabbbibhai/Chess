# Saucyfy

Track movies, shows and anime: save what you finished, rate it (1-10), keep your place (season / episode), and see your friends' lists.

- Kotlin + Jetpack Compose (Material 3)
- 7 built-in themes (Midnight, AMOLED, Ocean, Forest, Sunset, Sakura, Daylight) - Profile tab
- Email + password login via Supabase Auth (no third-party app registration)
- Library in Supabase Postgres (row level security, per user)
- Friends: find people by username, send / accept requests, view a friend's list (read-only) and add titles from it to yours
- Profile name, username and photo (photo max 1 MB, auto-compressed)
- Search: movies (iTunes), shows (TVMaze), anime (Jikan) in parallel, results appear as each answers; spelling help ("showing results for ..." / "did you mean ...?")
- Real season / episode limits per title
- Speed: library cached on disk (opens instantly), image + HTTP caches, optimistic updates
- Rate limiting: per-host request spacing, write throttling, login lockout, DB write limit (60/min), friend requests (20/hour)
- One account per network (IP): Postgres auth hook `public.hook_before_user_created`

## Supabase dashboard settings (one-time)

1. Authentication -> Sign In / Providers -> **Confirm email OFF**.
2. Authentication -> Auth Hooks -> **Before User Created** -> Postgres function -> `public.hook_before_user_created` (one account per IP).

The database (tables, grants, storage bucket, friends, rate limits) is already applied to the And-app project.

## Build the APK

Actions -> **Build Saucyfy APK** -> Run workflow. Download `Saucyfy.apk` from the repo's Releases page.

## App icon

Upload your image anywhere inside `saucyfy/assets/` (png / jpg / jpeg / webp, any file name). The build uses the largest image in that folder.
