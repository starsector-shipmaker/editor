---
name: starsector-tech-stack
description: Information about the required technology stack, environment JVM flags, and build plugins.
---

# Starsector Ship Editor — Technology Stack

## Skill Directory Structure

This skill is organized as follows:
- **`SKILL.md`**: Main instructions (this file).
- **`resources/`**: Configurations and templates.
  - [maven-toolchains.xml](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-tech-stack/resources/maven-toolchains.xml): Configuration showing how to bind Maven to Java 17/21 compiler paths.
- **`examples/`**: Code references.
  - [pom-compilation-plugins.xml](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-tech-stack/examples/pom-compilation-plugins.xml): Reference Maven plugin config for Java compiler and Lombok annotation processor.
- **`scripts/`**: Tooling.
  - [check_java_home.sh](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/starsector-tech-stack/scripts/check_java_home.sh): Script to verify environment compatibility.

## Core Language & Runtime

| Component | Version | Notes |
|---|---|---|
| **Java** | Source target 17 (`maven.compiler.release=17`) | Requires JDK 17–24 to compile (enforced via `maven-enforcer-plugin` `[17,25)`). Lombok 1.18.46 crashes on JDK 25+ with `TypeTag :: UNKNOWN ExceptionInInitializerError`. |
| **Maven** | 3.x | Build tool. **Fedora quirk**: Fedora's `mvn` wrapper ignores `update-alternatives` and reads `/etc/java/maven.conf`. Set `JAVA_HOME=/usr/lib/jvm/java-17-temurin-jdk` in `~/.mavenrc` to force Java 17. |

### JVM Flags (Production & Development)
```
-Xmx4g -XX:+UseG1GC -XX:+UseStringDeduplication
-XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20
-Dsun.java2d.opengl=false -Dsun.java2d.d3d=false
-Dsun.java2d.noddraw=true -Dsun.awt.noerasebackground=true
-Dorg.lwjgl.opengl.contextAPI=native
```
Configured via `exec-maven-plugin` (`mvn exec:exec`) and enforced at startup by `Main.java`.

### JVM Self-Relaunch & Single-Instance Lock (Quirks)
- [Main.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/Main.java) contains `checkAndRelaunch()` which detects if the JVM was launched with `< 3900 MB` max heap (~4 GB) or without required AWT/Java2D properties (`sun.awt.noerasebackground=true`, `sun.java2d.noddraw=true`, or Linux `DISPLAY`), and relaunches itself with `-Xmx4g` and `-Dshipeditor.relaunched=true` to prevent infinite relaunch loops.
- `Main.acquireApplicationLock()` acquires an OS-level `FileLock` on `.ship_editor.lock` (bypassable via `-Dshipeditor.force=true`) to enforce single-instance execution and prevent concurrent SQLite database corruption.

### Java2D Suppression
```java
System.setProperty("sun.java2d.opengl", "false");
System.setProperty("sun.java2d.d3d", "false");
System.setProperty("sun.java2d.noddraw", "true");
System.setProperty("sun.awt.noerasebackground", "true");
```
Java2D's OpenGL and Direct3D pipelines are explicitly disabled because they conflict with the LWJGL GL context sharing through `AWTGLCanvas`. Without these flags, some drivers produce black canvases, flickering, or segfaults.

---

## GUI Framework

| Component | Version | Purpose |
|---|---|---|
| **Swing/AWT** | JDK built-in | Core UI framework |
| **FlatLaf** | 3.7.2 | Modern look-and-feel with dark mode, scalable DPI, and IntelliJ themes |
| **Ikonli** | 12.4.0 | Vector icon packs: FontAwesome5, FluentUI, Boxicons |

### FlatLaf Configuration (Quirks)
- `JPopupMenu.setDefaultLightWeightPopupEnabled(false)` — Required because lightweight popups render behind the `AWTGLCanvas` heavyweight component.
- `ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false)` — Same issue for tooltips.
- `UIManager.put("FileChooser.readOnly", true)` — Prevents users from creating/deleting folders in the file chooser.
- **Early JToolTip initialization**: `new JToolTip().updateUI()` is called during startup to force the classloader to eagerly load ToolTip UI classes. Without this, the first tooltip display on EDT can trigger lazy classloading that causes a visible UI freeze.
- **Shell Folder Filtering**: A custom function removes the `::` pseudo-path (`SHELL_FOLDER_0_X_12`) from the file chooser shortcuts on Windows.

---

## Graphics & Rendering Pipeline

| Component | Version | Purpose |
|---|---|---|
| **LWJGL 3** (BOM) | 3.4.2 | Core library, OpenGL bindings, GLFW, JAWT |
| **lwjgl3-awt** | 0.2.4 | `AWTGLCanvas` bridge between Swing and OpenGL |
| **JOML** | 1.10.9 | `Matrix4f`, `Vector2f`, `Vector3f`, `Vector4f` math |

### Native Classifiers
Both Linux and Windows natives are included in the POM:
- `natives-linux` for `lwjgl`, `lwjgl-opengl`, `lwjgl-glfw`
- `natives-windows` for the same

macOS is not supported (no native classifiers declared).

---

## Data Handling & Persistence

| Component | Version | Purpose |
|---|---|---|
| **Jackson Core** | 2.18.9 | JSON parsing with extensive relaxed-mode features |
| **Jackson Databind** | 2.18.9 | Object mapping with custom coercion rules and `@JsonAnyGetter`/`@JsonAnySetter` |
| **Jackson CSV** | 2.18.9 | CSV parsing/writing with custom serializers |
| **Jackson Annotations** | 2.18.9 | `@JsonProperty`, `@JsonIgnoreProperties`, etc. |
| **SQLite JDBC** | 3.53.2.1 | Embedded database for file indexing |
| **HikariCP** | 5.1.0 | High-performance JDBC connection pooling (`SQLite-Pool`) |

---

## Utilities

| Component | Version | Purpose |
|---|---|---|
| **Lombok** | 1.18.46 | `@Getter`, `@Setter`, `@Builder`, `@Log4j2`, `@ToString` |
| **Log4j2** | 2.25.5 | `log4j-api`, `log4j-core`, `log4j-slf4j-impl` |
| **SpotBugs Annotations** | 4.10.3 | `@SuppressFBWarnings`, `@NonNull`, `@Nullable` |
| **Apache Commons Collections 4** | 4.5.0 | Advanced collection utilities |
| **JHLabs Filters** | 2.0.235-1 | Image manipulation filters |

### Lombok Inheritance Trap (Quirk)
When extending classes that use Lombok `@Getter` (e.g., `LayerPainter`), be careful of field shadowing. Always use polymorphic getter methods (`getSprite()`) in the base class rather than direct field access (`this.sprite`). Subclasses that override the getter will function correctly; direct field access bypasses the override and hits uninitialized base-class fields, causing `NullPointerException`s.

---

## Build Plugins

| Plugin | Version | Purpose |
|---|---|---|
| `maven-enforcer-plugin` | 3.6.3 | Enforces JDK version range `[17,25)` |
| `maven-checkstyle-plugin` | 3.5.0 | Naming convention enforcement (`checkstyle.xml`) in `validate` phase |
| `maven-compiler-plugin` | 3.11.0 | Java 17 target (`release=17`), Lombok + Log4j2 annotation processors |
| `maven-surefire-plugin` | 3.5.6 | Test execution (JUnit Jupiter 5.14.4 + jqwik 1.10.1) |
| `pitest-maven` | 1.25.9 | Mutation testing (`pitest-junit5-plugin` 1.2.3) |
| `spotbugs-maven-plugin` | 4.10.3.0 | Static analysis: Max effort, Low threshold, `spotbugs-exclude.xml` |
| `maven-jar-plugin` | 3.4.2 | JAR packaging |
| `maven-resources-plugin` | 3.3.1 | Copies help documentation from `modules/starsector-shipmaker-docs` |
| `exec-maven-plugin` | 3.1.0 | Launch via `mvn exec:exec` with `-Xmx4g` and OpenGL/Java2D JVM flags |
| `maven-shade-plugin` | 3.6.2 | Uber-JAR generation (`${project.basedir}/ship_editor.jar`) |
| `maven-javadoc-plugin` | 3.10.0 | Javadoc generation |

### Shade Plugin Configuration (Quirks)
- **`Log4j2PluginCacheFileTransformer`**: Merges Log4j2 plugin caches from multiple JARs. Without this, Log4j2 fails to discover its appenders at runtime.
- **`ServicesResourceTransformer`**: Merges `META-INF/services` files. Required for JDBC driver auto-discovery and Ikonli icon pack loading.
- **Exclusion Filters**: Strips `module-info.class`, signature files (`*.SF`, `*.DSA`, `*.RSA`), and duplicate metadata to prevent `SecurityException` and classloading conflicts.
- **Output**: The shaded JAR is written to `${project.basedir}/ship_editor.jar` (project root), not `target/`.

---

## Classpath Execution Model (Unnamed Module)

As of Release 0.0.1e, `module-info.java` was removed in favor of standard classpath (unnamed module) execution.
- This avoids JPMS split-package conflicts across LWJGL natives, Ikonli icon packs, and AWT/Swing internals while allowing Jackson databind reflection without maintaining fragile `opens` declarations.
- `maven-shade-plugin` explicitly strips `module-info.class` from shaded dependencies so the uber-JAR (`ship_editor.jar`) runs cleanly on the unnamed module.
