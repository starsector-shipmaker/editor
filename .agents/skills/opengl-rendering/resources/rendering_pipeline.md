# Starsector Ship Editor — Rendering Pipeline

## 1. Context & Container: `PrimaryViewer`

[PrimaryViewer.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/components/viewer/PrimaryViewer.java) is the conceptual root of the entire application. It is a `JPanel` (with `BorderLayout`) that hosts an `AWTGLCanvas` from `lwjgl3-awt`, bridging modern OpenGL with the Swing EDT.

### Initialization Sequence
```java
GLData data = new GLData();
data.majorVersion = 3;
data.minorVersion = 3;
data.profile = GLData.Profile.CORE;
data.samples = 4; // 4x MSAA
```
- **MSAA**: Hardcoded to 4x. There is no user-configurable anti-aliasing setting.
- **`initGL()`**: Called once by the canvas. Creates GL capabilities, enables alpha blending (`GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA`), and instantiates the two singleton renderers (`SpriteRenderer`, `ShapeRenderer`).

### GL Task Queue (Quirk)
`PrimaryViewer` maintains a `ConcurrentLinkedQueue<Runnable> glRunnables`. Any code that needs to run on the GL thread (e.g. texture uploads from a background indexing thread) must be enqueued via `queueGLTask()`. These runnables are drained **after** the paint pass but **before** `swapBuffers()`. This is important: the GL context is only current during `paintGL()`, so texture loads at any other time will crash.

### Viewport Centering Timing (Quirk)
`centerViewpoint()` is guarded by `this.getWidth() > 0 && this.getHeight() > 0`. This is necessary because Swing panels report `0×0` dimensions until they've been laid out by the window manager. Without this guard, the first load would translate the viewport to `(0, 0)` and the sprite would be invisible in the corner.

### `Toolkit.sync()` Call
After `swapBuffers()`, the render loop calls `java.awt.Toolkit.getDefaultToolkit().sync()`. This is a workaround for X11/Linux compositors that buffer AWT repaints. Without it, frames may appear to "stutter" on Wayland/X11 with compositing enabled.

### Minimum Panel Size
Hardcoded to `240×120` pixels. This prevents layout managers from collapsing the viewer to zero when other panels are resized.

---

## 2. Rendering Orchestration: `PaintOrderController`

[PaintOrderController.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/components/viewer/PaintOrderController.java) implements `OpenGLPainter` and defines the strict drawing order via the painter's algorithm.

### Draw Order (Back to Front)
1. **Checkerboard Background** — Drawn with `ShapeRenderer` using an identity view matrix (screen-space) and a GPU-computed checkerboard pattern via fragment shader uniform `useCheckerboard`.
2. **Axes** — World-space X/Y grid lines via `GuidesPainters.getAxesPaint()`.
3. **All Layers** — Iterates `LayerManager.getLayers()`. For each `ViewerLayer`, calls `LayerPainter.paint()` then all of its `AbstractPointPainter` children.
4. **Layer-Dependent Guides** — Borders, center markers, cursor guides (only if no drag is in progress).
5. **Misc Points** — `MarkPointsPainter` for floating overlay annotations.
6. **Dragged Objects** — Only rendered when `ViewerDropReceiver.isDragToViewerInProgress() && parent.isCursorInViewer()`. Draws ghostly 50%-opacity previews of items being dragged onto the canvas.
7. **Hotkey Help** — On-demand texture-based shortcut overlay.

### Repaint Timer (Quirk: Not `requestAnimationFrame`)
```java
Timer repaintTimer = new Timer(16, e -> {
    if (repaintQueued) { repaintViewer(); }
});
```
This is a Swing `javax.swing.Timer` running at ~60 FPS (~16ms interval). It does **not** repaint every tick. Instead, it only repaints if `repaintQueued == true`. This flag is set by any input event, EventBus notification, or explicit call to `setRepaintQueued()`. When there is no user interaction, the GPU is completely idle.

### SpotBugs Suppression
The class is annotated `@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})`. This is a deliberate choice — the mutable internal state of painters and the layer manager are intentionally exposed for performance. Making defensive copies of these objects every frame would be prohibitively expensive.

---

## 3. Sprite Rendering: `SpriteRenderer`

[SpriteRenderer.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/utility/graphics/opengl/SpriteRenderer.java) is the textured quad renderer for ships, weapons, projectiles, and preview ghosts.

### Shader (Inline GLSL 330 Core)
Shaders are defined as inline Java strings (not loaded from resource files).

**Vertex Shader:**
```glsl
layout (location = 0) in vec4 vertex; // <vec2 position, vec2 texCoords>
uniform mat4 model, view, projection;
void main() {
    TexCoords = vertex.zw;
    gl_Position = projection * view * model * vec4(vertex.xy, 0.0, 1.0);
}
```

**Fragment Shader:**
```glsl
uniform sampler2D image;
uniform vec4 spriteColor;
void main() {
    color = spriteColor * texture(image, TexCoords);
}
```
The `spriteColor` uniform acts as both a tint and an opacity multiplier. A `vec4(1,1,1,0.5)` gives a ghostly semi-transparent overlay; `vec4(1,1,1,1)` is full opacity.

### Static Quad Geometry
A single VAO/VBO is allocated once with a unit quad (`0,0` to `1,1`) and uploaded as `GL_STATIC_DRAW`. The quad is reused for every sprite — only the model matrix changes per draw call.

```java
float[] vertices = {
    // Pos      // Tex
    0.0f, 1.0f, 0.0f, 1.0f,   // Triangle 1
    1.0f, 0.0f, 1.0f, 0.0f,
    0.0f, 0.0f, 0.0f, 0.0f,
    0.0f, 1.0f, 0.0f, 1.0f,   // Triangle 2
    1.0f, 1.0f, 1.0f, 1.0f,
    1.0f, 0.0f, 1.0f, 0.0f
};
```

### Two `drawSprite` Overloads
1. **Center-pivot** (legacy): Computes `rotationAnchor` as center of sprite, then delegates.
2. **Custom anchor**: Full control over rotation origin. Used by engine flame sprites, weapon mounts, and module overlays.

### Model Matrix Construction (Quirk: Multiplication Order)
```java
Matrix4f model = new Matrix4f();
model.translate(rotationAnchor.x, rotationAnchor.y, 0.0f);
model.rotate(rotationRadians, new Vector3f(0.0f, 0.0f, 1.0f));
model.translate(position.x - rotationAnchor.x, position.y - rotationAnchor.y, 0.0f);
model.scale(size.x, size.y, 1.0f);
```
JOML's `Matrix4f` methods post-multiply. So the effective transformation reads right-to-left: **Scale → Translate-to-offset → Rotate → Translate-to-anchor**. This is the classic "rotate around arbitrary point" pattern.

### No Batching (Quirk)
Each `drawSprite` call issues its own `glDrawArrays`. There is no instanced rendering or sprite batching. For the typical workload (dozens of sprites, not thousands), this is acceptable. But if hundreds of fitted weapons are visible, draw calls can spike.

---

## 4. Shape Rendering: `ShapeRenderer`

[ShapeRenderer.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/utility/graphics/opengl/ShapeRenderer.java) handles all untextured geometry: grid lines, bounding boxes, shield circles, weapon arcs, and the background checkerboard.

### Checkerboard via Fragment Shader (Quirk)
Instead of a texture or CPU-computed pattern, the checkerboard is computed entirely in the GPU fragment shader using `gl_FragCoord`:
```glsl
if (useCheckerboard == 1) {
    float checkSize = 20.0;   // 20px squares, hardcoded
    float total = floor(gl_FragCoord.x / checkSize) + floor(gl_FragCoord.y / checkSize);
    if (mod(total, 2.0) == 0.0)
        color = vec4(1.0, 1.0, 1.0, 0.5);  // White at 50% alpha
    else
        color = vec4(0.8, 0.8, 0.8, 0.5);  // Light grey at 50% alpha
}
```
This means the checkerboard is always screen-aligned and never zooms or pans. The `useCheckerboard` uniform is toggled on/off by `PaintOrderController`.

### Begin/End State Machine (Quirk: Self-Healing)
```java
public void begin(Matrix4f projection, Matrix4f view) {
    if (isDrawing) {
        log.warn("ShapeRenderer was already drawing! Forcing end of previous batch to recover.");
        try { end(); } catch (IllegalStateException e) { isDrawing = false; }
    }
    // ...
}
```
If `begin()` is called while already drawing (a bug in caller code), the renderer logs a warning and **auto-recovers** by forcing `end()`. This prevents the application from crashing due to nested begin/end blocks from improperly structured painter code.

### Pre-computed Unit Circle (Quirk)
```java
private static final int CIRCLE_SEGMENTS = 64;
private static final float[] UNIT_CIRCLE_COS = new float[CIRCLE_SEGMENTS];
private static final float[] UNIT_CIRCLE_SIN = new float[CIRCLE_SEGMENTS];
static {
    for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
        double theta = 2.0 * Math.PI * i / CIRCLE_SEGMENTS;
        UNIT_CIRCLE_COS[i] = (float) Math.cos(theta);
        UNIT_CIRCLE_SIN[i] = (float) Math.sin(theta);
    }
}
```
Trigonometric values are computed once in a static initializer and reused for every circle draw. The circle vertex buffer is a pre-allocated off-heap `FloatBuffer` via `MemoryUtil.memAllocFloat(CIRCLE_SEGMENTS * 3 * 2)`, avoiding GC pressure from per-frame allocations.

### Dynamic Buffer Reallocation
The VBO is initialized with `1024 * Float.BYTES` of `GL_DYNAMIC_DRAW` storage. For small primitives (lines, rects), `glBufferSubData` updates the VBO in-place. For filled circles (which need `64 * 3 * 2 = 384` floats), the code checks if the required capacity exceeds 1024 floats and falls back to `glBufferData` (full reallocation) if needed.

---

## 5. Coordinate Systems & Transform Math

### AffineTransform ↔ Matrix4f Bridge

[ViewerTransform.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/utility/graphics/opengl/ViewerTransform.java) maintains the camera state using Java2D's `AffineTransform` (for compatibility with the legacy point-picking and mouse interaction code), and converts it to a JOML `Matrix4f` for the OpenGL pipeline.

### Critical Quirk: `preConcatenate` Not `concatenate`
All transform operations (`translate`, `zoom`, `rotate`) use `worldToScreen.preConcatenate(tx)` — meaning the new transform is applied in **screen space** before the existing world-to-screen transform. This matches the intuitive behavior where panning moves the view by screen pixels, not world units.

### Column-Major Mapping (Quirk: JOML Constructor Order)
```java
double[] matrix = new double[6]; // [m00, m10, m01, m11, m02, m12]
at.getMatrix(matrix);
return new Matrix4f(
    m00, m10, 0f, 0f,   // Col 0
    m01, m11, 0f, 0f,   // Col 1
    0f,  0f,  1f, 0f,   // Col 2
    m02, m12, 0f, 1f    // Col 3
);
```
Java2D's `getMatrix()` returns `[m00, m10, m01, m11, m02, m12]` — note the **interleaved** row/column order. JOML's 16-argument constructor takes arguments in **column-major** order. Getting this mapping wrong causes translations to be interpreted as perspective division coefficients, producing a completely black or distorted viewport.

### Null-Safety Fallback
`convertToMatrix4f()` returns `new Matrix4f()` (identity) if the input `AffineTransform` is `null`. `getScreenToWorld()` catches `NoninvertibleTransformException` and returns identity rather than crashing. Both are defensive measures against degenerate zoom states.

---

## 6. Texture Management: `Sprite` & `TextureLoader`

### Lazy Loading Pattern (Quirk)
[Sprite.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/utility/graphics/Sprite.java) uses a lazy-init pattern for `textureId`:
```java
public int getTextureId() {
    if (textureId == 0 && image != null) {
        textureId = TextureLoader.loadTexture(image);
    }
    return textureId;
}
```
The texture is uploaded to the GPU on **first access**, not on construction. This means the first frame that renders a new sprite will incur a texture upload stall. The value `0` is used as the "unloaded" sentinel (OpenGL guarantees `glGenTextures` never returns 0).

**Warning**: `getTextureId()` is **not thread-safe**. If called from a non-GL thread, the `glGenTextures` call will segfault. All texture access must happen within `paintGL()` or via the `glRunnables` queue.

### Pixel Unpacking (Quirk)
[TextureLoader.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/utility/graphics/opengl/TextureLoader.java) manually extracts ARGB pixels from `BufferedImage` and repacks them as RGBA:
```java
buffer.put((byte) ((pixel >> 16) & 0xFF));  // R
buffer.put((byte) ((pixel >> 8)  & 0xFF));  // G
buffer.put((byte) (pixel         & 0xFF));  // B
buffer.put((byte) ((pixel >> 24) & 0xFF));  // A
```
Java's `BufferedImage.getRGB()` returns ARGB (alpha in high byte), but OpenGL expects RGBA. The bit-shifting reorder is the critical conversion step.

### Texture Parameters
- **Wrap**: `GL_CLAMP_TO_EDGE` — prevents texture bleeding at sprite edges.
- **Filter**: `GL_LINEAR` for both min and mag — smooth scaling, no mipmaps.
- **Format**: `GL_RGBA8` — 32-bit color, 8 bits per channel.

No mipmaps are generated. This is acceptable because sprites are typically viewed at 1:1 or slightly zoomed, and the linear filter provides adequate quality.

---

## 7. Shader Program Management

[ShaderProgram.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/utility/graphics/opengl/ShaderProgram.java) wraps OpenGL shader lifecycle.

### Quirks
- **Shader detach after link**: After `glLinkProgram`, shaders are immediately detached (`glDetachShader`). This is correct practice — the compiled shader objects are no longer needed after linking.
- **Validation on every link**: `glValidateProgram` is called during linking. The validation result is logged as a `warn`, not an error, because some drivers report spurious warnings for valid programs.
- **Uniform cache**: Uniform locations are stored in a `HashMap<String, Integer>`. Looking up a uniform by name every frame is technically slower than caching, but the map access is O(1) amortized and avoids error-prone integer constants.
- **Stack-allocated matrix upload**: Matrix uniforms use `MemoryStack.stackPush()` to allocate a temporary `FloatBuffer` for `glUniformMatrix4fv`. This avoids heap allocation and is freed automatically when the try-with-resources block exits.

---

## 8. `DrawUtilities` — Legacy/GL Hybrid

[DrawUtilities.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/src/main/java/shipeditor/utility/graphics/DrawUtilities.java) contains both legacy `Graphics2D` methods (for text rendering, outlined shapes) and the modern GL method `paintInstallableGhostGL()`.

### Ghost Rendering
`paintInstallableGhostGL()` renders a semi-transparent (50% opacity) preview sprite of items being dragged onto the canvas. It computes the sprite center offset, constructs position/size/rotationAnchor vectors, and calls `SpriteRenderer.drawSprite()`.

### Stroke Caching
`DrawUtilities` maintains a static `HashMap<Float, Stroke> CACHED_STROKES` to avoid creating new `BasicStroke` objects for commonly used widths (2.5f, 3.0f, 5.0f). This is a micro-optimization relevant to the text rendering paths that still use `Graphics2D`.

### Draw Modes
An enum `DrawMode` controls rendering hints:
- `FAST` — disables quality rendering hints for speed.
- `QUALITY` — enables pure stroke control for crisp lines.
- `NORMAL` — default AWT hints.

The default is `FAST`, set as a private constant.
