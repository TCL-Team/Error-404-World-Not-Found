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

Note: this script only writes `assets/`. Everything under `data/` (recipes, loot tables,
advancements, tags) is hand-written and must not be generated from here.
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


def tex_spawn_egg():
    """Spawn egg: black shell, one magenta seam and a red eye - recognisably 422."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([4, 1, 11, 14], fill=(18, 18, 22, 255), outline=(70, 68, 78, 255))

    for i in range(5):
        d.point((6 + i, 5 + i // 2), fill=(228, 0, 232, 255))

    d.point((7, 9), fill=(190, 24, 34, 255))
    d.point((9, 9), fill=(190, 24, 34, 255))
    d.point((4, 6), fill=(70, 68, 78, 255))
    d.point((11, 6), fill=(70, 68, 78, 255))
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
    """
    Void stone is not "black rock" - it is the *absence* of the block that used to be
    here. So it is a dark tile with a faint dead grid and a few pixels that still flicker
    magenta, as if the world were still trying to load them.
    """
    img = Image.new("RGBA", (16, 16), (12, 11, 15, 255))
    d = ImageDraw.Draw(img)

    # deep, uneven grain so it does not read as a flat colour in-game
    for y in range(16):
        for x in range(16):
            v = random.randint(0, 9)
            d.point((x, y), fill=(12 + v, 11 + v, 15 + v, 255))

    # a dead grid, 4px pitch, only partly drawn - like a chunk that failed to load
    for i in range(0, 16, 4):
        for j in range(0, 16, 4):
            if random.random() < 0.75:
                d.line([(i, j), (i + 3, j)], fill=(30, 28, 38, 255))
            if random.random() < 0.75:
                d.line([(i, j), (i, j + 3)], fill=(30, 28, 38, 255))

    # two surviving "unloaded" pixels and a cold rim
    for _ in range(2):
        d.point((random.randint(0, 15), random.randint(0, 15)), fill=(228, 0, 232, 255))

    for _ in range(3):
        x, y = random.randint(1, 14), random.randint(1, 14)
        d.point((x, y), fill=(46, 58, 72, 255))

    return img


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
#
# UV layout is derived from the model in Error422Model, not copied from the player sheet.
# Blockbench box-UV order for a box, from texOffs(u, v):
#
#     +-------+-------+-------+-------+
#     | north | east  | south | west  |   <- v .. v+depth   (side strip)
#     +-------+-------+-------+-------+
#     | up    | down  | (free)        |   <- v+depth .. v+depth+height
#     +-------+-------+-------+-------+
#      u   u+d   u+d+w u+2d+w   u+2d+2w
#
# so the face a player looks at (-Z / north) lives at (u, v) and is `depth` wide.

HEAD_UV = (0, 0)          # 8x8x8  -> 28 wide, 16 tall
BODY_UV = (0, 32)         # 6x18x4 -> 16 wide, 22 tall
ARM_UV = (32, 0)          # 4x26x4 -> 12 wide, 30 tall
LEG_UV = (48, 0)          # 4x14x4 -> 12 wide, 18 tall

SHEET = (128, 128)


def box_rect(uv, w, h, d):
    """Exact face rectangles as ModelPart.Cube samples them.

    Verified against net.minecraft.client.model.geom.ModelPart.Cube:
    the side strip uses v..v+d for up/down and v+d..v+d+h for the four sides, and the
    horizontal order is west | north | east | south, so the north (front) face starts at u+d.
    """
    u, v = uv
    return {
        "west": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "east": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        "up": (u + d + w, v, u + 2 * d + w, v + d),
        "down": (u + d, v, u + d + w, v + d),
    }


def fill_rect(img, rect, color):
    ImageDraw.Draw(img).rectangle([rect[0], rect[1], rect[2] - 1, rect[3] - 1], fill=color)


def noise_rect(img, rect, color, amount=0.5, seed=0):
    """Speckle a rectangle with 1px noise so flat fills do not read as plastic."""
    r = random.Random(seed)
    d = ImageDraw.Draw(img)
    for y in range(rect[1], rect[3]):
        for x in range(rect[0], rect[2]):
            if r.random() < amount:
                d.point((x, y), fill=color)


def checker_rect(img, rect, cell, c1, c2, offset=(0, 0)):
    d = ImageDraw.Draw(img)
    for y in range(rect[1], rect[3]):
        for x in range(rect[0], rect[2]):
            gx = (x - rect[0] + offset[0]) // cell
            gy = (y - rect[1] + offset[1]) // cell
            d.point((x, y), fill=c1 if (gx + gy) % 2 == 0 else c2)


def paint_face(img, rect, style, seed):
    """Paints one flat cube face with an intentional look (no uniform noise soup)."""
    r = random.Random(seed)
    w = rect[2] - rect[0]
    h = rect[3] - rect[1]
    d = ImageDraw.Draw(img)

    if style == "dark":
        # flat near-black with a 1px lighter rim so the silhouette still reads at distance
        fill_rect(img, rect, (22, 21, 27, 255))
        d.rectangle([rect[0], rect[1], rect[2] - 1, rect[3] - 1], outline=(44, 42, 54, 255))
    elif style == "text":
        # torso front: terminal output that never finished printing
        fill_rect(img, rect, (18, 18, 23, 255))
        y = rect[1] + 1

        while y < rect[3] - 1:
            length = r.randrange(0, w + 1)
            x = rect[0]

            while x < rect[0] + length:
                if r.random() < 0.7:
                    d.point((x, y), fill=BONE if r.random() < 0.8 else (140, 146, 152, 255))

                x += 1

            y += 2
    elif style == "band":
        # a strip of missing-texture magenta across the middle of the face
        fill_rect(img, rect, (18, 18, 23, 255))
        top = rect[1] + max(1, h // 3)
        bottom = rect[1] + max(2, (2 * h) // 3)
        checker_rect(img, (rect[0], top, rect[2], bottom), 2, (232, 0, 236, 255), (14, 14, 18, 255))
    elif style == "checker":
        checker_rect(img, rect, 2, (228, 0, 232, 255), (12, 12, 16, 255))
    elif style == "silent":
        fill_rect(img, rect, (12, 12, 15, 255))


def head_layout(img, style, seed, face=None):
    """Paints the head box: dark hollow box, optional ring or face on the north side."""
    rects = box_rect(HEAD_UV, 8, 8, 8)

    for name in ("north", "east", "south", "west"):
        fill_rect(img, rects[name], (10, 10, 13, 255))
        noise_rect(img, rects[name], (20, 20, 26, 255), 0.12, seed)

    fill_rect(img, rects["up"], (14, 14, 18, 255))
    fill_rect(img, rects["down"], (7, 7, 9, 255))

    fx, fy, fx2, fy2 = rects["north"]
    fill_rect(img, rects["north"], (6, 6, 8, 255))
    d = ImageDraw.Draw(img)

    if face == "ring":
        # the loading ring, centred on the 8x8 face
        cx = (fx + fx2) / 2.0 - 0.5
        cy = (fy + fy2) / 2.0 - 0.5
        radius = 2.6
        for step in range(0, 320, 16):
            ang = math.radians(step)
            d.point((round(cx + radius * math.cos(ang)), round(cy + radius * math.sin(ang))),
                    fill=(226, 232, 238, 255))
        d.point((round(cx + radius), round(cy)), fill=(255, 255, 255, 255))
        d.point((round(cx), round(cy)), fill=(120, 24, 34, 255))
    elif face == "human":
        # a smear that reads as a face at a glance, never more than that
        fill_rect(img, (fx + 1, fy + 1, fx2 - 1, fy2 - 1), (26, 24, 26, 255))
        d.point((fx + 2, fy + 2), fill=(240, 240, 240, 255))
        d.point((fx + 5, fy + 2), fill=(240, 240, 240, 255))
        d.point((fx + 2, fy + 3), fill=(120, 20, 28, 255))
        d.point((fx + 5, fy + 3), fill=(120, 20, 28, 255))
        d.line([(fx + 2, fy + 5), (fx + 5, fy + 5)], fill=(176, 24, 38, 255))
        d.point((fx + 3, fy + 4), fill=(60, 60, 64, 255))
        d.point((fx + 4, fy + 4), fill=(60, 60, 64, 255))


def box_paint(img, uv, w, h, d, styles, seed):
    """Paint every face of one box. `styles` maps face name -> style (default 'skin')."""
    rects = box_rect(uv, w, h, d)

    for i, name in enumerate(("north", "east", "south", "west", "up", "down")):
        paint_face(img, rects[name], styles.get(name, styles.get("default", "skin")), seed + i * 17)


def tex_error_422():
    """128x128 sheet for Error422Model. Base variant: dark body, magenta band, ring head."""
    img = Image.new("RGBA", SHEET, (0, 0, 0, 0))

    head_layout(img, "skin", 11, face="ring")

    body = box_rect(BODY_UV, 6, 18, 4)
    paint_face(img, body["north"], "text", 301)     # what the player sees
    paint_face(img, body["south"], "band", 302)     # back: one corrupted strip
    paint_face(img, body["east"], "dark", 303)
    paint_face(img, body["west"], "dark", 304)
    paint_face(img, body["up"], "silent", 305)
    paint_face(img, body["down"], "dark", 306)

    arms = box_rect(ARM_UV, 4, 26, 4)

    for i, name in enumerate(("north", "south")):
        paint_face(img, arms[name], "band" if i == 0 else "dark", 510 + i)

    for i, name in enumerate(("east", "west")):
        paint_face(img, arms[name], "dark", 520 + i)

    paint_face(img, arms["up"], "dark", 530)
    paint_face(img, arms["down"], "silent", 531)

    legs = box_rect(LEG_UV, 4, 14, 4)

    for i, name in enumerate(("north", "south", "east", "west")):
        paint_face(img, legs[name], "dark", 700 + i)

    paint_face(img, legs["up"], "band", 710)
    paint_face(img, legs["down"], "silent", 711)

    # one red seam where the torso meets the legs
    nx, ny, nx2, ny2 = body["north"]
    ImageDraw.Draw(img).line([(nx, ny2 - 1), (nx2 - 1, ny2 - 1)], fill=(176, 24, 38, 255))

    return img


def tex_error_422_missing():
    """'Failed to load': full missing-texture checker with the alpha keyed out in bands."""
    img = Image.new("RGBA", SHEET, (0, 0, 0, 0))

    for uv, w, h, d in ((HEAD_UV, 8, 8, 8), (BODY_UV, 6, 18, 4), (ARM_UV, 4, 26, 4), (LEG_UV, 4, 14, 4)):
        rects = box_rect(uv, w, h, d)

        for name, rect in rects.items():
            checker_rect(img, rect, 2, (255, 0, 255, 255), (0, 0, 0, 255))

    head_layout(img, "checker", 91, face="ring")

    # a horizontal tear: 3 rows of the torso are simply not there
    d = ImageDraw.Draw(img)
    _, by, _, by2 = box_rect(BODY_UV, 6, 18, 4)["north"]
    d.rectangle([0, by + 9, 127, by + 11], fill=(0, 0, 0, 0))

    return img


def tex_error_422_face():
    """Final phase: same body, but the head is a face and the body has gone quiet."""
    img = tex_error_422()
    head_layout(img, "skin", 11, face="human")
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
    save(tex_spawn_egg(), "textures", "item", "error_422_spawn_egg.png")

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
    for name in ("data_fragment", "corrupt_shard", "error_log", "null_torch", "void_pearl", "error_422_spawn_egg"):
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
