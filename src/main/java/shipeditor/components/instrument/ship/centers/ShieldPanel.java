package shipeditor.components.instrument.ship.centers;

import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.ShieldCenterPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.components.viewer.painters.points.ship.ShieldPointPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.components.widgets.PointLocationWidget;

public class ShieldPanel extends AbstractCenterPanel {

    @Override
    protected EditorInstrument getMode() {
        return EditorInstrument.SHIELD;
    }

    @Override
    protected String getOpacityLabelKey() {
        return "SHIELD_OPACITY";
    }

    @Override
    protected String getVisibilityLabelKey() {
        return "SHIELD_VIEW";
    }

    @Override
    protected String getRadiusLabelKey() {
        return "SHIELD_RADIUS";
    }

    @Override
    protected AbstractPointPainter getPointPainter(ShipPainter shipPainter) {
        return shipPainter != null ? shipPainter.getShieldPointPainter() : null;
    }

    @Override
    protected BaseWorldPoint getTargetCenterPoint(ShipPainter shipPainter) {
        ShieldPointPainter painter = shipPainter != null ? shipPainter.getShieldPointPainter() : null;
        return painter != null ? painter.getShieldCenterPoint() : null;
    }

    @Override
    protected double getCurrentRadius(BaseWorldPoint centerPoint) {
        return centerPoint instanceof ShieldCenterPoint shieldCenter ? shieldCenter.getShieldRadius() : 0.0;
    }

    @Override
    protected void postRadiusChanged(BaseWorldPoint centerPoint, float radius) {
        if (centerPoint instanceof ShieldCenterPoint shieldCenter) {
            EditDispatch.postShieldRadiusChanged(shieldCenter, radius);
        }
    }

    @Override
    protected PointLocationWidget createCenterLocationWidget() {
        return new ShieldCenterLocationWidget(this);
    }
}
