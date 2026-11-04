import { createClient } from "@supabase/supabase-js";
import { timingSafeEqual } from "crypto";

const fail = (error, status = 400) => Response.json({ error }, { status });

function authed(pw) {
  const real = process.env.ADMIN_PASSWORD || "";
  const a = Buffer.from(String(pw || ""));
  const b = Buffer.from(real);
  return real.length > 0 && a.length === b.length && timingSafeEqual(a, b);
}

async function list(db) {
  const { data, error } = await db
    .from("users")
    .select("discord_id, chess_username, mention, created_at")
    .order("created_at");
  if (error) return fail("Database error.", 500);
  return Response.json({ users: data });
}

export async function POST(req) {
  const body = await req.json().catch(() => ({}));
  const db = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_SERVICE_KEY);
  const ip = (req.headers.get("x-forwarded-for") || "unknown").split(",")[0].trim();
  const { data: row } = await db.from("admin_attempts").select("*").eq("ip", ip).maybeSingle();

  if (row?.locked_until && new Date(row.locked_until) > new Date()) {
    return fail("Too many wrong tries. Locked for 15 minutes.", 429);
  }
  if (!authed(body.password)) {
    const fails = (row?.fails || 0) + 1;
    const lock = fails >= 5;
    await db.from("admin_attempts").upsert({
      ip,
      fails: lock ? 0 : fails,
      locked_until: lock ? new Date(Date.now() + 15 * 60 * 1000).toISOString() : null,
    });
    await new Promise((r) => setTimeout(r, 1000)); // slow down guessing
    return fail("Wrong password.", 401);
  }
  if (row) await db.from("admin_attempts").delete().eq("ip", ip);
  const id = String(body.discord_id || "").trim();

  if (body.action === "add") {
    const username = String(body.chess_username || "").trim().toLowerCase();
    if (!/^[a-z0-9_-]{3,25}$/.test(username)) return fail("Invalid chess.com username.");
    if (!/^\d{17,20}$/.test(id)) return fail("Discord ID must be 17 to 20 digits.");
    const check = await fetch(`https://api.chess.com/pub/player/${username}`, { headers: { "User-Agent": "chess-recap" } });
    if (!check.ok) return fail("That chess.com username does not exist.", 404);
    const { error } = await db.from("users").upsert(
      { discord_id: id, chess_username: username, created_at: new Date().toISOString(), last_end_time: 0 },
      { onConflict: "discord_id" }
    );
    if (error) return fail("Could not save.", 500);
  } else if (body.action === "remove") {
    if (!/^\d{17,20}$/.test(id)) return fail("Bad Discord ID.");
    const { error } = await db.from("users").delete().eq("discord_id", id);
    if (error) return fail("Could not remove.", 500);
  } else if (body.action === "mention") {
    if (!/^\d{17,20}$/.test(id)) return fail("Bad Discord ID.");
    const { error } = await db.from("users").update({ mention: !!body.mention }).eq("discord_id", id);
    if (error) return fail("Could not update.", 500);
  } else if (body.action !== "list") {
    return fail("Unknown action.");
  }
  return list(db);
}
