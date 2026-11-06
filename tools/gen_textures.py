#!/usr/bin/env python3
"""
Original textures + models for ERROR 404: WORLD NOT FOUND.

Everything is drawn procedurally here — no third-party assets. Run after editing:

    python3 tools/gen_textures.py

Outputs:
  src/main/resources/assets/error404/textures/{item,block,entity}/*.png
  src/main/resources/assets/error404/models/item/*.json
  src/main/resources/assets/error404/models/block/*.json
  src/main/resources/assets/error404/blockstates/*.json
  src/main/resources/assets/error404/items/*.json   (1.21.11 item definition format)
"""

import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

RES = os.path.join("src", "main", "resources", "assets", "error404")
random.seed(404422)

INK = (16, 16, 20, 255)          # near-black
BONE = (222, 226, 230, 255)      # off-white terminal text
BLOOD = (176, 24, 38, 255)       # error red
MAGENTA = (255, 0, 255, 255)     # classic missing-texture magenta
GRID = (58, 78, 94, 255)         # void grid blue-grey


def ensure(*parts):
    p = os.path.join(RES, *parts)
    os.makedirs(p, exist_ok=True)
    return p


def save(img: Image.Image, *parts):
    path = os.path.join(RES, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def dump_json(obj, *parts):
    path = os.path.join(RES, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def noise_layer(size, alpha=28, seed=1):
    r = random.Random(seed)
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    px = layer.load()
    for y in range(size[1]):
        for x in range(size[0]):
            v = r.randint(0, 255)
            if v > 190:
                px[x, y] = (255, 255, 255, r.randint(0, alpha))
            elif v < 60:
                px[x, y] = (0, 0, 0, r.randint(0, alpha))
    return layer


def checker(size, cell=4, c1=MAGENTA, c2=(0, 0, 0, 255)):
    img = Image.new("RGBA", size, c2)
    d = ImageDraw.Draw(img)
    for y in range(0, size[1], cell):
        for x in range(0, size[0], cell):
            if ((x // cell) + (y // cell)) % 2 == 0:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=c1)
    return img


# ---------------------------------------------------------------- item textures
def tex_data_fragment():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # a shard of another world: pale core, glitched edges
    pts = [(8, 1), (12, 5), (11, 12), (7, 15), (4, 10), (5, 5)]
    d.polygon(pts, fill=(176, 240, 255, 255), outline=(88, 168, 190, 255))
    d.polygon([(8, 3), (10, 7), (7, 11), (6, 7)], fill=(255, 255, 255, 210))
    for i in range(3):
        y = random.randint(4, 12)
        d.line([(3, y), (13, y)], fill=(120, 210, 235, 90))
    d.point((8, 14), fill=BLOOD)
    return img


def tex_corrupt_shard():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pts = [(9, 1), (14, 6), (12, 14), (5, 14), (2, 8), (5, 3)]
    d.polygon(pts, fill=(34, 18, 38, 255), outline=(255, 60, 255, 255))
    for y in range(2, 15, 2):
        for x in range(2, 15, 2):
            if ((x // 2) + (y // 2)) % 2 == 0:
                d.rectangle([x, y, x + 1, y + 1], fill=(255, 0, 255, 255))
    d.line([(5, 4), (10, 12)], fill=(255, 255, 255, 120))
    return img


def tex_error_log():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([2, 1, 13, 14], fill=(228, 228, 224, 255), outline=(120, 120, 116, 255))
    d.rectangle([2, 1, 3, 14], fill=(150, 30, 36, 255))
    for y in range(4, 13, 2):
        d.line([(5, y), (12, y)], fill=(90, 90, 90, 200))
    d.line([(5, 6), (12, 6)], fill=BLOOD)
    d.rectangle([6, 10, 11, 11], fill=(0, 0, 0, 180))
    return img


def tex_null_torch():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([7, 8, 8, 15], fill=(58, 56, 52, 255))
    d.rectangle([6, 4, 9, 8], fill=(24, 24, 28, 255))
    d.rectangle([7, 2, 8, 5], fill=(236, 240, 244, 255))
    d.point((6, 3), fill=(255, 255, 255, 160))
    d.point((9, 4), fill=(255, 255, 255, 120))
    return img


def tex_void_pearl():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([2, 2, 13, 13], fill=(12, 12, 16, 255), outline=(96, 130, 150, 255))
    d.ellipse([5, 5, 9, 9], fill=(30, 44, 56, 255))
    for a in range(0, 360, 45):
        x = 8 + int(5.5 * math.cos(math.radians(a)))
        y = 8 + int(5.5 * math.sin(math.radians(a)))
        d.point((x, y), fill=(180, 220, 240, 200))
    return img


# --------------------------------------------------------------- block textures
def tex_missing_texture():
    img = checker((16, 16), cell=2)
    return Image.alpha_composite(img, noise_layer((16, 16), 20, seed=3))


def tex_void_stone():
    img = Image.new("RGBA", (16, 16), (10, 10, 13, 255))
    d = ImageDraw.Draw(img)
    for i in range(0, 16, 8):
        d.line([(i, 0), (i, 15)], fill=(22, 30, 36, 255))
        d.line([(0, i), (15, i)], fill=(22, 30, 36, 255))
    for _ in range(10):
        x, y = random.randint(0, 15), random.randint(0, 15)
        d.point((x, y), fill=GRID)
    return Image.alpha_composite(img, noise_layer((16, 16), 16, seed=7))


def tex_null_torch_block():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([7, 8, 8, 15], fill=(48, 46, 44, 255))
    d.rectangle([6, 6, 9, 8], fill=(18, 18, 22, 255))
    d.rectangle([7, 4, 8, 6], fill=(226, 234, 240, 255))
    d.point((6, 5), fill=(255, 255, 255, 220))
    d.point((9, 6), fill=(160, 200, 230, 160))
    return img


def tex_anchor_core_side():
    img = Image.new("RGBA", (16, 16), (26, 28, 34, 255))
    d = ImageDraw.Draw(img)
    for y in (3, 12):
        d.line([(0, y), (15, y)], fill=(58, 62, 72, 255))
    d.rectangle([6, 5, 9, 10], fill=(18, 18, 22, 255))
    for x in range(0, 16, 4):
        d.point((x, 7), fill=(200, 206, 214, 255))
        d.point((x, 8), fill=(120, 126, 134, 255))
    d.line([(2, 14), (13, 14)], fill=BLOOD)
    return Image.alpha_composite(img, noise_layer((16, 16), 14, seed=11))


def tex_anchor_core_top():
    img = Image.new("RGBA", (16, 16), (30, 32, 38, 255))
    d = ImageDraw.Draw(img)
    d.ellipse([1, 1, 14, 14], outline=(210, 216, 224, 255))
    d.ellipse([4, 4, 11, 11], outline=(140, 150, 160, 255))
    d.ellipse([6, 6, 9, 9], fill=(240, 244, 248, 255))
    for a in range(0, 360, 30):
        x = 8 + int(6.2 * math.cos(math.radians(a)))
        y = 8 + int(6.2 * math.sin(math.radians(a)))
        d.point((x, y), fill=(255, 80, 96, 220))
    return img


# ------------------------------------------------------------- entity textures
def error_text_lines(size, count, color, seed):
    """Fake terminal error lines, drawn as 1px dashes so they read as corrupted text."""
    r = random.Random(seed)
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _ in range(count):
        y = r.randrange(0, size[1])
        x = r.randrange(0, size[0] - 10)
        length = r.randrange(6, min(26, size[0] - x))
        for i in range(0, length, 2):
            if r.random() < 0.72:
                c = color if r.random() < 0.75 else (color[0], color[1], color[2], 140)
                d.point((x + i, y), fill=c)
                if r.random() < 0.3:
                    d.point((x + i, y + 1), fill=c)
    return img


def tex_error_422():
    """128x128 skin sheet for the 422 model. Layout is documented in Error422Model."""
    size = (128, 128)
    base = Image.new("RGBA", size, (14, 14, 18, 255))

    # torso / arm areas get a faint checker corruption
    patch = checker((128, 128), cell=4, c1=(228, 0, 232, 255), c2=(10, 10, 14, 255))
    mask = Image.new("L", size, 0)
    md = ImageDraw.Draw(mask)
    md.rectangle([0, 34, 40, 96], fill=255)     # body area
    md.rectangle([40, 34, 64, 96], fill=110)    # arms area
    md.rectangle([0, 96, 32, 128], fill=70)     # glitch cubes area
    base = Image.composite(Image.blend(base, patch, 0.85), base, mask)

    # error text belongs to the body only - the head stays clean so the loading ring reads
    text_layer = error_text_lines(size, 220, BONE, seed=21)
    text_mask = Image.new("L", size, 0)
    ImageDraw.Draw(text_mask).rectangle([0, 32, 127, 127], fill=255)
    text_layer.putalpha(Image.composite(text_layer.getchannel("A"), Image.new("L", size, 0), text_mask))
    base = Image.alpha_composite(base, text_layer)
    blood_layer = error_text_lines(size, 60, BLOOD, seed=22)
    blood_layer.putalpha(Image.composite(blood_layer.getchannel("A"), Image.new("L", size, 0), text_mask))
    base = Image.alpha_composite(base, blood_layer)

    d = ImageDraw.Draw(base)
    # head box: 8x8x8 at texOffs(64,0) -> occupies 32x16
    d.rectangle([64, 0, 95, 15], fill=(10, 10, 12, 255))
    # face is the -Z side: in vanilla head layout that's the middle 8x8 block of the top row
    fx, fy = 72, 8
    d.rectangle([fx, fy, fx + 7, fy + 7], fill=(6, 6, 8, 255))
    # loading ring
    cx, cy, rad = fx + 3.5, fy + 3.5, 2.5
    for a in range(0, 300, 18):
        x = cx + rad * math.cos(math.radians(a))
        y = cy + rad * math.sin(math.radians(a))
        d.point((int(x), int(y)), fill=(214, 220, 226, 255))
    d.point((int(cx + rad), int(cy)), fill=(255, 255, 255, 255))

    # thin red error seam along the body
    d.line([(0, 33), (63, 33)], fill=(176, 24, 38, 255))
    return Image.alpha_composite(base, noise_layer(size, 18, seed=31))


def tex_error_422_missing():
    """Variant used when the entity is 'failing to load': harsher checker, no face."""
    size = (128, 128)
    img = checker(size, cell=8, c1=(255, 0, 255, 255), c2=(0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([64, 0, 95, 15], fill=(255, 0, 255, 255))
    d.rectangle([72, 8, 79, 15], fill=(0, 0, 0, 255))
    return Image.alpha_composite(img, error_text_lines(size, 180, (255, 255, 255, 235), seed=41))


def tex_error_422_face():
    """Final-phase face: the loading ring has finished and something is looking back."""
    size = (128, 128)
    img = tex_error_422().copy()
    d = ImageDraw.Draw(img)
    d.rectangle([64, 0, 95, 15], fill=(8, 8, 10, 255))
    fx, fy = 72, 8
    # a smeared human-ish face made of dashes (deliberately unreadable)
    d.rectangle([fx + 1, fy + 1, fx + 6, fy + 6], fill=(30, 28, 30, 255))
    d.point((fx + 2, fy + 2), fill=(240, 240, 240, 255))
    d.point((fx + 5, fy + 2), fill=(240, 240, 240, 255))
    d.line([(fx + 2, fy + 5), (fx + 5, fy + 5)], fill=(176, 24, 38, 255))
    d.line([(fx + 3, fy + 3), (fx + 4, fy + 4)], fill=(90, 90, 94, 255))
    return img


# --------------------------------------------------------------------- models
def item_model(name, texture=None):
    texture = texture or name
    return {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"error404:item/{texture}"},
    }


def block_model(name, texture=None):
    texture = texture or name
    return {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": f"error404:block/{texture}"},
    }


def item_definition(name, model_name=None):
    model_name = model_name or name
    return {"model": {"type": "minecraft:model", "model": f"error404:item/{model_name}"}}


def block_item_definition(name):
    return {"model": {"type": "minecraft:model", "model": f"error404:block/{name}"}}


def blockstate(name):
    return {"variants": {"": {"model": f"error404:block/{name}"}}}


def main():
    # ---- item textures
    save(tex_data_fragment(), "textures", "item", "data_fragment.png")
    save(tex_corrupt_shard(), "textures", "item", "corrupt_shard.png")
    save(tex_error_log(), "textures", "item", "error_log.png")
    save(tex_null_torch(), "textures", "item", "null_torch.png")
    save(tex_void_pearl(), "textures", "item", "void_pearl.png")

    # ---- block textures
    save(tex_missing_texture(), "textures", "block", "missing_texture.png")
    save(tex_void_stone(), "textures", "block", "void_stone.png")
    save(tex_null_torch_block(), "textures", "block", "null_torch.png")
    save(tex_anchor_core_side(), "textures", "block", "anchor_core_side.png")
    save(tex_anchor_core_top(), "textures", "block", "anchor_core_top.png")

    # ---- entity textures
    save(tex_error_422(), "textures", "entity", "error_422.png")
    save(tex_error_422_missing(), "textures", "entity", "error_422_missing.png")
    save(tex_error_422_face(), "textures", "entity", "error_422_face.png")

    # ---- item models + 1.21.11 item definitions
    for name in ("data_fragment", "corrupt_shard", "error_log", "null_torch", "void_pearl"):
        dump_json(item_model(name), "models", "item", f"{name}.json")
        dump_json(item_definition(name), "items", f"{name}.json")

    # ---- simple cube blocks
    dump_json(block_model("missing_texture"), "models", "block", "missing_texture.json")
    dump_json(block_model("void_stone"), "models", "block", "void_stone.json")
    dump_json(blockstate("missing_texture"), "blockstates", "missing_texture.json")
    dump_json(blockstate("void_stone"), "blockstates", "void_stone.json")
    dump_json(block_item_definition("missing_texture"), "items", "missing_texture.json")
    dump_json(block_item_definition("void_stone"), "items", "void_stone.json")

    # ---- anchor core (different top texture)
    dump_json(
        {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {
                "top": "error404:block/anchor_core_top",
                "bottom": "error404:block/anchor_core_side",
                "side": "error404:block/anchor_core_side",
            },
        },
        "models", "block", "anchor_core.json",
    )
    dump_json(blockstate("anchor_core"), "blockstates", "anchor_core.json")
    dump_json(block_item_definition("anchor_core"), "items", "anchor_core.json")

    # ---- null torch (torch-shaped model, backed by the item sprite)
    dump_json(
        {
            "parent": "minecraft:block/template_torch",
            "textures": {"torch": "error404:block/null_torch"},
        },
        "models", "block", "null_torch.json",
    )
    dump_json(
        {
            "variants": {
                "facing=north": {"model": "error404:block/null_torch"},
                "facing=south": {"model": "error404:block/null_torch", "y": 180},
                "facing=east": {"model": "error404:block/null_torch", "y": 90},
                "facing=west": {"model": "error404:block/null_torch", "y": 270},
            }
        },
        "blockstates", "null_torch.json",
    )
    dump_json(
        {
            "model": {"type": "minecraft:model", "model": "error404:block/null_torch"}
        },
        "items", "null_torch.json",
    )

    print("textures + models written to", RES)


if __name__ == "__main__":
    main()
