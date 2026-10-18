package shipeditor.components.viewer.painters.points.ship;

import shipeditor.components.viewer.painters.GlColors;
import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.ShipCenterPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.ZoomFadedLabel;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/** * Also intended to handle collision radii and their painting.*/
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class CenterPointPainter extends AbstractRadiusPainter {

    private static final float COLLISION_OPACITY = 0.2f;
    private final List<BaseWorldPoint> points = new ArrayList<>();

    @Getter
    private ShipCenterPoint centerPoint;

    @Getter @Setter
    private Point2D moduleAnchorOffset;

    public CenterPointPainter(ShipPainter parent) {
        super(parent);
        this.initModeListening();
        this.initHotkeys();
        this.setInteractionEnabled(StaticController.getEditorMode() == EditorInstrument.COLLISION);
        this.setPaintOpacity(COLLISION_OPACITY);
    }

    public void changeModuleAnchor(Point2D updated) {
        EditDispatch.postModuleAnchorChanged(this, updated);
    }

    @Override
    public void cleanupListeners() {
        super.cleanupListeners();
    }

    @Override
    protected EditorInstrument getRadiusInstrument() {
        return EditorInstrument.COLLISION;
    }

    @Override
    protected BaseWorldPoint getRadiusPoint() {
        return this.centerPoint;
    }

    @Override
    protected void postRadiusChanged(float radius) {
        EditDispatch.postCollisionRadiusChanged(this.centerPoint, radius);
    }

    public void initCenterPoint(Point2D translatedCenter, HullSpecFile hullSpecFile) {
        if (this.centerPoint != null) {
            this.removePoint(centerPoint);
        }
        this.centerPoint = new ShipCenterPoint(translatedCenter,
                (float) hullSpecFile.getCollisionRadius(), this.getParentLayer(), this);
        this.addPoint(centerPoint);
    }



    @Override
    protected void paintPainterContent(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        if (moduleAnchorOffset == null) return;

        Point2D centerPosition = this.centerPoint.getPosition();
        double x = centerPosition.getX() - moduleAnchorOffset.getY();
        double y = centerPosition.getY() - moduleAnchorOffset.getX();
        Point2D resultAnchorLocation = new Point2D.Double(x, y);
        Color moduleColor = WeaponType.STATION_MODULE.getColor();

        // Draw cross lines using shapeRenderer
        AffineTransform worldToScreen = StaticController.getViewer().getWorldToScreen();
        Point2D screenLoc = worldToScreen.transform(resultAnchorLocation, null);
        org.joml.Vector2f screenPos = new org.joml.Vector2f((float) screenLoc.getX(), (float) screenLoc.getY());

        float size = 6.0f;
        org.joml.Vector2f horizStart = new org.joml.Vector2f(screenPos.x - size, screenPos.y);
        org.joml.Vector2f horizEnd = new org.joml.Vector2f(screenPos.x + size, screenPos.y);
        org.joml.Vector2f vertStart = new org.joml.Vector2f(screenPos.x, screenPos.y - size);
        org.joml.Vector2f vertEnd = new org.joml.Vector2f(screenPos.x, screenPos.y + size);

        float opacity = this.getPaintOpacity() != 0.0f ? 1.0f : 0.5f;
        org.joml.Vector4f colorVec = GlColors.of(moduleColor, opacity);

        shapeRenderer.drawLine(horizStart, horizEnd, colorVec);
        shapeRenderer.drawLine(vertStart, vertEnd, colorVec);

        Point2D toDisplay = Utility.getPointCoordinatesForDisplay(resultAnchorLocation);
        String coords = StringManager.getString("MODULE_ANCHOR") + " (" + toDisplay.getX() + ", " + toDisplay.getY() + ")";

        // Conditionally paint coords text using drawTextGL (replaces drawWithConditionalOpacity)
        if (ZoomFadedLabel.isVisible()) {
            shapeRenderer.end();
            ZoomFadedLabel.draw(spriteRenderer, projection, coords, resultAnchorLocation);
            shapeRenderer.begin(projection, new Matrix4f());
        }
    }

    @Override
    public List<BaseWorldPoint> getPointsIndex() {
        return points;
    }

    @Override
    public void removePoint(BaseWorldPoint point) {
        super.removePoint(point);
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
    protected Class<ShipCenterPoint> getTypeReference() {
        return ShipCenterPoint.class;
    }
}