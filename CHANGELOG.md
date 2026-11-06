# Changelog

Sab notable changes. Format [Keep a Changelog](https://keepachangelog.com/), versioning [SemVer](https://semver.org/).

## [0.8.0] - 2026-11-06

**"Anything can happen."** Chaos layer + an AI that learns you. Everything in-game is English now.

### Added

- **Chaos layer** (`config.chaos`, default on) — from DELETION onwards, one named event every 60-180 s:
  - `chunk` — an entire chunk near the player is removed at once, the way an unloaded chunk looks
  - `inventory` — hotbar contents reshuffled, nothing lost
  - `death` — a real lightning bolt with no storm, plus "this player is not in the save file"
  - `doors` — every door and trapdoor within 20 blocks opens at once
  - `sound` — full-volume audio directly behind the player, nothing there when you turn
  - `lightning` — a visual-only bolt on the block you were about to walk onto
  - `mobs` — a zombie/skeleton/enderman/warden spawns behind you, already targeting you
  - `teleport` — displaced 8-18 blocks, never into a wall or lava
  - `message` — a fake diagnostic in another player's name
- **Path memory** — from CASCADE, 422 stops chasing you and replays your own route from 60 s ago,
  block for block, at walking speed.
- **Predictive approach** — it aims at where you will be in two seconds, not where you are.
- **Player model** — it learns whether you hide underground, whether you stand still, whether you carry
  a torch, and adjusts where it appears.
- **Proximity drone** — a 19 s dissonant sub-bass loop (33 Hz vs 34.1 Hz beating) while 422 is close.
- **Death sting** — the sudden-death impact, also played when the player is nearly dead.
- **`/error404 chaos on|off|fire <event>`** — the whole chaos pool, testable in one command.
- **Error log** now reports deaths, lost chunks, threat level and an incident log of everything
  the world did to the player this session.

### Changed

- **All in-game text is English** — structure books, signs, whispers, chat. `hi_in` now mirrors
  `en_us`; `de_de` / `ja_jp` keep translated UI keys only.
- Chaos respects the anchor zone and never fires before DELETION.

## [0.7.0] - 2026-11-06

**"Ab woh khatarnak hai."** 422 ab sirf ek silhouette nahi hai.

### Added

- **Contact damage** — 3 blocks ke andar aate hi silent hit (6 dmg standard, 10 nightmare).
- **Escalating stare damage** — 3 s se zyada dekhne par 3 dmg/s, blindness, nausea; har 3 s me damage badhta hai.
- **Chase teleports** — jo player nazar nahi rakhta, uske liye woh 2.5–10 block ka jump karti hai.
- **Wall breaking** — rasta band ho to deewar tod deti hai (config `realBlockDeletion` respect karta hai,
  aur todе hue blocks journal me jaate hain, `restore` se wapas aate hain).
- **Sleep haunting** — CASCADE ke baad sote hue player ko utha deti hai, bed tod deti hai, aur ANNEXATION me
  bed ke paas teleport ho jaati hai.
- **Darkness aura** (ANNEXATION, 20 blocks).
- **Death feeding** — har player death uski speed badhata hai (cap ke saath).
- **Mercy rule** — player ko maarne ke baad woh 5 minute ke liye withdraw karti hai aur nearby copies vanish
  ho jaati hain. Menace, not respawn-camping.
- **Anchor sanctuary** — anchor zone me 422 kabhi nahi aati; teleport bhi zone se bahar hi land karta hai.
- **Difficulty presets** — `chill` / `standard` / `nightmare`, `/error404 difficulty` se live switch.
- **Proximity dread overlay** — vignette, red/cyan seam, screen tearing, hotbar dropout (photosensitivity-safe).
- **Emissive eye layers** — cold eyes always, red eyes only while the stare is live.
- **Home invasion** — 100 s tak ek hi jagah camp karne par 422 base ke andar aa jaati hai, 12–18 block
  peeche, aur ghar ki saari lights ek-ek karke band ho jaati hain. Anchor zone me trigger nahi hota.
- **Ambience ducking** — uske 18 block ke andar aate hi world ka apna ambient bed band ho jaata hai.
- **Phantom server haunting** — "left the game" / "joined the game" / chat lines in your own name /
  "removed from the whitelist" / footsteps behind you, all in vanilla server line format.
- **Custom damage types** — `error404:unprocessable`, `error404:world_deleted` with own death messages.
- **Advancements actually awarded** — 7 code-driven advancements (JSON criteria moved to `impossible`).
- **Deletion journal** — every removed block is remembered so `/error404 restore` really restores.

### Fixed

- **Endings never fired** — `setIntegrity` ended the world by itself with a hardcoded code, so the ending
  director (which picks SALVAGE / DELETE / 0x00 and plays the finale) was unreachable.
- **Deletion front re-cleared the same chunks** and never advanced outward.
- **Entity UV layout** — skins were painted in the player-sheet layout while the model uses custom `texOffs`.
- **Void pearl** teleported players into stone; now it finds headroom and applies slow falling.
- **Audio** — `structure_revoke` clipped at 1.53 peak; ambience loops had a seam and a restart gap.
- **`announceOnJoin`** default off — the mod never tells the player it is installed.

### Changed

- Contact damage is capped at one hit per second; a full-health player survives ~3.5 s of contact
  instead of dying on the first tick.
- Anchor ritual commits fragments straight from the inventory (no 8 separate clicks).
- `mod_version` 0.6.0 → 0.7.0.

## [0.6.0] - 2026-11-06

Pehla proper release. Mod ab shuru se ant tak khela ja sakta hai.

### Added

- **Release pipeline** — `v*` tag push par `release.yml` build karke jar + auto-generated release notes publish karta hai.
- **Deletion journal** (`deletion/DeletionJournal`) — front jo bhi block hataata hai, usse pehle likhta hai.
  `/error404 restore` ab sach me blocks wapas laata hai (pehle sirf chunk reload karta tha, jo saved chunk par kuch nahi karta).
- **Natural 422 spawner** — DESYNC ke baad 422 khud aati hai, hamesha player ke **24 block peeche**,
  max 2 alive, 90 second cooldown. Pehle sirf `/error404 summon` se aati thi.
- **Anchor ritual** — anchor core par right click karne se inventory ke data fragments khud commit hote hain
  (8/8 hone par SALVAGE). Pehle har fragment alag click karna padta tha.
- **Proximity heartbeat + night whispers + phase ambience** — 422 ke paas hone par dhadkan tez hoti hai.
- **Spawn egg** for 422 (creative tab, staging only).
- **Languages** — `hi_in`, `en_gb`, `de_de`, `ja_jp`.
- **Data files** — 4 block loot tables, mineable/needs-tool tags, `data_fragment` recipe.

### Fixed

- **Endings kabhi fire nahi hote the.** `IntegrityState.setIntegrity()` khud `end(2)` call kar deta tha,
  isliye `EndingManager` (jo SALVAGE / DELETE / 0x00 chunta hai aur finale bajata hai) tak baat kabhi pahunchti hi nahi thi.
  Ab director transition ka owner hai aur har tick zero check karta hai.
- **Entity textures ka UV layout galat tha** — skins player-sheet layout me painted thi jabki
  `Error422Model` custom `texOffs` use karta hai. Ab dono ek hi box-UV maths se generate hote hain.
- **`realBlockDeletion=false` (safe mode)** 422 ke world-drain aur light-delete par bhi lagta hai.
- **Announce chatter** default off — mod presence chat me announce nahi karta.

### Changed

- `mod_version` 0.1.0 → 0.6.0 (jar ka naam ab release se match karta hai).

[0.6.0]: https://github.com/TCL-Team/Error-404-World-Not-Found/releases/tag/v0.6.0
