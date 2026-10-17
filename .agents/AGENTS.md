# Starsector Shipmaker - Agent Guidelines

## Project Overview
- **Tech Stack:** Java 17 / Swing / Maven / Jackson / Lombok / Log4j2 / LWJGL 3 (OpenGL)
- **Description:** A visual editor for Starsector `.ship`, `.variant`, `.skin`, and `.wpn` files
- **Location:** `/run/media/banglc/work/starsector/starsector-shipmaker`

## Critical Starsector Engine Knowledge
1. **Launch Bays are weapon slots**: They live in the `weaponSlots` array with `type: LAUNCH_BAY`. They share the same ID namespace as weapons (e.g., `WS xxx`).
2. **`locations` array format**: Flat `[x1, y1, x2, y2, ...]` pairs. First pair = bay center. Subsequent pairs = spawn ports. A single-port bay MUST duplicate its coordinate.
3. **ID prefix**: ALL weapon slots including launch bays MUST use the `WS` prefix (e.g., `WS 001`). Never use `LB`.
4. **Default values**: `arc` should default to 360, `mount` to HIDDEN, `size` to LARGE for launch bays.
5. **Coordinate system**: `locations` are relative to the ship `center`. The `center` field is `[Y_from_bottom, X_from_left]`.
6. **Engine style serialization**: Always use raw `styleID` strings, never rely on resolved style objects.

## Build & Test
- **CRITICAL BUILD RULE:** Always use `mvn package -DskipTests` instead of `mvn compile` for builds and verification to ensure complete shader/dependency packaging and Checkstyle validation.
- `mvn test` - Run full test suite
- `mvn package -DskipTests` - Create release JAR

## GitHub Operations
- Use `gh` CLI for GitHub interactions.
- Central skills repository: `starsector-mods/skills`.
- If auth fails, use `env -u GITHUB_TOKEN git push`.

## Architecture Quick Reference
- **EventBus**: Used for decoupled communication between components (`EventBus.subscribe(lifecycleParent, listener)` or typed `EventBus.subscribe(lifecycleParent, Class<T>, Consumer<T>)`; clean up via `EventBus.unsubscribeByParent(parent)`).
- **Undo/Redo**: Handled via `EditDispatch`. `BoundsListEdit` unifies bounds sorting/replacement in `PointEdits`. `Edit.getTargetEntity()` tracks dirty state for module variants in `UndoOverseer` and `LayerManager.isVariantDirty()`.
- **Coordinate transforms**: `rotatePointByCenter` (load) ↔ `SHIP_CENTER` mode (save).
- **Initialization & Saving**:
  - `ShipPainterInitialization` loads hull data; `SaveHullAction`, `SaveVariantAction`, `SaveWeaponAction`, and `SaveProjectileAction` serialize spec files back to disk.
  - `SaveActionUtilities.mergeUnrecognizedProperties()` and `@JsonAnyGetter`/`@JsonAnySetter` on spec files preserve unrecognized custom third-party mod JSON properties non-destructively.
  - `SaveVariantAction` performs cascading save detection for dirty fitted module variants, and `WeaponSlotContextMenu` / `LayerManager` support editing or forking module variants directly in tabs.
  - `SaveCSVAction` and `CSVIDSyncHandler` provide UTF-8 BOM-aware row ID lookup (`findRowId`, `findRowIdKey`) and atomic CSV saving.
- **Shared UI & Graphics Abstractions**:
  - `AbstractCenterPanel` (shared base for `CollisionPanel` and `ShieldPanel`), `AbstractSkinOverridesPanel`, `AbstractWingListPane`, `AbstractHullmodsListPane`, `AbstractQAReportDialog`, and `DialogHelper`.
  - `PolygonSimplifier` (Douglas-Peucker decimation) and `SpriteContourTracer` (Moore-Neighbor boundary tracing with Jacob's stopping criterion), delegated to by `CollisionHullGenerator` and `SpriteOutlineTracer`.
- **OpenGL Weapon & Missile Rendering**:
  - Weapon recoil direction: In editor space (0° pointing UP), recoil vector is `(-Math.sin(rotRads), Math.cos(rotRads)) * recoilOffset` (recoiling DOWN into the hardpoint/mount).
  - Hardpoint sprite pivot: Engine uses `setCenter(width/2, height/4)` for hardpoints vs `(width/2, height/2)` for turrets. In editor top-down space, hardpoint Y-ratio is `0.75` (not `0.5`).
  - Missile sprite scaling: Engine calls `sprite.setSize(size.x, size.y)` using the `.proj` `"size"` field, then `sprite.setCenter(center.x, center.y)` using `.proj` `"center"`. The center is relative to the `"size"` dimensions (bottom-left origin), NOT the raw image pixel dimensions. Use `spriteDimensions` (from `.proj` size) for both draw size and Y-flip pivot in `ProjectilePainter`.
  - Loaded missile animation: When rendering missiles inside launchers in `WeaponPainter.paintLoadedMissilesGL`, add the launcher's `recoilVector` to the missile's `paintAnchor` so missiles move synchronously with the recoil.
- **Database & Memory Indexing**:
  - `CoreIndexManager`: In-memory index of `starsector-core` data (`CORE_FILES_BY_TYPE`, `CORE_FILES_BY_ENTITY_ID`, `CORE_FILES_BY_PATH`). Collections must be cleared at the start of `loadCoreData()` to prevent memory cache accumulation.
  - `DatabaseQueryService`: SQLite layer using HikariCP (`SQLite-Pool`) and WAL mode, with `TYPE_CACHE` (`clearTypeCache()`), `getFileByEntityId`, `deleteFilesByModId`, and `csv_cache` support. `getFilesByType` deduplicates entries by canonical file path.
  - `HullsTreePanel` & `WingCSVEntry`: Tree cell renderer prefixes hull size `[Frigate]`, `[Destroyer]`, `[Cruiser]`, `[Capital]`, resolves fallback titles for fighter/module hulls, and displays entity ID/name for duplicate base names. `GameDataRepository` filters out blank/comment CSV rows on load.

## Mod Data Integrity Pitfalls
1. **Duplicate Slot IDs**: Always check for duplicate slot IDs in arrays. The engine uses strict parsing, whereas scripting parsers (like Python's `json`) may silently overwrite duplicates, causing unexpected serialization drops in nested objects (like `builtInWeapons`).
2. **.skin File Dependencies**: Changes to a `.ship` file's slot IDs break any `.skin` file that references them in `weaponSlotChanges`.
3. **.variant Hull IDs**: A `.variant` file's `hullId` can point to either a base `.ship` ID or a `.skin` ID.
4. **Java 17 Bytecode Requirement**: Starsector 0.98a runs OpenJDK 17 LTS. All mod JARs must target bytecode version 61.0 (`--release 17`). Ignore misleading "Java 7" error messages caused by JDK 21+ bytecode.
5. **Weapon Barrel Balance**: Burst sizes in `weapon_data.csv` must not exceed physical launch tube / barrel counts from `.wpn` offsets.
6. **Skin OP Standardization**: Buffed faction skins add +5/+10/+15/+20 OP by hull size; pirate/scavenged skins apply a flat -10% OP delta.


## Coding Standards & Refactoring
- **Skill Module:** Agents must always refer to the `starsector-coding-standards` skill (`.agents/skills/starsector-coding-standards/SKILL.md`) for all code modification workflows.
- **Rules Summary:**
  - Zero open-source copying.
  - Keep basic iteration loops (`for`, `while`) simple; do not over-complicate with Java 8 Streams unless abstracting array generators.
  - Use Java 14+ `switch` expressions for complex `if..else if` chains evaluating variables.
  - Wrap risky logic (I/O, Parsing) in `try-catch` blocks and use `@Log4j2` for all logging. No silent swallowing.
  - Add trace points (`log.debug`, `assert`) before complex blocks acting as runtime breakpoints.
  - Naming conventions (`PascalCase` types, `camelCase` methods/fields/variables, `UPPER_SNAKE_CASE` `static final` constants except `log`) are enforced by `maven-checkstyle-plugin` (`checkstyle.xml`) during the `validate` phase.
