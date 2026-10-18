package shipeditor.undo.edits;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Getter;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.CenterPointPainter;
import shipeditor.undo.AbstractEdit;
import shipeditor.undo.Edit;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.overseers.StaticController;

import java.awt.geom.Point2D;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class LayerEdits {

    private LayerEdits() {
    }

    private static void queueLayerPropertiesAndCenterPanelsRepaint() {
        var repainter = StaticController.getScheduler();
        repainter.queueLayerPropertiesRepaint();
        repainter.queueCenterPanelsRepaint();
    }

    public static class HullCoversColorEdit extends AbstractEdit {
        @Getter
        private shipeditor.components.viewer.layers.ViewerLayer layer;
        private final java.awt.Color oldColor;
        private final java.awt.Color updatedColor;

        public HullCoversColorEdit(shipeditor.components.viewer.layers.ViewerLayer layer, java.awt.Color oldColor, java.awt.Color updatedColor) {
            this.layer = layer;
            this.oldColor = oldColor;
            this.updatedColor = updatedColor;
        }

        @Override
        public void undo() {
            if (layer instanceof shipeditor.components.viewer.layers.ship.ShipLayer shipLayer && shipLayer.getHull() != null) {
                shipLayer.getHull().setCoversColor(oldColor);
                StaticController.reselectCurrentLayer();
            }
        }

        @Override
        public void redo() {
            if (layer instanceof shipeditor.components.viewer.layers.ship.ShipLayer shipLayer && shipLayer.getHull() != null) {
                shipLayer.getHull().setCoversColor(updatedColor);
                StaticController.reselectCurrentLayer();
            }
        }

        @Override
        public String getName() {
            return "Hull Covers Color";
        }

        @Override
        public LayerPainter getLayerPainter() {
            return this.layer.getPainter();
        }

        @Override
        public void cleanupReferences() {
            this.layer = null;
        }

        public boolean isSimilar(Edit edit) {
            return edit instanceof HullCoversColorEdit checked &&
                    checked.getLayer() == this.layer &&
                    this.oldColor != null &&
                    this.updatedColor != null;
        }

        public void assimilate(Edit edit) {
            if (edit instanceof HullCoversColorEdit checked && isSimilar(checked)) {
                checked.setFinished(true);
            }
        }
    }

    public static class AnchorOffsetEdit extends AbstractEdit {
        @Getter
        private LayerPainter layerPainter;
        private final Point2D oldOffset;
        private final Point2D updatedOffset;

        public AnchorOffsetEdit(LayerPainter painter, Point2D old, Point2D updated) {
            this.layerPainter = painter;
            this.oldOffset = old;
            this.updatedOffset = updated;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            layerPainter.setAnchor(oldOffset);
            queueLayerPropertiesAndCenterPanelsRepaint();
        }

        @Override
        public void redo() {
            layerPainter.setAnchor(updatedOffset);
            redoSubEdits();
            queueLayerPropertiesAndCenterPanelsRepaint();
        }

        @Override
        public String getName() {
            return "Anchor Offset";
        }

        @Override
        public void cleanupReferences() {
            this.cleanupSubEditsOfType(AnchorOffsetEdit.class);
            this.layerPainter = null;
        }
    }

    public static class LayerRotationEdit extends AbstractEdit {
        @Getter
        private LayerPainter layerPainter;
        private final double oldRotation;
        private final double updatedRotation;

        public LayerRotationEdit(LayerPainter painter, double old, double updated) {
            this.layerPainter = painter;
            this.oldRotation = old;
            this.updatedRotation = updated;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            layerPainter.setRotationRadians(oldRotation);
            queueLayerPropertiesAndCenterPanelsRepaint();
        }

        @Override
        public void redo() {
            layerPainter.setRotationRadians(updatedRotation);
            redoSubEdits();
            queueLayerPropertiesAndCenterPanelsRepaint();
        }

        @Override
        public String getName() {
            return "Rotate Layer";
        }

        @Override
        public void cleanupReferences() {
            this.cleanupSubEditsOfType(LayerRotationEdit.class);
            this.layerPainter = null;
        }
    }

    public static class ModuleAnchorEdit extends AbstractEdit {
        @Getter
        private CenterPointPainter centersPainter;
        private final Point2D oldAnchor;
        private final Point2D updatedAnchor;

        public ModuleAnchorEdit(CenterPointPainter painter, Point2D old, Point2D updated) {
            this.centersPainter = painter;
            this.oldAnchor = old;
            this.updatedAnchor = updated;
            this.setFinished(false);
        }

        @Override
        public void undo() {
            undoSubEdits();
            centersPainter.setModuleAnchorOffset(oldAnchor);
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueCenterPanelsRepaint();
            repainter.queueModulesRepaint();
        }

        @Override
        public void redo() {
            centersPainter.setModuleAnchorOffset(updatedAnchor);
            redoSubEdits();
            var repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueCenterPanelsRepaint();
            repainter.queueModulesRepaint();
        }

        @Override
        public LayerPainter getLayerPainter() {
            return centersPainter.getParentLayer();
        }

        @Override
        public String getName() {
            return "Module Anchor Change";
        }

        @Override
        public void cleanupReferences() {
            this.cleanupSubEditsOfType(ModuleAnchorEdit.class);
            this.centersPainter = null;
        }
    }

    public static class SpriteSwapEdit extends AbstractEdit {
        private LayerPainter layer;
        private final Sprite oldSprite;
        private final Sprite newSprite;

        public SpriteSwapEdit(LayerPainter layer, Sprite oldSprite, Sprite newSprite) {
            this.layer = layer;
            this.oldSprite = oldSprite;
            this.newSprite = newSprite;
        }

        @Override
        public void undo() {
            layer.reconfigureSpriteCircumstance(oldSprite);
            if (layer instanceof ShipPainter shipPainter) {
                shipPainter.setBaseHullSprite(oldSprite);
            }
            EventBus.publish(new ActiveLayerUpdated(layer.getParentLayer()));
        }

        @Override
        public void redo() {
            layer.reconfigureSpriteCircumstance(newSprite);
            if (layer instanceof ShipPainter shipPainter) {
                shipPainter.setBaseHullSprite(newSprite);
            }
            EventBus.publish(new ActiveLayerUpdated(layer.getParentLayer()));
        }

        @Override
        public String getName() {
            return "Sprite Swap";
        }

        @Override
        public LayerPainter getLayerPainter() {
            return layer;
        }

        @Override
        public void cleanupReferences() {
            this.layer = null;
        }
    }
}
