# Elog

Track movies, shows, anime, manga and music: save what you finished, rate it (1-10), keep your place (season / episode / chapter), see your friends' lists, and chat with them.

(The code folder is still called `saucyfy/` and the internal app id is `com.saucyfy.app`.)

- Kotlin + Jetpack Compose (Material 3), 7 themes, each with its own header color
- Email + password login via Supabase Auth
- Library in Supabase Postgres (row level security, per user): movies, shows, anime, manga (volume / chapter), music (tracks)
- Friends, read-only friend libraries, chats with GIFs (history stored on the phones; the server mailbox only holds undelivered messages)
- **In-app updates:** when you push a build from the admin website, every phone on an older build shows an Update splash with the change list and a download + install button; after updating, a one-time "What's new" splash
- Sources (no keys): Cinemeta/iTunes (movies), TVMaze (shows), Jikan/Kitsu (anime, manga), iTunes/Deezer (music), nekos.best (GIFs), Wikipedia (spelling help)

## Releasing an update (admin website)

The admin site lives in `admin-web/` (hosted on Vercel).

1. Push your change. GitHub Actions builds and publishes `Elog.apk` as a release (`elog-build-N`).
2. Open the admin website, sign in, find the build, press **Push to devices...**, edit the notes, confirm. Or don't - a build you don't push never reaches anyone.
3. Phones on an older build see the Update splash when Elog opens (or within ~30 minutes if it is already open). **Withdraw** takes it back.

The app downloads the APK link stored with the pushed build, so the repo must stay public.

## Signing key

The first build creates `saucyfy/keystore/elog.jks` and commits it. Every later build reuses it, which is what lets a new APK install **over** the old one. (If you ever delete that file, phones must uninstall once before updating.)

## Supabase dashboard settings (one-time)

1. Authentication -> Sign In / Providers -> **Confirm email OFF**.
2. Authentication -> Auth Hooks -> **Before User Created** -> Postgres function -> `public.hook_before_user_created`.

## App icon

Upload your image anywhere inside `saucyfy/assets/` (png / jpg / jpeg / webp). The largest image there is used.
