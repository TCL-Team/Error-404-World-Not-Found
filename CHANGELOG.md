# Changelog

Sab notable changes. Format [Keep a Changelog](https://keepachangelog.com/), versioning [SemVer](https://semver.org/).

## [0.9.2] - 2026-11-06

**"Peeche dekho. Kuch nahi hai. Isi liye bura hai."** Nether/End ab safe room nahi, ek poori
"ban-worthy" layer, aur ek aisa presence jo bina kisi entity ke hota hai.

### Fixed (ye asli bugs the, dikhne wale nahi)

- **Nether aur End me mod band ho jaata tha.** Lagbhag har system `server.overworld()` par chalta tha,
  to Nether portal se bachna asal me escape tha: 422 follow nahi karta tha, chaos nahi hota tha,
  deletion front wahan pahunchti hi nahi thi, exploration cost nahi lagti thi. Ab har system
  `getAllLevels()` par chalta hai aur zone apni dimension yaad rakhta hai.
- **`/error404 restore` Nether ka data overworld me likh deta tha.** Journal ka key sirf
  (chunkX, chunkZ) tha, isliye Nether ke tode hue blocks overworld ke same coordinates par
  "restore" ho jaate the — grass ki jagah netherrack. Ab key me dimension hai aur restore sahi
  dimension me hota hai. Purane save files bhi load hote hain.
- **Anchor core sirf apni dimension me protect karta hai.** Pehle Nether me rakha anchor overworld
  ka same coordinate bachata tha aur jahan rakha tha wahan kuch nahi karta tha — yaani escape hatch
  chup-chaap fail ho raha tha. Ab anchor dimension ke saath save hota hai (purane anchors overworld
  maan liye jaate hain).
- **Jumpscare aur crash overlay frame par tick ho rahe the, tick par nahi.** 240 fps par 9-tick
  jumpscare 0.04 s chalta tha, 30 fps par 0.3 s. Ab dono `ClientTickEvents` se chalte hain.
  Dread overlay ka clock bhi pehle kabhi start hi nahi hota tha (koi call site nahi tha).
- **Zone me patli column chhoot jaati thi** jab wave ka budget column ke beech me khatam hota tha —
  "deleted" area ke andar khade rehne wale blocks.
- **Photosensitive mode intruder timer ko freeze kar deta tha**, to switch off karte hi ek queued
  silhouette turant screen par aa jaata tha.
- **Personal collapse portal ke through galat dimension me fire hota tha** (9 second countdown ke
  beech Nether jaane par overworld me Nether ke coordinates par zone).
- **Memory leak:**PlayerModel/PathMemory/Scares har us player ka data rakhte the jo kabhi join hua;
  ab disconnect par saaf.
- Zone ab apni actual surface se shuru hota hai (pehle poora budget aasman kha jaata tha), aur
  dragon egg, end gateway, vault, trial spawner protect hain.

### Added

- **Ban layer** (`banLayer`, default **off**, config screen aur `/error404 banlayer on|off`):
  - **BED DENIAL** — tumhara spawn point chala jaata hai, bina bataye. Agli death par pata chalta hai.
  - **HOME EVICTION** — jis bed me so rahe ho, wahi bed aur uske aas-paas ka floor obsidian ban jaata hai.
  - **NAME THEFT** — tab list me tumhara naam 30 second ke liye `ERR_404_xxx` ho jaata hai.
  - **DOOR SEAL** — jis darwaze se aaye, wo peeche se band ho jaata hai.
  - **INVENTORY RANSOM** — ek item type 300 blocks door ek chest me, coordinates ke saath. Chori nahi, ransom.
  - **SLOW THEFT** — ek item type aadha gayab, koi message nahi, koi sound nahi. Sabse zyada chubhne wala.
  - **THE LOCKDOWN** — ANNEXATION me 15% se neeche, ek baar: blindness, nausea, saare lights gayab,
    spawn bed gaya, darwaza band, aur "nothing was saved" server-wide.
  - **MOTD capture** — server multiplayer list me apna naam badal deta hai: `ERROR 404: World Not Found`.
    `/error404 restore` par, config off par, aur server stop par wapas.
- **Presence** — koi entity nahi, sirf awaazein jo ek asli aadmi ki jagah se aati hain:
  peeche se aate huye chaar kadam jo ruk jaate hain; teen knocks us darwaze par jis taraf tum nahi
  dekh rahe; aur tumhari apni saans, zara si side me, zara si zyada loud.
- **DESYNC me ek early cue** — whisper only, face nahi. Jo player apni duniya saaf rakhta hai use
  bhi pehle ghante me pata chal jaata hai ki kya install kiya hai.
- **Off-axis ambience** — whisper head ke andar se nahi, kandhe ke peeche se.

### Fixed (design)

- Ban layer ka har action `/error404 restore`, `/error404 banlayer off`, config off, aur server stop
  se undo hota hai. Names 30 second me khud wapas, MOTD restore par.

## [0.9.1] - 2026-11-06

**"Woh chup-chaap tumhara naam likh raha hai."** 20x20 deletion, jumpscares, aur ek layer jo darata
nahi — rok deta hai.

### Added

- **Jumpscares.** Static plate → face → corrupted frame → gone, 0.45 s total. Three original textures
  (`scare_face`, `scare_face_err`, `scare_glitch`, sab `tools/gen_scare_faces.py` se) aur teen naye
  original sounds (`scare_sting`, `scare_breath`, `scare_whisper_name`). Sirf **justified** triggers:
  422 ka window rule, personal collapse ka warning, quarter-health hit, paas me structure appear,
  aur ANNEXATION me contact. 2 min cooldown per player, DELETION se pehle nahi, anchor zone me nahi,
  `chill` me nahi, pehle minute me nahi.
- **Disturbing layer** — darr nahi, galat-pan:
  - naam ki sign jahan tum ek ghante pehle khade the, jise tumne kabhi nahi banaya
  - tumhare tode hue block types se bani 3x3 hut, andar bed, koi darwaza nahi
  - tumhara hi item, tumhare naam se renamed, pairon me gira hua
  - obsidian pillars, ek har death ke liye
- **Dread overlay** — tunnel vision + desaturation integrity ke saath, aur kabhi-kabhi screen ke kone se
  ek kaala silhouette guzarta hai jo entity nahi hai (koi hitbox nahi, koi sound nahi).

### Changed

- **Deletion ab 20x20 blocks ka zone hai, 16x16 chunk nahi.** Ek saath gayab nahi hota: 4-column waves
  me khud ko khaata hai (~2 s), phir 20 block offset par padosi zone seed karta hai — to woh ek
  **chalti hui front** lagti hai, alag-alag gaddhe nahi. Har block journal me jaata hai, `restore` waisa
  hi kaam karta hai. `DeletionJournal.PER_CHUNK` 1024 → 4096 (ek poora zone per chunk recoverable).
- **Commands default me chhupe hain.** Poora `/error404` tree register hota hai par dikhta nahi — sirf
  operator, cheats-on host, ya `/error404 enable` ke baad. `/error404 disable` se dobara chhupa do.
  Mod khud chalta rehta hai; commands sirf testing ke liye.
- Zones ab har tick advance hoti hain (pehle deletion front ke throttle par latak rahi thin, jo unhone
  ko dheema kar diya tha).

## [0.9.0] - 2026-11-06

**"Woh dekh raha hai — aur dekhne nahi deta."** 422 ka window rule, teen naye chaos events, in-game settings.

### Added

- **Window rule.** 422 ko dekhte rehna ab free nahi hai: 2.25 second continuous eye contact ke baad
  woh *sight wapas le leta hai* — static burst, particles, aur woh exactly us jagah se gayab ho jaata
  hai jahan aap dekh rahe the, aapke cone ke bahar. Damage nahi (woh ANNEXATION ka stare rule hai),
  sirf yeh ki aapki aankhein defence nahi hain.
- **Watching you sleep.** Sote hue player ke bistar ke paas chup-chaap khada ho jaata hai — koi awaaz
  nahi, koi damage nahi. `hauntSleepers()` wala violent version alag hai; yeh wala bas wahan hota hai.
- **Teen naye chaos events** (pool 9 se 12):
  - `night` — dopahar me hi raat, ek tick me (client ko explicit time packet, warna sky purana hi rehta hai)
  - `hunger` — bhookh turant 4-9 point gir jaati hai, koi reason nahi, aakhri 2 point safe
  - `silence` — 24 block ke andar saari awaazein 8 second ke liye band
- **Settings screen** — Mod Menu entry, ya `/error404config` bina Mod Menu ke. Sab switches turant
  `config/error404.json` me, destructive waalon pe tooltip ki exactly kya destroy hota hai.
- **`ERR_404_X` — The Heartbeat Vault.** Sealed obsidian room, har floor block ek pressure plate jo
  kahin wired nahi, aur beech me ek heartbeat. Andar jaane ka ek hi raasta hai: deewar todna.
- **Static audit (`tools/audit.py`)** — CI me har push par: har `ModSounds` constant sounds.json me
  hona chahiye aur uski ogg file maujood ho, har `translatable()` key ka translation ho, har model ka
  texture disk pe ho, saare locales `en_us` ko mirror karein, aur Java literals me non-English text na ho.
- **Store art** — `docs/art/banner.png`, `docs/art/icon_400.png` (`tools/gen_promo.py`, is repo ke
  apne panorama + wordmark se), aur `docs/LISTING.md` me paste-ready listing copy.

### Changed

- `/error404 summon` ab 422 ko aapke **dekhne ke bahar** rakhta hai (bilkul natural spawn ki tarah),
  aur `/error404 help` chaos ke saare fire-able event names list karta hai.
- Saare locales (hi_in, en_gb, de_de, ja_jp) ab `en_us` ke saath exactly same keys rakhte hain.

## [0.8.1] - 2026-11-06

**"Woh paas rehta hai."** Chunk collapse dikhta hai, apna chunk bhi jaata hai, aur title screen creepier.

### Added

- **Visible chunk collapse** — deleted chunks are now taken apart right in front of you: the one you
  are looking at (within a 3-9 chunk cone), block by block, with the sound and particles to match,
  then instantly resynced to the client so you watch the hole appear instead of walking into it later.
- **Personal collapse** — from CASCADE, after your first death, the mod can pick *your* chunk. Nine
  seconds of announcement ("YOU ARE STANDING IN A DELETED CHUNK", actionbar countdown, heartbeat),
  then the chunk goes. Still inside? Teleported into the void, 8 damage, and the server tells everyone
  you were deleted with your chunk. Out in time? It tells you that too.
- **Creepy title screen** — shipped as a built-in resource pack (`Error 404: World Not Found - title
  screen`, on by default, switchable off in Resource Packs):
  - an original panorama of a world that has been deleted — terrain that ends at a jagged cut,
    floating blocks over the void, ruins on the skyline, and a tall silhouette that is closer than
    it was the last time you looked
  - an original `ERROR 404 / WORLD NOT FOUND` wordmark with a corrupted first letter and a
    misplaced slice, plus a `NULL EDITION` bar, replacing vanilla's logo
  - scanlines, drifting error lines, a rare burst of static, and stack-trace splashes
- **`/error404 chaos fire <event>`** and `chaos on|off` were already in 0.8.0; the chunk event is now
  budget-capped so it cannot delete more than one journal's worth of blocks.

- **Settings screen** — Mod Menu entry (`/error404config` without Mod Menu). Every switch writes
  straight to `config/error404.json`; the destructive ones carry tooltips that say exactly what they
  destroy.
- **`ERR_404_X` — the heartbeat vault** — a sealed obsidian room with every floor plate wired to
  nothing and a heartbeat inside. Breaking in is the only way to the chest.

### Changed

- **422 no longer wanders off.** It is leashed to the nearest player (40 blocks): too far away, and it
  repositions instead of quietly despawning. Its despawn timer only runs while nobody is near, so a
  watched 422 never leaves. That is what "it follows you" should have meant all along.
- **422 spawns from the very first phase** (45 s cooldown instead of 90 s, no longer gated behind
  DESYNC), and the difficulty presets allow one extra alive at once, so a session meets it in minutes
  instead of twenty.
- Panorama overlay is the vanilla 16x128 size now — a 1024x512 overlay only ever showed its top-left
  corner under vanilla's fixed-size quad, which made the whole screen darker than intended.

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
