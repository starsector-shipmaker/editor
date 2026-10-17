package shipeditor.components.instrument.ship.centers;

import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.ShipCenterPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.components.viewer.painters.points.ship.CenterPointPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.components.widgets.PointLocationWidget;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public class CollisionPanel extends AbstractCenterPanel {

    private ModuleAnchorPanel moduleAnchorWidget;

    @Override
    protected EditorInstrument getMode() {
        return EditorInstrument.COLLISION;
    }

    @Override
    protected String getOpacityLabelKey() {
        return "COLLISION_OPACITY";
    }

    @Override
    protected String getVisibilityLabelKey() {
        return "COLLISION_VIEW";
    }

    @Override
    protected String getRadiusLabelKey() {
        return "COLLISION_RADIUS";
    }

    @Override
    protected AbstractPointPainter getPointPainter(ShipPainter shipPainter) {
        return shipPainter != null ? shipPainter.getCenterPointPainter() : null;
    }

    @Override
    protected BaseWorldPoint getTargetCenterPoint(ShipPainter shipPainter) {
        CenterPointPainter painter = shipPainter != null ? shipPainter.getCenterPointPainter() : null;
        return painter != null ? painter.getCenterPoint() : null;
    }

    @Override
    protected double getCurrentRadius(BaseWorldPoint centerPoint) {
        return centerPoint instanceof ShipCenterPoint shipCenter ? shipCenter.getCollisionRadius() : 0.0;
    }

    @Override
    protected void postRadiusChanged(BaseWorldPoint centerPoint, float radius) {
        if (centerPoint instanceof ShipCenterPoint shipCenter) {
            EditDispatch.postCollisionRadiusChanged(shipCenter, radius);
        }
    }

    @Override
    protected PointLocationWidget createCenterLocationWidget() {
        return new ShipCenterLocationWidget(this);
    }

    @Override
    protected JComponent createCenterExtraComponent() {
        moduleAnchorWidget = new ModuleAnchorPanel();
        JPanel moduleAnchorWrapper = new JPanel(new BorderLayout());
        moduleAnchorWrapper.add(moduleAnchorWidget, BorderLayout.PAGE_START);
        return moduleAnchorWrapper;
    }

    @Override
    protected void refreshExtraComponents(ShipPainter shipPainter) {
        if (moduleAnchorWidget != null) {
            moduleAnchorWidget.setCenterPainter(shipPainter != null ? shipPainter.getCenterPointPainter() : null);
            moduleAnchorWidget.refresh(shipPainter);
        }
    }
}
