# ERROR 404: WORLD NOT FOUND

> *The world is being deleted. He is the one deleting it.*

Minecraft **Java 1.21.11** (Fabric) ke liye ek slow-burn horror mod.
Tum apni normal duniya join karte ho — aur dheere dheere woh **delete hona shuru** ho jaati hai.

![Error 404 logo](docs/concept/logo-404.png)

---

## Idea ek line me

Chunks gayab hote hain, unki jagah **ajib structures** ugte hain (aadha ghar, aasman tak seedhi, tumhare purane base ki galat copy),
aur duniya ke "integrity" ke saath ek entity aa jaati hai — **422: The Unprocessable** — jo tumhe duniya ka hissa nahi maanti.

## 404 + 422 = ek hi lore

| Error | Matlab | Mod me |
|---|---|---|
| `404` | *Not Found* | Tumhari duniya, uske chunks, uske structures — sab "nahi mile" ho rahe hain |
| `422` | *Unprocessable Entity* | Woh cheez jo is duniya ko process nahi kar pa rahi... aur tum bhi uske liye process nahi ho rahe |

ERROR 422 (creepypasta mod) se **inspiration** hai, copy nahi — entity, texture, behaviour, sounds, sab original.

---

## Gameplay

**World Integrity 100% → 0%.** Integrity girti hai jab tum explore karte ho, chunks load hote hain, 422 paas aati hai.
Player ka goal: **8 Data Fragments + Anchor Core** bana ke *The Last Chunk* par duniya bacha lena.

| Phase | Naam | Duniya kya karti hai |
|---|---|---|
| P0 | **SIGNAL** | Chup, reverb, ek galat block, ek aadhi chat line |
| P1 | **DESYNC** | Render me holes, sun stutter, peeche 1 kadam ki awaaz, pehla silhouette |
| P2 | **DELETION** | Chunks sach me delete, anomalies spawn, 422 stalking |
| P3 | **CASCADE** | Deletion tez, structures khud mit-te hain, light khaa rahi hai |
| P4 | **ANNEXATION** | Duniya floating fragments, 422 hunt, sirf Last Chunk bacha |

### 422 ka do-faced AI (locked design)

**P1–P3 tk woh classic stalker hai:** fog me khadi rehti hai, out-of-frame move karti hai, tumhare paas aakar rukti hai.
**P4 (endgame) me signature rule unlock hota hai:**

> **422 ko dekhna = damage. Nazar hatana = safe.**

3 second se zyada dekha → static, item "unprocessable", chunk ko nuksaan. Ignore kiya → woh chali jaati hai.
Usse ladna nahi hai — pehle usse **bachna** hai, endgame me usse **dekhna seekhna** hai (ya na dekhna).

### Anomaly structures

Prebuilt nahi — ek ruleset se ad-hoc bante hain, isliye har playthrough alag:

<a href="docs/concept/anomaly-structures.png"><img src="docs/concept/anomaly-structures.png" width="800" alt="anomaly structures concept"></a>

`null_hut` (aadha ghar) · `staircase_to_nowhere` (aasman tak seedhi) · `memory_of_home` (tumhare base ki galat copy) ·
`loadbar_wall` (100% hote hi chunks delete) · `mirror_village` (frozen villagers, sab tumhe dekhte hain) ·
`chunk_seam` (do biomes ki galat silaai) · `the_sinkhole` · `inverted_tree` · `door_to_room` · `archive` · `revoke` event

### 422: The Unprocessable

<a href="docs/concept/entity-422-concept.png"><img src="docs/concept/entity-422-concept.png" width="800" alt="422 entity concept art"></a>

3 block lambi, patli, pair nahi — 0.2 block float karti hai. Body par white terminal error text aur magenta
"missing texture" checker patches. Chehra nahi — khaali head ke andar ek **loading ring** ghoomta hai.
10 block ke andar aate hi uska chehra **tumhara hi chehra** ban jaata hai (still frame — bilkul galat lagta hai).

<img src="docs/concept/void-fragments.png" width="800" alt="deleted world fragments concept art">

---

## Difficulty preset: HARDCORE (locked)

- Chunks sach me delete hote hain, structures khud mit-te hain (revoke), 422 light aur blocks khaati hai.
- **Lekin** hardcore ka matlab ye nahi ki tumhara save mar jaaye: session start par **world backup** (region zip),
  aur DELETE ending default me sirf **rename + backup** karta hai (`destructiveEnding=false`).
  Chaho to config me `destructiveEnding=true` karke poora "save gaya" wala experience le sakte ho.
- Fairness ke non-negotiable rules (hardcore me bhi): player ke 8 block ke andar deletion nahi,
  chests/beds/spawn protect, creative/spectator skip, `/error404 stop` + `/error404 restore` hamesha on.

## Safety (pehle se soch liya)

- Session start par **world backup**, default ending me **rename + backup** (real delete nahi).
- Player ke 8 block ke andar koi deletion nahi; chests/beds/spawn protect.
- Creative/spectator skip. `/error404 stop` aur `/error404 restore` hamesha available.
- Photosensitivity flag (rapid flashing off).
- Mod khud game/server crash nahi karta — crash screen sirf ek overlay hai.

---

## Docs

- 📖 **[GAME_DESIGN.md](docs/GAME_DESIGN.md)** — pura design: phases, anomalies, entity AI, endings, commands, loot
- 🛠 **[ROADMAP.md](docs/ROADMAP.md)** — 1.21.11 Fabric technical plan, milestones v0.1→v1.0, risks, CI
- 🎨 **[docs/concept/](docs/concept/)** — concept art (logo, entity, anomalies, void)

## Install

1. **Fabric Loader 0.20.1+** for Minecraft **1.21.11** ([installer](https://fabricmc.net/use/installer/))
2. **[Fabric API 0.141.6+1.21.11](https://modrinth.com/mod/fabric-api)**
3. `error404-0.6.0.jar` from the [latest release](https://github.com/TCL-Team/Error-404-World-Not-Found/releases/latest)
4. Dono jars `.minecraft/mods` me daalo, game start karo.

Pehli baar khelne se pehle:

```
/error404 demo       # 15x pacing: poora 100% -> 0% run ~5 minute me
/error404 status     # integrity, phase, session seed
/error404 restore    # jo bhi delete hua, sab wapas
```

Mod **chupchaap** shuru hota hai — chat me kuch nahi likhta (`announceOnJoin=false`), kyunki horror
ka pehla rule hai ki player ko pata na chale ki kuch galat hai.

## Build / run (local)

```bash
# JDK 25 chahiye (Loom 1.19 ka requirement; mod khud Java 21 bytecode hai)
./gradlew build        # jar -> build/libs/error404-<version>+1.21.11.jar
./gradlew runClient    # mod ke saath Minecraft launch
```

CI har push par GitHub Actions se build hota hai (`.github/workflows/build.yml`).

## Status

| Milestone | Kya | State |
|---|---|---|
| Design (v0.1 docs) | lore, phases, anomalies, entity, endings, safety | ✅ locked |
| **v0.1 code** | Fabric 1.21.11 project, integrity director, `/error404` commands, JSON config, CI build | ✅ build green |
| **v0.2 assets** | 19 original synthesised sounds, textures, models, blockstates, item defs, lang, recipes | ✅ in game |
| **v0.2 world** | chunk deletion front (hardcore), void_stone residue, restore path, world backup | ✅ in game |
| **v0.3 anomalies** | 12 procedural structures, discovery logging, revoke, anomaly manager | ✅ in game |
| **v0.4 entity** | 422 - 3 textures, custom model, Mix AI (stalker -> stare rule), glow, vanish loot | ✅ in game |
| **v0.5 endings** | SALVAGE / DELETE / 0x00, anchor ritual, advancements, restore hardening | ✅ in game |
| **v0.6 polish** | HUD glitch layer, fog/void horizon, ambience cross-fade, photosensitivity mode | ✅ in game |
| **v0.6.0 release** | release workflow (tag → jar + notes), deletion journal (`restore` sach me wapas deta hai), natural 422 spawner, anchor ritual, 5 languages | ✅ [v0.6.0](https://github.com/TCL-Team/Error-404-World-Not-Found/releases/tag/v0.6.0) |

Har push par GitHub Actions build hota hai (JDK 25 + Gradle 9.8.2). `v*` tag push karne par
`release.yml` jar + release notes publish karta hai.
Latest verified jar: **error404-0.6.0.jar, 753 KB** (release `v0.6.0`).

## In-game commands (operator only)

```
/error404 status                 integrity, phase, session seed
/error404 start | stop           session control
/error404 restore                integrity 100%, world un-ended, har deleted block journal se wapas
/error404 phase <name>           force a phase (testing)
/error404 setintegrity <0-100>   jump to a value
/error404 demo                   15x pacing for a ~5 min full run
/error404 summon                 spawn 422 six blocks in front of you
/error404 anomaly <code>         force one structure (ERR_404_A ... ERR_404_X)
/error404 ending <s|d|null>      trigger SALVAGE / DELETE / 0x00
/error404 backup                 write a world zip immediately
/error404 reload | config save   config file round-trip
```

## Content

- **5 phases** — SIGNAL → DESYNC → DELETION → CASCADE → ANNEXATION (integrity 100 → 0)
- **12 anomalies** — half house, staircase to nowhere, chunk seam, mirror village, load-bar wall,
  door to room, sinkhole, archive, inverted tree, memory of home, the last chunk, revoke
- **1 entity** — 422, three texture variants (base / missing-texture / the face), 3 blocks tall
- **4 items / 4 blocks** — data fragment, corrupt shard, error log, void pearl · anchor core,
  missing texture, void stone, null torch
- **19 sounds** — all synthesised from scratch in `tools/gen_sounds.py` (no third-party audio)
- **3 endings + 7 advancements**
- **5 languages** — en_us, en_gb, hi_in, de_de, ja_jp
- **1 spawn egg** — 422 ke liye, sirf creative/staging (survival me kabhi nahi milta)

## License

Code: [MIT](LICENSE) · Concept/art: © repo author. Minecraft assets ka koi use nahi — sab original ya vanilla references tak limited.
