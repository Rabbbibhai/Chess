'use client';
import { useEffect, useMemo, useState } from 'react';
import { Chess } from 'chess.js';
import { createClient } from '@supabase/supabase-js';
import * as E from '../lib/engine';

const U = process.env.NEXT_PUBLIC_SUPABASE_URL, K = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const sb = createClient(U, K);
const COL = ['#e5484d', '#3e7bfa', '#f5c518', '#30a46c'];
const G = { K: '♚', Q: '♛', R: '♜', B: '♝', N: '♞', P: '♟' }, T = '\uFE0E';
const LBL = { 2: '1v1', 3: '3 players', 4: '4 players' };
const mine = () => JSON.parse(localStorage.getItem('me') || 'null');

async function api(action, body = {}) {
  const r = await fetch(U + '/functions/v1/api', { method: 'POST', headers: { 'Content-Type': 'application/json', apikey: K }, body: JSON.stringify({ action, token: mine()?.token, ...body }) });
  const j = await r.json();
  if (j.error) {
    if (j.error.startsWith('Please sign in')) { localStorage.removeItem('me'); location.reload(); }
    throw new Error(j.error);
  }
  return j;
}

// Profiles are fetched once and cached (memory + localStorage) to save database reads.
const cache = new Map(JSON.parse(localStorage.getItem('pc') || '[]'));
function useNames(ids) {
  const [, f] = useState(0), key = ids.join();
  useEffect(() => {
    const miss = [...new Set(ids)].filter((i) => i && !cache.has(i));
    if (!miss.length) return;
    sb.from('profiles').select('id,username,avatar').in('id', miss).then(({ data }) => {
      (data || []).forEach((p) => cache.set(p.id, p));
      localStorage.setItem('pc', JSON.stringify([...cache]));
      f((n) => n + 1);
    });
  }, [key]);
  return (id) => cache.get(id) || { id, username: '…' };
}

function Av({ p, s = 36 }) {
  const n = p?.username || '?', h = [...n].reduce((a, c) => a + c.charCodeAt(0) * 7, 0) % 360;
  return p?.avatar
    ? <img src={p.avatar} width={s} height={s} className='av' alt='' />
    : <span className='av' style={{ width: s, height: s, background: `hsl(${h} 55% 45%)`, fontSize: s * 0.45 }}>{n[0].toUpperCase()}</span>;
}

function Gate({ onDone }) {
  const [name, setName] = useState(''), [av, setAv] = useState(null), [err, setErr] = useState(''), [busy, setBusy] = useState(false);
  const pick = (f) => {
    if (!f) return;
    const img = new Image();
    img.onload = () => {
      const c = document.createElement('canvas'); c.width = c.height = 96;
      const s = Math.min(img.width, img.height);
      c.getContext('2d').drawImage(img, (img.width - s) / 2, (img.height - s) / 2, s, s, 0, 0, 96, 96);
      setAv(c.toDataURL('image/jpeg', 0.7));
    };
    img.src = URL.createObjectURL(f);
  };
  const go = async () => {
    setBusy(true); setErr('');
    try {
      const r = await api('signup', { username: name.trim(), avatar: av });
      const m = { token: r.token, id: r.profile.id, username: r.profile.username, avatar: r.profile.avatar };
      localStorage.setItem('me', JSON.stringify(m)); onDone(m);
    } catch (e) { setErr(e.message); }
    setBusy(false);
  };
  return (
    <main>
      <div className='card'>
        <h2>Welcome</h2>
        <p className='mut'>Pick a name your friends will see. A profile picture is optional.</p>
        <div className='row' style={{ alignItems: 'center', marginBottom: 12 }}>
          <Av p={{ username: name || '?', avatar: av }} s={64} />
          <label className='mut'>Profile picture (optional)<input type='file' accept='image/*' onChange={(e) => pick(e.target.files[0])} /></label>
        </div>
        <label>Username<input value={name} maxLength={16} onChange={(e) => setName(e.target.value)} autoCapitalize='none' /></label>
        {err && <p className='err'>{err}</p>}
        <button className='pri' disabled={busy || name.trim().length < 3} onClick={go}>Let me in</button>
      </div>
    </main>
  );
}

function Lobby({ me, go }) {
  const [games, setGames] = useState([]), [err, setErr] = useState('');
  useEffect(() => {
    let on = true;
    sb.from('games').select('*').neq('status', 'finished').order('created_at', { ascending: false }).limit(30).then(({ data }) => on && setGames(data || []));
    const ch = sb.channel('lobby').on('postgres_changes', { event: '*', schema: 'public', table: 'games' }, (p) => {
      const row = p.eventType === 'DELETE' ? p.old : p.new;
      setGames((gs) => {
        const rest = gs.filter((x) => x.id !== row.id);
        return p.eventType === 'DELETE' || row.status === 'finished' ? rest : [row, ...rest];
      });
    }).subscribe();
    return () => { on = false; sb.removeChannel(ch); };
  }, []);
  const name = useNames(games.flatMap((g) => g.players));
  const make = async (m) => { try { go((await api('create', { mode: m })).id); } catch (e) { setErr(e.message); } };
  const open = async (g) => {
    try { if (g.status === 'waiting' && !g.players.includes(me.id)) await api('join', { id: g.id }); go(g.id); } catch (e) { setErr(e.message); }
  };
  return (
    <>
      <div className='card'>
        <b>Start a game</b>
        <div className='row' style={{ marginTop: 8 }}>
          {[2, 3, 4].map((m) => <button key={m} className='pri' onClick={() => make(m)}>{LBL[m]}</button>)}
        </div>
        {err && <p className='err'>{err}</p>}
      </div>
      <h3>Lobby</h3>
      {!games.length && <p className='mut'>No games yet. Start one and send your friends the link.</p>}
      {games.map((x) => (
        <div className='card g' key={x.id}>
          <div className='pls'>
            {x.players.map((p) => <Av key={p} p={name(p)} s={28} />)}
            {Array.from({ length: x.mode - x.players.length }).map((_, i) => <span key={i} className='av ghost' />)}
          </div>
          <div><b>{LBL[x.mode]}</b><div className='mut'>{x.status === 'waiting' ? 'Waiting' : 'Live'} · {x.players.map((p) => name(p).username).join(', ')}</div></div>
          <button className={x.status === 'waiting' ? 'pri' : ''} onClick={() => open(x)}>{x.players.includes(me.id) ? 'Open' : x.status === 'waiting' ? 'Join' : 'Spectate'}</button>
        </div>
      ))}
    </>
  );
}

function Board({ n, cell, pick }) {
  const out = [];
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) {
    const x = cell(r, c), k = r * n + c;
    out.push(x.ok
      ? <div key={k} className={'sq ' + ((r + c) % 2 ? 'd' : 'l') + (x.sel ? ' sel' : '') + (x.last ? ' last' : '')} onClick={() => pick(r, c)}>
          {x.glyph && <span className={'pc' + (x.stroke ? ' w' : '')} style={{ color: x.color }}>{x.glyph}</span>}
          {x.tg && <i className='dot' />}
        </div>
      : <div key={k} />);
  }
  return <div className='board' style={{ '--n': n }}>{out}</div>;
}

function Game({ id, me, go }) {
  const [g, setG] = useState(null), [st, setSt] = useState(null), [sel, setSel] = useState(null), [err, setErr] = useState('');
  useEffect(() => {
    let on = true;
    sb.from('games').select('*').eq('id', id).maybeSingle().then(({ data }) => on && setG(data || false));
    sb.from('game_state').select('state').eq('game_id', id).maybeSingle().then(({ data }) => on && data && setSt(data.state));
    const ch = sb.channel('g' + id)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'games', filter: `id=eq.${id}` }, (p) => setG(p.eventType === 'DELETE' ? false : p.new))
      .on('postgres_changes', { event: '*', schema: 'public', table: 'game_state', filter: `game_id=eq.${id}` }, (p) => p.new?.state && setSt(p.new.state))
      .subscribe();
    return () => { on = false; sb.removeChannel(ch); };
  }, [id]);
  const name = useNames(g ? g.players : []);
  const chess = useMemo(() => {
    if (st?.kind !== 'chess') return null;
    const c = new Chess();
    st.moves.forEach((m) => c.move({ from: m.slice(0, 2), to: m.slice(2, 4), promotion: m[4] }));
    return c;
  }, [st]);
  if (g === null) return <p className='mut'>Loading…</p>;
  if (g === false) return <p>Game not found. <button onClick={() => go(null)}>Lobby</button></p>;
  const inGame = g.players.includes(me.id);
  const act = (a, b = {}) => api(a, { id, ...b }).catch((e) => setErr(e.message));

  if (g.status === 'waiting' || !st) return (
    <div className='card'>
      <h2>{LBL[g.mode]} game</h2>
      <div className='pls'>{g.players.map((p) => <span key={p} className='pl'><Av p={name(p)} s={30} /><b>{name(p).username}</b></span>)}</div>
      <p className='mut'>Waiting for {g.mode - g.players.length} more player(s). Share this page's link with your friends.</p>
      <div className='row'>
        {inGame ? <button onClick={async () => { await act('leave'); go(null); }}>Leave</button> : <button className='pri' onClick={() => act('join')}>Join</button>}
        <button onClick={() => navigator.clipboard.writeText(location.href)}>Copy invite link</button>
      </div>
      {err && <p className='err'>{err}</p>}
    </div>
  );

  const seat = st.seats.indexOf(me.id), over = g.status === 'finished';
  const myTurn = !over && seat >= 0 && st.turn === seat;
  let cell, pick;
  if (chess) {
    const bd = chess.board(), flip = seat === 1, lm = st.moves[st.moves.length - 1];
    const tg = sel ? chess.moves({ square: sel, verbose: true }).map((m) => m.to) : [];
    const at = (r, c) => { const [br, bc] = flip ? [7 - r, 7 - c] : [r, c]; return ['abcdefgh'[bc] + (8 - br), bd[br][bc]]; };
    cell = (r, c) => {
      const [sq, p] = at(r, c);
      return { ok: true, glyph: p && G[p.type.toUpperCase()] + T, color: p?.color === 'w' ? '#fff' : '#111', stroke: p?.color === 'w', sel: sel === sq, tg: tg.includes(sq), last: lm && (lm.slice(0, 2) === sq || lm.slice(2, 4) === sq) };
    };
    pick = (r, c) => {
      const [sq, p] = at(r, c);
      if (!myTurn) return;
      if (sel && tg.includes(sq)) { setSel(null); act('move', { from: sel, to: sq }); }
      else setSel(p && p.color === (seat === 0 ? 'w' : 'b') ? sq : null);
    };
  } else {
    const rot = (r, c) => { for (let i = 0; i < Math.max(seat, 0); i++) [r, c] = [c, 13 - r]; return [r, c]; };
    const tg = sel ? E.movesFrom(st, sel[0], sel[1]) : [];
    const hit = (t, a, b) => t[0] === a && t[1] === b;
    cell = (r, c) => {
      const [br, bc] = rot(r, c);
      if (!E.ok(br, bc)) return { ok: false };
      const p = st.board[br * 14 + bc], l = st.last;
      return { ok: true, glyph: p && G[p[1]] + T, color: p && COL[+p[0]], stroke: false, sel: sel && hit(sel, br, bc), tg: tg.some((t) => hit(t, br, bc)), last: l && ((l[0] === br && l[1] === bc) || (l[2] === br && l[3] === bc)) };
    };
    pick = (r, c) => {
      const [br, bc] = rot(r, c);
      if (!myTurn) return;
      if (sel && tg.some((t) => hit(t, br, bc))) { const f = sel; setSel(null); act('move', { from: f, to: [br, bc] }); }
      else { const p = st.board[br * 14 + bc]; setSel(p && +p[0] === seat ? [br, bc] : null); }
    };
  }
  const res = g.result;
  return (
    <>
      <div className='bar'>
        {st.seats.map((pid, s) => pid && (
          <div key={s} className={'pl' + (!over && st.turn === s ? ' on' : '') + (st.alive && !st.alive[s] ? ' out' : '')}>
            <Av p={name(pid)} s={26} /><b>{name(pid).username}</b><i style={{ background: chess ? (s ? '#111' : '#fff') : COL[s] }} />
          </div>
        ))}
      </div>
      {res && <p className='big'>{res.winner ? `${name(res.winner).username} wins (${res.reason})` : `Draw (${res.reason})`}</p>}
      {!res && <p className='mut'>{seat < 0 ? 'Spectating' : myTurn ? 'Your turn' : 'Waiting for opponent'}</p>}
      <Board n={chess ? 8 : 14} cell={cell} pick={pick} />
      {err && <p className='err'>{err}</p>}
      <div className='row' style={{ justifyContent: 'center' }}>
        {seat >= 0 && !over && <button onClick={() => confirm('Resign?') && act('resign')}>Resign</button>}
        <button onClick={() => go(null)}>Lobby</button>
      </div>
    </>
  );
}

export default function App() {
  const [me, setMe] = useState(mine);
  const [gid, setGid] = useState(() => new URLSearchParams(location.search).get('g'));
  useEffect(() => {
    const f = () => setGid(new URLSearchParams(location.search).get('g'));
    addEventListener('popstate', f);
    return () => removeEventListener('popstate', f);
  }, []);
  const go = (id) => { history.pushState({}, '', id ? '?g=' + id : location.pathname); setGid(id); };
  if (!me) return <Gate onDone={setMe} />;
  return (
    <main>
      <header>
        <h1 onClick={() => go(null)}>gooningchessbuddies</h1>
        <div className='me'><Av p={me} s={32} /><b>{me.username}</b></div>
      </header>
      {gid ? <Game id={gid} me={me} go={go} /> : <Lobby me={me} go={go} />}
    </main>
  );
}
