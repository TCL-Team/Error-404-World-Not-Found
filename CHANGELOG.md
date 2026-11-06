# Changelog

Sab notable changes. Format [Keep a Changelog](https://keepachangelog.com/), versioning [SemVer](https://semver.org/).

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
