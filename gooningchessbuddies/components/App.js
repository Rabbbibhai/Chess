'use client';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Chess } from 'chess.js';
import { createClient } from '@supabase/supabase-js';
import * as E from '../lib/engine';

const U = process.env.NEXT_PUBLIC_SUPABASE_URL, K = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
const sb = createClient(U, K);
const COL = ['#e5484d', '#3e7bfa', '#f5c518', '#30a46c'];
const LBL = { 2: '1v1', 3: '3 players', 4: '4 players' };
const TCS = [[180, '3 min'], [300, '5 min'], [600, '10 min'], [0, 'No clock']];
const mine = () => JSON.parse(localStorage.getItem('me') || 'null');

// ---- sounds: tiny synthesized beeps, no audio files ----
let ac;
const tone = (f, d, type = 'sine', v = 0.15, at = 0) => {
  if (localStorage.getItem('mute') === '1') return;
  try {
    ac = ac || new (window.AudioContext || window.webkitAudioContext)();
    const t = ac.currentTime + at, o = ac.createOscillator(), g = ac.createGain();
    o.type = type; o.frequency.value = f;
    g.gain.setValueAtTime(v, t); g.gain.exponentialRampToValueAtTime(0.001, t + d);
    o.connect(g); g.connect(ac.destination); o.start(t); o.stop(t + d);
  } catch {}
};
const sfx = {
  move: () => tone(520, 0.07, 'triangle'),
  cap: () => { tone(200, 0.12, 'square', 0.1); tone(330, 0.09, 'triangle', 0.15, 0.02); },
  end: () => { tone(523, 0.15); tone(659, 0.15, 'sine', 0.15, 0.15); tone(784, 0.35, 'sine', 0.15, 0.3); },
};

// x-region runs the server function right next to the database, which makes every call much faster.
async function api(action, body = {}) {
  const r = await fetch(U + '/functions/v1/api', { method: 'POST', headers: { 'Content-Type': 'application/json', apikey: K, 'x-region': 'us-east-1' }, body: JSON.stringify({ action, token: mine()?.token, ...body }) });
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

// ---- piece artwork (vector, tinted per player) ----
const SYM = (
  <svg width='0' height='0' style={{ position: 'absolute' }} aria-hidden='true'>
    <defs>
      <symbol id='P' viewBox='0 0 45 45'><circle cx='22.5' cy='13' r='5.5' /><path d='M17 33c0-6 2-9 5.5-12 3.5 3 5.5 6 5.5 12z' /><path d='M13 33h19v4H13z' /></symbol>
      <symbol id='R' viewBox='0 0 45 45'><path d='M11 38h23v-4H11z' /><path d='M14 34V21h17v13z' /><path d='M13 21V12h4v3h3.5v-3h4v3H28v-3h4v9z' /></symbol>
      <symbol id='N' viewBox='0 0 45 45'><path d='M12 37c1-8 1-12 4-14l-5-1 9-11 1-4 3 3c9 1 14 8 14 27z' /><circle cx='20' cy='15' r='1.3' style={{ fill: 'var(--sk)' }} /></symbol>
      <symbol id='B' viewBox='0 0 45 45'><circle cx='22.5' cy='10' r='2.6' /><path d='M22.5 13c-5 4-8 8-8 13 0 3 2 6 4 7h8c2-1 4-4 4-7 0-5-3-9-8-13z' /><path d='M12 38h21v-4.5H12z' /></symbol>
      <symbol id='Q' viewBox='0 0 45 45'><path d='M9 16l5 19h17l5-19-7 9-3.5-12-3 12-3-12-3.5 12z' /><circle cx='9' cy='15' r='2.2' /><circle cx='19.5' cy='12' r='2.2' /><circle cx='25.5' cy='12' r='2.2' /><circle cx='36' cy='15' r='2.2' /><path d='M12 38h21v-3.5H12z' /></symbol>
      <symbol id='K' viewBox='0 0 45 45'><path d='M21 5h3v3h3v3h-3v3h-3v-3h-3V8h3z' /><path d='M22.5 15c-6 3-9 8-9 13 0 3 1 5 3 7h12c2-2 3-4 3-7 0-5-3-10-9-13z' /><path d='M12 38h21v-3.5H12z' /></symbol>
    </defs>
  </svg>
);
const STYLE = `.pc{width:90%;height:90%;fill:var(--pf);stroke:var(--sk);stroke-width:1.5;stroke-linejoin:round;filter:drop-shadow(0 1px 1px rgba(0,0,0,.45));pointer-events:none}
.clk{margin-left:auto;font-variant-numeric:tabular-nums;font-weight:800;background:#1b1a18;padding:2px 6px;border-radius:5px;font-size:.85rem}
.clk.low{background:#8a2a2a}.clk+i{margin-left:6px}
.caps{display:flex;gap:8px;flex-wrap:wrap;margin:0 0 6px}
.cp{display:flex;align-items:center;gap:1px;background:var(--card);padding:2px 8px;border-radius:8px}
.cp i{width:10px;height:10px;border-radius:50%;margin-right:5px;border:1px solid #666}
.moves{display:flex;flex-wrap:wrap;gap:4px 10px;max-height:104px;overflow:auto;background:var(--card);border-radius:10px;padding:10px 12px;font-size:.9rem;color:var(--mut);margin-bottom:12px}
.moves b{color:#fff;margin-right:3px}.moves .dt{display:inline-block;width:8px;height:8px;border-radius:50%;margin-right:4px}
.tcs{display:flex;gap:6px;flex-wrap:wrap;margin:8px 0 12px}.tcs button{padding:7px 12px;font-size:.9rem}`;
function Pc({ t, color, stroke = '#1b1b1b', size }) {
  return <svg className='pc' viewBox='0 0 45 45' style={{ '--pf': color, '--sk': stroke, ...(size && { width: size, height: size }) }}><use href={'#' + t} /></svg>;
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
  const [games, setGames] = useState([]), [err, setErr] = useState(''), [tc, setTc] = useState(300);
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
  const make = async (m) => { try { go((await api('create', { mode: m, tc })).id); } catch (e) { setErr(e.message); } };
  const open = async (g) => {
    try { if (g.status === 'waiting' && !g.players.includes(me.id)) await api('join', { id: g.id }); go(g.id); } catch (e) { setErr(e.message); }
  };
  return (
    <>
      <div className='card'>
        <b>Start a game</b>
        <div className='tcs'>{TCS.map(([v, l]) => <button key={v} className={tc === v ? 'pri' : ''} onClick={() => setTc(v)}>{l}</button>)}</div>
        <div className='row'>
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
          <div><b>{LBL[x.mode]} · {x.tc ? x.tc / 60 + ' min' : 'no clock'}</b><div className='mut'>{x.status === 'waiting' ? 'Waiting' : 'Live'} · {x.players.map((p) => name(p).username).join(', ')}</div></div>
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
          {x.t && <Pc t={x.t} color={x.color} stroke={x.sk} />}
          {x.tg && <i className='dot' />}
        </div>
      : <div key={k} />);
  }
  return <div className='board' style={{ '--n': n }}>{out}</div>;
}

const INIT = { p: 8, n: 2, b: 2, r: 2, q: 1 };
function chessCaps(c) {
  const cnt = { w: {}, b: {} };
  c.board().flat().forEach((x) => { if (x && x.type !== 'k') cnt[x.color][x.type] = (cnt[x.color][x.type] || 0) + 1; });
  const miss = (col) => Object.entries(INIT).flatMap(([t, k]) => Array(Math.max(0, k - (cnt[col][t] || 0))).fill(t.toUpperCase()));
  return [miss('b'), miss('w')];
}

function Game({ id, me, go }) {
  const [g, setG] = useState(null), [st, setSt] = useState(null), [sel, setSel] = useState(null), [err, setErr] = useState('');
  const [, tick] = useState(0), [mute, setMute] = useState(localStorage.getItem('mute') === '1');
  const recv = useRef(Date.now()), prevN = useRef(null), flagN = useRef(-1), mv = useRef(null);
  useEffect(() => {
    let on = true;
    sb.from('games').select('*').eq('id', id).maybeSingle().then(({ data }) => on && setG(data || false));
    sb.from('game_state').select('state').eq('game_id', id).maybeSingle().then(({ data }) => on && data && setSt(data.state));
    const ch = sb.channel('g' + id)
      .on('postgres_changes', { event: '*', schema: 'public', table: 'games', filter: `id=eq.${id}` }, (p) => setG(p.eventType === 'DELETE' ? false : p.new))
      .on('postgres_changes', { event: '*', schema: 'public', table: 'game_state', filter: `game_id=eq.${id}` }, (p) => p.new?.state && setSt((s) => (s && s.n > p.new.state.n ? s : p.new.state)))
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
  useEffect(() => { const t = setInterval(() => tick((x) => x + 1), 250); return () => clearInterval(t); }, []);
  useEffect(() => {
    recv.current = Date.now();
    if (st && prevN.current !== null && st.n > prevN.current) {
      const last = chess ? chess.history().slice(-1)[0] : (st.log || []).slice(-1)[0];
      sfx[/x/.test(last || '') ? 'cap' : 'move']();
    }
    prevN.current = st ? st.n : null;
    if (mv.current) mv.current.scrollTop = 1e6;
  }, [st?.n]);
  useEffect(() => { if (g?.status === 'finished' && prevN.current !== null) sfx.end(); }, [g?.status]);
  useEffect(() => {
    // if the player on turn is out of time, any player's screen asks the server to confirm it
    if (!st?.clock || g?.status !== 'active' || st.seats.indexOf(me.id) < 0) return;
    if (st.clock[st.turn] - (Date.now() - recv.current) <= 0 && flagN.current !== st.n) {
      flagN.current = st.n;
      api('flag', { id }).catch(() => setTimeout(() => { flagN.current = -1; }, 1500));
    }
  });
  if (g === null) return <p className='mut'>Loading…</p>;
  if (g === false) return <p>Game not found. <button onClick={() => go(null)}>Lobby</button></p>;
  const inGame = g.players.includes(me.id);
  const act = (a, b = {}) => api(a, { id, ...b }).catch((e) => setErr(e.message));

  if (g.status === 'waiting' || !st) return (
    <div className='card'>
      <h2>{LBL[g.mode]} game · {g.tc ? g.tc / 60 + ' min' : 'no clock'}</h2>
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
  const clockOf = (s) => {
    if (!st.clock || st.clock[s] == null) return null;
    const ms = Math.max(0, st.clock[s] - (!over && st.turn === s ? Date.now() - recv.current : 0));
    return { t: Math.floor(ms / 60000) + ':' + String(Math.floor(ms / 1000) % 60).padStart(2, '0'), low: ms < 10000 };
  };
  // The move shows on your screen instantly; the server confirms in the background (and we undo if it refuses).
  const doMove = async (from, to) => {
    const prev = st;
    setErr('');
    try {
      let next;
      if (chess) {
        const c = new Chess(chess.fen()), m = c.move({ from, to, promotion: 'q' });
        next = { ...st, moves: [...st.moves, from + to + (m.promotion || '')], turn: 1 - st.turn, n: st.n + 1 };
      } else next = E.apply(st, from[0], from[1], to[0], to[1]);
      if (st.clock) { next.clock = [...st.clock]; next.clock[seat] = st.clock[seat] - (Date.now() - recv.current); }
      setSt(next);
      const r = await api('move', { id, from, to });
      if (r.state) setSt((s) => (s && s.n > r.state.n ? s : r.state));
      if (r.result) setG((x) => ({ ...x, status: 'finished', result: r.result }));
    } catch (e) { setSt(prev); setErr(e.message); }
  };
  let cell, pick;
  if (chess) {
    const bd = chess.board(), flip = seat === 1, lm = st.moves[st.moves.length - 1];
    const tg = sel ? chess.moves({ square: sel, verbose: true }).map((m) => m.to) : [];
    const at = (r, c) => { const [br, bc] = flip ? [7 - r, 7 - c] : [r, c]; return ['abcdefgh'[bc] + (8 - br), bd[br][bc]]; };
    cell = (r, c) => {
      const [sq, p] = at(r, c), w = p?.color === 'w';
      return { ok: true, t: p && p.type.toUpperCase(), color: w ? '#fff' : '#3a3a3a', sk: w ? '#222' : '#000', sel: sel === sq, tg: tg.includes(sq), last: lm && (lm.slice(0, 2) === sq || lm.slice(2, 4) === sq) };
    };
    pick = (r, c) => {
      const [sq, p] = at(r, c);
      if (!myTurn) return;
      if (sel && tg.includes(sq)) { const f = sel; setSel(null); doMove(f, sq); }
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
      return { ok: true, t: p && p[1], color: p && COL[+p[0]], sk: '#1b1b1b', sel: sel && hit(sel, br, bc), tg: tg.some((t) => hit(t, br, bc)), last: l && ((l[0] === br && l[1] === bc) || (l[2] === br && l[3] === bc)) };
    };
    pick = (r, c) => {
      const [br, bc] = rot(r, c);
      if (!myTurn) return;
      if (sel && tg.some((t) => hit(t, br, bc))) { const f = sel; setSel(null); doMove(f, [br, bc]); }
      else { const p = st.board[br * 14 + bc]; setSel(p && +p[0] === seat ? [br, bc] : null); }
    };
  }
  const caps = chess ? chessCaps(chess) : st.seats.map((_, s) => (st.log || []).filter((e) => +e[0] === s && e.includes('x')).map((e) => e.split('x')[1]));
  const res = g.result;
  return (
    <>
      <style>{STYLE}</style>
      <div className='bar'>
        {st.seats.map((pid, s) => { const c = clockOf(s); return pid && (
          <div key={s} className={'pl' + (!over && st.turn === s ? ' on' : '') + (st.alive && !st.alive[s] ? ' out' : '')}>
            <Av p={name(pid)} s={26} /><b>{name(pid).username}</b>
            {c && <span className={'clk' + (c.low ? ' low' : '')}>{c.t}</span>}
            <i style={{ background: chess ? (s ? '#111' : '#fff') : COL[s] }} />
          </div>
        ); })}
      </div>
      {res && <p className='big'>{res.winner ? `${name(res.winner).username} wins (${res.reason})` : `Draw (${res.reason})`}</p>}
      {!res && <p className='mut'>{seat < 0 ? 'Spectating' : myTurn ? 'Your turn' : 'Waiting for opponent'}</p>}
      <Board n={chess ? 8 : 14} cell={cell} pick={pick} />
      {caps.some((c) => c.length) && (
        <div className='caps'>
          {st.seats.map((pid, s) => pid && caps[s].length > 0 && (
            <span key={s} className='cp'><i style={{ background: chess ? (s ? '#111' : '#fff') : COL[s] }} />{caps[s].map((t, i) => <Pc key={i} t={t} color='#cfcfcf' stroke='#333' size={18} />)}</span>
          ))}
        </div>
      )}
      {err && <p className='err'>{err}</p>}
      <div className='moves' ref={mv}>
        {chess
          ? (chess.history().length ? chess.history().map((m, i) => <span key={i}>{i % 2 === 0 && <b>{i / 2 + 1}.</b>}{m}</span>) : 'No moves yet')
          : ((st.log || []).length ? st.log.map((e, i) => <span key={i}><i className='dt' style={{ background: COL[+e[0]] }} />{e.slice(1).replace('x', ' ×')}</span>) : 'No moves yet')}
      </div>
      <div className='row' style={{ justifyContent: 'center' }}>
        {seat >= 0 && !over && <button onClick={() => confirm('Resign?') && act('resign')}>Resign</button>}
        <button onClick={() => { localStorage.setItem('mute', mute ? '0' : '1'); setMute(!mute); }}>{mute ? 'Sound off' : 'Sound on'}</button>
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
  if (!me) return <>{SYM}<style>{STYLE}</style><Gate onDone={setMe} /></>;
  return (
    <main>
      {SYM}
      <style>{STYLE}</style>
      <header>
        <h1 onClick={() => go(null)}>gooningchessbuddies</h1>
        <div className='me'><Av p={me} s={32} /><b>{me.username}</b></div>
      </header>
      {gid ? <Game id={gid} me={me} go={go} /> : <Lobby me={me} go={go} />}
    </main>
  );
}
