# ERROR 404: WORLD NOT FOUND — Technical Roadmap

Target: **Minecraft Java 1.21.11 + Fabric** · Loader: **Fabric** · Preset: **Hardcore** · 422 AI: **Mix (stalker → signature)**

Yeh document code phase ka blueprint hai.

---

## 0. Sandbox ki reality (padho pehle)

Is repo ke dev sandbox me **JDK / Gradle / Maven repo access nahi hai** (outbound sirf `github.com`, npm, pypi).
Matlab mod ko yahan `./gradlew build` se compile nahi kiya ja sakta.
Isliye verification ka plan ye hai:

- **GitHub Actions** par build workflow (`.github/workflows/build.yml`) — har push par `./gradlew build`,
  log + jar artifact. Sandbox se `gh run view` / `gh run download` se result milta hai.
- Local test user ke PC par: `./gradlew runClient` (Minecraft + mod launch).
- Har milestone ke saath **smoke-test checklist** (Section 8) — Jo cheez CI verify nahi kar sakti (visuals, sound, feel),
  woh checklist se verify hogi.

> Agar chaho to main sandbox me **network ke bina bhi** structure + code likh sakta hoon, par tab compile errors ka
> risk rahega. Workflow ke saath risk kam ho jaata hai — build fail hua to log se fix karenge.

---

## 1. Tech stack (locked)

Target: **Minecraft Java 1.21.11 + Fabric**. Yahi document code phase ka blueprint hai.

| Cheez | Version | Note |
|---|---|---|
| Minecraft | `1.21.11` | "Mounts of Mayhem", 9 Dec 2025 |
| Fabric Loader | `0.20.1` | |
| Fabric API | `0.141.6+1.21.11` | official example mod ke saath match |
| Loom | `1.19-SNAPSHOT` | plugin id `net.fabricmc.fabric-loom-remap` |
| Gradle | `9.8.2` (wrapper) | wrapper jar repo me committed hai |
| Java (build) | **JDK 25** | Loom 1.19.x ka minimum runtime |
| Java (bytecode) | `--release 21` | jar 1.21.11 ke 21 JVM par bhi chale |
| Mappings | `loom.officialMojangMappings()` | |
| Mod id | `error404` | group/package `dev.error404` |

**Split source sets:** `src/main` (server + common), `src/client` (render/sound/screen). Loom ka `splitEnvironmentSourceSets()`.

**Loader choice ka reason:** Fabric — fast iteration, chhota jar, aur 1.21.11 ke liye official template ready hai.
NeoForge port baad me (code me loader-specific sirf 3 jagah hoga: entrypoints, events, networking registration).

### 1.21.11 mapping gotchas (bahut important)

Yeh 1.21.9/1.21.11 me badla hai, purane tutorials galat hain:

- `ResourceLocation` → **`Identifier`** (`net.minecraft.resources.Identifier.fromNamespaceAndPath(...)`)
- NBT save/load → **`ValueInput` / `ValueOutput`** (`addAdditionalSaveData(ValueOutput)`), `Optional` returns (`getInt("x").orElse(0)`)
- `SavedData` + **`SavedDataType`**, load via `level.getDataStorage().computeIfAbsent(TYPE)`
- Entity rendering → **`EntityRenderState` / `extractRenderState`** pattern, `MobRenderer<T, S, M>`
- HUD → **`HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ...)`** (purana `HudRenderCallback` nahi)
- Networking → `CustomPacketPayload` + `PayloadTypeRegistry.playS2C()/playC2S()` + `ClientPlayNetworking`
- Commands permissions → `source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)`
- Model layers → `EntityModelLayerRegistry.registerModelLayer` (Fabric), `PartNames.HEAD/BODY/...`
- Data-driven stuff (blockstates/models) → naye `client/data/models` generators; hum json haath se likhenge

---

## 2. Repo structure (target)

```
Error-404-World-Not-Found/
├── build.gradle                  # loom + deps
├── gradle.properties             # versions (single source of truth)
├── settings.gradle
├── gradlew / gradle/wrapper/*    # Gradle 9.8.2
├── LICENSE                       # MIT (recommend)
├── README.md
├── docs/
│   ├── GAME_DESIGN.md            # concept (done)
│   ├── ROADMAP.md                # this file
│   └── concept/*.png             # concept art
├── .github/workflows/build.yml   # CI: gradlew build
└── src/
    ├── main/java/dev/error404/
    │   ├── Error404.java                 # ModInitializer, registry wiring
    │   ├── ModRegistry.java              # items/blocks/entity/sounds (ek jagah)
    │   ├── ModSounds.java
    │   ├── config/ModConfig.java         # JSON config (hand-rolled, no deps)
    │   ├── integrity/IntegrityState.java # SavedData: integrity, phase, seed, flags
    │   ├── integrity/Phase.java          # enum P0..P4 + thresholds
    │   ├── integrity/IntegrityDirector.java # per-tick server logic, phase transitions
    │   ├── deletion/DeletionFront.java   # chunk delete/corrupt queue + budget
    │   ├── deletion/DeletionRules.java   # protection rules (spawn/bed/player radius)
    │   ├── anomaly/AnomalyType.java      # enum: 12 anomalies + metadata
    │   ├── anomaly/AnomalyPlacer.java    # placement rules (80-140 blocks, P2+)
    │   ├── anomaly/builders/*.java       # per-anomaly structure builders (server-side block placement)
    │   ├── entity/Error422Entity.java
    │   ├── entity/ModEntities.java       # EntityType + attributes
    │   ├── entity/goals/*.java           # ObserveGoal, ApproachGoal, PresenceGoal, HuntGoal
    │   ├── item/DataFragmentItem.java, CorruptShardItem.java, ErrorLogItem.java, NullTorchItem.java
    │   ├── block/AnchorCoreBlock.java, MissingTextureBlock.java, VoidStoneBlock.java
    │   ├── command/Error404Command.java  # /error404 ...
    │   ├── network/ModNetworking.java    # payload registration
    │   ├── network/payload/*.java        # SyncStateS2C, ClientEffectS2C, LookAt422C2S, ...
    │   └── util/ErrorText.java           # random ERR_ lines generator
    ├── main/resources/
    │   ├── fabric.mod.json
    │   ├── error404.mixins.json
    │   ├── error404.classtweaker (only if needed)
    │   ├── assets/error404/{icon.png, lang/en_us.json, textures/...}
    │   ├── data/error404/{recipe, loot_table, tags, damage_type}
    │   └── sounds.json + sounds/*.ogg
    └── client/java/dev/error404/client/
        ├── Error404Client.java           # renderers, hud, keybind, payload receivers
        ├── render/Error422Renderer.java, Error422Model.java
        ├── render/VoidSkyRenderer.java   # black void + grid + "loading bar" horizon
        ├── render/HudGlitchRenderer.java # scanlines, ERR_ lines, integrity text (opt-in)
        ├── render/StaticFlash.java       # fullscreen static overlay
        ├── mixin/ChunkHidingMixin.java   # render-layer chunk hiding (RISK — see §6)
        ├── mixin/FogMixin.java
        └── sound/*.java                  # whisper, heartbeat, blip
```

---

## 3. Milestones — all shipped, CI-verified

Har milestone ke baad jar GitHub Actions par build hota hai (latest run: green).

| | Milestone | Kya bana | State |
|---|---|---|---|
| **v0.1** | Skeleton | Gradle project, `fabric.mod.json`, entrypoints, mixin configs, `IntegrityState` SavedData, 5-phase enum, per-tick director, JSON config, `/error404` command tree | ✅ |
| **v0.2** | Deletion | `DeletionFront` (1 chunk/pass, 256 blocks, never blocks the tick), `DeletionRules`, `void_stone` residue, `WorldBackup` (region zip, 6h debounce, 2 GB cap), full restore path | ✅ |
| **v0.2b** | Assets | 19 synthesised OGGs (`tools/gen_sounds.py`), 13 textures + 3 entity skins (`tools/gen_textures.py`), models/blockstates/item defs, lang, 6 recipes | ✅ |
| **v0.3** | Anomalies | 12 procedural builders + `AnomalyManager` (placement, discovery, revoke, `freeze()`), error codes `ERR_404_A..X` | ✅ |
| **v0.4** | Entity | `Error422Entity` (mode state machine, stare rule at P4, `vanish()` loot), custom 128x128 model, 3 textures, renderer with jitter + stare wobble | ✅ |
| **v0.5** | Endings | `EndingManager` (SALVAGE / DELETE / 0x00), anchor ritual (8 fragments + right click), non-destructive archive, `restore` hardening, 7 advancements | ✅ |
| **v0.6** | Polish | HUD glitch layer (scanlines, drifting ERR lines, phase flash, static, vignette), fog + void-horizon mixin, ambience cross-fade, photosensitivity + HUD sync flags | ✅ |
| **v1.0** | Release | CurseForge/Modrinth metadata, banner + trailer, optional Mod Menu GUI | ⏳ |

### CI ke baad kya bacha hai (honest list)

- **In-game feel pass:** sirf player verify kar sakta hai — sound mix levels, fog aggressiveness,
  422 ki pacing, anomaly density. Sab `config/error404.json` se tunable hai.
- **Mod Menu / config GUI** — optional dep, abhi file-based config.
- **hi_in lang file** — flavour lines, abhi `en_us` complete hai.
- **Dedicated-server soak test** — code MSPT-flat hai (1 chunk / pass), par 4+ ghante ka run
  bina test kiye claim nahi kar sakta.

---

## 4. Data & state

`IntegrityState` (SavedData, overworld):
```java
int integrity            // 0..100
Phase phase              // P0..P4 (derived + latency: phase ek baar badha to wapas nahi)
long sessionSeed         // deterministic client effects ke liye
List<String> foundAnomalies  // error codes
Set<Long> deletionQueue  // chunk keys (x, z) — restore ke liye
int dataFragmentsCollected
boolean ended            // 0/1/2
BlockPos anchorPos       // Last Chunk
```
- Tick: `IntegrityDirector` 20 ticks me ek baar chalta hai (performance).
- Drop rates: `-1 integrity / 60s` base, `-1 / 10s` naye chunk explore karne par (anti-rush), `-2` 422 ke 10 block ke andar.
- Raise: Anchor Core zone me `+1 / 30s`, `data_fragment` use karne par `+6`.

---

## 5. Safety implementation (must)

```
config/error404.json
{
  "enabled": true,
  "destructiveEnding": false,      // HARDCORE preset + safety: false => rename + backup only (set true for the "save is gone" experience)
  "backupOnStart": true,
  "realBlockDeletion": true,       // HARDCORE: on. false => sirf render layer (safe mode)
  "playerSafeRadius": 8,
  "maxChunksPerSecond": 2,
  "protectBlockEntities": true,    // chests, spawners, beds...
  "dummyWorld": false,             // true => alag "error404_demo" world me hi deletion
  "hud": false,
  "phaseDurations": {...}
}
```
- Session start: `saves/<world>/error404_backups/<timestamp>.zip` (region folder only).
- `/error404 restore` → deleted chunks `chunk.setUnsaved(true)` + regeneration, integrity 100.

---

## 6. Risks & fallbacks

| Risk | Impact | Fallback |
|---|---|---|
| **Chunk render hiding** ke liye mixin (`ViewArea` / `LevelRenderer`) 1.21.11 me signature badal sakta hai | build break | Pehle **void sky + dense fog + block decay** se same feel; mixin ko alag commit me rakho, flag ke peeche (`clientChunkHiding`), fail ho to disable |
| Block decay slow machines par lag | stutter | Ek tick me max 64 block, section-wise, `ServerLevel` scheduler thread se |
| Multiplayer desync (client static flash vs server state) | confusion | Sab kuch server-authoritative + `sessionSeed` se deterministic client visuals |
| Entity model/render API change | entity invisible | Vanilla skeleton reuse (skeleton model + custom texture) as v0.3 fallback |
| Deletion se player ka base kha jaana | data loss complaints | backup + protected radius + `/error404 restore` + creative skip |
| 422 jumpscare se log complain karein (epilepsy) | harm | `photosensitivity` config flag: no rapid flashing, static ko "slow" mode me |

---

## 7. Assets plan

| Asset | Count | Spec |
|---|---|---|
| Entity textures | 3 | 64x64: `error_422.png` (base), `error_422_missing.png`, `error_422_face.png` |
| Item textures | 5 | 16x16, palette-clean (white/grey + red accent) |
| Block textures | 3 | 16x16 |
| GUI | 1 | `error_log.png` (v0.5) |
| Item/entity models | 8 json | java block model |
| Sounds | 14 | `.ogg` mono 44.1k, original synthesized (no copyright samples) |
| Lang | 2 | `en_us.json`, `hi_in.json` (Hinglish flavour lines) |
| Icon / banner | 2 | 128x128 icon, 1280x720 banner — docs/concept art se derive |

Sab asset **original** honge (ERROR 422 ka koi asset use nahi).

---

## 8. Smoke-test checklist (manual, har milestone)

- [ ] `./gradlew runClient` launch, crash nahi
- [ ] naya world → 30 sec me P0 clear, integrity 100
- [ ] `/error404 phase P2` → chunk deletion dikhti hai, FPS 60+
- [ ] `/error404 anomaly null_hut` → structure spawn, error code chat me
- [ ] `/error404 summon422` → entity dikhti hai, dekhne par freeze
- [ ] 3s look → damage event, ignore → despawn
- [ ] `/error404 stop` → sab normal, koi residual glitch nahi
- [ ] `/error404 restore` → deleted area wapas
- [ ] dedicated server: 2 clients, same phase dikhta hai
- [ ] config edits reload (`/error404 config reload`)

---

## 9. GitHub CI workflow (proposed)

```yaml
name: build
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build --stacktrace
      - uses: actions/upload-artifact@v4
        with: { name: error404-jar, path: build/libs/*.jar }
```

---

## 10. Next actions (jab tum "code" bolo)

1. gradle skeleton + CI workflow + `/error404 status` (v0.1) — ek PR.
2. CI green hone ke baad v0.2 deletion.
3. Assets tabs tak main placeholder textures banaunga (16x16 / 64x64 pixel-art), baad me polish.
