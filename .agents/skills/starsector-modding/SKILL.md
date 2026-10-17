---
name: starsector-modding
description: Patterns and mechanics for Starsector modding, including combat/campaign AI scripting, salvage drop calculations, skin OP standardization, weapon balancing, Nexerelin integration, Java 17 bytecode rules, and 0.98a compatibility.
---

# Starsector Modding Guidelines & Patterns

This skill captures comprehensive knowledge, design patterns, and debugging solutions for creating, auditing, updating, and balancing Starsector mods.

---

## 1. Java 17 Bytecode & 0.98a Runtime Standards (CRITICAL)

* **Target JRE 17 (Bytecode 61.0):** Starsector 0.98a runs on an OpenJDK 17 LTS runtime (`jre_linux`). All compiled JARs must strictly target Java 17 bytecode (class file major version `61.0`).
* **The "Java 7" Misleading Error Trap:**
  * If a class is compiled with JDK 21 (bytecode version `65.0`), the Starsector Java 17 runtime throws an `UnsupportedClassVersionError`.
  * Starsector's legacy exception handler catches this and misleadingly prints:
    ```text
    Compiled for the wrong version of Java, change the compile target to Java 7
    ```
  * **DO NOT compile for Java 7.** The actual cause is bytecode > 61 (e.g., compiled with Java 21). Always compile with `-source 17 -target 17` or `--release 17`.
* **Standard Compilation Command:**
  ```bash
  javac -encoding UTF-8 -source 17 -target 17 -d <out_dir> -cp "/run/media/banglc/work/starsector/*:/run/media/banglc/work/starsector/mods/LazyLib/jars/*:/run/media/banglc/work/starsector/mods/GraphicsLib/jars/*:/run/media/banglc/work/starsector/mods/MagicLib/jars/*:/run/media/banglc/work/starsector/mods/Nexerelin/jars/*:/run/media/banglc/work/starsector/mods/LunaLib/jars/*" @sources.txt
  ```
* **Decompiler Type-Casting Fixes (Procyon / CFR):**
  Decompilers often inject invalid `(Object)` casts into generic method invocations on newer OpenJDK versions.
  * *Fix:* Remove the cast and use strongly typed generics:
    ```java
    // Incorrect (Decompiler artifact):
    WeightedRandomPicker<String> picker = (WeightedRandomPicker<String>) new WeightedRandomPicker();
    picker.add((Object)"faction_id", weight);

    // Correct:
    WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>();
    picker.add("faction_id", weight);
    ```
* **0.98a Skill & Admin Updates:**
  * `fighter_doctrine` is obsolete in 0.98a. Replace with `carrier_group` in `.faction` commander skill lists.
  * Market admin skill initialization: assign `industrial_planning` (level 1) + `planetary_operations`.

---

## 2. Weapon Balancing & Barrel Geometry Rules

* **Burst Size vs. Barrel Count Rule:**
  * If a weapon's `burst size` in `weapon_data.csv` exceeds the physical barrel or launch tube count defined in its `.wpn` file (`turretOffsets` / `hardpointOffsets`), **cut the burst size in half**.
  * This prevents disproportionate alpha-strike damage and aligns projectile output with the weapon's visual geometry.
* **Kinetic DPS Benchmarks for Medium Ballistics:**
  * Medium ballistic kinetics should generally range between **110–250 sustained DPS** (e.g., Heavy Needler = 112.5 DPS, Heavy Autocannon = 200 DPS, Arbalest = 143 DPS).
  * Continuous medium kinetics exceeding 300+ DPS out-compete large mounts (Mark IX = 288 DPS) and must be tuned down or given magazine reload limits.
* **Missile Durability & Saturation:**
  * **Guided Cruise Missiles (e.g. Nova):** Require heavy HP pools (500–750 HP) and weaving AI (`MagicTargeting` / sinusoidal paths) to bypass point defense, but volley sizes must be restricted (1–2 missiles per burst) to avoid overwhelming shields instantly.
  * **Rocket Swarms (e.g. Hail):** Multi-stage HE rocket swarms with high projectile density easily saturate point defense. Limit ammo capacity to 5–8 volleys.
  * **Shotgun / Grapeshot Pellets:** Standardize pellet damage and ensure pellet count maps to weapon mount tiers.

---

## 3. Ship Skin (.skin) & Variant OP Standardization

* **Skin Ordnance Point (OP) Standardization:**
  When balancing custom variants vs base hulls, use standardized OP deltas:
  * **Buffed Faction Variants (e.g., Canebianco):** Scale by hull size:
    * Frigates: Base +5 OP
    * Destroyers: Base +10 OP
    * Cruisers: Base +15 OP
    * Capital Ships: Base +20 OP
  * **Scavenged / Pirate Variants:** Apply a flat **-10% penalty** to base hull OP.
  * **Special / Neutral Variants:** Keep OP normalized to match base hull OP.
  * **Implementation:** Always modify the `.skin` JSON override block rather than changing `.variant` files.
* **Under-OP Variant Optimization:**
  When ship variants have unspent OP after mounting weapons, flux vents, and capacitors, fill the remaining capacity with standard quality-of-life hullmods (e.g., `augmentedengines`, `fluxdistributors`, `heavyarmor`, `hardenedshieldemitter`).

---

## 4. Salvage Drop Mechanics (`salvage_entity_gen_data.csv`)

* **Value Calculation:** The `drop_value` column operates on **credit values**, not physical unit quantities.
* **Equation:**
  $$\text{Physical Units Dropped} = \frac{\text{Drop Value (Credits)}}{\text{Commodity Base Price}}$$
* **Vanilla Base Prices (`commodities.csv` reference):**
  * Supplies: 100 credits
  * Fuel: 25 credits
  * Heavy Machinery: 150 credits
* **Example:** To drop exactly 2,500 Supplies, 2,000 Fuel, and 500 Heavy Machinery, set `drop_value` to:
  `supplies:250000, fuel:50000, machinery:75000`

---

## 5. Combat & Campaign AI Scripting

* **Combat AI Threat Loops (`ShipSystemAIScript`):**
  * **Never** nest general threat evaluation (missiles, enemy ships, flux level) inside conditional target filtering loops. Doing so prevents the AI from activating when the filtered list is empty.
  * **Closing Distance:** For movement systems (e.g. Plug Jets), it is valid to activate the system when `!ship.areAnyEnemiesInRange()` if `ammo > 1`, as it allows the ship to close distance dynamically while saving the final charge for emergencies.
* **Campaign Fleet Assignment AI & Intel:**
  * **Fringe Exploration:** Direct campaign fleets to explore or scavenge by searching for unpopulated systems and picking target entities utilizing the `Tags.SALVAGEABLE` API (maps to derelicts, probes, and debris fields).
  * **Dynamic Cargo:** Courier/Smuggling fleets should dynamically populate cargo lists at runtime based on the target market's industry IDs (`militarybase`, `heavyindustry`, `mining`, `commerce`).
  * **Intel Integration:** Match the description of cargo in the `BaseIntelPlugin` class to the dynamically generated cargo list. Ensure map tags are set dynamically (`Tags.INTEL_EXPLORATION` or `Tags.INTEL_HOSTILITIES`).

---

## 6. Nexerelin Integration & Faction Configuration

* **Officer Quality (`officerQuality`):**
  * Set `officerQuality` in `.faction` files appropriately. A value of `1` produces very weak officers; a value of `3` or `4` provides competent, mid-to-high tier officers with better starting skills, higher level caps, and elite skills.
  * Factions intended to be competitive must have an `officerQuality` of at least `3`.
* **Starting Ship Packages (NGC):**
  Ensure all New Game (NGC) starting categories are populated in `data/config/exerelinFactionConfig/<faction_id>.json`:
  * *Standard Starts:* `startShipsSolo`, `startShipsCombatSmall`, `startShipsCombatLarge`, `startShipsTradeSmall`, `startShipsTradeLarge`, `startShipsCarrierSmall`, `startShipsCarrierLarge`.
  * *Expanded Starts:* `startShipsExplorerSmall`, `startShipsExplorerLarge`, `startShipsSuper` (flagship/battlecarrier starts), and `startShipsGrandFleet` (full diverse fleet launch).

---

## 7. JSON, CSV, and Asset Integrity Standards

* **JSON Float Formatting:** Never use Java float suffixes like `0.5f` or `0f` in `.json` files (e.g. `engine_styles.json`). JSON requires numeric literals (`0.5`, `0`).
* **Trailing Commas:** Trailing commas in arrays/objects in `.json`, `.variant`, `.faction`, or trailing commas in `.csv` headers will cause parser failures in Starsector core.
* **Module References:** Station variants (`.variant`) must reference valid modular hull IDs in their `modules` dictionary without dangling commas.
* **Mod Info Configuration (`mod_info.json`):**
  * Always specify exact version compatibility: `"gameVersion": "0.98a-RC8"`.
  * Declare required dependencies explicitly (`lw_lazylib`, `MagicLib`, `GraphicsLib`, etc.).

---

## 8. Nexerelin Campaign Integration

To integrate custom faction mods with Nexerelin's 4X campaign and RPG mechanics, modders must interact with specific configurations and use "safe wrappers" to prevent crashes.

### `exerelinFactionConfig` JSON Mechanics
Create `<mod_folder>/data/config/exerelinFactionConfig/<faction_id>.json` to define your faction's role in the 4X map.
* **`playableFaction` (boolean):** Must be `true` for players to select the faction on game start.
* **`corvusCompatible` (boolean):** Must be `true` if this faction naturally exists in the vanilla sector map.
* **`alignments`:** Maps ideological parameters (e.g. `technocratic`, `militarist`, `hierarchical`) from `-1` to `1` which governs alliance formations and base hostility.
* **`diplomacyTraits`:** AI behavior descriptors like `"paranoid"` or `"law_and_order"` dictating how the faction handles diplomacy events.
* **`colonyExpeditionChance`:** Controls NPC expansion rate for building new colonies.

### Java Hooks (Custom Starts & Backgrounds)
Modders can inject custom campaign RPG elements by extending Nexerelin classes:
* **Custom Starts (`exerelin.campaign.customstart.CustomStart`):**
  * Registered in `data/config/exerelin/customStarts.json`.
  * Override `execute()` to configure the player's initial fleet (`NGCAddStartingShipsByFleetType`), set the faction (`PlayerFactionStore`), or execute lore text.
* **Character Backgrounds (`exerelin.campaign.backgrounds.BaseCharacterBackground`):**
  * Registered in `data/config/exerelin/character_backgrounds.csv` under the `plugin` column.

### Safe Wrapper Pattern (Preventing Missing Mod Crashes)
**CRITICAL:** Never import `exerelin.*` packages directly in your main `ModPlugin` or base scripts. Doing so causes a `NoClassDefFoundError` if the player launches Starsector without Nexerelin installed.
1. **Check Status:** `boolean isNexEnabled = Global.getSettings().getModManager().isModEnabled("nexerelin");`
2. **Wrapper Class:** Create a dedicated integration class (e.g., `MyNexIntegration.java`) where all Nexerelin imports (e.g., `SectorManager.getManager().isCorvusMode()`) live.
3. **Conditional Invocation:** Only invoke methods from your wrapper class inside an `if (isNexEnabled)` block. Java's ClassLoader will not attempt to resolve the `exerelin` dependencies unless the block is executed.

### Other Configurations (`data/config/exerelin/`)
* **`corvus_spawnpoints.csv`:** Defines exactly where the faction spawns if Corvus Mode is turned on.
* **`agent_steal_ship_config.csv`:** Whitelists/blacklists hulls that spies can steal.
* **`mining_weapons.csv`:** Configures which ship weapons contribute to asteroid mining yield.

---

## 9. Git & Mod Repository Management

* **Standard Mod `.gitignore`:**
  ```gitignore
  # Java / IDE
  *.class
  .idea/
  *.iml
  bin/
  out/
  sources.txt
  .system_generated/

  # OS specific
  .DS_Store
  Thumbs.db

  # Starsector / Backups
  *.bak
  *.tmp
  *.old
  *.sav
  starsector.log*
  ```
* **GitHub Organization:** Host mod repositories under the [`starsector-mods`](https://github.com/starsector-mods) GitHub organization as private or public repositories.

---

## 10. Weapon & Missile Sprite Rendering Engine Internals

These rules were discovered by decompiling `starfarer_obf.jar` (class `com.fs.starfarer.combat.entities.ship.A.ooOO` and `com.fs.starfarer.combat.entities.Missile`).

### Hardpoint vs Turret Sprite Pivot
The engine sets different Y-axis pivot points depending on mount type:
```java
if (slot.isHardpoint()) {
    sprite.setCenter(sprite.getWidth() / 2.0f, sprite.getHeight() / 4.0f);
} else {
    sprite.setCenter(sprite.getWidth() / 2.0f, sprite.getHeight() / 2.0f);
}
```
* **Turret / Hidden:** Pivot at exact geometric center `(width/2, height/2)`.
* **Hardpoint:** Pivot at `(width/2, height/4)` — 1/4 from the bottom of the sprite. This makes the weapon visually protrude forward 3/4 of its height from the slot coordinate, giving hardpoints their characteristic recessed-in-hull look.
* **For editor rendering (top-down Y-axis):** Convert `height/4 from bottom` to `height * 0.75 from top`.

### Missile Sprite Scaling & Center Anchor
The engine scales loaded missile sprites to the `.proj` file's `"size"` field, NOT the raw image pixel dimensions:
```java
// From Missile.createSprite():
sprite.setCenter(center.x, center.y);   // Raw .proj "center" values
sprite.setSize(size.x, size.y);          // Scales to .proj "size" values
sprite.renderAtCenter(offsetX, offsetY); // Renders at barrel offset point
```
* **`"size": [w, h]`** — The engine renders the missile sprite at these dimensions (scaling the PNG texture).
* **`"center": [x, y]`** — The pivot/anchor point, relative to the `"size"` dimensions (bottom-left origin). NOT relative to the raw image pixel dimensions.
* **Example:** Harpoon missile has `"size": [10, 22]`, `"center": [5, 12]`, but the actual PNG is 12×26 pixels. The engine scales to 10×22 and pivots at `(5, 12)` within that scaled space.

### Loaded Missile Rendering in Launcher Barrels
When a weapon has `RENDER_LOADED_MISSILES` or `RENDER_LOADED_MISSILES_UNLESS_HIDDEN` in its `renderHints`:
1. One missile sprite is created per barrel (offset point count).
2. Each missile is positioned at the barrel's fire offset point (transformed by weapon facing and mount position).
3. Missile facing = `weaponFacing + angleOffset - 90°` (the -90° converts from sprite-up to combat-facing).
4. Reload fade: missiles fade in during the last portion of the refire delay cycle.

