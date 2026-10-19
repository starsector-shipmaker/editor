package shipeditor.components.viewer.painters;

import shipeditor.utility.text.StringManager;

import shipeditor.utility.graphics.opengl.OpenGLPainter;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import shipeditor.components.datafiles.entities.CSVEntry;
import shipeditor.components.datafiles.entities.InstallableEntry;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.datafiles.entities.transferable.Transferables.TransferableEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.ViewerDropReceiver;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.utility.Utility;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.graphics.opengl.TextRenderer;

import java.awt.Color;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.geom.Point2D;

public class DraggedObjectsPainter implements OpenGLPainter {



    @SuppressWarnings("ChainOfInstanceofChecks")
    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        InstallableEntry dragged = ViewerDropReceiver.getDraggedEntry();
        Point2D currentCursor = StaticController.getCorrectedWithoutRotate();

        double rotation = 0;
        WeaponMount mount = WeaponMount.TURRET;
        WeaponSlotPoint selectedWeaponSlot = StaticController.getSelectedAndEligibleSlot();
        if (selectedWeaponSlot != null) {
            rotation = selectedWeaponSlot.getAngle();
            ShipPainter weaponSlotParent = selectedWeaponSlot.getParent();
            double rotationRadians = weaponSlotParent.getRotationRadians();
            rotation -= Math.toDegrees(rotationRadians);

            rotation = Utility.flipAngle(rotation);
            mount = selectedWeaponSlot.getWeaponMount();
        }

        EditorInstrument editorMode = StaticController.getEditorMode();
        Font font = Utility.getOrbitron(12);

        if (dragged instanceof ShipCSVEntry shipEntry) {
            double conditionalAngle = rotation;

            boolean isModuleMode = editorMode == EditorInstrument.VARIANT_MODULES;
            if (!isModuleMode) {
                conditionalAngle = 0;
            }
            shipEntry.paintEntry(spriteRenderer, shapeRenderer, projection, view, conditionalAngle, currentCursor);

            paintShipEntryHints(spriteRenderer, projection, shipEntry, isModuleMode, selectedWeaponSlot, font);

        } else if (dragged instanceof WeaponCSVEntry weaponEntry) {
            boolean isWeaponsMode = editorMode == EditorInstrument.VARIANT_WEAPONS
                    || editorMode == EditorInstrument.WEAPON_SLOTS;
            if (!isWeaponsMode) {
                rotation = 0;
            }

            weaponEntry.paintEntry(spriteRenderer, shapeRenderer, projection, view,
                    rotation, currentCursor, mount);

            if (isWeaponsMode) {
                paintSlotStatus(spriteRenderer, projection, selectedWeaponSlot, font, weaponEntry, currentCursor);
            } else {
                TextRenderer.drawTextGL(spriteRenderer, projection, "Not in install mode", font, Color.GRAY, currentCursor);
            }
        }
    }

    private void paintShipEntryHints(SpriteRenderer spriteRenderer, Matrix4f projection, CSVEntry shipEntry,
                                     boolean isModuleMode, WeaponSlotPoint selectedWeaponSlot, Font font) {
        if (isModuleMode && StaticController.isShipLayerActive()) {
            if (StaticController.isShipVariantActive()) {
                paintSlotStatus(spriteRenderer, projection, selectedWeaponSlot, font, shipEntry, StaticController.getCorrectedWithoutRotate());
            } else {
                TextRenderer.drawTextGL(spriteRenderer, projection, "No active variant", font, Color.RED, StaticController.getCorrectedWithoutRotate());
            }
        } else {
            DataFlavor draggedFlavor = ViewerDropReceiver.getCurrentFlavor();
            if (draggedFlavor != null) {
                if (draggedFlavor == TransferableEntry.TRANSFERABLE_SHIP) {
                    TextRenderer.drawTextGL(spriteRenderer, projection, "Hull to layer", font, Color.GREEN, StaticController.getCorrectedWithoutRotate());
                }
                else if (draggedFlavor == TransferableEntry.TRANSFERABLE_VARIANT) {
                    TextRenderer.drawTextGL(spriteRenderer, projection, "Variant to layer", font, Color.GREEN, StaticController.getCorrectedWithoutRotate());
                }
            }
        }
    }

    private void paintSlotStatus(SpriteRenderer spriteRenderer, Matrix4f projection,
                                 WeaponSlotPoint selectedWeaponSlot, Font font,
                                 CSVEntry entry, Point2D cursor) {
        if (selectedWeaponSlot == null) {
            TextRenderer.drawTextGL(spriteRenderer, projection, StringManager.getString("SLOT_NOT_SELECTED"), font, Color.GRAY, cursor);
        } else if (!selectedWeaponSlot.canFit(entry)) {
            TextRenderer.drawTextGL(spriteRenderer, projection, StringManager.getString("UNFIT_FOR_SLOT"), font, Color.RED, cursor);
        } else {
            TextRenderer.drawTextGL(spriteRenderer, projection, StringManager.getString("CAN_INSTALL"), font, Color.GREEN, cursor);
        }
    }

}
