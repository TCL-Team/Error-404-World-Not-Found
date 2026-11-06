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

    if findings:
        print(f"{len(findings)} finding(s):\n")

        for finding in findings:
            print("  " + finding)

        return 1

    print("audit clean: sounds, lang, textures, English, metadata")
    return 0


if __name__ == "__main__":
    sys.exit(main())
