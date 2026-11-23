# Elog

Track movies, shows and anime: save what you finished, rate it (1-10), keep your place (season / episode), see your friends' lists, and chat with them.

(The code folder is still called `saucyfy/` and the internal app id is `com.saucyfy.app`, so new builds install over old ones and keep you signed in.)

- Kotlin + Jetpack Compose (Material 3), 7 themes
- Email + password login via Supabase Auth
- Library in Supabase Postgres (row level security, per user)
- Friends: find people by username, send / accept requests, view a friend's list (read-only)
- **Chats (very low database use):** history is stored on the phones. The server `mailbox` table only holds a message until the recipient's app downloads it, then it is deleted (undelivered mail expires after 14 days). Max 500 characters per message, 30 messages / minute, 100 unread per person. While the app is open it checks for new messages every 20 s (4 s inside a chat). GIFs are sent as a ~60-byte link (reaction GIFs from nekos.best, no key).
- Search: Cinemeta/iTunes (movies), TVMaze (shows), Jikan/Kitsu (anime) in parallel with fallbacks; spelling help
- Real season / episode limits per title
- Speed: library cached on disk, image + HTTP caches, optimistic updates
- Rate limiting: per-host request spacing, write throttling, login lockout, DB limits
- One account per network (IP): Postgres auth hook `public.hook_before_user_created`

## Supabase dashboard settings (one-time)

1. Authentication -> Sign In / Providers -> **Confirm email OFF**.
2. Authentication -> Auth Hooks -> **Before User Created** -> Postgres function -> `public.hook_before_user_created`.

## Build the APK

Actions -> **Build Elog APK** -> Run workflow. Download `Elog.apk` from the repo's Releases page.

## App icon

Upload your image anywhere inside `saucyfy/assets/` (png / jpg / jpeg / webp, any file name). The largest image there is used.
