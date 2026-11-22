"""Generates launcher + logo images for Saucyfy from the uploaded artwork.

Preference order: assets/icon.png|jpg|jpeg (full-res, you upload) -> assets/icon.b64 (embedded copy).
The middle of the artwork is kept; a little is trimmed from the outer edges (top/bottom included).
"""
import base64
import io
import os

from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")


def load():
    for name in ("icon.png", "icon.jpg", "icon.jpeg"):
        p = os.path.join(ROOT, "assets", name)
        if os.path.exists(p):
            im = Image.open(p).convert("RGB")
            w, h = im.size
            s = min(w, h)
            left, top = (w - s) // 2, (h - s) // 2
            im = im.crop((left, top, left + s, top + s))
            c = int(s * 0.06)
            return im.crop((c, c, s - c, s - c))
    try:
        with open(os.path.join(ROOT, "assets", "icon.b64")) as f:
            data = base64.b64decode(f.read().strip())
        return Image.open(io.BytesIO(data)).convert("RGB")
    except Exception as exc:  # fall back to a plain gradient so the build never breaks
        print("icon fallback:", exc)
        im = Image.new("RGB", (288, 288), (30, 127, 224))
        return im


def save(img, rel):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


base = load().resize((1024, 1024), Image.LANCZOS)

# Adaptive icon foreground (108dp @ xxxhdpi = 432px). The artwork is shrunk so the whole
# visible (72dp) area is picture, and the outer ring is a blurred copy so masks never show seams.
bg = base.resize((432, 432), Image.LANCZOS).filter(ImageFilter.GaussianBlur(14))
fg = base.resize((330, 330), Image.LANCZOS)
bg.paste(fg, (51, 51))
save(bg, "drawable-nodpi/ic_launcher_foreground.png")

# In-app logo used on the sign-in screen.
save(base.resize((512, 512), Image.LANCZOS), "drawable-nodpi/logo.png")
print("icons generated")
