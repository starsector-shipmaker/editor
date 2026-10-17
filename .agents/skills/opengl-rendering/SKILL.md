---
name: opengl-rendering
description: Core guidelines, coordinate transformation mathematics, high-performance drawing techniques, and state recovery patterns for OpenGL rendering in Starsector Ship Editor.
---

# OpenGL Rendering in Starsector Ship Editor

This skill provides comprehensive instructions for agents modifying or adding drawing capabilities using the custom LWJGL 3 OpenGL pipeline.

## Skill Directory Structure

This skill is organized as follows:
- **`SKILL.md`**: Main instructions (this file).
- **`resources/`**: Documents, assets, and guides.
  - [rendering_migration_guide.md](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/opengl-rendering/resources/rendering_migration_guide.md): The full details of the OpenGL migration, coordinate spaces, shaders, and quads.
  - [rendering_pipeline.md](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/opengl-rendering/resources/rendering_pipeline.md): Comprehensive documentation of the rendering pipeline, PaintOrderController, SpriteRenderer, ShapeRenderer, and Transform Math.
- **`examples/`**: Code references.
  - [RenderingPatternsReference.java](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/opengl-rendering/examples/RenderingPatternsReference.java): Reference implementation of lookup tables, memory buffers, double-pass opacity, and robust state recovery.
- **`scripts/`**: Tooling.
  - [rebuild_and_run.sh](file:///run/media/banglc/work/starsector/starsector-shipmaker/.agents/skills/opengl-rendering/scripts/rebuild_and_run.sh): Script to automate compilation, packaging, and execution.

## Rendering Architecture Overview

The rendering context is integrated within Swing components:
- **`PrimaryViewer.java`**: Host container enclosing the `AWTGLCanvas`. Sets up the viewport, projection, and view matrices, then triggers repaints.
- **`PaintOrderController.java`**: Orchestrates the draw order (background -> grid axes -> layers -> guides -> hotkeys).
- **`SpriteRenderer.java`**: Handles textured sprite quads.
- **`ShapeRenderer.java`**: Handles UI, overlays, grids, lines, rectangles, and circle geometry.
- **`TextRenderer.java`**: LRU-cached (`MAX_CACHE_SIZE = 256` with `glDeleteTextures` eviction) AWT glyph-outline to OpenGL texture renderer (`drawTextGL`, `drawTextScreenGL`).
- **`FramebufferUtilities.java`**: Offscreen FBO (`GL_FRAMEBUFFER`) rendering for exporting layers or the viewer canvas to `BufferedImage` / PNG files (`renderLayerToImage`, `printLayerToImage`, `printViewerToImage`).
- **`PolygonSimplifier.java` & `SpriteContourTracer.java`**: Geometric utilities implementing Douglas-Peucker polygon decimation and Moore-Neighbor boundary tracing (with Jacob's stopping criterion), delegated to by `CollisionHullGenerator` and `SpriteOutlineTracer`.
- **Animated Weapon Frame Cycling**: `WeaponSprites` evaluates `currentFrame % frames.size()` per sprite part so static 1-frame barrels remain visible while multi-frame parts cycle via the `F6` hotkey.

## Coordinate & Transformation Math

Always ensure correct coordinate mapping between World Space and Screen/NDC spaces:
- **Viewport/Projection**: OpenGL Y-coordinates increase upwards, while Swing/AWT increases downwards. An orthographic projection maps NDC space to screen pixels:
  ```java
  projectionMatrix.setOrtho(0.0f, getWidth(), getHeight(), 0.0f, -1.0f, 1.0f);
  ```
- **World-to-Screen**: Camera translation/zoom are tracked as `AffineTransform` and mapped to `Matrix4f` using column-major mapping (translations mapped to column 3).
- **Rotations**: To rotate a sprite around a custom anchor point in world coordinates:
  $$M = T(\text{rotAnchor}) \cdot R(\theta) \cdot T(\text{position} - \text{rotAnchor}) \cdot S(\text{size})$$

## Best Practices for High-Performance Rendering

To maintain a smooth 60 FPS viewport, adhere to the following rules:
1. **Trig-Free Render Loops**: Never compute `Math.cos` or `Math.sin` inside render calls. Utilize pre-calculated unit-circle coordinates:
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
2. **Allocation-Free Drawing**: Avoid allocating memory (like direct arrays or FloatBuffers) dynamically during rendering frames. Instead, allocate a persistent native buffer at initialization and reuse it:
   ```java
   private final java.nio.FloatBuffer circleBuffer = org.lwjgl.system.MemoryUtil.memAllocFloat(CIRCLE_SEGMENTS * 3 * 2);
   ```
   *Remember to free the memory in the `cleanup()` method of the renderer.*
3. **Double-Pass Opacity Rendering**: For UI elements like collision/shield bubbles, perform rendering in two passes for high visual fidelity:
   - **Pass 1 (Interior Fill)**: Use low opacity (e.g. `parentPainter.getPaintOpacity()`).
   - **Pass 2 (Boundary Ring Outline)**: Draw the outline with a distinct line width (e.g. `glLineWidth(3.0f)`) and higher opacity (e.g. `0.5f`).

## Robust Drawing State & Error Recovery

Any Swing paint error or unhandled runtime exception can break the `begin()`/`end()` drawing state block. To prevent cascading rendering crashes (`IllegalStateException` loops), `ShapeRenderer` utilizes automatic recovery:
- If `begin()` is called while `isDrawing == true`, the renderer logs a warning, forces the unclosed batch to end (calls `end()`), resets OpenGL state, and resumes a new batch safely.
  ```java
  public void begin(Matrix4f projection, Matrix4f view) {
      if (isDrawing) {
          log.warn("ShapeRenderer was already drawing! Forcing end of previous batch to recover.");
          try {
              end();
          } catch (Exception e) {
              isDrawing = false;
          }
      }
      isDrawing = true;
      // Bind shader and VAO...
  }
  ```

## Weapon Recoil & Projectile Anchor Transformations

1. **Weapon Recoil Vector Calculation**:
   - In Starsector / editor coordinate space, rotation angle 0 maps to facing UP (positive Y in standard Starsector, 0 radians CW in `LayerPainter`).
   - The recoil vector must push the weapon barrel backward (DOWN into the mount when facing UP).
   - Recoil Vector formula:
     $$\vec{v}_{\text{recoil}} = \left(-\sin(\theta), \cos(\theta)\right) \cdot \text{recoilOffset}$$
   - *Bug Trap:* Using `(\cos(\theta), \sin(\theta))` causes weapons pointing UP to slide sideways (left/right) instead of retracting into their hardpoints.

2. **Missile Pivot Anchor Conversion**:
   - Starsector `.proj` files define projectile centers from the **bottom-left** of the missile sprite (where the engine nozzle is located).
   - The OpenGL rendering pipeline uses top-left sprite quad coordinates:
     $$y_{\text{anchor}} = \text{spriteDimensions.getHeight()} - y_{\text{projCenter}}$$
   - This allows missiles to pivot and sit flush by their rocket nozzles rather than being misaligned by their nose cones.

3. **Loaded Missile Animation Synchronization**:
   - When rendering loaded missiles inside launchers (e.g., in `WeaponPainter.paintLoadedMissilesGL`), missiles must follow the weapon barrel's recoil during animations.
   - Always add the active `recoilVector` of the weapon to the missile's `paintAnchor` before applying rotation and rendering.

## Coordinate Systems & Legacy Java2D Porting

The original `Ship-Editor` repository (by `ontheheaven`) used Java2D `Graphics2D` rendering. In that architecture, mathematical points (like `ShipCenterPoint` or `WeaponSlotPoint`) were **never rotated**. Instead, `PaintOrderController` rotated the entire global canvas `AffineTransform` matrix, and Java2D drew the unrotated points at a rotated angle on the screen.

When porting to OpenGL, the global `view` matrix rotation was intentionally dropped from `PaintOrderController` in favor of component-level transformations. Because of this architectural shift:

1. **Explicit Point Rotation**: You must explicitly rotate the physical points (using `AffineTransform` mathematics) when a layer or module is rotated, otherwise the points will remain at 0 degrees while the sprite rotates beneath them.
2. **Stable Pivot Calculations**: When manually rotating points, be extremely careful about **pivot anchor calculations** (like `getRotationAnchor()`). If a pivot calculation relies on a point (like `ShipCenterPoint`) that is being physically moved during rotation, the pivot will become unstable. You must ensure that pivot logic calculates the anchor *before* points are moved (using the old angle) or mathematically rotates the offsets to construct a perfectly stationary pivot. Failure to do so will cause sprite coordinates to desync and fly apart.

