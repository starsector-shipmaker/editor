---
name: starsector-architecture
description: Teaches agents the starsector-shipmaker editor's internal architecture
---

# Starsector Shipmaker Architecture

## Core Architecture
- **EventBus**: Decoupled pub/sub communication. Events are records in `communication/events/`. Subscribe via `EventBus.subscribe(lifecycleParent, listener)` or the typed overload `EventBus.subscribe(lifecycleParent, EventClass.class, handler)`. Always clean up with `EventBus.unsubscribeByParent(this)` or `EventBus.unsubscribe(listener)`.
- **Undo/Redo**: `EditDispatch` posts edits. `PointEdits` (including unified `BoundsListEdit`), `SlotEdits`, `FeatureEdits`, and `SkinOverrideEdits` handle undoable actions. `Edit.getTargetEntity()` tracks dirty state for module variants in `UndoOverseer` and `LayerManager.isVariantDirty()`.
- **Layer System**: `ViewerLayer` → `ShipLayer` / `WeaponLayer` / `ProjectileLayer`. Each has a `LayerPainter` for canvas rendering. `WeaponSlotContextMenu` and `LayerManager` support editing or forking fitted module variants directly in tabs.
- **Painters**: `ShipPainter` owns sub-painters: `WeaponSlotPainter`, `LaunchBayPainter`, `EngineSlotPainter`, `BoundPointsPainter`, `CenterPointPainter`, `ShieldPointPainter`. Weapon slots inherit `renderOrderMod` across creation, defaulting, and mirroring.
- **UI Strings / Localization**: Hardcoded UI strings are managed via `shipeditor.utility.text.StringManager`. Values are extracted to JSON resource files (e.g., `system_messages.json`, `ui_general.json`) and retrieved via `StringManager.getString(KEY)`.
- **Shared UI Base Classes & Dialog Helpers**:
  - `AbstractCenterPanel`: Base class for `CollisionPanel` and `ShieldPanel` (unifies auto-calculate radius, sprite center snapping, opacity slider, visibility dropdown, coordinates readout, and radius spinner).
  - `AbstractSkinOverridesPanel`: Base class for `SkinSlotOverridesPanel` and `SkinEngineOverridesPanel`.
  - `AbstractWingListPane`: Base class for `BaseWingListPane` and `SkinWingListPane`.
  - `AbstractHullmodsListPane`: Base class for `BaseHullmodsListPane`, `SkinHullmodsListPane`, and `VariantHullmodsListPane`.
  - `AbstractQAReportDialog`: Base class for `HullQAReportDialog` and `WeaponQAReportDialog`.
  - `DialogHelper`: Centralizes `resolvePackageName()` and `resolveModIdName()` for `PickHullmodDialog` and `PickWeaponDialog`.
- **Graphics & Geometric Algorithms**:
  - `PolygonSimplifier` (Douglas-Peucker polygon decimation) and `SpriteContourTracer` (Moore-Neighbor boundary tracing with Jacob's stopping criterion), delegated to by `CollisionHullGenerator` and `SpriteOutlineTracer`.

## Launch Bay Architecture
- `LaunchBay`: Data model (id, arc, angle, size, mount, renderOrderMod, portPoints list)
- `LaunchPortPoint`: Physical spawn coordinate. Extends `BaseWorldPoint`, implements `SlotPoint`. Delegates properties to parent `LaunchBay`.
- `LaunchBayPainter`: Manages both `baysList` (LaunchBay objects) and `portsIndex` (LaunchPortPoint objects). Hotkeys: Shift=add port to selected bay, Ctrl=create new bay.
- `BayDataControlPane`: UI panel for editing bay properties (angle, arc, size, mount, ID).
- `LaunchBaysTree`: Tree view where bays are parent nodes, ports are draggable children.

## Data Loading Pipeline
1. `JsonSpecLoader.loadHullFile()` → Jackson deserializes to `HullSpecFile` (retaining custom fields via `@JsonAnySetter`)
2. `ShipPainterInitialization.loadHullData()` → converts JSON data to canvas entities
3. `initSlots()` → iterates `weaponSlots[]`, creates `WeaponSlotPoint` or `LaunchBay`+`LaunchPortPoint`
4. `rotatePointByCenter()` → transforms JSON coords to canvas coords (90° CCW rotation)

## Data Saving Pipeline
1. `SaveHullAction.rebuildHullFile()` → collects all editor state
2. `rebuildWeaponSlots()` → merges weapon slots + launch bays
3. `transformSlotsFromBays()` → converts `LaunchPortPoint` canvas positions back to JSON coords via `SHIP_CENTER` mode
4. `SlotLocationsSerializer` → writes `Point2D.Double[]` as flat `[x1, y1, x2, y2]` array
5. Single-port bays auto-duplicate their coordinate
6. `SaveActionUtilities.mergeUnrecognizedProperties()` → preserves unrecognized third-party mod JSON fields across `SaveHullAction`, `SaveVariantAction`, `SaveWeaponAction`, and `SaveProjectileAction`
7. `SaveVariantAction.saveVariant()` → performs cascading save detection for dirty fitted module variants via `LayerManager.isVariantDirty()`
8. `SaveCSVAction` & `CSVIDSyncHandler` → perform UTF-8 BOM-aware row ID lookup (`findRowId`, `findRowIdKey`) and atomic CSV saving

## Coordinate Transforms
| Direction | Method | Formula |
|---|---|---|
| Load (JSON→Canvas) | `rotatePointByCenter(input, tc)` | `canvasX = -input.Y + tc.X`, `canvasY = -input.X + tc.Y` |
| Save (Canvas→JSON) | `SHIP_CENTER` mode | `jsonX = -(canvasY - centerY)`, `jsonY = -(canvasX - centerX)` |

These are exact mathematical inverses.

## Additional Resources
- [event_bus_template.md](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-architecture/resources/event_bus_template.md): Template for defining and subscribing to EventBus events.
- [EventSubscriptionExample.java.txt](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-architecture/examples/EventSubscriptionExample.java.txt): Reference implementation of lifecycle-bound EventBus subscriptions.
- [find_leaking_subscribers.sh](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-architecture/scripts/find_leaking_subscribers.sh): Script to detect classes subscribing to `EventBus` without corresponding unsubscription.
