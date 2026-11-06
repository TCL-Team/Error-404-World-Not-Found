# ERROR 404: WORLD NOT FOUND — Game Design Document

> **Mod:** horror mod for Minecraft Java Edition **1.21.11** (Fabric)
> **Status:** concept locked (v0.1) — code phase next
> **One line:** duniya dheere dheere delete ho rahi hai, aur jo bacha hai woh tumhe yaad kar raha hai.

---

## 1. Pitch

Player apni normal duniya me spawn hota hai. Kuchh bhi galat nahi lagta — bas chup zyada hai.
Phir, ek ek karke, **chunks gayab hone lagte hain**. Kahin ek gaon ka aadha ghar reh jaata hai, kahin seedhiyan aasman me khatam
ho jaati hain, aur kahin tumhara hi purana base dobara ban jaata hai — lekin andar se khaali.

Jab duniya ka "integrity" girta hai, ek entity aati hai: **422 — the Unprocessable**.
Yeh chase nahi karti. Yeh **dekhti** hai, aur jab tum na dekho tab badalti hai.
Mod ka dar jumpscare me nahi — **duniya ke rules tutne** me hai. (Isi wajah se ERROR 422 se inspiration hai, copy nahi:
assets, texture, behaviour aur kahani sab original.)

**Theme line (title screen / lang):**
> `ERR_404: requested world could not be found.`
> `ERR_422: entity could not be processed.`

---

## 2. Naam aur identity

| Cheez | Value |
|---|---|
| Mod name | **Error 404: World Not Found** |
| Mod id | `error404` |
| Entity | **422 — the Unprocessable** (internal id `error_422`) |
| Design language | white-grey terminal text, blood-red `ERR_` lines, magenta "missing texture" checker, black void + faint grid |
| Tagline | *The world is being deleted. He is the one deleting it.* |

Lore ko chhota rakha hai taki player khud theory banaye:

> Ek server tha jo crash ho gaya. Uska aakhri save aadha likha gaya.
> 404 bolta hai "page not found". 422 bolta hai "request samajh nahi aaya".
> Tum us duniya me late ho gaye ho jise abhi bhi likha ja raha hai —
> aur 422 tumhe us duniya ka hissa nahi maanta.

---

## 3. Core loop

```
explore -> anomalies mile -> integrity gira -> chunks delete -> 422 aayi
   ^                                                            |
   |                                                            v
   +---- data fragments / anchor core se duniya bacha lo <---- (choice)
```

- Duniya ek invisible counter chalati hai: **World Integrity 100% → 0%**.
- Integrity **time + player actions** se girti hai (explore karne se, naye chunks load hone se, sone se, 422 ke paas jaane se).
- Player ka goal: **8 Data Fragments** + **Anchor Core** bana ke "The Last Chunk" par duniya stabilize karna.
- Fail = integrity 0 = **World Not Found** ending.

Har phase me duniya zyada "galat" hoti hai, but player ke paas hamesha ek counter-play hota hai
(stability zone, torch, anchor, ya bas 422 ko ignore karna).

---

## 4. Phases (pacing)

Ek session ~ 15–25 min, poora arc ~ 3–4 sessions (configurable). Phase integrity se decide hota hai, time se nahi,
isliye slow player ko zyada time milta hai aur rusher ko jaldi milta hai.

### P0 — SIGNAL (100% → 80%)
- Ambience flat: crickets band, birds band, reverb badh jaata hai.
- Kabhi kabhi ek block galat jagah (aapke base me ek torch gayab, ek block neeche shift).
- Chat me ek hi line: `[Server] chunk (12, -48) failed to save.`
- 422 ka sirf ek silhouette, fog me, 1 tick ke liye. Koi sound nahi.

### P1 — DESYNC (80% → 60%)
- Door ke bahar chunk ke tukde render hona band (client-side hole + void), wapas aa jaate hain.
- Sun/moon ek frame stutter karte hain. F3 me world ka naam `world_not_found` dikhta hai.
- Peeche kadam ki awaaz — 1 step, hamesha 1 step.
- 422 pehli baar dikhti hai: 24–32 block door, fog ke kinare, hilte nahi. Dekho to gayab.

### P2 — DELETION (60% → 40%)
- **Real deletion shuru:** chunks ek "deletion front" ki tarah spawn se bahar ki taraf khaali hote hain.
  Pehle blocks dissolve hote hain (upar se neeche), phir chunk void ban jaata hai.
- **Anomaly structures** spawn hone lagte hain (Section 6).
- 422 active stalker ban jaati hai: out of frame move karti hai, pass aa kar rukti hai.
- Sky me patli grid lines, horizon pe "loading bar" wall.

### P3 — CASCADE (40% → 20%)
- Deletion front tez. Anomalies ke andar blocks **undo** hone lagte hain (jo structure tha, woh chalte-chalte mit jaata hai).
- Anomalies me se kuch **aapke builds ki corrupted copy** hoti hain.
- 422 ke touch se torch bujhte hain, blocks delete hote hain, footsteps sync chhod dete hain.
- Mobs freeze karke face karne lagte hain. Villagers ka sound band.
- Randomized "static flash" + inventory name corruption (visual only).

### P4 — ANNEXATION (20% → 0%)
- Duniya ab floating fragments ka archipelago hai, void ke beech me.
- 422 hunt karti hai (walk + teleport), lekin sirf **jab aap usse dekhte ho** damage karti hai.
- Sirf ek jagah bachti hai: **The Last Chunk** (anchor). Player ko wahan jaana hai.
- Finale (Section 8).

Har phase me transition par ek **hard cue**: 1 second screen static + ek deep sub-bass hit + chat line
`[Server] integrity at 60% — some regions are no longer available.`

---

## 5. Chunk deletion — mechanic detail

Teen layers, taaki horror bane **aur** world na toote:

1. **RENDER LAYER (safe, default ON)**
   Selected chunk columns client par render hi nahi hote → asli "gayab" feel, zero damage.
   Iske saath custom sky/fog (black void + grid) so that hole ke peeche void dikhe, "unloaded terrain" nahi.

2. **DECAY LAYER (default ON, reversible)**
   Server blocks ko ek budget ke andar hataata hai: max ~2 chunks/sec, surface se neeche layer by layer,
   particles ke saath, aur player ke 8 block ke andar **never**. Jo blocks hate, unka "dust" (item) nahi girta —
   woh simply "never existed". Playability ke liye deletion front player ko 30s pehle chat + sound se warn karta hai.

3. **CORRUPTION LAYER (opt-in)**
   Chunk ko naye random seed se regenerate karna → terrain ka seam, do biome ek doosre me ghusey hue,
   "aadha ghar" type anomalies naturally ban jaate hain. Ye sabse disturbing layer hai, isliye default OFF.

**Stability zones:** `Anchor Core` block ke around 32-block radius me koi deletion nahi hoti.
Player apna "safe base" bana sakta hai — lekin anchor core ke liye 4 `corrupt_shard` chahiye jo 422 ke paas se milte hain.
(matlab: safe jagah ke liye risk lena padta hai.)

**Deletion front rules**
- Spawn point se bahar ki taraf failta hai, per-player naye chunks pehle marte hain.
- Player ke chunk me sirf tab aata hai jab integrity < 25% (endgame pressure).
- Bed/spawn agar delete hua to player ko "last known location" par respawn karwaya jaata hai, infinite death loop nahi.

---

## 6. Anomaly structures ("ajib ajib structures")

Ye **prebuilt structures ka list nahi** — ye ek ruleset hai jo player ke aas paas, uske data se, ad-hoc banti hain.
Isi se "kabhi kuch ban jata hai, kabhi kuch gayab ho jata hai" wala feel aata hai.

**Placement rules:** 80–140 blocks away, sirf P2+, ek baar me max 3 active,
player ke base ke 200 block ke andar kabhi nahi (except `memory_of_home` — woh exception hai, wohi darr hai).

| # | Anomaly | Kya hai | Hook |
|---|---|---|---|
| 1 | `null_hut` | Aadha ghar: 2 deewaarein, chhat nahi, andar bed hai jispe koi soya hua tha | Chest me written book: *"maine deewar banayi thi. kahan gayi?"* |
| 2 | `staircase_to_nowhere` | Stone-brick seedhiyan aasman tak, aakhri step ke aage kuch nahi | Chadhne par sound: tumhara hi footsteps, 2 blocks peeche |
| 3 | `memory_of_home` | Tumhare pehle base ki 1:1 copy — darwaze andar ki taraf khulte hain, chests khaali, ek chest me tumhare hi coordinates likha book | Proximity par 422 spawn chance 3x |
| 4 | `loadbar_wall` | 40 block unchi white concrete wall, beech me kaali progress bar jo dheere dheere bharta hai | Jab bar 100% ho → nearby chunks turant delete |
| 5 | `mirror_village` | Gaon jisme sab villagers frozen, sab tumhari taraf face, koi sound nahi | Kisi ek ghar ke andar `data_fragment` |
| 6 | `chunk_seam` | Do biomes galat silaai: snow biome aur desert ek hi chunk me, terrain ek doosre me ghusa hua | Yahan se `corrupt_shard` milta hai |
| 7 | `the_last_chunk` | Void me ek akela intact chunk, grass + ek ped + ek stone slab | Finale ka anchor point |
| 8 | `archive` | Ek corridor jisme signs hain — har sign par tumhare pichhle deaths / coordinates / playtime | Lore dump, jumpscare nahi |
| 9 | `the_sinkhole` | Perfect circular hole bedrock tak, kinare perfectly smooth, neeche se dhuaan nahi — sirf andhera | Andar `corrupt_shard` x2 |
| 10 | `inverted_tree` | Zameen ke neeche ulta ped, roots aasman ki taraf | Digging karne par reward, par 422 trigger |
| 11 | `door_to_room` | Beech me ek akela oak door, khulta hai ek 5x5 stone room me jisme koi exit nahi | Room ka ek block "false" hai — usme chest |
| 12 | `revoke` (event, structure nahi) | Jo structure tum dekh rahe ho, woh blocks ko **hata kar** gayab hota hai, ek-ek karke | Player ke inventory me us structure ka ek block copy aa jaata hai |

Har anomaly ke saath:
- ek **error code** (`ERR_404_A`, `ERR_404_B`…) jo player ke Error Log me record hota hai,
- ek **sound stinger** (alag-alag, sab original),
- ek **chance** ki woh structure "revoke" ho jaaye.

---

## 7. Entity: 422 — the Unprocessable

**Kya hai:** 3 block lambi, patli, pair nahi hain (0.2 block float karti hai), haath ghutno se neeche tak.
Body par white terminal text (corrupt error lines) aur magenta missing-texture checker patches.
Chehra nahi hai — khaali head ke andar ek **loading ring** dheere ghoomta hai.
Jab woh 10 block ke andar aati hai, uska chehra **tumhara hi chehra** ban jaata hai (player skin ka face, still frame — bilkul wrong).

**Kaise behave karti hai (per phase)**

| Mode | Trigger | Behaviour |
|---|---|---|
| OBSERVE | P1+ | 24–32 blocks, fog me, **dekhte waqt freeze**, nigaahein hatate hi move |
| APPROACH | P2+, integrity < 50% | Out of frame me 8–12 block jump; har jump par ek static blip + whisper |
| PRESENCE | P2+ | 5 block par aakar rukti hai, screen 1s desaturate, sab sound band (sirf heartbeat) |
| HUNT | P4 | Walk + teleport, light bujhati hai, blocks khaati hai |
| DORMANT | ending ke baad | Ghar ke bahar 40 block par khadi milti hai, hilte nahi. Bas. |

**Sabse important rule (mod ka signature):**
> **422 ko dekhna = damage. Ignore karna = safe.**
> 3 second se zyada look → screen glitch + inventory me se ek item "unprocessable" + chunk damage.
> Nazar hatate hi woh chali jaati hai.

Yeh typical "weeping angel" ka ulta hai aur player ko **kya dekhna hai** wala decision deta hai — asli horror yahi hai.

**Combat:** 422 immune hai. Usko "mar" nahi sakte.
Sirf 3 cheezein usse door rakhti hain: (1) roshni me na aana, (2) anchor core zone, (3) usse dekhna nahi.

**Sounds:** reversed whisper, dial-up/data chirp, deep sub hit, tumhara hi footsteps 1 step delay se,
ek "error tone" (synth se original), heartbeat jo integrity ke saath tez hota hai.

**Drops:** `corrupt_shard` (1–2) jab woh apne aap despawn ho — 4 shards = 1 Anchor Core.

---

## 8. Endings

Integrity 0 par **hamesha** ek explicit choice milta hai (config me auto bhi ho sakta hai):

**A. SALVAGE — "World Found"**
Last Chunk par Anchor place karo, 8 data fragments daalo, 60 second hold karo jab 422 attack karti hai.
Success → sky normal, deletion ruk jaati hai, sab anomalies reh jaate hain (jaan bujh kar — duniya ab tumhari hai, par nishaan reh gaye).
Ek 422 `dormant` reh jaati hai, base se 40 block door. Kabhi kabhi woh wahan hoti hai.

**B. DELETE — "World Not Found"**
Integrity 0 → fake client crash screen:
`java.io.IOException: ERROR 422 — world region could not be processed`
`Saved world moved to: saves/<name>/error404_world_not_found/`
Fir title screen par woh world **list me nahi** milta (rename ho jaata hai), aur ek naya "empty" world banta hai.
**Default: world ka automatic backup zip** ban jaata hai (`error404_backups/`), taki galti se kuchh na khoye.
Yeh "save delete" tabhi hota hai jab config me `destructiveEnding=true` ho — default me sirf rename + backup.

**C. 0x00 (secret / hardcore)**
Integrity 0 + player ne 422 ko 3 baar 3 second se zyada dekha → "0x00 ending": world literally empty void, player free-floating, aur ek final line:
`> nothing to process.`

---

## 9. Items, blocks, UI

**Items**
- `data_fragment` — glowing, cracked shard, 8 needed
- `corrupt_shard` — 422 drop, crafting material
- `error_log` — "Diagnostics" item: written book style, lists discovered anomalies, integrity, phase (v1 = book, v2 = custom GUI)
- `null_torch` — light jo sirf player ke liye hai (422 ke liye andhera), 422 ko 8 block tak door rakhta hai
- `static_glass` — translucent glitch block, rendering artifacts ke saath

**Blocks**
- `anchor_core` — stability zone 32 radius (light 15, particles, hum sound)
- `missing_texture_block` — decorative, magenta-black checker, crafting me use hota hai
- `void_stone` — deletion ke baad bachne wala "residue" block, walk karne par footstep sound nahi

**UI / HUD**
- Default me **koi naya HUD nahi** (horror ke liye zaroori).
- F3 debug me ek line: `World integrity: 62% (phase: DELETION)` — sirf tab jab `/error404 hud true`.
- `error_log` item ka GUI (v2), v1 me book.
- Phase transitions par action-bar / title, aur chat me `[Server]` fake lines.

**Commands** (`/error404`, operator only — v0.6 implementation)

| Command | Kya karta hai |
|---|---|
| `status` | integrity, phase, session seed, fragments, uptime |
| `start` / `stop` | session control (stop = world chhodna band, integrity wahi rahti hai) |
| `restore` | integrity 100 + `ended` clear + deleted chunks regenerate + stare count reset |
| `phase <signal\|desync\|deletion\|cascade\|annexation>` | phase force (integrity bhi set hoti hai) |
| `setintegrity <0-100>` | direct value |
| `demo` | 15x pacing — full 100→0 run ~5 min |
| `summon` | 422 ko 6 block aage spawn |
| `anomaly <ERR_404_A..X>` | ek structure abhi banao |
| `ending <salvage\|delete\|null>` | ending trigger (preview/test) |
| `backup` | world zip abhi likho |
| `reload` / `config save` | config round-trip |

---

## 10. Safety / fairness (bahut important)

Horror mod jo duniya khaata hai, usme guardrails must hain:

- **Backup first:** session start par world ka zip backup + warning chat line.
- **Non-destructive default:** real block deletion on, but *deletion of saved region files* off; endings me sirf rename + backup.
- **Protected radius:** player ke 8 block ke andar kabhi deletion nahi; bed/spawn/end portal frames/chests kabhi delete nahi (configurable).
- **Creative/spectator** players skip.
- **Multiplayer:** dedicated server par bhi chalta hai, session seed server deta hai, sab players same phase dekhte hain.
- **`/error404 stop`** turant sab kuchh normal kar deta hai. `restore` se deleted area wapas generate hota hai.
- **No real crash:** fake crash screen sirf overlay hai. Mod kabhi game/server crash nahi karta.

---

## 11. Content list (v1 target)

- 1 entity + 1 model + 3 textures (base, missing-texture, camouflaged face)
- 12 anomalies (6 v1 me: null_hut, staircase_to_nowhere, memory_of_home, chunk_seam, the_sinkhole, the_last_chunk)
- 5 items, 3 blocks
- 14 sounds (ambience, static, whisper, heartbeat, error tone, structure stinger, anchor hum…)
- 5 phases + 3 endings
- lang: `en_us` + `en_gb` (flavour) + `hi_in` (Hinglish flavour lines — mazedaar but optional)

---

## 12. Why this works (design notes)

- **404 + 422 ek hi kahani:** 404 = duniya nahi mili, 422 = tum process nahi hue. Do error codes ek loop banate hain.
- **Chunk gayab hona** Minecraft ka sabse "systemic" darr hai — player ko lagta hai game toot raha hai, jumpscare nahi.
- **Anomalies procedural** hain, isliye har playthrough me structures alag dikhte hain (replay value).
- **Look = damage** ka rule horror ko interactive banata hai, player ko agency deta hai.
- **Non-destructive default** mod ko servers/streamers ke liye usable rakhta hai.

---

## 11. 422 — lethal kit (v0.7)

Design rule: every ability must be **learnable** and **survivable**, or it is just a rage quit.
422 has no health bar and cannot be killed; it is avoided, not fought.

| Ability | Phase | Numbers (standard) | Counter |
|---|---|---|---|
| Contact | PRESENCE (P2+) | 6 dmg on contact, silent | distance, anchor zone |
| Stare | ANNEXATION (P4), look-rule tier | 3 dmg/s, +1.5 per 3 s stage, blindness 2 s + nausea 5 s | look away |
| Chase teleport | P2+ | 1 in 40 ticks, closes 2.5–10 blocks, never lands in an anchor zone | keep it visible |
| Wall breaking | HUNT (P4) | 4 blocks per 20–10 ticks along the line to the player | open ground |
| Sleep haunting | configurable (CASCADE default) | wakes the sleeper, destroys the bed, teleports adjacent in P4 | do not sleep near it |
| Darkness aura | P4 | Darkness I, 6 s, refreshed every 3 s inside 20 blocks | light or anchor |
| Death feeding | always | +0.03 movement speed per player death, cap 0.42 | — |
| Mercy withdrawal | always | after a kill: vanishes nearby copies, 5 min spawn cooldown | respawn safely |

### Threat presets

| Preset | Contact | Stare | Walls | Sleep from | Max alive | Teleport 1-in |
|---|---|---|---|---|---|---|
| `chill` | 0 | 0 | never | never | 1 | 90 |
| `standard` | 6 | 3 + 1.5/stage | every 20 t | CASCADE | 2 | 40 |
| `nightmare` | 10 | 5 + 2/stage | every 10 t | DESYNC | 3 | 20 |

Switched live with `/error404 difficulty <preset>`; persisted in `config/error404.json` as `difficulty`.

### The one safe place

Anchor cores create a 32-block stability zone. 422 cannot enter it, cannot teleport into it, and will
pace the edge instead. That is the mechanical reason the anchor ritual exists: it is not a score, it is
the only place in the world the entity has been told "already processed".

---

## 12. Chaos layer (v0.8)

The design question was: how do you beat ERROR 422's reputation for "it does anything" without shipping
a broken mod? Answer: a **named, bounded pool** of events with a visible cooldown, all recoverable.

- Gate: DELETION phase or later, config `chaos = true`, never inside an anchor zone.
- Cadence: first event at 90 s, then one every 60-180 s. Expected ~14 events in a 100% -> 0% run.
- Every world change is journaled: `/error404 restore` puts the chunk back exactly as it was.
- Every event is logged in the player's Error Log, so a player can see the mod was deliberate, not a bug.

| Event | Weight | Destructive | Recoverable |
|---|---|---|---|
| chunk gone | 22% | yes (1 chunk) | yes (journal) |
| inventory scramble | 12% | no | yes (it is shuffled, not deleted) |
| sudden death | 12% | kills at low health | no, but mercy rules apply |
| doors open | 10% | no | yes |
| sound behind you | 10% | no | n/a |
| lightning ahead | 10% | no (visual only) | n/a |
| mobs behind you | 10% | yes (fights) | yes (kill them) |
| displacement | 8% | no | yes (walk back) |
| fake message | 6% | no | n/a |

### AI: what makes it different from a stalker

| Layer | Behaviour |
|---|---|
| Path memory | replays the player's own last 60 s of movement from CASCADE onward |
| Prediction | targets `position + velocity * 40 ticks` instead of the player |
| Habit model | counts stillness, depth, torch use, door use; changes where it appears |
| Pressure timer | if it has not been seen for 45 s, it relocates into the blind spot (20-28 blocks) |
| Sleep haunting | wakes sleepers, destroys the bed, stands next to it in ANNEXATION |
| Wall breaking | chews through anything between it and the player |
| Sanctuary | cannot enter, cannot teleport into a 32-block anchor zone |
