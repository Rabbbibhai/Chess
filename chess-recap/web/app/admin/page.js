"use client";
import { useState } from "react";

const small = { width: "auto", padding: "8px 12px", fontSize: "0.9rem" };

export default function Admin() {
  const [pw, setPw] = useState("");
  const [users, setUsers] = useState(null);
  const [msg, setMsg] = useState("");
  const [busy, setBusy] = useState(false);
  const [chess, setChess] = useState("");
  const [discord, setDiscord] = useState("");

  async function call(action, extra = {}) {
    setBusy(true);
    setMsg("");
    try {
      const res = await fetch("/api/admin", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password: pw, action, ...extra }),
      });
      const data = await res.json();
      if (!res.ok) {
        setMsg(data.error || "Something went wrong.");
        if (res.status === 401) setUsers(null);
      } else {
        setUsers(data.users);
        if (action === "add") { setChess(""); setDiscord(""); }
      }
    } catch {
      setMsg("Could not reach the server.");
    }
    setBusy(false);
  }

  function tryLogin() {
    call("list");
  }

  function remove(u) {
    if (window.confirm(`Remove ${u.chess_username}? They stop getting recaps.`)) call("remove", { discord_id: u.discord_id });
  }

  if (!users) {
    return (
      <>
        <div className="strip" />
        <main>
          <h1>Admin</h1>
          <label htmlFor="p">Password</label>
          <input id="p" type="password" value={pw} onChange={(e) => setPw(e.target.value)} onKeyDown={(e) => e.key === "Enter" && tryLogin()} />
          <button onClick={tryLogin} disabled={busy || !pw}>Enter</button>
          {msg && <div className="msg err" role="status">{msg}</div>}
        </main>
      </>
    );
  }

  return (
    <>
      <div className="strip" />
      <main>
        <h1>Players ({users.length})</h1>
        {users.map((u) => (
          <div key={u.discord_id} style={{ border: "2px solid #1c2430", borderRadius: 4, padding: 12, marginBottom: 12, background: "#fff" }}>
            <strong>{u.chess_username}</strong>
            <div style={{ fontSize: "0.85rem", color: "#4a5565", wordBreak: "break-all" }}>{u.discord_id}</div>
            <div style={{ margin: "8px 0" }}>Pings: <strong>{u.mention ? "on" : "off"}</strong></div>
            <div style={{ display: "flex", gap: 8 }}>
              <button style={small} disabled={busy} onClick={() => call("mention", { discord_id: u.discord_id, mention: !u.mention })}>
                {u.mention ? "Stop pings" : "Turn pings on"}
              </button>
              <button style={{ ...small, background: "#a4342b" }} disabled={busy} onClick={() => remove(u)}>Remove</button>
            </div>
          </div>
        ))}
        <h2 style={{ marginTop: 32 }}>Add player</h2>
        <label htmlFor="c">chess.com username</label>
        <input id="c" value={chess} onChange={(e) => setChess(e.target.value)} autoCapitalize="none" autoComplete="off" />
        <label htmlFor="d">Discord user ID</label>
        <input id="d" value={discord} onChange={(e) => setDiscord(e.target.value)} inputMode="numeric" autoComplete="off" />
        <button onClick={() => call("add", { chess_username: chess, discord_id: discord })} disabled={busy || !chess || !discord}>Add</button>
        {msg && <div className="msg err" role="status">{msg}</div>}
      </main>
    </>
  );
}
