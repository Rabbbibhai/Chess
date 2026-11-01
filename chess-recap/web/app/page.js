"use client";
import { useState } from "react";

export default function Home() {
  const [chess, setChess] = useState("");
  const [discord, setDiscord] = useState("");
  const [state, setState] = useState({ busy: false, msg: "", ok: false });

  async function submit() {
    setState({ busy: true, msg: "", ok: false });
    try {
      const res = await fetch("/api/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ chess_username: chess, discord_id: discord }),
      });
      const data = await res.json();
      setState({ busy: false, ok: res.ok, msg: res.ok ? "Saved. Your next finished game will show up in Discord." : data.error });
    } catch {
      setState({ busy: false, ok: false, msg: "Could not reach the server. Try again." });
    }
  }

  return (
    <>
      <div className="strip" />
      <main>
        <h1>Your game review, in Discord</h1>
        <p className="lead">After each chess.com game, get your great, excellent, inaccurate and blunder counts, and your opponent's.</p>
        <label htmlFor="c">chess.com username</label>
        <input id="c" value={chess} onChange={(e) => setChess(e.target.value)} autoCapitalize="none" autoComplete="off" />
        <small>Only games you finish after saving are analyzed.</small>
        <label htmlFor="d">Discord user ID</label>
        <input id="d" value={discord} onChange={(e) => setDiscord(e.target.value)} inputMode="numeric" autoComplete="off" />
        <small>Discord Settings, Advanced, turn on Developer Mode. Then right-click your profile and choose Copy User ID.</small>
        <button onClick={submit} disabled={state.busy}>{state.busy ? "Saving" : "Save"}</button>
        {state.msg && <div className={"msg " + (state.ok ? "ok" : "err")} role="status">{state.msg}</div>}
      </main>
    </>
  );
}
