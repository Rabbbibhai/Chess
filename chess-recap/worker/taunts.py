# Friendly trash talk and hype. {name} = player's nickname, {opp} = opponent's chess.com username.


def _mix(heads, tags):
    return [f"{h} {t}" for h in heads for t in tags]


# ---------- Sabir / Nafi: when they LOSE ----------
_LOSS_HEADS = [
    "{name} just took the biggest L of the week.",
    "{name} got absolutely cooked on the board.",
    "{name} fumbled the whole game.",
    "{name} hung a piece like it was free real estate.",
    "{name} is chess larping and the rating shows it.",
    "{name} played like an NPC with no dialogue.",
    "{name} blundered, then kept blundering for fun.",
    "{name} folded harder than a lawn chair.",
    "{name} got mogged by a pawn.",
    "{name} had zero rizz with that queen.",
    "{name} fell off hard.",
    "{name} was down bad from the opening.",
    "{name} thought he was cooking but he was the one being cooked.",
    "{name} went from main character to background NPC in one move.",
    "{name} crashed out on the board.",
    "{name} got washed by a total stranger.",
    "{name} is playing 4D chess but forgot the 2D rules.",
    "{name} left the queen hanging like a bad text.",
    "{name} lost the game and the aura in the same move.",
    "{name} played goofy ahh moves all game.",
    "{name} got clapped.",
    "{name} is not him. Not even close.",
    "{name} walked into that like a tutorial boss.",
    "{name} blundered his whole bag.",
    "{name} lost and the engine is laughing.",
    "{name} was cooking, just the wrong meal.",
    "{name} speedran an L and called it a strategy.",
    "{name} brought a knife to a queen fight and lost the knife.",
    "{name} treated his king like an NPC and left him unprotected.",
    "{name} was in his flop era the entire game.",
]
_LOSS_TAGS = [
    "Skill issue.", "L + ratio.", "Touch grass.", "No cap.", "Fr fr.", "Bro is cooked. 💀",
    "NPC behavior.", "Massive L.", "Mid at best.", "Negative aura.", "Washed.", "Bozo behavior. 🤡",
    "Ngl that was crazy bad.", "Chat, is this real?", "Delulu.", "Clapped.", "Down astronomical. 📉",
    "Unc moves.", "Cringe.", "It's giving loser.",
]
ROAST_LOSS = [
    "{name}, tmi ki dumb? 💀",
    "{name} lost again. The engine is crying.",
    "{name}, even a random move generator would have done better.",
    "{name} hung everything and called it a strategy. 🤡",
    "Bro {name}, chess is not a contact sport against your own pieces.",
    "{name}, have you thought about checkers?",
    "{name} speedran losing. New personal best.",
    "{name}, your brain went on vacation mid-game.",
    "That was painful to watch, {name}. Truly.",
    "{name} played like he was blindfolded. With oven mitts. 🧤",
    "{name} didn't get beaten, he gift-wrapped the game. 🎁",
    "{name}, skill issue. A big one.",
    "RIP {name}'s rating 📉",
    "{name}, tmi ki dumb? Chess larping is not a personality.",
    "{name}, tmi ki dumb? That was an L speedrun.",
    "{name}, tmi ki dumb? Delete the app. 💀",
    "{name}, tmi ki dumb? Even the bots are embarrassed.",
] + _mix(_LOSS_HEADS, _LOSS_TAGS)

# ---------- Sabir / Nafi: when they WIN (it was luck) ----------
_WIN_HEADS = [
    "{name} won but it was pure fluke.",
    "{name} got the luckiest win of his life.",
    "{name} won because the opponent threw, not because of skill.",
    "{name} won by accident, certified fluke.",
    "{name} stumbled into a win.",
    "{name} won by mistake.",
    "{name} got carried by the opponent's blunders.",
    "{name} got lucky and it shows.",
    "{name} won with no clue what was going on.",
    "{name} hit a lucky one, chat.",
    "{name} won, but the engine says it was mid.",
    "{name} is not that guy, the opponent just fell off.",
    "{name} won through sheer delulu energy.",
    "{name} survived on pure vibes.",
    "{name} backed into a W.",
    "{name} won, but the aura is still negative.",
    "{name} got a free W from the chess gods.",
    "{name} larped his way to a win.",
]
_WIN_TAGS = [
    "Pure luck.", "Fluke fr.", "Don't let it get to your head.", "Cap if you say it was skill.",
    "Certified accident.", "Ngl that was lucky.", "Next game he's cooked.", "Not skill, just vibes.",
    "Run it back and it's an L.", "Mid win, but a win.", "Touch grass, it was luck.", "Lucky, no cap. 🍀",
]
ROAST_WIN = [
    "{name} won?? Pure luck, the opponent must have blinked. 🍀",
    "{name} won by mistake. Don't get used to it.",
    "Lucky win, {name}. The opponent clearly threw it.",
    "Even a broken clock is right twice a day, {name}. Enjoy the fluke.",
    "{name} got lucky. Again. The chess gods took pity. 🙏",
    "The opponent lost, {name} just happened to be sitting there.",
    "Accident of the day: {name} won a chess game. 🎰",
    "{name} won, but we all saw that was luck.",
] + _mix(_WIN_HEADS, _WIN_TAGS)

# ---------- Jahid: when he LOSES (GOAT, opponent cheated) ----------
_GOAT_LOSS_HEADS = [
    "{name} is the GOAT 🐐 and {opp} was obviously cheating.",
    "{name} didn't lose, {opp} had an engine open.",
    "{opp} was cheating and {name} still has infinite aura.",
    "No way {opp} played that clean, sus.",
    "{name} lost to a cheater, so it doesn't count.",
    "{opp} is not that good, that was Stockfish talking.",
    "{name} got robbed by {opp}'s second monitor.",
    "{opp} had a coach whispering in his ear, {name} was playing alone.",
    "{name} outplayed {opp} spiritually.",
    "{opp} got carried by an engine, {name} remains the GOAT.",
    "{name} is still the main character, {opp} just cheated.",
    "Rigged. {name} is the sigma of chess and {opp} is a cheater.",
    "{opp} cheated and the aura of {name} is untouchable.",
    "{name} lost a game but not the crown, because {opp} cheated.",
    "Report {opp} immediately. {name} is still the GOAT.",
    "{name} was cooking and {opp} brought a cheat code.",
]
_GOAT_LOSS_TAGS = [
    "Chess.com should ban {opp}.", "Clear cheating, no cap.", "Fr fr.", "Rematch, no engines allowed.",
    "L for {opp}'s integrity.", "Aura +1000 regardless.", "Dog behavior from {opp}.", "Cheater detected. 🚨",
    "GOAT status unchanged. 🐐", "Everyone knows it.", "{name} > {opp}, always.", "Mid opponent, mid methods.",
]
GOAT_LOSS = [
    "{name} is still the GOAT 🐐. {opp} was obviously cheating.",
    "{name} didn't lose, {opp} had an engine open. Case closed.",
    "No way {opp} played that clean. Sus. {name} is still the GOAT. 🐐",
    "{opp} was cheating, everyone knows it. {name} is the real winner.",
    "Rigged! {name} is the GOAT and {opp} is clearly using Stockfish. 🕵️",
    "{name} lost to a cheater. It doesn't count. 🐐",
] + _mix(_GOAT_LOSS_HEADS, _GOAT_LOSS_TAGS)

# ---------- Jahid: when he WINS (normal, a bit of hype) ----------
_GOAT_WIN_HEADS = [
    "{name} wins.", "Solid win for {name}.", "{name} takes the W.", "Another one for {name}.",
    "{name} cooked that game.", "{name} is him.", "Clean game from {name}.", "{name} mogged the board.",
    "W for {name}.", "{name} wins, no surprise.",
]
_GOAT_WIN_TAGS = ["GG.", "Easy.", "No cap.", "Next game.", "Aura +1000.", "W rizz.", "Chess is easy. 🐐", "Clean."]
GOAT_WIN = [
    "{name} wins. GG.",
    "Solid win for {name}.",
    "{name} takes the W. Next game.",
    "Another one for {name}.",
    "{name} wins. Clean game.",
] + _mix(_GOAT_WIN_HEADS, _GOAT_WIN_TAGS)

if __name__ == "__main__":
    for n, p in (("ROAST_LOSS", ROAST_LOSS), ("ROAST_WIN", ROAST_WIN), ("GOAT_LOSS", GOAT_LOSS), ("GOAT_WIN", GOAT_WIN)):
        for line in p:
            line.format(name="X", opp="Y")
        print(n, len(p), "unique", len(set(p)))
