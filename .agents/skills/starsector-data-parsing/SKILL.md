---
name: starsector-data-parsing
description: Teaches agents how Starsector data files work and how the editor parses/serializes them.
---

# Starsector Data Parsing

This skill teaches agents how Starsector data files work and how the editor parses/serializes them.

## Skill Directory Structure
- **`SKILL.md`**: Main instructions (this file).
- **`resources/`**:
  - [JacksonConfig.java.txt](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-data-parsing/resources/JacksonConfig.java.txt): Reference Jackson ObjectMapper configuration for relaxed Starsector JSON parsing.
- **`examples/`**:
  - [CustomCsvSerializer.java.txt](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-data-parsing/examples/CustomCsvSerializer.java.txt): Reference implementation for preserving Starsector CSV structure and comments.
- **`scripts/`**:
  - [verify_json.py](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-data-parsing/scripts/verify_json.py): Helper script to validate Starsector JSON files.

## .ship File Format
- JSON with comments (`#` style), parsed by custom `JsonProcessor`
- Key fields: `hullId`, `hullName`, `hullSize`, `center` [Y,X], `height`, `width`, `spriteName`, `style`, `bounds`, `weaponSlots`, `engineSlots`, `builtInWeapons`, `builtInWings`, `builtInMods`
- `weaponSlots` array contains ALL slots including LAUNCH_BAY type
- `locations` is a flat array of X,Y pairs (NOT nested arrays)
- For LAUNCH_BAY: first pair = bay center, subsequent = spawn ports. Single-port bays need duplicate coordinates.
- Weapon slot IDs must use `WS` prefix with space and 3-digit padding: `WS 001`

## .variant File Format
- JSON with `variantId`, `hullId`, `weaponGroups` (array of groups with slot-to-weapon mappings), `wings` (array of wing IDs), `hullMods`
- Slot IDs in variants must match the `.ship` file's `weaponSlots` IDs exactly

## .skin File Format
- JSON overriding hull properties: sprite, weapon slot overrides, engine style overrides

## Serialization Pipeline
- Jackson ObjectMapper with custom serializers/deserializers.
- **Non-Destructive Custom JSON Property Preservation**: `HullSpecFile`, `VariantFile`, `SkinSpecFile`, `WeaponSpecFile`, and `ProjectileSpecFile` use `@JsonAnyGetter` and `@JsonAnySetter` (`unrecognizedProperties` map) paired with `SaveActionUtilities.mergeUnrecognizedProperties()` during save actions (`SaveHullAction`, `SaveVariantAction`, `SaveWeaponAction`, `SaveProjectileAction`) so unrecognized third-party mod JSON fields on disk are preserved across saves.
- **`JsonProcessor` Escaped Quote & Comment Handling**: `JsonProcessor.straightenMalformed()` tracks backslash escapes (`\"`) inside string literals so `#`, `//`, or `;` characters inside strings are not corrupted, while stripping `#` and `//` / `/* ... */` comments outside strings.
- **Robust Parsing**: Deserializers like `CustomDeserializers.java` (e.g., `Point2DArrayDeserializer`, `ModulesDeserializer`, `ColorArrayRGBADeserializer`) must always use strict `try-catch` blocks, null/bounds checks, and `log.error` to prevent crashes on malformed JSON nodes.
- `Point2DArrayDeserializer`: reads flat number arrays as Point2D pairs.
- `SlotLocationsSerializer` / `Point2DArraySerializer`: writes Point2D[] back as flat arrays.
- `SaveHullAction.transformSlotsFromBays()`: converts LaunchBay port positions using `SHIP_CENTER` coordinate mode.
- `SaveHullAction` single-port duplication fix: `if (locations.length == 1) { locations = new Point2D.Double[]{locations[0], locations[0]}; }`
- **Cascading Module Variant Saves**: `SaveVariantAction.saveVariant()` checks `LayerManager.isVariantDirty()` across all fitted module variants (`variant.getFittedModules()`) and prompts the user to save dirty module variants before saving the parent variant.
- **CSV Loading & Saving**: `GameDataRepository` filters out blank/comment CSV rows on load and flattens package entries via `flattenPackageEntries()`. `SaveCSVAction` and `CSVIDSyncHandler` use UTF-8 BOM-aware row ID lookup (`findRowId()`, `findRowIdKey()`) and atomic file replacement on save.

## Coordinate System
- Ship JSON `center` field: `[Y_offset_from_bottom, X_offset_from_left]`
- All `locations` in weapon slots: relative to `center` point
- Editor load transform: `rotatePointByCenter()` — 90° CCW rotation
- Editor save transform: `SHIP_CENTER` mode — exact inverse
- Round-trip is mathematically lossless

## Common Pitfalls
- Never use `LB` prefix for launch bay IDs — engine silently ignores them
- Never save launch bays with arc=0 — use 360 as default
- Never save single-port bays without duplicating the coordinate
- Always preserve raw string IDs for engine styles, don't resolve to objects
- Odd number of values in `locations` array crashes the engine

## Modding Edge Cases & Integrity Pitfalls
- **Python JSON Parsers & Duplicate Keys**: Modders often copy-paste weapon slots, resulting in duplicate JSON keys (e.g. two `"WS 001"` in a `.variant`'s `weapons` block or `.ship`'s `builtInWeapons`). Python's default `json.loads` will silently overwrite/delete the first duplicate. This causes `org.json.JSONException` crashes in Starsector's strict engine. Always use strict parsing or custom string replacement when standardizing mod data.
- **.skin File Traps**:
  - `weaponSlotChanges` and `removeWeaponSlots` in `.skin` files reference the `baseHullId` slot IDs. If you standardize a base `.ship` file's IDs (e.g., `WS0001` -> `WS 001`), you MUST also update every `.skin` file that references it, or Starsector will throw a `NullPointerException` trying to `setArc()` on a null slot object.
  - A `.variant` file's `hullId` can point to a `skinHullId` instead of a base `.ship` ID. Batch-processing scripts must trace `variant.hullId -> skinHullId -> baseHullId -> .ship` to safely validate slot IDs.
- **Station Modding**: Multi-part stations (like boss encounters) use `.variant` files located in subdirectories like `data/variants/stations/` or `data/variants/drones/`. These are easily missed by flat-directory parsing scripts.
- **Save-Game Ghost Data**: Modifying weapon slot IDs across a mod is completely save-breaking. Furthermore, Starsector heavily caches custom player variants in `saves/missions/` and `saves/common/`. A cached variant referencing an old slot ID can cause the Main Menu to instantly crash (`RuntimeException: Slot id [X] not found`) when the game attempts to render random background fleets. Wipe `saves/` when performing mod-wide structural refactors.
