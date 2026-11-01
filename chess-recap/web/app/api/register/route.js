import { createClient } from "@supabase/supabase-js";

const err = (error, status = 400) => Response.json({ error }, { status });

export async function POST(req) {
  const body = await req.json().catch(() => ({}));
  const username = String(body.chess_username || "").trim().toLowerCase();
  const discordId = String(body.discord_id || "").trim();

  if (!/^[a-z0-9_-]{3,25}$/.test(username)) return err("Enter a valid chess.com username.");
  if (!/^\d{17,20}$/.test(discordId)) return err("Discord user ID must be 17 to 20 digits.");

  const check = await fetch(`https://api.chess.com/pub/player/${username}`, { headers: { "User-Agent": "chess-recap" } });
  if (!check.ok) return err("That chess.com username does not exist.", 404);

  const sb = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY);
  const { error } = await sb.from("users").upsert(
    { discord_id: discordId, chess_username: username, created_at: new Date().toISOString() },
    { onConflict: "discord_id" }
  );
  if (error) return err("Could not save. Try again.", 500);
  return Response.json({ ok: true });
}
