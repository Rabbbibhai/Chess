import io, math, os
from datetime import datetime
import chess, chess.engine, chess.pgn, requests

KEY = os.environ["SUPABASE_SERVICE_KEY"]
SB = os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1"
HOOK = os.environ["DISCORD_WEBHOOK_URL"]
SF = os.environ.get("STOCKFISH_PATH", "/usr/games/stockfish")
H = {"apikey": KEY, "Authorization": f"Bearer {KEY}", "Content-Type": "application/json"}
CC = {"User-Agent": "chess-recap"}
LABELS = ["Great", "Best", "Excellent", "Good", "Inaccurate", "Mistake", "Blunder"]
MAX_GAMES_PER_RUN = 10
DEPTH = 12


def wp(cp):  # centipawns -> win % (0-100)
    return 50 + 50 * (2 / (1 + math.exp(-0.00368208 * cp)) - 1)


def analyze(game, engine):
    board = game.board()
    moves = list(game.mainline_moves())
    wps, tops, gaps = [], [], []
    played, best_san = [], []  # per position: white win %, best move, gap best vs 2nd (mover POV)
    for i in range(len(moves) + 1):
        if board.is_checkmate():
            wps.append(0 if board.turn == chess.WHITE else 100); tops.append(None); gaps.append(0)
        elif board.is_game_over():
            wps.append(50); tops.append(None); gaps.append(0)
        else:
            info = engine.analyse(board, chess.engine.Limit(depth=DEPTH), multipv=2)
            wps.append(wp(info[0]["score"].white().score(mate_score=10000)))
            tops.append(info[0]["pv"][0])
            gap = 0
            if len(info) > 1:
                a = wp(info[0]["score"].pov(board.turn).score(mate_score=10000))
                b = wp(info[1]["score"].pov(board.turn).score(mate_score=10000))
                gap = a - b
            gaps.append(gap)
        if i < len(moves):
            played.append(board.san(moves[i]))
            best_san.append(board.san(tops[i]) if tops[i] else None)
            board.push(moves[i])

    counts = {"white": dict.fromkeys(LABELS, 0), "black": dict.fromkeys(LABELS, 0)}
    blunders = {"white": [], "black": []}
    for i, m in enumerate(moves):
        white = i % 2 == 0
        loss = max(wps[i] - wps[i + 1] if white else wps[i + 1] - wps[i], 0)
        if m == tops[i]:
            label = "Great" if gaps[i] >= 15 else "Best"
        elif loss < 2: label = "Excellent"
        elif loss < 5: label = "Good"
        elif loss < 10: label = "Inaccurate"
        elif loss < 20: label = "Mistake"
        else:
            label = "Blunder"
            num = f"{i // 2 + 1}." if white else f"{i // 2 + 1}..."
            better = f" (better: {best_san[i]})" if best_san[i] else ""
            blunders["white" if white else "black"].append(f"{num} {played[i]}{better}")
        counts["white" if white else "black"][label] += 1
    return counts, blunders


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


def post(g, counts, blunders, ids):
    w, b = g["white"], g["black"]
    block = lambda c: "```\n" + "\n".join(f"{k:<11}{v}" for k, v in c.items()) + "\n```"
    fmt = lambda l: "\n".join(l[:6]) + (f"\n+{len(l) - 6} more" if len(l) > 6 else "") if l else "None"
    payload = {
        "content": " ".join(f"<@{i}>" for i in ids),
        "allowed_mentions": {"users": list(ids)},
        "embeds": [{
            "title": f"{w['username']} vs {b['username']}",
            "url": g["url"],
            "description": f"{w['username']}: {w['result']}. {b['username']}: {b['result']}.",
            "fields": [
                {"name": f"White: {w['username']} ({w['rating']})", "value": block(counts["white"]), "inline": True},
                {"name": f"Black: {b['username']} ({b['rating']})", "value": block(counts["black"]), "inline": True},
                {"name": f"{w['username']} blunders", "value": fmt(blunders["white"]), "inline": False},
                {"name": f"{b['username']} blunders", "value": fmt(blunders["black"]), "inline": False},
            ],
        }],
    }
    requests.post(HOOK, json=payload, timeout=20).raise_for_status()


def main():
    users = requests.get(f"{SB}/users?select=*", headers=H, timeout=20).json()

    pending = {}  # game url -> {"g": game, "ids": set of discord ids}
    for u in users:
        created = datetime.fromisoformat(u["created_at"].replace("Z", "+00:00")).timestamp()
        since = max(created, u.get("last_end_time") or 0)  # per-user bookmark replaces the old "already analyzed" list
        for g in recent_games(u["chess_username"], since):
            pending.setdefault(g["url"], {"g": g, "ids": set()})["ids"].add(u["discord_id"])

    if pending:
        engine = chess.engine.SimpleEngine.popen_uci(SF)
        engine.configure({"Threads": 2, "Hash": 128})
        try:
            todo = sorted(pending.items(), key=lambda kv: kv[1]["g"]["end_time"])[:MAX_GAMES_PER_RUN]
            for url, item in todo:
                game = chess.pgn.read_game(io.StringIO(item["g"]["pgn"]))
                counts, blunders = analyze(game, engine)
                post(item["g"], counts, blunders, item["ids"])
                for did in item["ids"]:  # move each player's bookmark past this game
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
