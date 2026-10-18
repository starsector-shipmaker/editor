package shipeditor.utility.components.dialog;

import org.junit.jupiter.api.Test;
import shipeditor.components.viewer.layers.ship.ShipLayer;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Headless-safe checks for the pure parts of {@link DialogUtilities}.
 * The double-click callbacks of the weapon/hullmod pickers are lambdas created inside
 * {@code showWeaponPickerDialog}/{@code showHullmodPickerDialog}, which block on a modal
 * {@code JDialog}; they cannot be exercised without a display or without refactoring.
 */
class DialogUtilitiesTest {

    @Test
    void resolveShipPackageOfNullLayerIsNull() {
        assertNull(DialogUtilities.resolveShipPackage(null));
    }

    @Test
    void resolveShipPackageOfLayerWithoutPainterOrHullIsNull() {
        assertNull(DialogUtilities.resolveShipPackage(new ShipLayer()));
    }
}
