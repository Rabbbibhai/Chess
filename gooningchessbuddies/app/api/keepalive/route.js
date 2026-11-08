export const dynamic = 'force-dynamic';

// Runs once a day (Vercel cron): wipes old games and keeps the free Supabase project awake.
export async function GET() {
  const r = await fetch(process.env.NEXT_PUBLIC_SUPABASE_URL + '/functions/v1/api', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action: 'cleanup' }),
  });
  return Response.json({ ok: r.ok });
}
