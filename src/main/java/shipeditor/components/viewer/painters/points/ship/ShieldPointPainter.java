package shipeditor.components.viewer.painters.points.ship;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.ShieldCenterPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.ship.HullStyle;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.graphics.ColorUtilities;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class ShieldPointPainter extends AbstractRadiusPainter {

    private final List<BaseWorldPoint> points = new ArrayList<>();

    @Getter
    private ShieldCenterPoint shieldCenterPoint;


    public ShieldPointPainter(ShipPainter parent) {
        super(parent);
        this.initModeListening();
        this.initHotkeys();
        this.setInteractionEnabled(StaticController.getEditorMode() == EditorInstrument.SHIELD);
    }

    @Override
    public void cleanupListeners() {
        super.cleanupListeners();
    }

    public void initShieldPoint(Point2D translated, HullSpecFile hullSpecFile) {
        HullStyle style = GameDataRepository.fetchStyleByID(hullSpecFile.getStyle());
        if (style == null) {
            style = new HullStyle();
        }
        double shieldRadius = hullSpecFile.getShieldRadius();

        if (this.shieldCenterPoint != null) {
            this.removePoint(shieldCenterPoint);
        }

        this.shieldCenterPoint = new ShieldCenterPoint(translated,
                (float) shieldRadius, this.getParentLayer(), style, this);
        this.addPoint(shieldCenterPoint);
        Color shieldInnerColor = style.getShieldInnerColor();
        float styleInnerColorOpacity = ColorUtilities.getOpacityFromAlpha(shieldInnerColor.getAlpha());
        this.setPaintOpacity(styleInnerColorOpacity);
    }

    public void setShieldStyle(HullStyle style) {
        Color shieldInnerColor = style.getShieldInnerColor();
        float styleInnerColorOpacity = ColorUtilities.getOpacityFromAlpha(shieldInnerColor.getAlpha());
        this.setPaintOpacity(styleInnerColorOpacity);

        this.shieldCenterPoint.setAssociatedStyle(style);
    }

    @Override
    protected EditorInstrument getRadiusInstrument() {
        return EditorInstrument.SHIELD;
    }

    @Override
    protected BaseWorldPoint getRadiusPoint() {
        return this.shieldCenterPoint;
    }

    @Override
    protected void postRadiusChanged(float radius) {
        EditDispatch.postShieldRadiusChanged(this.shieldCenterPoint, radius);
    }

    @Override
    public List<BaseWorldPoint> getPointsIndex() {
        return points;
    }

    @Override
    protected void addPointToIndex(BaseWorldPoint point) {
        points.add(point);
    }

    @Override
    protected void removePointFromIndex(BaseWorldPoint point) {
        points.remove(point);
    }

    @Override
    public int getIndexOfPoint(BaseWorldPoint point) {
        return points.indexOf(point);
    }

    @Override
    protected Class<ShieldCenterPoint> getTypeReference() {
        return ShieldCenterPoint.class;
    }

}
