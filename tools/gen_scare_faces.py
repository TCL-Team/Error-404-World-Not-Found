#!/usr/bin/env python3
"""
Generates the faces used by the jumpscare overlay.

Three 512x512 RGBA textures, all drawn from scratch - nothing traced, nothing sampled:

  scare_face.png     - a tall pale face, eyes too far apart, mouth a thin black gash
  scare_face_err.png - the same face corrupted: scanline tears, chromatic split, RGB rot
  scare_glitch.png   - full-frame static plate, used as a one-frame flash

They are drawn at 2x and downsampled so the edges are slightly soft: a jumpscare face
should look photographed badly, not drawn crisply.

    python3 tools/gen_scare_faces.py
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources")
OUT = os.path.join(RES, "assets", "error404", "textures", "gui", "scare")

SIZE = 512
SS = 2  # supersample factor

random.seed(404)


def skin_tone(y, height):
    """Vertical gradient: cold grey at the top, faintly bloodless at the bottom."""
    t = y / max(1, height)
    return (
        int(214 - 60 * t),
        int(208 - 58 * t),
        int(198 - 52 * t),
    )


def draw_face(corrupt=False):
    w, h = SIZE * SS, SIZE * SS
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # --- head: an ellipse that is a little too long -------------------------------
    cx, cy = w / 2, h * 0.52
    rx, ry = w * 0.30, h * 0.44

    for y in range(int(cy - ry), int(cy + ry)):
        rel = (y - cy) / ry
        if abs(rel) >= 1:
            continue
        half = rx * math.sqrt(max(0.0, 1 - rel * rel))
        tone = skin_tone(y - (cy - ry), 2 * ry)
        d.line([(cx - half, y), (cx + half, y)], fill=tone + (255,))

    # --- shading: cheekbones, temple hollows, a shadow under the jaw ---------------
    shade = Image.new("L", (w, h), 0)
    sd = ImageDraw.Draw(shade)
    sd.ellipse([cx - rx * 1.05, cy - ry * 0.2, cx - rx * 0.35, cy + ry * 0.55], fill=70)
    sd.ellipse([cx + rx * 0.35, cy - ry * 0.2, cx + rx * 1.05, cy + ry * 0.55], fill=70)
    sd.ellipse([cx - rx * 0.8, cy + ry * 0.35, cx + rx * 0.8, cy + ry * 1.15], fill=110)
    shade = shade.filter(ImageFilter.GaussianBlur(w * 0.05))
    dark = Image.new("RGBA", (w, h), (20, 16, 18, 255))
    img.paste(dark, (0, 0), shade)

    # --- eyes: sockets first, then the whites, then a pupil that is too small -----
    eye_y = cy - ry * 0.18
    eye_dx = rx * 0.52
    ex_r = rx * 0.30
    ey_r = ry * 0.13

    for sign in (-1, 1):
        ecx = cx + sign * eye_dx

        # socket shadow
        socket = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(socket).ellipse(
            [ecx - ex_r * 1.5, eye_y - ey_r * 2.2, ecx + ex_r * 1.5, eye_y + ey_r * 2.0],
            fill=(10, 8, 10, 190))
        img.alpha_composite(socket.filter(ImageFilter.GaussianBlur(w * 0.02)))

        # sclera: not white, the yellow of a bruise
        d.ellipse([ecx - ex_r, eye_y - ey_r, ecx + ex_r, eye_y + ey_r], fill=(226, 222, 198, 255))
        # iris
        d.ellipse([ecx - ex_r * 0.44, eye_y - ey_r * 0.52, ecx + ex_r * 0.44, eye_y + ey_r * 0.52],
                  fill=(38, 30, 30, 255))
        # pupil: a vertical slit rather than a circle
        d.ellipse([ecx - ex_r * 0.13, eye_y - ey_r * 0.5, ecx + ex_r * 0.13, eye_y + ey_r * 0.5],
                  fill=(0, 0, 0, 255))
        # the only light in the face
        d.ellipse([ecx - ex_r * 0.06, eye_y - ey_r * 0.30, ecx + ex_r * 0.02, eye_y - ey_r * 0.18],
                  fill=(255, 255, 255, 235))

        # a vein in the sclera
        for _ in range(3):
            x0 = ecx + random.uniform(-ex_r * 0.8, ex_r * 0.8)
            d.line([(x0, eye_y + ey_r * 0.2), (x0 + random.uniform(-6, 6), eye_y + ey_r * 0.7)],
                   fill=(188, 78, 78, 170), width=2)

    # --- nose: two shadows and a hole, nothing more ------------------------------
    nose_y = cy + ry * 0.18
    for sign in (-1, 1):
        d.line([(cx + sign * rx * 0.10, nose_y - ry * 0.12),
                (cx + sign * rx * 0.16, nose_y + ry * 0.06)],
               fill=(96, 88, 86, 210), width=int(w * 0.008))
    d.ellipse([cx - rx * 0.10, nose_y + ry * 0.02, cx + rx * 0.10, nose_y + ry * 0.09],
              fill=(14, 12, 14, 230))

    # --- mouth: a long thin gash, stitched shut at the corners -------------------
    mouth_y = cy + ry * 0.52
    mouth_w = rx * 0.72
    d.line([(cx - mouth_w, mouth_y - ry * 0.02), (cx + mouth_w, mouth_y + ry * 0.03)],
           fill=(8, 6, 8, 255), width=int(h * 0.016))
    d.line([(cx - mouth_w, mouth_y - ry * 0.026), (cx + mouth_w, mouth_y + ry * 0.024)],
           fill=(120, 96, 96, 120), width=2)

    # stitches
    for i in range(9):
        t = i / 8.0
        sx = cx - mouth_w + t * mouth_w * 2
        sy = mouth_y - ry * 0.02 + t * ry * 0.05
        d.line([(sx, sy - h * 0.012), (sx, sy + h * 0.012)], fill=(64, 52, 52, 200),
               width=int(w * 0.004))

    # --- hair: a black curtain that eats the top of the face ---------------------
    hair = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hair)
    hd.ellipse([cx - rx * 1.08, cy - ry * 1.30, cx + rx * 1.08, cy - ry * 0.05],
               fill=(6, 6, 7, 255))

    for _ in range(140):
        x = cx + random.uniform(-rx * 1.05, rx * 1.05)
        length = random.uniform(ry * 0.25, ry * 0.8)
        hd.line([(x, cy - ry * 0.35), (x + random.uniform(-8, 8), cy - ry * 0.35 + length)],
                fill=(4, 4, 5, 230), width=random.randint(2, 5))

    img.alpha_composite(hair.filter(ImageFilter.GaussianBlur(w * 0.003)))

    # --- grain and a single vertical tear ---------------------------------------
    px = img.load()

    for y in range(h):
        for x in range(0, w, 2):
            if random.random() < 0.06:
                r, g, b, a = px[x, y]
                if a > 0:
                    n = random.randint(-16, 16)
                    px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)),
                                max(0, min(255, b + n)), a)

    if corrupt:
        img = corrupt_face(img, w, h)
        d = ImageDraw.Draw(img)

    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    return img


def corrupt_face(img, w, h):
    """
    Tears the image the way a broken decoder would: shifted row bands, then a genuine
    RGB channel split (the red and blue channels are offset from the green one), which
    is why it reads as a photograph that failed rather than as a purple rectangle.
    """
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.alpha_composite(img)

    # displaced row bands, drawn over the clean image
    for _ in range(22):
        y0 = random.randint(0, h - 1)
        band_h = random.randint(2, 10)
        shift = random.randint(-46, 46)
        band = img.crop((0, y0, w, min(h, y0 + band_h)))
        out.alpha_composite(band, (shift, y0))

    # true channel split: keep green where it is, move red and blue in opposite directions
    box = (int(w * 0.12), int(h * 0.20), int(w * 0.88), int(h * 0.62))
    region = out.crop(box)
    r, g, b, a = region.split()

    shifted = Image.new("RGBA", region.size, (0, 0, 0, 0))
    red = Image.merge("RGBA", (r, Image.new("L", region.size, 0), Image.new("L", region.size, 0), a))
    blue = Image.merge("RGBA", (Image.new("L", region.size, 0), Image.new("L", region.size, 0), b, a))
    green = Image.merge("RGBA", (Image.new("L", region.size, 0), g, Image.new("L", region.size, 0), a))

    shifted.alpha_composite(red, (7, 0))
    shifted.alpha_composite(blue, (-7, 0))
    shifted.alpha_composite(green, (0, 0))

    out.paste(shifted, box)

    # a dead black band across the mouth, and one thin tear above the eyes
    d = ImageDraw.Draw(out)
    d.rectangle([0, int(h * 0.615), w, int(h * 0.632)], fill=(0, 0, 0, 225))
    d.rectangle([0, int(h * 0.235), w, int(h * 0.243)], fill=(0, 0, 0, 170))

    return out


def draw_glitch_plate():
    """One frame of full-screen static, with a few structural lines in it."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    for y in range(SIZE):
        for x in range(0, SIZE, 2):
            v = random.randint(90, 255) if random.random() < 0.86 else 0
            a = random.randint(150, 255)
            d.point((x, y), fill=(v, v, v, a))
            if random.random() < 0.8:
                d.point((x + 1, y), fill=(v, v, v, a))

    # horizontal tearing bars
    for _ in range(20):
        y = random.randint(0, SIZE - 1)
        d.rectangle([0, y, SIZE, y + random.randint(1, 4)],
                    fill=(random.randint(0, 40),) * 3 + (220,))

    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    draw_face(False).save(os.path.join(OUT, "scare_face.png"), optimize=True)
    draw_face(True).save(os.path.join(OUT, "scare_face_err.png"), optimize=True)
    draw_glitch_plate().save(os.path.join(OUT, "scare_glitch.png"), optimize=True)
    print("scare faces written to", OUT)


if __name__ == "__main__":
    main()
