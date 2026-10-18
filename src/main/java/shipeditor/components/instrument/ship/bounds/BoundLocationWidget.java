package shipeditor.components.instrument.ship.bounds;

import shipeditor.components.viewer.entities.BoundPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.BoundPointsPainter;
import shipeditor.utility.components.widgets.PointLocationWidget;

import java.awt.geom.Point2D;
import java.util.function.Consumer;
import java.util.function.Supplier;

class BoundLocationWidget extends PointLocationWidget {

    BoundLocationWidget(BoundsPanel parent) {
        super(parent);
    }

    @Override
    protected String getDependentCoordinatesName() {
        return "Bound position:";
    }

    @Override
    protected String getPanelTitleText() {
        return "Selected Bound";
    }

    @Override
    protected Supplier<Point2D> retrieveGetter() {
        return () -> {
            ShipPainter shipPainter = getEligibleShipPainter();
            if (shipPainter != null) {
                BoundPointsPainter boundsPainter = shipPainter.getBoundsPainter();
                BoundPoint selected = boundsPainter.getSelected();
                if (selected != null) {
                    return selected.getPosition();
                }
            }
            return null;
        };
    }

    @Override
    protected Consumer<Point2D> retrieveSetter() {
        return point -> {
            ShipPainter shipPainter = getEligibleShipPainter();
            if (shipPainter != null) {
                BoundPointsPainter boundsPainter = shipPainter.getBoundsPainter();
                BoundPoint selected = boundsPainter.getSelected();
                if (selected != null) {
                    boundsPainter.dragPointWithMirrorCheck(point);
                }
            }
        };
    }

}
