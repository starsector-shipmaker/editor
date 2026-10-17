package shipeditor.utility.graphics.opengl;

import lombok.extern.log4j.Log4j2;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;

/**
 * Reference implementation demonstrating Google Antigravity skills patterns
 * for high-performance and robust OpenGL rendering inside Starsector Ship Editor.
 */
@Log4j2
public class RenderingPatternsReference {

    // ==========================================
    // PATTERN A: TRIG-FREE GEOMETRY LOOKUP
    // ==========================================
    private static final int CIRCLE_SEGMENTS = 64;
    private static final float[] UNIT_CIRCLE_COS = new float[CIRCLE_SEGMENTS];
    private static final float[] UNIT_CIRCLE_SIN = new float[CIRCLE_SEGMENTS];

    static {
        // Pre-calculate trigonometry lookup tables once at class loading
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double theta = 2.0 * Math.PI * i / CIRCLE_SEGMENTS;
            UNIT_CIRCLE_COS[i] = (float) Math.cos(theta);
            UNIT_CIRCLE_SIN[i] = (float) Math.sin(theta);
        }
    }

    // ==========================================
    // PATTERN B: ALLOCATION-FREE RENDER LOOPS
    // ==========================================
    // Allocate a persistent native memory buffer once to reuse on every frame.
    // Avoids GC pressure and JVM/C heap allocation/deallocation overhead.
    private final FloatBuffer circleBuffer = MemoryUtil.memAllocFloat(CIRCLE_SEGMENTS * 3 * 2);

    private boolean isDrawing;
    private int vbo;
    private int vao;
    private ShaderProgram shader;

    public RenderingPatternsReference() {
        // Initialize OpenGL buffers and shaders here
    }

    // ==========================================
    // PATTERN C: ROBUST STATE & EXCEPTION RECOVERY
    // ==========================================
    public void begin(Matrix4f projection, Matrix4f view) {
        if (isDrawing) {
            // Mismatched state recovery: log and close previous uncompleted drawing batch
            log.warn("Renderer was already drawing! Forcing end of previous batch to prevent pipeline lock.");
            try {
                end();
            } catch (Exception e) {
                isDrawing = false;
            }
        }
        isDrawing = true;

        shader.bind();
        shader.setUniform("projection", projection);
        shader.setUniform("view", view);
        GL30.glBindVertexArray(vao);
    }

    public void end() {
        if (!isDrawing) {
            throw new IllegalStateException("Renderer is not drawing!");
        }
        isDrawing = false;
        GL30.glBindVertexArray(0);
        shader.unbind();
    }

    /**
     * Renders a circle using lookup tables and the persistent buffer.
     */
    public void drawCircle(Vector2f center, float radius, Vector4f color, boolean filled) {
        int count = filled ? CIRCLE_SEGMENTS * 3 : CIRCLE_SEGMENTS;
        circleBuffer.clear();

        // Populate persistent buffer without Math.cos/Math.sin calls
        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            int nextIdx = (i + 1) % CIRCLE_SEGMENTS;

            float x1 = radius * UNIT_CIRCLE_COS[i] + center.x;
            float y1 = radius * UNIT_CIRCLE_SIN[i] + center.y;

            float x2 = radius * UNIT_CIRCLE_COS[nextIdx] + center.x;
            float y2 = radius * UNIT_CIRCLE_SIN[nextIdx] + center.y;

            if (filled) {
                circleBuffer.put(center.x).put(center.y);
                circleBuffer.put(x1).put(y1);
                circleBuffer.put(x2).put(y2);
            } else {
                circleBuffer.put(x1).put(y1);
            }
        }

        circleBuffer.flip();
        shader.setUniform("shapeColor", color);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, circleBuffer);

        GL11.glDrawArrays(filled ? GL11.GL_TRIANGLES : GL11.GL_LINE_LOOP, 0, count);
    }

    // ==========================================
    // PATTERN D: DOUBLE-PASS OPACITY RENDERING
    // ==========================================
    public void paintBubble(Vector2f center, float radius, Vector4f fillColor, float fillOpacity, Vector4f ringColor, float ringOpacity) {
        // Pass 1: Semi-transparent interior fill
        Vector4f finalFillColor = new Vector4f(fillColor.x, fillColor.y, fillColor.z, fillOpacity);
        drawCircle(center, radius, finalFillColor, true);

        // Pass 2: Distinct outline border ring
        Vector4f finalRingColor = new Vector4f(ringColor.x, ringColor.y, ringColor.z, ringOpacity);
        GL11.glLineWidth(3.0f);
        drawCircle(center, radius, finalRingColor, false);
    }

    /**
     * Clean up native memory allocations to prevent GPU/off-heap leaks.
     */
    public void cleanup() {
        if (circleBuffer != null) {
            MemoryUtil.memFree(circleBuffer);
        }
    }
}
