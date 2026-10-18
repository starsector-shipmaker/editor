package shipeditor.components.instrument.ship.centers;

import shipeditor.utility.text.StringManager;

import shipeditor.components.viewer.entities.ShipCenterPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.CenterPointPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.components.widgets.PointLocationWidget;
import java.awt.geom.Point2D;
import java.util.function.Consumer;
import java.util.function.Supplier;

class ShipCenterLocationWidget extends PointLocationWidget {

    ShipCenterLocationWidget(CollisionPanel parent) {
        super(parent);
    }

    @Override
    protected String getDependentCoordinatesName() {
        return "Center position:";
    }

    @Override
    protected String getPanelTitleText() {
        return StringManager.getString("SHIP_CENTER");
    }

    @Override
    protected Supplier<Point2D> retrieveGetter() {
        return () -> {
            ShipPainter shipPainter = getEligibleShipPainter();
            if (shipPainter != null) {
                CenterPointPainter centerPointPainter = shipPainter.getCenterPointPainter();
                ShipCenterPoint shipCenterPoint = centerPointPainter.getCenterPoint();
                return shipCenterPoint.getPosition();
            }
            return null;
        };
    }

    @Override
    protected Consumer<Point2D> retrieveSetter() {
        return point -> {
            ShipPainter shipPainter = getEligibleShipPainter();
            if (shipPainter != null) {
                CenterPointPainter centerPointPainter = shipPainter.getCenterPointPainter();
                ShipCenterPoint shipCenterPoint = centerPointPainter.getCenterPoint();
                EditDispatch.postPointDragged(shipCenterPoint, point);
            }
        };
    }

}
