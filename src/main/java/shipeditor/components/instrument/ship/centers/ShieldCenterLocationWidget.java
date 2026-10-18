package shipeditor.components.instrument.ship.centers;

import shipeditor.utility.text.StringManager;

import shipeditor.components.viewer.entities.ShieldCenterPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.ShieldPointPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.components.widgets.PointLocationWidget;
import java.awt.geom.Point2D;
import java.util.function.Consumer;
import java.util.function.Supplier;

class ShieldCenterLocationWidget extends PointLocationWidget {

    ShieldCenterLocationWidget(ShieldPanel parent) {
        super(parent);
    }

    @Override
    protected String getDependentCoordinatesName() {
        return StringManager.getString("SHIELD_POSITION");
    }

    @Override
    protected String getPanelTitleText() {
        return StringManager.getString("SHIELD_CENTER");
    }

    @Override
    protected Supplier<Point2D> retrieveGetter() {
        return () -> {
            ShipPainter shipPainter = getEligibleShipPainter();
            if (shipPainter != null) {
                ShieldPointPainter shieldPointPainter = shipPainter.getShieldPointPainter();
                ShieldCenterPoint shieldCenterPoint = shieldPointPainter.getShieldCenterPoint();

                return shieldCenterPoint.getPosition();
            }
            return null;
        };
    }

    @Override
    protected Consumer<Point2D> retrieveSetter() {
        return point -> {
            ShipPainter shipPainter = getEligibleShipPainter();
            if (shipPainter != null) {
                ShieldPointPainter shieldPointPainter = shipPainter.getShieldPointPainter();
                ShieldCenterPoint shieldCenterPoint = shieldPointPainter.getShieldCenterPoint();
                EditDispatch.postPointDragged(shieldCenterPoint, point);
            }
        };
    }

}
