"""Generates launcher + logo images for Saucyfy from your artwork.

Upload your image anywhere inside saucyfy/assets/ (png / jpg / jpeg / webp). If there are several,
the largest file is used. The middle of the artwork is kept and a little is trimmed off the edges.
If no image is found a drawn fallback icon is generated so the build never breaks.
"""
import glob
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
ASSETS = os.path.join(ROOT, "assets")


def find_upload():
    found = []
    for ext in ("png", "jpg", "jpeg", "webp", "PNG", "JPG", "JPEG", "WEBP"):
        found += glob.glob(os.path.join(ASSETS, f"*.{ext}"))
    if not found:
        return None
    return max(found, key=os.path.getsize)


def fallback():
    """Drawn stand-in: blue sky, green hill, silver media-player frame."""
    s = 1024
    im = Image.new("RGB", (s, s))
    d = ImageDraw.Draw(im)
    for y in range(s):
        t = y / s
        d.line([(0, y), (s, y)], fill=(int(25 + 70 * t), int(120 + 80 * t), int(235 + 10 * t)))
    d.ellipse((-350, 650, s + 350, s + 700), fill=(84, 182, 48))
    d.rounded_rectangle((200, 170, 824, 800), radius=100, fill=(205, 210, 217), outline=(150, 156, 166), width=12)
    screen = Image.new("RGB", (500, 340))
    sd = ImageDraw.Draw(screen)
    for y in range(340):
        sd.line([(0, y), (500, y)], fill=(40 + y // 6, 110 + y // 5, 200))
    sd.ellipse((-120, 170, 620, 560), fill=(58, 150, 52))
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 499, 339), radius=46, fill=255)
    im.paste(screen, (262, 240), mask)
    d.ellipse((290, 620, 470, 800), fill=(225, 230, 236), outline=(130, 136, 146), width=10)
    d.ellipse((330, 660, 430, 760), fill=(70, 160, 240))
    d.rounded_rectangle((520, 650, 650, 760), radius=40, fill=(186, 192, 200))
    d.rounded_rectangle((670, 650, 780, 760), radius=40, fill=(186, 192, 200))
    return im


def load():
    p = find_upload()
    if p:
        try:
            im = Image.open(p).convert("RGB")
            w, h = im.size
            s = min(w, h)
            left, top = (w - s) // 2, (h - s) // 2
            im = im.crop((left, top, left + s, top + s))
            c = int(s * 0.06)
            print("using", os.path.basename(p))
            return im.crop((c, c, s - c, s - c))
        except Exception as exc:
            print("could not read", p, exc)
    print("no usable image in assets/, drawing fallback icon")
    return fallback()


def save(img, rel):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


base = load().resize((1024, 1024), Image.LANCZOS)

# Adaptive icon foreground (108dp @ xxxhdpi = 432px). Artwork is shrunk so the whole visible
# (72dp) area is picture; the outer ring is a blurred copy so masks never show seams.
bg = base.resize((432, 432), Image.LANCZOS).filter(ImageFilter.GaussianBlur(14))
fg = base.resize((330, 330), Image.LANCZOS)
bg.paste(fg, (51, 51))
save(bg, "drawable-nodpi/ic_launcher_foreground.png")

# In-app logo used on the sign-in screen.
save(base.resize((512, 512), Image.LANCZOS), "drawable-nodpi/logo.png")
print("icons generated")
