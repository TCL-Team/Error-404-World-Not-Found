#!/usr/bin/env python3
"""
Generates the title-screen panorama: a world that has already been deleted.

Rendered procedurally with PIL - no screenshots, no third-party assets.

Geometry, which vanilla is specific about: the panorama is a cubemap of six textures,
and the four side faces cover 90 degrees of yaw each (the remaining two are the sky above
and the void below). So this tool paints one continuous 360 degree strip and slices it
into four tiles, which is why the horizon lines up across the seams instead of jumping.

Tile order in the files is not yaw order - CubeMapTexture stacks them as
[+X, -X, +Y, -Y, +Z, -Z] from the suffixes [_1, _3, _5, _4, _0, _2] - so the mapping below
is written out explicitly rather than assumed.

    python3 tools/gen_panorama.py
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

RES = os.path.join("src", "main", "resources")
# shipped as a built-in resource pack that overrides vanilla's minecraft namespace
OUT = os.path.join(RES, "resourcepacks", "title", "assets", "minecraft",
                   "textures", "gui", "title", "background")

FACE_W, FACE_H = 1024, 512     # vanilla panorama face size (2:1)
STRIP_W = FACE_W * 4           # 360 degrees of yaw
HORIZON = int(FACE_H * 0.34)   # where sky meets ground, constant across the whole strip

random.seed(40422)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint_sky(img, d):
    """
    Overcast dusk. Bright enough that the silhouette reads, dim enough that the screen
    still feels wrong: vanilla draws the panorama under its own dark overlay, so painting
    it dark as well produced a black rectangle on the first attempt.
    """
    for y in range(HORIZON):
        t = y / HORIZON
        d.line([(0, y), (STRIP_W, y)], fill=lerp((22, 30, 34), (86, 104, 96), t ** 0.85))

    # banded haze, a few stacked glows just above the horizon
    for band in range(5):
        y0 = HORIZON - 10 - band * 14
        for i in range(14):
            alpha = int(20 * (1.0 - i / 14.0))
            img.alpha_composite(Image.new("RGBA", (STRIP_W, 1), (150, 175, 150, alpha)),
                                (0, y0 - i))


def paint_ground(img, d):
    """
    A plain with a bite taken out of it: cubes receding in perspective, then the world
    simply stops at a jagged cut, black nothing behind it, a few blocks left floating.
    That read - terrain that ends mid-frame - is what makes the menu look like a save file
    that failed to load rather than a dark screenshot.
    """
    for y in range(HORIZON, FACE_H):
        t = (y - HORIZON) / max(1, FACE_H - HORIZON)
        d.line([(0, y), (STRIP_W, y)], fill=lerp((92, 104, 96), (30, 35, 38), t ** 0.75))

    # cubes standing on the plain, in bands of depth so the rows read as one plane
    y = int(HORIZON + (FACE_H - HORIZON) * 0.10)

    while y < CUT_MIN_Y - 20:
        depth = (y - HORIZON) / (FACE_H - HORIZON)
        step = max(8, int(10 + depth * 46))
        shade = int(40 + (1.0 - depth) * 34)

        for x in range(0, STRIP_W, step):
            if random.random() < 0.72:
                continue

            size = max(4, int(step * 0.55))
            jitter = random.randint(-3, 3)
            top = (shade + 16 + jitter, shade + 22 + jitter, shade + 18 + jitter)
            side = (shade - 8 + jitter, shade - 2 + jitter, shade - 4 + jitter)
            d.rectangle([x, y, x + size, y + size], fill=top)
            d.rectangle([x, y + size, x + size, y + size + max(2, size // 2)], fill=side)

        y += step

    # the cut, continuous across the whole strip so the seam is invisible
    edge = []
    x, y = 0, HORIZON + int((FACE_H - HORIZON) * 0.52)

    while x <= STRIP_W:
        edge.append((x, y))
        x += random.randint(6, 22)
        y = min(CUT_MAX_Y, max(CUT_MIN_Y, y + random.randint(-4, 4)))

    d.polygon(edge + [(STRIP_W, FACE_H), (0, FACE_H)], fill=(3, 4, 5))

    # the void is not flat black: it fades, and it glows a little where the world ends
    top_of_void = min(p[1] for p in edge)

    for vy in range(top_of_void, FACE_H):
        t = (vy - top_of_void) / max(1, FACE_H - top_of_void)
        glow = int(14 * (1.0 - t) ** 2)
        d.line([(0, vy), (STRIP_W, vy)], fill=(glow, glow + 3, glow + 3))

    # a thin lit lip on the cut, as if the world mesh simply ends there
    d.line(edge, fill=(112, 128, 118), width=2)
    d.line([(px, py + 2) for px, py in edge], fill=(26, 30, 30), width=1)

    # fog spilling out of the hole
    for _ in range(60):
        fx = random.randint(0, STRIP_W)
        fy = random.choice(edge)[1] + random.randint(2, 40)
        r = random.randint(24, 80)
        fog = Image.new("RGBA", (r * 2, r * 2), (0, 0, 0, 0))
        ImageDraw.Draw(fog).ellipse([0, 0, r * 2, r * 2], fill=(120, 138, 130, 16))
        img.alpha_composite(fog.filter(ImageFilter.GaussianBlur(22)), (fx - r, fy - r), )

    # blocks left floating over the void, lit from the side that still exists
    for _ in range(26):
        fx = random.randint(60, STRIP_W - 60)
        fy = random.choice(edge)[1] + random.randint(10, 120)
        size = random.randint(9, 34)
        d.rectangle([fx, fy, fx + size, fy + size], fill=(16, 18, 19))
        d.line([(fx, fy), (fx + size, fy)], fill=(84, 96, 90), width=2)
        d.line([(fx, fy), (fx, fy + size)], fill=(60, 70, 66), width=1)

    # the horizon seam, straight across the whole strip
    d.line([(0, HORIZON + 4), (STRIP_W, HORIZON + 4)], fill=(124, 142, 130))
    d.line([(0, HORIZON + 5), (STRIP_W, HORIZON + 5)], fill=(40, 46, 44))


CUT_MIN_Y = HORIZON + int((FACE_H - HORIZON) * 0.34)
CUT_MAX_Y = FACE_H - 30


def paint_ruins(d, x, base, scale):
    """Dead architecture on the skyline: broken columns and a leaning slab, all near-black."""
    body = (7, 9, 10)
    rim = (58, 68, 64)

    for dx, h, w in [(0, 74, 9), (16, 46, 7), (30, 96, 11), (50, 30, 8)]:
        px = x + int(dx * scale)
        top = base - int(h * scale)
        d.rectangle([px, top, px + int(w * scale), base], fill=body)
        d.line([(px, top), (px, base)], fill=rim)

        # broken top: chew a few pixels off so the columns do not look extruded
        for _ in range(3):
            x0 = px + random.randint(0, max(1, int(w * scale)) - 1)
            d.rectangle([x0, top, x0 + random.randint(1, max(2, int(w * scale) // 2)),
                         top + random.randint(1, 3)], fill=(3, 4, 5))

    d.line([(x + int(58 * scale), base), (x + int(96 * scale), base - int(110 * scale))],
           fill=body, width=int(9 * scale))
    d.line([(x + int(58 * scale), base), (x + int(96 * scale), base - int(110 * scale))],
           fill=rim, width=1)


def paint_figure(img, x, height_px=92, eye=True):
    """
    The silhouette: 3:1 tall, no legs, no face, arms far too long.

    Rendered at 2x and downsampled, so its edges are not pixel-perfect - it should look
    like something the capture device could not resolve, not a sprite pasted on top.
    """
    scale = 2
    layer = Image.new("RGBA", (STRIP_W * scale, FACE_H * scale), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    base = (HORIZON + 26) * scale
    height = height_px * scale
    width = int(height_px * 0.24) * scale
    cx = x * scale
    body = (2, 2, 3, 255)
    rim = (104, 118, 112, 255)

    # contact shadow: it is standing on the plain, so the plain has to say so
    for i in range(10):
        r = (40 - i * 3) * scale
        d.ellipse([cx - r, base - 4 * scale, cx + r, base + 4 * scale],
                  fill=(0, 0, 0, 8 + i * 3))

    d.rectangle([cx - width // 2, base - height, cx + width // 2, base], fill=body)
    d.rectangle([cx + width // 2 - 2 * scale, base - height, cx + width // 2, base], fill=rim)

    head_w = int(width * 1.4)
    head_h = int(height * 0.28)
    d.rectangle([cx - head_w // 2, base - height - head_h, cx + head_w // 2, base - height], fill=body)
    d.rectangle([cx + head_w // 2 - 2 * scale, base - height - head_h,
                 cx + head_w // 2, base - height], fill=rim)

    # arms, far too long
    arm_w = max(3, width // 4)
    d.rectangle([cx - width // 2 - arm_w, base - height + 6 * scale,
                 cx - width // 2, base - 4 * scale], fill=body)
    d.rectangle([cx + width // 2, base - height + 6 * scale,
                 cx + width // 2 + arm_w, base - 4 * scale], fill=body)

    # two eyes - the only pixels that are not black, and wide enough to survive a 1024px
    # face being stretched across a whole screen
    if eye:
        ew = max(2, width // 6)
        ey = base - height - head_h + head_h // 3
        d.rectangle([cx - head_w // 4 - ew, ey, cx - head_w // 4, ey + ew], fill=(140, 232, 244, 255))
        d.rectangle([cx + head_w // 4, ey, cx + head_w // 4 + ew, ey + ew], fill=(140, 232, 244, 255))

    layer = layer.resize((STRIP_W, FACE_H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(0.6))
    img.alpha_composite(layer)


def paint_artefacts(img):
    """Compression damage: a few rows where the image 'failed to load'."""
    d = ImageDraw.Draw(img)

    for _ in range(4):
        y = random.randint(0, FACE_H - 2)
        h = random.randint(1, 3)
        x0 = random.randint(0, STRIP_W // 2)
        x1 = x0 + random.randint(40, STRIP_W // 2)
        band = img.crop((x0, y, x1, y + h)).filter(ImageFilter.GaussianBlur(1))
        img.paste(band.rotate(random.choice([0, 0, 0, 180])), (x0, y))
        d.line([(x0, y), (x1, y)], fill=random.choice([(34, 8, 34), (8, 30, 30), (34, 34, 34)]))


def vignette(img):
    """Gentle edge darkening only - the first version painted the whole tile black."""
    W, H = img.size
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).ellipse([-W // 6, -H // 6, W + W // 6, H + H // 6], fill=120)
    mask = mask.filter(ImageFilter.GaussianBlur(140))
    dark = Image.new("RGBA", (W, H), (0, 0, 0, 92))
    img.paste(dark, (0, 0), Image.eval(mask, lambda v: max(0, 92 - int(v * 1.1))))


def build_strip():
    img = Image.new("RGBA", (STRIP_W, FACE_H), (0, 0, 0, 255))
    paint_sky(img, ImageDraw.Draw(img))
    paint_ground(img, ImageDraw.Draw(img))

    d = ImageDraw.Draw(img)

    # ruin clusters and figures, placed so the rotation brings each back into view once
    for x, base_off, scale in [(1150, 6, 1.0), (3050, 6, 0.7)]:
        paint_ruins(d, x, HORIZON + base_off, scale)

    paint_figure(img, 1760, height_px=104)   # the one you notice
    paint_figure(img, 3620, height_px=44, eye=False)  # the one you notice later

    paint_artefacts(img)
    vignette(img)
    return img.convert("RGB")


def build_top():
    """+Y: the sky straight up. Darkest at the zenith, glowing towards the horizon."""
    img = Image.new("RGB", (FACE_W, FACE_H))
    px = img.load()
    cx, cy = FACE_W / 2, FACE_H / 2

    for y in range(FACE_H):
        for x in range(FACE_W):
            r = math.hypot((x - cx) / cx, (y - cy) / cy) / math.sqrt(2.0)
            col = lerp((86, 104, 96), (16, 21, 24), r ** 0.8)
            px[x, y] = col

    return img


def build_bottom():
    """-Y: the void under the world, with a little residue of the chunk that used to be here."""
    img = Image.new("RGB", (FACE_W, FACE_H), (3, 4, 5))
    d = ImageDraw.Draw(img)

    for _ in range(40):
        x = random.randint(0, FACE_W)
        y = random.randint(0, FACE_H)
        size = random.randint(4, 18)
        shade = random.randint(10, 26)
        d.rectangle([x, y, x + size, y + size], fill=(shade, shade + 2, shade + 2))

    return img


def build_overlay():
    """
    The texture vanilla blends over the panorama. Vanilla's is 16x128 and the renderer
    samples all of it with a fixed 16x128 quad, so anything larger shows only its top-left
    corner - keep the exact vanilla size and paint a vertical falloff into it. The
    pixel-accurate scanlines are drawn by TitleScreenGlitch instead.
    """
    overlay = Image.new("RGBA", (16, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)

    for y in range(128):
        d.line([(0, y), (15, y)], fill=(0, 0, 0, int(20 + 70 * (y / 127.0) ** 1.5)))

    # a faint red stain along the bottom, like a warning painted over
    for y in range(120, 128):
        d.line([(0, y), (15, y)], fill=(38, 0, 0, 90))

    return overlay


def main():
    os.makedirs(OUT, exist_ok=True)

    strip = build_strip()

    # yaw order around the axis is +Z, +X, -Z, -X; the textures they want are _0, _1, _2, _3
    for index, suffix in enumerate(["0", "1", "2", "3"]):
        tile = strip.crop((index * FACE_W, 0, (index + 1) * FACE_W, FACE_H))
        tile.save(os.path.join(OUT, f"panorama_{suffix}.png"), optimize=True)

    build_bottom().save(os.path.join(OUT, "panorama_4.png"), optimize=True)  # -Y
    build_top().save(os.path.join(OUT, "panorama_5.png"), optimize=True)     # +Y
    build_overlay().save(os.path.join(OUT, "panorama_overlay.png"), optimize=True)

    print("panorama written to", OUT)


if __name__ == "__main__":
    main()
