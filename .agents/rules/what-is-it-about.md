---
trigger: always_on
---

```yaml
project: "Starsector Ship Editor"
description: "Rules & Skills"

technology_stack:
  language: "Java 17"
  gui_framework: "Swing with FlatLaf (for modern look, custom components & dark mode) and LWJGL 3 / lwjgl3-awt AWTGLCanvas & JOML (for OpenGL Viewer and matrix/AffineTransform capabilities)."
  icons: "Ikonli (FontAwesome5, FluentUI, Boxicons)."
  build_tool: "Maven."
  data_binding: "Jackson (for both JSON and CSV files)."
  boilerplate_reduction: "Lombok."
  logging: "Log4j2."

architecture_and_coding_conventions:
  event_bus:
    package: "shipeditor.communication"
    rules:
      - "The application relies heavily on an Event Bus system for loose coupling between components."
      - "Do not pass hard references between distinct UI components or controllers where possible. Instead, fire events and have listeners subscribe via `EventBus.subscribe(lifecycleParent, listener)` or the typed overload `EventBus.subscribe(lifecycleParent, Class<T>, Consumer<T>)`."
      - "Always implement proper listener cleanup (`EventBus.unsubscribeByParent(this)` or `EventBus.unsubscribe(listener)`) to avoid memory leaks on layer removal or object destruction."
  layer_system:
    package: "shipeditor.components"
    rules:
      - "Support for simultaneous viewing and editing of multiple ship entities using Layers."
      - "UI features interacting with the data must account for the currently active layer."
      - "Ensure custom graphics operations utilize AffineTransform correctly to preserve interactions during layer rotation, zooming, or scaling."
  data_model_and_parsing:
    package: "shipeditor.representation & parsing"
    rules:
      - "All JSON/CSV data wrappers are encapsulated in representations."
      - "Handle unconventional JSON structures specific to Starsector gracefully, preserving unrecognized custom mod JSON properties via `@JsonAnyGetter`/`@JsonAnySetter` and `SaveActionUtilities.mergeUnrecognizedProperties()`."
      - "Do not hardcode absolute paths; use the Data walker which supports symlinks and game packages."
      - "CSV Editing & Serialization: When modifying Starsector CSV files via Jackson, never overwrite files using standard serializations as this can corrupt Starsector's formatting (like unnecessarily quoting comments). Always cache the raw parsed data maps alongside the original `CsvSchema` in `GameDataRepository` during loading. Upon saving, apply a custom Jackson module with `JsonSerializer<Map<?, ?>>` that bypasses default Jackson behaviors to reconstruct the exact raw structure, rebuilding the schema with `setUseHeader(true)`."
  undo_redo:
    package: "shipeditor.undo"
    rules:
      - "User actions that modify the project state must be encapsulated as edits to plug into the global Undo/Redo system."
  threading_and_ui:
    rules:
      - "Since the app is built on Swing, all UI modifications must occur on the Event Dispatch Thread (EDT). Use SwingUtilities.invokeLater() when needed."
      - "Graphic repaints are heavily optimized (using a timed repaint technique). Do not indiscriminately call .repaint() on large panels unnecessarily to prevent performance drops."
      - "Swing Silent NPEs: Uncaught exceptions (like NullPointerExceptions) that occur within Swing painting or rendering methods (e.g. `DefaultTreeCellRenderer` calling `toString()`) will be caught by the EDT, causing Swing to silently abort rendering that specific component. This results in completely blank/empty UI panels without crashing the application. Always register a global `Thread.setDefaultUncaughtExceptionHandler` to log these effectively."

common_operations:
  adding_new_data_types:
    - "Update Jackson parsing models in representation, add corresponding Event types, and update the UI trees/tables."
  graphic_editing:
    - "For anything drawn on the map, use the custom point painting and layer hierarchy tools, respecting PaintOrderController."

code_cleanliness_and_refactoring:
  rules:
    - "Always remove unused imports, especially when refactoring complex Swing components, layout managers, or EventBus listeners."
    - "When removing redundant UI elements or panels, ensure that all helper types, enums (e.g., LeftsideTabType), and obsolete listeners are completely purged and deleted from the codebase. Do not leave dead code blocks."
  lombok_and_inheritance:
    - "When extending classes that use Lombok `@Getter` annotations (e.g., `LayerPainter`), be careful of field shadowing. Always use polymorphic getter methods (like `getSprite()`) in the base class rather than direct field access (`this.sprite`) to ensure subclasses that override the getter function correctly instead of encountering `NullPointerException`s on uninitialized base class fields."

maven_and_environment_quirks:
  rules:
    - "Lombok Cascading Errors: If a Maven build crashes with a massive amount of 'cannot find symbol' errors on Lombok-generated methods (e.g. `getSprite()`, `getAngle()`) in completely unrelated files, it is almost always a cascading failure. A single genuine syntax error in any file (like a duplicated method or missing type) will cause `javac` to abort annotation processing for the rest of the compilation round. Always filter out the Lombok-generated classes or check the very first `[ERROR]` in the build log to find the true culprit instead of assuming Lombok is broken."

linux_x11_rendering:
  rules:
    - "If the user is running the application from an IDE terminal (e.g. VS Code Server) where the `$DISPLAY` environment variable is not automatically set, they must fall back to `export DISPLAY=:0` in `~/.bashrc` instead of `:99`. Setting it to `:99` will cause `AWTGLCanvas` to render successfully on a hidden Xvfb virtual display instead of the physical monitor, resulting in the user seeing 'nothing' on their physical screen. However, as an edge case, if the user explicitly requests to open the application inside the IDE's built-in X11 previewer (e.g. Antigravity IDE preview tab), `DISPLAY=:99` must be used instead."
    - "When restoring window bounds in `PrimaryWindow.java` via `restoreSize()`, the window manager on X11/XFCE may fail to render the application entirely (invisible window) if the saved bounds perfectly match the exact maximum physical resolution. To prevent this, apply a 50-pixel inset calculation (e.g., `Math.min(saved.getWidth(), screenSize.width - x - 50)`) when loading the `SimpleRectangle` bounds."
    - "Due to the mixing of AWT heavyweights (`AWTGLCanvas`) and Swing lightweights (`JPanel`), `sun.awt.noerasebackground=true` and `sun.java2d.noddraw=true` are strictly required in the VM options to prevent rendering artifacts or display failures on Linux XWayland bridges."
```