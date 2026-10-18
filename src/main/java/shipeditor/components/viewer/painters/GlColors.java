package shipeditor.components.viewer.painters;

import org.joml.Vector4f;

import java.awt.Color;

/**
 * AWT {@link Color} to normalized OpenGL RGBA conversions used by the viewer painters.
 */
public final class GlColors {

    private static final float CHANNEL_MAX = 255.0f;

    private GlColors() {
    }

    /**
     * Writes the color with an explicit alpha into the target vector, avoiding allocations.
     * @return the target vector.
     */
    public static Vector4f set(Vector4f target, Color color, float alpha) {
        return target.set(
                color.getRed() / CHANNEL_MAX,
                color.getGreen() / CHANNEL_MAX,
                color.getBlue() / CHANNEL_MAX,
                alpha
        );
    }

    /**
     * Writes the color using its own alpha channel into the target vector, avoiding allocations.
     * @return the target vector.
     */
    public static Vector4f set(Vector4f target, Color color) {
        return set(target, color, color.getAlpha() / CHANNEL_MAX);
    }

    /**
     * @return a new vector holding the color with an explicit alpha.
     */
    public static Vector4f of(Color color, float alpha) {
        return set(new Vector4f(), color, alpha);
    }

    /**
     * @return a new vector holding the color with its own alpha channel.
     */
    public static Vector4f of(Color color) {
        return set(new Vector4f(), color);
    }

}
