# Elog

Track movies, shows, anime, manga and music: save what you finished, rate it (1-10), keep your place (season / episode / chapter), see your friends' lists, and chat with them.

(The code folder is still called `saucyfy/` and the internal app id is `com.saucyfy.app`.)

- Kotlin + Jetpack Compose (Material 3), 7 themes, each with its own header color
- Email + password login via Supabase Auth
- Library in Supabase Postgres (row level security, per user): movies, shows, anime, **manga (volume / chapter)**, **music (tracks)**
- Friends, read-only friend libraries, chats with GIFs (history stored on the phones; the server mailbox only holds undelivered messages)
- **In-app updates:** when a newer build is published, every phone shows an Update splash with the change list and a download + install button; after updating, a one-time "What's new" splash
- **Admin panel** (Profile tab, admin accounts only): write the notes for the build you are running and publish them
- Sources (no keys): Cinemeta/iTunes (movies), TVMaze (shows), Jikan/Kitsu (anime, manga), iTunes/Deezer (music), nekos.best (GIFs), Wikipedia (spelling help)

## How releasing an update works

1. Push your change. GitHub Actions builds and publishes the APK (`Elog.apk`) as a release.
2. Install that build on your phone, open **Profile -> Admin panel**, type what changed (one line each), tap **Publish**.
3. Everyone on an older build sees the Update splash next time they open Elog (or within ~30 minutes if it is open).

The app downloads `https://github.com/Rabbbibhai/Chess/releases/latest/download/Elog.apk`, so the repo must stay public.

## Signing key

The first build creates `saucyfy/keystore/elog.jks` and commits it. Every later build reuses it, which is what lets a new APK install **over** the old one. (If you ever delete that file, phones must uninstall once before updating.)

## Supabase dashboard settings (one-time)

1. Authentication -> Sign In / Providers -> **Confirm email OFF**.
2. Authentication -> Auth Hooks -> **Before User Created** -> Postgres function -> `public.hook_before_user_created`.

## App icon

Upload your image anywhere inside `saucyfy/assets/` (png / jpg / jpeg / webp). The largest image there is used.
