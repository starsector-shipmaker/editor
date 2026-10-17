---
name: starsector-testing
description: Guidelines for JUnit, jqwik property-based testing, SpotBugs static analysis, and verification rules.
---

# Starsector Ship Editor — Testing, Verification & Validation

## Skill Directory Structure

This skill is organized as follows:
- **`SKILL.md`**: Main instructions (this file).
- **`resources/`**: Configurations and templates.
  - [spotbugs-exclude-template.xml](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-testing/resources/spotbugs-exclude-template.xml): Template for SpotBugs bug suppressions.
- **`examples/`**: Code references.
  - [SampleJqwikTest.java.txt](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-testing/examples/SampleJqwikTest.java.txt): Reference implementation of jqwik property-based tests.
- **`scripts/`**: Automation.
  - [run_checks.sh](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-testing/scripts/run_checks.sh): Helper script to build, test, and perform static analysis.

## 1. Test Frameworks

| Framework | Version | Scope | Purpose |
|---|---|---|---|
| **JUnit Jupiter** | 5.14.4 | `test` | Standard unit/integration test runner and assertions |
| **jqwik** | 1.10.1 | `test` | Property-based testing with random input generation |
| **PIT (pitest-maven)** | 1.25.9 | `test` | Mutation testing (`pitest-junit5-plugin` 1.2.3) |

Executed via `maven-surefire-plugin` (3.5.6).

---

## 2. Existing Test Suite

The test suite comprises **39 automated JUnit 5 and jqwik test classes** across 16 packages:

### Data Parsing, Loading & Serialization (`shipeditor.parsing.*`)
- [`JsonProcessorPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/parsing/JsonProcessorPropertiesTest.java) — Property-based tests for `JsonProcessor.straightenMalformed()` (comments, escaped quotes, semicolons, unquoted tokens).
- [`SpecFileSerializationTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/parsing/SpecFileSerializationTest.java) — 58 unit tests covering `.ship`, `.variant`, `.skin`, `.wpn`, and `.proj` serialization round-trips and `@JsonAnyGetter`/`@JsonAnySetter` custom property preservation.
- [`CustomSerializersPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/parsing/serialize/CustomSerializersPropertiesTest.java), [`FileUtilitiesPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/parsing/FileUtilitiesPropertiesTest.java), [`CsvLoaderPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/parsing/loading/CsvLoaderPropertiesTest.java), [`DataLoadingPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/parsing/loading/DataLoadingPropertiesTest.java), [`LibModFilterTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/parsing/loading/LibModFilterTest.java).
- [`GameDataParsingIntegrationTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/parsing/GameDataParsingIntegrationTest.java) & [`DatabaseLoadingIntegrationTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/parsing/DatabaseLoadingIntegrationTest.java) — End-to-end integration tests for game data loading and indexing.

### Persistence & SQLite Database (`shipeditor.persistence.*`)
- [`DatabaseQueryServiceTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/persistence/database/DatabaseQueryServiceTest.java), [`DataReferenceQueriesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/persistence/database/DataReferenceQueriesTest.java), [`JsonDatabaseIntegrationTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/persistence/database/JsonDatabaseIntegrationTest.java), [`GameFolderDetectionTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/persistence/GameFolderDetectionTest.java).

### Communication, Representation & Undo/Redo (`shipeditor.communication`, `shipeditor.representation`, `shipeditor.undo`)
- [`EventBusTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/communication/EventBusTest.java) — Lifecycle subscriptions, typed `EventBus.subscribe(parent, Class<T>, Consumer<T>)`, `unsubscribeByParent`, nested event dispatch.
- [`GameDataRepositoryPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/representation/GameDataRepositoryPropertiesTest.java) & [`UndoOverseerPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/undo/UndoOverseerPropertiesTest.java).

### UI Components & Layer Transforms (`shipeditor.components.*`, `shipeditor.utility.components.*`)
- [`LayerPainterTransformTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/components/viewer/layers/LayerPainterTransformTest.java), [`DataTreePanelTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/components/datafiles/trees/DataTreePanelTest.java), [`ModSelectionDialogTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/components/dialogs/ModSelectionDialogTest.java), [`ModSelectionDialogPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/components/dialogs/ModSelectionDialogPropertiesTest.java), [`DialogHelperTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/components/dialog/DialogHelperTest.java), [`SpinnersPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/components/widgets/SpinnersPropertiesTest.java).

### Graphics & Geometry (`shipeditor.utility.graphics`)
- [`PolygonSimplifierTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/PolygonSimplifierTest.java), [`CollisionHullGeneratorTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/CollisionHullGeneratorTest.java), [`SpriteOutlineTracerTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/SpriteOutlineTracerTest.java), [`SocketContourDetectorTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/SocketContourDetectorTest.java), [`RealSpriteRegressionTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/RealSpriteRegressionTest.java), [`ColorSpaceTransformTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/ColorSpaceTransformTest.java), [`ColorUtilitiesPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/ColorUtilitiesPropertiesTest.java), [`DeltaEMathTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/DeltaEMathTest.java), [`GraphicsLibMapGeneratorTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/GraphicsLibMapGeneratorTest.java), [`ImageProcessingTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/ImageProcessingTest.java), [`PaletteAnalyzerTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/PaletteAnalyzerTest.java), [`ShapeUtilitiesPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/ShapeUtilitiesPropertiesTest.java), [`SpriterMaskExporterTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/SpriterMaskExporterTest.java), [`StencilExtractorTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/graphics/StencilExtractorTest.java).

### Core Mathematical & Localization Utilities (`shipeditor.utility`, `shipeditor.utility.text`)
- [`UtilityPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/UtilityPropertiesTest.java) — Property-based tests for `Utility` parsing, angle clamping, flipping, rounding, and coordinate angle transforms.
- [`CoordinatesFormatterPropertiesTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/text/CoordinatesFormatterPropertiesTest.java) — Property-based tests for coordinate rounding and overflow guards.
- [`StringManagerTest`](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/test/java/shipeditor/utility/text/StringManagerTest.java) — Verifies localization resource JSON bundles and key resolution.

---

## 3. What Requires Live GPU Verification

### Live OpenGL Canvas (`AWTGLCanvas`) Rendering
While Swing dialogs, trees, spinners, and `LayerPainter` coordinate transforms are covered by automated unit and property-based tests, `PrimaryViewer`, `PaintOrderController`, and GL shader execution require a live OpenGL 3.3 context and display server for visual verification.

---

## 4. Static Analysis: Checkstyle & SpotBugs

### Checkstyle
- **Plugin**: `maven-checkstyle-plugin` 3.5.0 (`checkstyle.xml`)
- Bound to the `validate` phase; enforces `PascalCase` types, `camelCase` methods/fields/parameters/local variables, and `UPPER_SNAKE_CASE` `static final` constants (except `log`).

### SpotBugs
- **Plugin**: `spotbugs-maven-plugin` 4.10.3.0 (`spotbugs-annotations` 4.10.3, filter `spotbugs-exclude.xml`)
- **Configuration**: `effort=Max`, `threshold=Low`, `xmlOutput=true`

It catches:
- Null pointer dereferences and redundant null checks
- Unconfirmed casts (`BC_UNCONFIRMED_CAST`)
- Unclosed resources (streams, connections)
- Concurrency issues (race conditions, unsynchronized access)
- Mutable object exposure (`@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})` is used where mutable internal state is intentionally exposed for rendering performance)

---

## 5. Build Pipeline as Validation Gate

- `mvn package -DskipTests` — Runs `enforcer:enforce`, `checkstyle:check`, `compile`, `jar`, and `shade` (creating `ship_editor.jar`).
- `mvn test -DskipTests=false` — Executes the full JUnit Jupiter and jqwik test suite (since `<skipTests>true</skipTests>` is set by default in `pom.xml` for fast packaging).
- `mvn spotbugs:check` — Runs SpotBugs static analysis verification.

### Shade Plugin Validation (Quirk)
If the `Log4j2PluginCacheFileTransformer` is missing or misconfigured, the shaded JAR will start but Log4j2 will silently fall back to `NullAppender`, producing zero log output. This is a **silent failure** — the application appears to work but cannot be debugged. The transformer merges `Log4j2Plugins.dat` cache files from all dependencies.

Similarly, if `ServicesResourceTransformer` is missing, JDBC driver auto-discovery fails and `DatabaseManager.getConnection()` throws `ClassNotFoundException` for `org.sqlite.JDBC` (which is why the explicit `Class.forName()` fallback exists).

---

## 6. Runtime Verification

### Manual Launch Test
The editor is launched via:
```bash
mvn exec:exec
```
or directly:
```bash
java -Xmx4g -XX:+UseG1GC -XX:+UseStringDeduplication \
     -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 \
     -Dsun.java2d.opengl=false -Dsun.java2d.d3d=false \
     -Dsun.java2d.noddraw=true -Dsun.awt.noerasebackground=true \
     -jar ./ship_editor.jar
```

Verification checklist:
- [ ] Window opens without exceptions
- [ ] OpenGL context initializes (checkerboard background visible)
- [ ] Sprite loading and rendering works
- [ ] Coordinate display updates on mouse movement
- [ ] Undo/redo functions correctly
- [ ] Database indexing completes without errors
- [ ] Uber-JAR launches identically to classpath launch

### GPU-Specific Quirk
On Linux with multiple GPUs (e.g., integrated + discrete), the editor must use the correct GPU. The `DRI_PRIME=1` environment variable or explicit `/dev/dri/card0` mapping may be needed to target the discrete GPU for adequate OpenGL 3.3 support.

---

## 7. `.jqwik-database`

A `.jqwik-database` file exists in the project root. This is jqwik's internal database for tracking which random seeds have been tested and which have found failures. It enables **shrinking** (finding minimal failing inputs) and **replay** (re-running previously failing seeds). The file is currently 4 bytes, indicating minimal or no persisted failure history.

---

## 8. Compiler and IDE Warning Resolutions

When writing and refactoring Java code, adhere to these conventions to keep the build completely warning-free:

### Null pointer warnings on conditional check variables (false-positives)
Avoid saving state to a boolean variable before performing null checks. Static analyzers (like Eclipse/ECJ) fail to link the boolean state to the null state of the original object.
- **Bad**:
  ```java
  boolean present = selected != null && selected.getPainter() != null;
  if (!present) return;
  selected.getPainter().doSomething(); // Compiler warning: potential null pointer access
  ```
- **Good**:
  ```java
  if (selected == null || selected.getPainter() == null) return;
  selected.getPainter().doSomething(); // Safe
  ```

### Autoboxing/Unboxing type safety in generic Consumers
When passing method references to generic containers/spinners that expect wrapper types (e.g. `Consumer<Integer>` or `Consumer<Boolean>`), do not use method references targeting primitives (e.g. `void setFluxVents(int)`). This triggers "Null type safety" warnings under strict ECJ compilation. Use lambdas with explicit null-safe defaults instead.
- **Bad**:
  ```java
  ventsSpinner.enableSpinner(layer, variant.getFluxVents(), maxVents, variant::setFluxVents);
  ```
- **Good**:
  ```java
  ventsSpinner.enableSpinner(layer, variant.getFluxVents(), maxVents, val -> variant.setFluxVents(val != null ? val : 0));
  ```

### Redundant Suppressions
Avoid leaving `@SuppressWarnings("unused")` or similar compiler overrides on classes/methods unless there is a specific, active reason to silence a compiler error. Redundant suppressions cause ECJ warnings about unused annotations.
