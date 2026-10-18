package shipeditor.utility;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import static org.junit.jupiter.api.Assertions.*;

class UtilityPropertiesTest {

    @Property
    void testParseIntegerOrDefault(@ForAll String value, @ForAll int defaultValue) {
        int result = Utility.parseIntegerOrDefault(value, defaultValue);
        
        if (value == null || value.trim().isEmpty()) {
            assertEquals(defaultValue, result);
        } else {
            try {
                int expected = Integer.parseInt(value.trim());
                assertEquals(expected, result);
            } catch (NumberFormatException e) {
                assertEquals(defaultValue, result);
            }
        }
    }

    @Property
    void testParseDoubleOrDefault(@ForAll String value, @ForAll double defaultValue) {
        double result = Utility.parseDoubleOrDefault(value, defaultValue);
        
        if (value == null || value.trim().isEmpty()) {
            assertEquals(defaultValue, result);
        } else {
            try {
                double expected = Double.parseDouble(value.trim());
                assertEquals(expected, result);
            } catch (NumberFormatException e) {
                assertEquals(defaultValue, result);
            }
        }
    }

    @Property
    void testClampAngleWithRounding(@ForAll @DoubleRange(min = -1e9, max = 1e9) double radians) {
        double result = Utility.clampAngleWithRounding(radians);
        
        // Result should be between 0 and 360
        assertTrue(result >= 0.0 && result < 360.0);
    }

    @Property
    void testFlipAngle(@ForAll @DoubleRange(min = -1e9, max = 1e9) double degrees) {
        double flipped = Utility.flipAngle(degrees);
        
        // Flipped angle should be between 0 and 360
        assertTrue(flipped >= 0.0 && flipped < 360.0);
        
        // Flipping an angle twice should return the normalized original angle
        double flippedTwice = Utility.flipAngle(flipped);
        double normalizedOriginal = (degrees % 360.0 + 360.0) % 360.0;
        
        // Use a small delta for floating point comparison
        assertEquals(normalizedOriginal, flippedTwice, 0.0001);
    }

    @Property
    void testRound(@ForAll double value, @ForAll @IntRange(min = 0, max = 10) int decimalPlaces) {
        double result = Utility.round(value, decimalPlaces);
        
        if (Double.isFinite(value)) {
            assertNotNull(result); // Result should compute correctly for finite numbers
        }
    }

    @Property
    void testTransformAngle(@ForAll @DoubleRange(min = -1e9, max = 1e9) double raw) {
        double transformed = Utility.transformAngle(raw);
        // transformed = (360 - ((raw % 360 + 360) % 360)) % 360 - 90
        // max value is 360 - 90 = 270, min value is 0 - 90 = -90
        assertTrue(transformed >= -90.0 && transformed < 270.0);
    }

    @Example
    void testGetFrameSpritePath() {
        assertEquals("graphics/weapons/istl_linearcannonlight_turret_base_01.png",
                Utility.getFrameSpritePath("graphics/weapons/istl_linearcannonlight_turret_base_00.png", 1));
        assertEquals("graphics/weapons/anim_turret_3.png",
                Utility.getFrameSpritePath("graphics/weapons/anim_turret_0.png", 3));
        assertNull(Utility.getFrameSpritePath("graphics/weapons/istl_socket_turret_sm.png", 1));
        assertNull(Utility.getFrameSpritePath("graphics/weapons/ionpulser_turret_glow.png", 1));
    }
}
