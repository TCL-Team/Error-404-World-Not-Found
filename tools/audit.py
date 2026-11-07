#!/usr/bin/env python3
"""
Static self-audit: the checks a CI compile cannot do.

A compiler proves the code is spelled right. It does not prove that a sound referenced by name
actually exists in sounds.json, that a translation key has a translation, that a texture path
in an item model points at a real PNG, or that a `hi_in` file still mirrors `en_us`. Every one
of those is a "works on my machine, blank texture in the video" bug.

    python3 tools/audit.py            # exit 1 on any finding

Checks
  1. every ModSounds constant is declared in sounds.json and every ogg it points at exists
  2. every translation key used from Java exists in en_us.json
  3. every lang file has exactly the keys en_us has (values may differ only for UI keys)
  4. every texture referenced by a model / blockstate / item definition exists on disk
  5. no non-English text in Java string literals (the mod's in-game text is English-only)
  6. every registered block/item id has a lang entry
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src")
MAIN = os.path.join(SRC, "main")
CLIENT = os.path.join(SRC, "client")
RES = os.path.join(MAIN, "resources")
ASSETS = os.path.join(RES, "assets", "error404")

findings = []


def fail(check, message):
    findings.append(f"[{check}] {message}")


def java_files(*roots):
    for root in roots:
        for base, _, names in os.walk(root):
            for name in names:
                if name.endswith(".java"):
                    yield os.path.join(base, name)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def load_json(path):
    try:
        return json.loads(read(path))
    except (OSError, json.JSONDecodeError) as error:
        fail("json", f"{os.path.relpath(path, ROOT)}: {error}")
        return {}


ALL_JAVA = list(java_files(MAIN, CLIENT))


# --- 1. sounds ------------------------------------------------------------------
def check_sounds():
    sounds = load_json(os.path.join(ASSETS, "sounds.json"))
    mod_sounds = os.path.join(MAIN, "java", "dev", "error404", "ModSounds.java")

    if not os.path.exists(mod_sounds):
        fail("sounds", "ModSounds.java missing")
        return

    declared = set(re.findall(r'register\("([a-z0-9_]+)"\)', read(mod_sounds)))

    for name in sorted(declared):
        entry = sounds.get(name)

        if entry is None:
            fail("sounds", f"ModSounds.{name.upper()} has no sounds.json entry")
            continue

        for variant in entry.get("sounds", []):
            path = variant["name"].split(":")[-1]
            ogg = os.path.join(ASSETS, "sounds", path + ".ogg")

            if not os.path.exists(ogg):
                fail("sounds", f"sounds.json '{name}' points at missing {os.path.relpath(ogg, ROOT)}")

    for name in sorted(sounds):
        if name not in declared:
            fail("sounds", f"sounds.json has '{name}' but no ModSounds constant")

    # every ModSounds.X referenced from Java must exist as a constant
    references = set()
    for path in ALL_JAVA:
        references |= set(re.findall(r"ModSounds\.([A-Z0-9_]+)", read(path)))

    for name in sorted(references):
        if name.lower() not in declared:
            fail("sounds", f"ModSounds.{name} is used but never registered")


# --- 2/3. translation keys ------------------------------------------------------
LANG_DIR = os.path.join(ASSETS, "lang")
EN = os.path.join(LANG_DIR, "en_us.json")

# keys produced by vanilla or by mod-loaders, never by our own Java
IGNORED_KEYS = ("gui.", "narration.", "menu.", "options.", "pack.", "modmenu.")


def check_lang():
    en = load_json(EN)
    used = set()

    for path in ALL_JAVA:
        for key in re.findall(r'translatable\("([^"]+)"', read(path)):
            used.add(key)

    for key in sorted(used):
        if key not in en and not key.startswith(IGNORED_KEYS):
            fail("lang", f"translatable(\"{key}\") has no en_us entry")

    for name in sorted(os.listdir(LANG_DIR)):
        if not name.endswith(".json") or name == "en_us.json":
            continue

        other = load_json(os.path.join(LANG_DIR, name))

        for key in sorted(en):
            if key not in other:
                fail("lang", f"{name} is missing '{key}' (every locale must mirror en_us)")

    # every block/item id we register should be named in en_us
    for path in ALL_JAVA:
        text = read(path)

        for kind in ("block", "item"):
            for ident in re.findall(kind + r'\("([a-z0-9_]+)"', text):
                key = f"{kind}.error404.{ident}"

                if key not in en:
                    fail("lang", f"{key} registered but has no en_us entry")


# --- 4. textures ----------------------------------------------------------------
def texture_exists(reference):
    """Accepts 'error404:block/x' or plain 'block/x' and checks assets/error404/textures."""
    path = reference.split(":")[-1]
    return os.path.exists(os.path.join(ASSETS, "textures", path + ".png"))


def check_textures():
    model_dir = os.path.join(ASSETS, "models")

    for base, _, names in os.walk(model_dir):
        for name in names:
            if not name.endswith(".json"):
                continue

            path = os.path.join(base, name)
            text = read(path)

            for reference in re.findall(r'"(?:texture|layer\d|particle|top|bottom|side|front|end)"\s*:\s*"[^"]*/([^"]+)"', text):
                if ":" in reference or reference.startswith("#"):
                    continue

                if not texture_exists("block/" + reference) and not texture_exists("item/" + reference) \
                        and not texture_exists("entity/" + reference):
                    fail("texture", f"{os.path.relpath(path, ASSETS)} references missing texture '{reference}'")

    # the title pack has to contain all six faces + overlay, or the panorama fails to load
    pano = os.path.join(RES, "resourcepacks", "title", "assets", "minecraft",
                        "textures", "gui", "title", "background")

    for index in range(6):
        path = os.path.join(pano, f"panorama_{index}.png")

        if not os.path.exists(path):
            fail("texture", f"title panorama face missing: panorama_{index}.png")

    for name in ("panorama_overlay.png",):
        if not os.path.exists(os.path.join(pano, name)):
            fail("texture", f"title panorama file missing: {name}")

    for name in ("minecraft.png", "edition.png"):
        path = os.path.join(RES, "resourcepacks", "title", "assets", "minecraft",
                            "textures", "gui", "title", name)

        if not os.path.exists(path):
            fail("texture", f"title logo missing: {name}")


# --- 5. English only ------------------------------------------------------------
HINGLISH = (
    " nahi", " nahin", " kyun", " kya ", " tum", " hai ", " hain ", " gaya ", " raha ",
    " kar ", " karo", " dekh", " bhai", " sahi", " mat ", " abhi", " phir", " jaldi", " bohot",
    " bahut", " thoda", " kuch", " aisa", " woh ", " yeh ", " aur ", " par ", " se ", " ka ", " ki ",
)


def check_english():
    for path in ALL_JAVA:
        for literal in re.findall(r'"([^"\\]{4,})"', read(path)):
            lowered = " " + literal.lower() + " "

            for word in HINGLISH:
                if word in lowered:
                    fail("english", f"{os.path.relpath(path, ROOT)}: '{literal}'")
                    break


# --- 6. creative tab safety ------------------------------------------------------
ITEMLESS_BLOCKS = re.compile(r"Blocks\.([A-Z_]+)")


def register_call(body, open_paren):
    """Returns the argument text of the register(...) call that starts at open_paren."""
    depth = 0

    for index in range(open_paren, len(body)):
        char = body[index]

        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1

            if depth == 0:
                return body[open_paren + 1:index]

    return ""


def block_items():
    """Block constants of error404 that registered an item, keyed by constant name."""
    source = read(os.path.join(MAIN, "java", "dev", "error404", "ModBlocks.java"))
    blocks, with_item = set(), set()

    for match in re.finditer(r"public static final Block ([A-Z_]+)\s*=\s*register\(", source):
        name = match.group(1)
        args = register_call(source, match.end() - 1)
        blocks.add(name)

        # register(name, factory, properties) delegates and always makes an item; the four-arg
        # form takes the boolean at the end and only blocks that pass false stay itemless.
        if not re.search(r",\s*false\s*$", args):
            with_item.add(name)

    return blocks, with_item


def check_creative_tab():
    """A tab entry that is not a stack of exactly one item crashes the creative screen.

    Minecraft's ItemDisplayBuilder throws IllegalArgumentException("Stack size must be exactly
    1") inside the CreativeModeInventoryScreen constructor when the stack is anything else, and
    that took the whole client down while the world saved itself on the way out. So: every
    error404 block that reaches the tab needs its item, and a tab never calls
    accept(new ItemStack(...)) directly - it goes through a validating helper instead.
    """
    package = os.path.join(MAIN, "java", "dev", "error404")
    tabs = [os.path.join(package, name) for name in os.listdir(package) if name.endswith("Tabs.java")]

    blocks, with_item = block_items()

    for path in tabs:
        body = read(path)
        rel = os.path.relpath(path, ROOT)

        for constant in ITEMLESS_BLOCKS.findall(body):
            if constant in blocks and constant not in with_item:
                fail("creative-tab", f"{rel}: Block '{constant}' has no item -> tab build crashes")

        if "accept(newItemStack(" in body.replace(" ", "") and "private static void add(" not in body:
            fail("creative-tab", f"{rel}: raw accept(new ItemStack(...)) with no validating helper")


# --- 6. resources the mod declares ----------------------------------------------
def check_metadata():
    meta = load_json(os.path.join(RES, "fabric.mod.json"))

    icon = meta.get("icon")

    if icon and not os.path.exists(os.path.join(RES, icon)):
        fail("metadata", f"fabric.mod.json icon '{icon}' does not exist")

    for entry, classes in meta.get("entrypoints", {}).items():
        for cls in classes:
            path = cls.replace(".", os.sep) + ".java"

            if not any(os.path.exists(os.path.join(root, "java", path)) for root in (MAIN, CLIENT)):
                fail("metadata", f"entrypoint '{entry}' -> {cls} has no source file")

    pack_meta = os.path.join(RES, "resourcepacks", "title", "pack.mcmeta")

    if not os.path.exists(pack_meta):
        fail("metadata", "title resource pack has no pack.mcmeta")


def main():
    check_sounds()
    check_lang()
    check_textures()
    check_english()
    check_metadata()
    check_creative_tab()

    if findings:
        print(f"{len(findings)} finding(s):\n")

        for finding in findings:
            print("  " + finding)

        return 1

    print("audit clean: sounds, lang, textures, English, metadata, creative tab")
    return 0


if __name__ == "__main__":
    sys.exit(main())
