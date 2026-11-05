import io, math, os, random
from datetime import datetime
from urllib.parse import quote
import chess, chess.engine, chess.pgn, requests
from taunts import ROAST_LOSS, ROAST_WIN, GOAT_LOSS, GOAT_WIN

KEY = os.environ["SUPABASE_SERVICE_KEY"]
SB = os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1"
HOOK = os.environ["DISCORD_WEBHOOK_URL"]
SF = os.environ.get("STOCKFISH_PATH", "/usr/games/stockfish")
H = {"apikey": KEY, "Authorization": f"Bearer {KEY}", "Content-Type": "application/json"}
CC = {"User-Agent": "chess-recap"}

LABELS = ["Brilliant", "Great", "Best", "Excellent", "Good", "Book", "Inaccuracy", "Mistake", "Miss", "Blunder"]
MARKS = {"Brilliant": "!!", "Great": "!", "Best": "*", "Excellent": "+", "Good": "=",
         "Book": "bk", "Inaccuracy": "?!", "Mistake": "?", "Miss": "x", "Blunder": "??"}
VAL = {chess.PAWN: 1, chess.KNIGHT: 3, chess.BISHOP: 3, chess.ROOK: 5, chess.QUEEN: 9, chess.KING: 0}
MAX_GAMES_PER_RUN = 10
DEPTH = 12
BOOK_PLIES = 12  # early moves that are not worse than "Excellent" count as Book

# Friendly trash talk, keyed by lowercase chess.com username (the lines live in taunts.py)
TAUNTS = {
    "tungpearlsahur": {"name": "Sabir", "style": "roast"},
    "nafikhann": {"name": "Nafi", "style": "roast"},
    "raybuy": {"name": "Jahid", "style": "goat"},
}
LOSS_RESULTS = {"checkmated", "timeout", "resigned", "lose", "abandoned"}


def wp(cp):  # centipawns -> win % (0-100)
    return 50 + 50 * (2 / (1 + math.exp(-0.00368208 * cp)) - 1)


def move_accuracy(loss):  # Lichess-style accuracy from win% lost
    return min(100, max(0, 103.1668 * math.exp(-0.04354 * loss) - 3.1669))


def material(board, color):
    return sum(VAL[p.piece_type] for p in board.piece_map().values() if p.color == color)


def is_sacrifice(board_before, move, pv_after, color):
    """True if the mover ends up at least 2 points of material down after the expected next plies."""
    d0 = material(board_before, color) - material(board_before, not color)
    b = board_before.copy(stack=False)
    b.push(move)
    for mv in pv_after[:2]:
        if mv in b.legal_moves:
            b.push(mv)
    d1 = material(b, color) - material(b, not color)
    return d1 - d0 <= -2


def classify(i, move, loss, prev_loss, top, gap, wp_before, wp_after, board_before, pv_after, color):
    if i < BOOK_PLIES and loss < 2:
        return "Book"
    if loss < 2 and wp_after >= 45 and wp_before <= 90 and is_sacrifice(board_before, move, pv_after, color):
        return "Brilliant"
    if move == top:
        return "Great" if gap >= 15 else "Best"
    if loss < 2:
        return "Excellent"
    if loss < 5:
        return "Good"
    if prev_loss >= 10 and loss < 20:
        return "Miss"  # opponent just slipped and this move did not punish it
    if loss < 10:
        return "Inaccuracy"
    if loss < 20:
        return "Mistake"
    return "Blunder"


def analyze(game, engine):
    board = game.board()
    moves = list(game.mainline_moves())
    boards, wps, pvs, gaps = [], [], [], []
    sans, best_sans, fens = [], [], []
    for i in range(len(moves) + 1):
        boards.append(board.copy(stack=False))
        if board.is_checkmate():
            wps.append(0 if board.turn == chess.WHITE else 100); pvs.append([]); gaps.append(0)
        elif board.is_game_over():
            wps.append(50); pvs.append([]); gaps.append(0)
        else:
            info = engine.analyse(board, chess.engine.Limit(depth=DEPTH), multipv=2)
            wps.append(wp(info[0]["score"].white().score(mate_score=10000)))
            pvs.append(list(info[0].get("pv", []))[:3])
            gap = 0
            if len(info) > 1:
                a = wp(info[0]["score"].pov(board.turn).score(mate_score=10000))
                b = wp(info[1]["score"].pov(board.turn).score(mate_score=10000))
                gap = a - b
            gaps.append(gap)
        if i < len(moves):
            sans.append(board.san(moves[i]))
            best_sans.append(board.san(pvs[i][0]) if pvs[i] else None)
            board.push(moves[i])
            fens.append(board.fen())

    losses = []
    for i in range(len(moves)):
        l = wps[i] - wps[i + 1] if i % 2 == 0 else wps[i + 1] - wps[i]
        losses.append(max(l, 0))

    sides = {s: {"counts": dict.fromkeys(LABELS, 0), "acc": [], "moves": []} for s in ("white", "black")}
    for i, m in enumerate(moves):
        white = i % 2 == 0
        key = "white" if white else "black"
        color = chess.WHITE if white else chess.BLACK
        wp_before = wps[i] if white else 100 - wps[i]
        wp_after = wps[i + 1] if white else 100 - wps[i + 1]
        top = pvs[i][0] if pvs[i] else None
        label = classify(i, m, losses[i], losses[i - 1] if i else 0, top, gaps[i],
                         wp_before, wp_after, boards[i], pvs[i + 1], color)
        num = f"{i // 2 + 1}." if white else f"{i // 2 + 1}..."
        sides[key]["counts"][label] += 1
        sides[key]["acc"].append(move_accuracy(losses[i]))
        sides[key]["moves"].append({"label": label, "loss": losses[i], "text": f"{num} {sans[i]}",
                                    "better": best_sans[i], "fen": fens[i], "uci": m.uci()})

    for key, s in sides.items():
        s["accuracy"] = round(sum(s["acc"]) / len(s["acc"]), 1) if s["acc"] else 0.0
        blunders = [x for x in s["moves"] if x["label"] == "Blunder"]
        s["blunders"] = blunders
        s["worst"] = max(blunders, key=lambda x: x["loss"]) if blunders else None
        s["best"] = None
        for lab in ("Brilliant", "Great", "Excellent"):
            found = [x for x in s["moves"] if x["label"] == lab]
            if found:
                s["best"] = found[0]
                break
    return sides


def recent_games(username, since):
    r = requests.get(f"https://api.chess.com/pub/player/{username}/games/archives", headers=CC, timeout=20)
    if not r.ok:
        return []
    out = []
    for url in r.json().get("archives", [])[-2:]:
        g = requests.get(url, headers=CC, timeout=20)
        if g.ok:
            out += [x for x in g.json().get("games", [])
                    if x.get("end_time", 0) > since and x.get("pgn") and x.get("rules") == "chess"]
    return out


def board_img(fen, uci, orientation):  # free board picture from Lichess
    return ("https://lichess1.org/export/fen.gif?fen=" + quote(fen, safe="")
            + "&lastMove=" + uci + "&orientation=" + orientation)


def table(res):
    rows = [f"{'':<11}{'White':>6}{'Black':>7}",
            f"{'Accuracy':<11}{res['white']['accuracy']:>6}{res['black']['accuracy']:>7}"]
    for lab in LABELS:
        rows.append(f"{lab:<11}{res['white']['counts'][lab]:>3} {MARKS[lab]:^3}{res['black']['counts'][lab]:>3}")
    return "```\n" + "\n".join(rows) + "\n```"


def taunt_for(g):
    lines = []
    for side, other in (("white", "black"), ("black", "white")):
        p = g[side]
        cfg = TAUNTS.get(p["username"].lower())
        if not cfg:
            continue
        won, lost = p["result"] == "win", p["result"] in LOSS_RESULTS
        if not (won or lost):
            continue  # draws get no comment
        if cfg["style"] == "goat":
            pool = GOAT_WIN if won else GOAT_LOSS
        else:
            pool = ROAST_WIN if won else ROAST_LOSS
        lines.append(random.choice(pool).format(name=cfg["name"], opp=g[other]["username"]))
    return "\n".join(lines)


def post(g, res, names, ids, mention_ids):
    w, b = g["white"], g["black"]
    fmt = lambda s: ("\n".join(f"{x['text']} (better: {x['better']})" if x["better"] else x["text"]
                               for x in s["blunders"][:6])
                     + (f"\n+{len(s['blunders']) - 6} more" if len(s["blunders"]) > 6 else "")) or "None"
    embeds = [{
        "title": f"{w['username']} vs {b['username']}",
        "url": g["url"],
        "description": (f"White {w['username']} ({w['rating']}): {w['result']}\n"
                        f"Black {b['username']} ({b['rating']}): {b['result']}\n" + table(res)),
        "fields": [
            {"name": f"{w['username']} blunders", "value": fmt(res["white"])[:1000]},
            {"name": f"{b['username']} blunders", "value": fmt(res["black"])[:1000]},
        ],
    }]
    # pictures for the registered players' own worst blunder and best move
    for did in ids:
        uname = names.get(did, "")
        side = "white" if w["username"].lower() == uname else "black"
        s = res[side]
        if s["worst"]:
            x = s["worst"]
            embeds.append({"title": f"{uname}: blunder {x['text']}",
                           "description": f"Better: {x['better']}" if x["better"] else "",
                           "color": 0xE5484D, "image": {"url": board_img(x["fen"], x["uci"], side)}})
        if s["best"]:
            x = s["best"]
            embeds.append({"title": f"{uname}: {x['label'].lower()} move {x['text']}",
                           "color": 0x5BA34A, "image": {"url": board_img(x["fen"], x["uci"], side)}})
    pings = [i for i in ids if i in mention_ids]  # people who asked to stop pings are skipped
    content = (" ".join(f"<@{i}>" for i in pings) + "\n" + taunt_for(g)).strip()
    payload = {"content": content,
               "allowed_mentions": {"users": pings}, "embeds": embeds[:10]}
    requests.post(HOOK, json=payload, timeout=20).raise_for_status()


def main():
    users = requests.get(f"{SB}/users?select=*", headers=H, timeout=20).json()
    names = {u["discord_id"]: u["chess_username"].lower() for u in users}
    mention_ids = {u["discord_id"] for u in users if u.get("mention", True)}

    pending = {}  # game url -> {"g": game, "ids": set of discord ids}
    for u in users:
        created = datetime.fromisoformat(u["created_at"].replace("Z", "+00:00")).timestamp()
        since = max(created, u.get("last_end_time") or 0)
        for g in recent_games(u["chess_username"], since):
            pending.setdefault(g["url"], {"g": g, "ids": set()})["ids"].add(u["discord_id"])

    if pending:
        engine = chess.engine.SimpleEngine.popen_uci(SF)
        engine.configure({"Threads": 2, "Hash": 128})
        try:
            todo = sorted(pending.items(), key=lambda kv: kv[1]["g"]["end_time"])[:MAX_GAMES_PER_RUN]
            for url, item in todo:
                game = chess.pgn.read_game(io.StringIO(item["g"]["pgn"]))
                res = analyze(game, engine)
                post(item["g"], res, names, item["ids"], mention_ids)
                for did in item["ids"]:
                    requests.patch(f"{SB}/users?discord_id=eq.{did}", headers=H,
                                   json={"last_end_time": item["g"]["end_time"]}, timeout=20)
                requests.post(f"{SB}/analyzed_games", headers=H, json={"game_url": url}, timeout=20)
                print("Posted", url)
        finally:
            engine.quit()
    else:
        print("No new games")

    # Every 5 matches, wipe the match log. Accounts (users table) are never touched.
    rows = requests.get(f"{SB}/analyzed_games?select=game_url", headers=H, timeout=20).json()
    if len(rows) >= 5:
        requests.delete(f"{SB}/analyzed_games?game_url=not.is.null", headers=H, timeout=20)
        print("Cleared", len(rows), "old matches")


if __name__ == "__main__":
    main()
