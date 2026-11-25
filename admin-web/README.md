# Elog Admin

Single-page admin site for Elog. Sign in with an admin account, then choose which builds get pushed to users' devices.

- Lists every `elog-build-N` release from `Rabbbibhai/Chess`.
- **Push to devices** writes a row to the Supabase `app_updates` table. Phones on an older build then show the Update screen with your notes.
- **Withdraw** deletes that row. Builds you never push stay private.
- Only accounts with `profiles.is_admin = true` can write (enforced by Supabase row level security, not by this page).

Static site, no build step. Deployed on Vercel with root directory `admin-web`.
