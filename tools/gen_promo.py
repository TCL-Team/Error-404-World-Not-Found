#!/usr/bin/env python3
"""
Generates the store-listing art: a Modrinth/CurseForge banner and a square icon.

Everything here is composed from this repo's own assets - the generated title panorama and the
generated wordmark - so the listing art can never be accused of being somebody else's screenshot.

    python3 tools/gen_promo.py
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

RES = os.path.join("src", "main", "resources")
PANO = os.path.join(RES, "resourcepacks", "title", "assets", "minecraft", "textures", "gui", "title")
LOGO = os.path.join(PANO, "minecraft.png")

OUT_DIR = os.path.join("docs", "art")

BANNER = (1568, 610)   # Modrinth project banner
ICON = (400, 400)


FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"


def mono(size):
    """A monospace face suits the fake-log framing; fall back to PIL's bitmap font if absent."""
    try:
        return ImageFont.truetype(FONT_MONO, size)
    except OSError:
        return ImageFont.load_default()


def load_logo(width):
    logo = Image.open(LOGO).convert("RGBA")
    scale = width / logo.width
    return logo.resize((width, int(logo.height * scale)), Image.LANCZOS)


def backdrop(size, tile_index=0):
    img = Image.open(os.path.join(PANO, "background", f"panorama_{tile_index}.png")).convert("RGB")

    # cover-fit the 2:1 tile into the banner, then darken so the text carries
    target_w, target_h = size
    scale = max(target_w / img.width, target_h / img.height)
    img = img.resize((int(img.width * scale) + 1, int(img.height * scale) + 1), Image.LANCZOS)

    left = (img.width - target_w) // 2
    top = int((img.height - target_h) * 0.35)
    img = img.crop((left, top, left + target_w, top + target_h)).convert("RGBA")

    img.alpha_composite(Image.new("RGBA", size, (0, 0, 0, 130)))
    return img


def scanlines(img, alpha=22, step=3):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    for y in range(0, img.height, step):
        d.line([(0, y), (img.width, y)], fill=(0, 0, 0, alpha))

    img.alpha_composite(layer)
    return img


def vignette(img, strength=150):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).ellipse([-w // 5, -h // 5, w + w // 5, h + h // 5], fill=140)
    mask = mask.filter(ImageFilter.GaussianBlur(min(w, h) // 5))
    dark = Image.new("RGBA", (w, h), (0, 0, 0, strength))
    img.paste(dark, (0, 0), Image.eval(mask, lambda v: max(0, strength - int(v * 1.1))))
    return img


def build_banner():
    img = backdrop(BANNER, tile_index=0)
    scanlines(img)
    vignette(img, 130)

    logo = load_logo(920)
    img.alpha_composite(logo, ((BANNER[0] - logo.width) // 2, 172))

    d = ImageDraw.Draw(img)
    font = mono(19)
    small = mono(15)

    tagline = "the world deletes itself. something stays behind."
    tw = d.textlength(tagline, font=font)
    d.text(((BANNER[0] - tw) / 2, 420), tagline, fill=(196, 202, 198), font=font)

    meta = "minecraft 1.21.11  ·  fabric  ·  singleplayer + server"
    mw = d.textlength(meta, font=small)
    d.text(((BANNER[0] - mw) / 2, 452), meta, fill=(126, 134, 130), font=small)

    # a thin red rule under the wordmark, and the error code in the corner
    d.line([(BANNER[0] // 2 - 300, 402), (BANNER[0] // 2 + 300, 402)], fill=(120, 24, 28), width=1)
    d.text((24, BANNER[1] - 34), "ERR_404", fill=(126, 34, 38), font=small)
    no = "no save data"
    d.text((BANNER[0] - 24 - d.textlength(no, font=small), BANNER[1] - 34), no,
           fill=(96, 102, 98), font=small)

    img.convert("RGB").save(os.path.join(OUT_DIR, "banner.png"), optimize=True)


def build_icon():
    img = backdrop(ICON, tile_index=0)
    scanlines(img)
    vignette(img, 170)

    logo = load_logo(360)
    img.alpha_composite(logo, ((ICON[0] - logo.width) // 2, (ICON[1] - logo.height) // 2 - 10))

    d = ImageDraw.Draw(img)
    label = "ERR_404"
    font = mono(26)
    lw = d.textlength(label, font=font)
    d.text(((ICON[0] - lw) / 2, ICON[1] - 82), label, fill=(150, 34, 38), font=font)

    # rounded corners, the way both stores crop
    mask = Image.new("L", ICON, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, ICON[0] - 1, ICON[1] - 1], radius=48, fill=255)
    out = Image.new("RGBA", ICON, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    out.save(os.path.join(OUT_DIR, "icon_400.png"), optimize=True)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    build_banner()
    build_icon()
    print("promo art written to", OUT_DIR)


if __name__ == "__main__":
    main()
