"""Generates the title-screen wordmark shipped in the built-in title resource pack.

The vanilla logo is a 256x64 texture plus a 128x16 "EDITION" bar; both are replaced here
with original pixel art, drawn from a hand-written 5x7 bitmap font rather than by editing
Minecraft's own textures.

    python tools/gen_title_logo.py
"""
import os

from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources")
OUT = os.path.join(RES, "resourcepacks", "title", "assets", "minecraft", "textures", "gui", "title")

FONT = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00110", "01000", "10000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["01110", "10000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00001", "01110"],
    " ": ["00000"] * 7,
    ":": ["00000", "00100", "00100", "00000", "00100", "00100", "00000"],
    ".": ["00000", "00000", "00000", "00000", "00000", "00100", "00100"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    "/": ["00001", "00010", "00010", "00100", "01000", "01000", "10000"],
}

SHADOW = (0, 0, 0, 255)
STONE_TOP = (198, 198, 198, 255)
STONE_BODY = (138, 138, 138, 255)
STONE_LOW = (78, 78, 78, 255)
RUST = (150, 32, 38, 255)


def text_width(text, scale):
    return len(text) * (6 * scale) - scale


def draw_text(draw, text, x, y, scale, colors, shadow=True):
    """colors: list of row colours, top row first; shorter list = banded letters."""
    if shadow:
        px = x
        for ch in text:
            glyph = FONT.get(ch.upper(), FONT[" "])
            for row, bits in enumerate(glyph):
                for col, bit in enumerate(bits):
                    if bit == "1":
                        draw.rectangle([px + col * scale + scale, y + row * scale + scale,
                                        px + col * scale + 2 * scale - 1,
                                        y + row * scale + 2 * scale - 1], fill=SHADOW)
            px += 6 * scale

    px = x
    for ch in text:
        glyph = FONT.get(ch.upper(), FONT[" "])
        for row, bits in enumerate(glyph):
            col = colors[min(row, len(colors) - 1)]
            for c, bit in enumerate(bits):
                if bit == "1":
                    draw.rectangle([px + c * scale, y + row * scale,
                                    px + c * scale + scale - 1, y + row * scale + scale - 1],
                                   fill=col)
        px += 6 * scale


def build_logo():
    img = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # main wordmark: stone-textured letters that darken towards the bottom
    title = "ERROR 404"
    scale = 3
    x = (256 - text_width(title, scale)) // 2
    draw_text(draw, title, x, 6, scale, [STONE_TOP, STONE_TOP, STONE_BODY,
                                         STONE_BODY, STONE_LOW, STONE_LOW, STONE_LOW])

    # the first glyph is the corrupted one; recolour only its own cells
    glyph = FONT["E"]
    for row, bits in enumerate(glyph):
        for col, bit in enumerate(bits):
            if bit == "1":
                draw.rectangle([x + col * scale, 6 + row * scale,
                                x + col * scale + scale - 1, 6 + row * scale + scale - 1],
                               fill=RUST)

    # a horizontal slice of the wordmark is displaced a few pixels: the glitch
    slice_box = (x, 18, x + text_width(title, scale), 21)
    band = img.crop(slice_box)
    draw.rectangle(slice_box, fill=(0, 0, 0, 0))
    img.alpha_composite(band, (x + 2, 18))

    # subtitle: smaller, greyer, with a struck-through word
    sub = "WORLD NOT FOUND"
    sscale = 2
    sx = (256 - text_width(sub, sscale)) // 2
    draw_text(draw, sub, sx, 40, sscale,
              [(206, 206, 206, 255), (206, 206, 206, 255), (206, 206, 206, 255),
               (150, 150, 150, 255), (150, 150, 150, 255), (110, 110, 110, 255),
               (110, 110, 110, 255)])

    # "NOT" is crossed out and the strike-through bleeds
    not_x = sx + 6 * sscale * len("WORLD ")
    draw.rectangle([not_x - 2, 46, not_x + 6 * sscale * 3 - 4, 48], fill=RUST)
    draw.rectangle([not_x + 10, 52, not_x + 30, 53], fill=(90, 12, 16, 255))

    img.save(os.path.join(OUT, "minecraft.png"), optimize=True)


def build_edition():
    img = Image.new("RGBA", (128, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # the black bar vanilla puts behind "EDITION"
    draw.rectangle([4, 2, 123, 13], fill=(0, 0, 0, 190))
    text = "NULL EDITION"
    x = (128 - text_width(text, 1)) // 2
    draw_text(draw, text, x, 5, 1, [(176, 176, 176, 255)] * 7, shadow=False)

    img.save(os.path.join(OUT, "edition.png"), optimize=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    build_logo()
    build_edition()
    print("title logo written to", OUT)


if __name__ == "__main__":
    main()
