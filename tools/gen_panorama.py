#!/usr/bin/env python3
"""
Generates the title-screen panorama: six 2:1 tiles of a world that has already been deleted.

Rendered procedurally with PIL - no screenshots, no third-party assets. The six tiles are one
continuous 360 degree strip (each tile is what the game would show at 60 degrees apart), so the
horizon lines up across the seams and the panorama does not visibly "jump" when it rotates.

    python3 tools/gen_panorama.py        # writes assets/error404/textures/gui/title/background/

Design: dead flat plain under a black-green sky, one thin band of light on the horizon, and a
single 422 silhouette standing far away in tile 3 that the rotation will bring back around.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

RES = os.path.join("src", "main", "resources")
OUT = os.path.join(RES, "assets", "error404", "textures", "gui", "title", "background")

W, H = 1024, 512          # 2:1, what vanilla uses for panorama tiles
TILES = 6
HORIZON = int(H * 0.46)   # where sky meets ground

random.seed(40422)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sky(img, d):
    """
    Overcast dusk. Bright enough that the silhouette reads, dim enough that the screen still
    feels wrong - vanilla shows the panorama under a dark overlay, so painting it dark too
    produced a black rectangle on the first attempt.
    """
    for y in range(HORIZON):
        t = y / HORIZON
        col = lerp((22, 30, 34), (86, 104, 96), t ** 0.85)
        d.line([(0, y), (W, y)], fill=col)

    # banded haze: a few stacked, slightly offset horizontal glows
    for band in range(5):
        y0 = HORIZON - 10 - band * 14
        for i in range(14):
            alpha = int(20 * (1.0 - i / 14.0))
            img.alpha_composite(Image.new("RGBA", (W, 1), (150, 175, 150, alpha)), (0, y0 - i))


def ground(img, d):
    """Dead plain: pale at the horizon (distance fog), darker towards the camera."""
    for y in range(HORIZON, H):
        t = (y - HORIZON) / max(1, (H - HORIZON))
        col = lerp((74, 84, 78), (18, 21, 24), t ** 0.55)
        d.line([(0, y), (W, y)], fill=col)

    # sparse blocks receding to the horizon - scale and shade both follow depth
    for _ in range(320):
        depth = random.random() ** 2.0
        y = int(HORIZON + depth * (H - HORIZON))
        scale = 2 + int(depth * 26)
        x = random.randint(-40, W + 40)
        shade = int(38 + (1.0 - depth) * 40)
        col = (shade, shade + 4, shade + 2)
        d.rectangle([x, y - scale, x + scale, y], fill=col)
        # a single lighter top edge so the blocks read as cubes, not squares
        d.line([(x, y - scale), (x + scale, y - scale)], fill=(shade + 16, shade + 18, shade + 16))

    # the boundary of the deleted region: a hard seam across the whole strip
    d.line([(0, HORIZON + 4), (W, HORIZON + 4)], fill=(120, 140, 128))
    d.line([(0, HORIZON + 5), (W, HORIZON + 5)], fill=(40, 46, 44))


def figure(img, x):
    """The silhouette. Deliberately 3:1 tall, no legs, no face, slightly off-centre."""
    d = ImageDraw.Draw(img)
    base = HORIZON + 26
    height = 92
    width = 22
    body = (2, 2, 3)

    d.rectangle([x - width // 2, base - height, x + width // 2, base], fill=body)
    # rim light down one side: the silhouette has to separate from the sky at a glance
    d.line([(x + width // 2, base - height), (x + width // 2, base)], fill=(96, 110, 104))
    # head: a box, a beat too wide
    d.rectangle([x - 15, base - height - 26, x + 15, base - height], fill=body)
    d.line([(x + 15, base - height - 26), (x + 15, base - height)], fill=(96, 110, 104))
    # arms, far too long
    d.rectangle([x - width // 2 - 6, base - height + 6, x - width // 2, base - 4], fill=body)
    d.rectangle([x + width // 2, base - height + 6, x + width // 2 + 6, base - 4], fill=body)
    # two eyes, the only pixels that are not black
    d.point((x - 6, base - height - 16), fill=(120, 220, 235))
    d.point((x + 6, base - height - 16), fill=(120, 220, 235))


def artefacts(img):
    """Compression damage: a few rows where the image 'failed to load'."""
    d = ImageDraw.Draw(img)

    for _ in range(5):
        y = random.randint(0, H - 2)
        h = random.randint(1, 3)
        x0 = random.randint(0, W // 2)
        x1 = x0 + random.randint(40, W // 2)
        shade = random.choice([(60, 0, 60), (0, 40, 40), (40, 40, 40)])
        band = img.crop((x0, y, x1, y + h))
        band = band.filter(ImageFilter.GaussianBlur(1))
        img.paste(band.rotate(random.choice([0, 0, 0, 180])), (x0, y))
        d.line([(x0, y), (x1, y)], fill=shade)

    # gentle edge vignette only - the first version painted the whole tile black
    vign = Image.new("L", (W, H), 0)
    vd = ImageDraw.Draw(vign)
    vd.ellipse([-W // 6, -H // 6, W + W // 6, H + H // 6], fill=120)
    vign = vign.filter(ImageFilter.GaussianBlur(140))
    dark = Image.new("RGBA", (W, H), (0, 0, 0, 110))
    img.paste(dark, (0, 0), Image.eval(vign, lambda v: max(0, 110 - int(v * 0.9))))


def main():
    os.makedirs(OUT, exist_ok=True)

    for tile in range(TILES):
        img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
        d = ImageDraw.Draw(img)

        sky(img, d)
        d = ImageDraw.Draw(img)
        ground(img, d)

        # the entity is in one tile only, so rotating brings it back into view
        if tile == 3:
            figure(img, W // 2 + 90)
        elif tile == 0:
            # a second one, tiny, on the seam - easy to miss the first ten times
            figure(img, 40)

        artefacts(img)
        img.convert("RGB").save(os.path.join(OUT, f"panorama_{tile}.png"), optimize=True)

    # the overlay vanilla tints/creates a vignette with; ours adds scanlines instead
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)

    for y in range(0, H, 3):
        od.line([(0, y), (W, y)], fill=(0, 0, 0, 40))

    overlay.save(os.path.join(OUT, "panorama_overlay.png"), optimize=True)
    print("panorama written to", OUT)


if __name__ == "__main__":
    main()
