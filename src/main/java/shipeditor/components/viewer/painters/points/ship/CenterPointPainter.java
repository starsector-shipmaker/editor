package shipeditor.components.viewer.painters.points.ship;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.ShipCenterPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.utility.graphics.opengl.TextRenderer;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.communication.events.viewer.points.PointEvents.InstrumentModeChanged;
import shipeditor.communication.events.viewer.points.PointEvents.PointCreationQueued;

/** * Also intended to handle collision radii and their painting.*/
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class CenterPointPainter extends SinglePointPainter {

    private static final float COLLISION_OPACITY = 0.2f;
    private final List<BaseWorldPoint> points = new ArrayList<>();

    @Getter
    private ShipCenterPoint centerPoint;

    @Getter @Setter
    private Point2D moduleAnchorOffset;

    private static final int DRAG_COLLISION_RADIUS_HOTKEY = KeyEvent.VK_CONTROL;
    private boolean collisionRadiusHotkeyPressed;

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

    private void updateCollisionRadiusFromMouseEvent(MouseEvent me) {
        if (this.centerPoint == null) return;
        AffineTransform screenToWorld = StaticController.getScreenToWorld();
        Point2D transformed = screenToWorld.transform(me.getPoint(), null);
        if (ControlPredicates.isCursorSnappingEnabled()) {
            transformed = screenToWorld.transform(StaticController.getAdjustedCursor(), null);
        }
        Point2D centerPointPosition = this.centerPoint.getPosition();
        float radius = (float) centerPointPosition.distance(transformed);
        float result = radius;
        if (ControlPredicates.isCursorSnappingEnabled()) {
            result = Math.round(radius * 2) / 2.0f;
        }
        EditDispatch.postCollisionRadiusChanged(this.centerPoint, result);
    }

    private void initModeListening() {
        BusEventListener modeListener = event -> {
            if (event instanceof InstrumentModeChanged checked) {
                EditorInstrument editorInstrument = EditorInstrument.COLLISION;
                this.setInteractionEnabled(checked.newMode() == editorInstrument);
                EventBus.publish(new InstrumentRepaintQueued(editorInstrument));
            }
        };
        EventBus.subscribe(this, modeListener);

        // Adjust radius on Ctrl + LMB (press or drag)
        BusEventListener rawMouseDraggedListener = event -> {
            if (event instanceof shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMouseDragged checked && isInteractionEnabled()) {
                MouseEvent me = checked.mouseEvent();
                if ((me.isControlDown() || collisionRadiusHotkeyPressed) && javax.swing.SwingUtilities.isLeftMouseButton(me)) {
                    updateCollisionRadiusFromMouseEvent(me);
                }
            }
        };
        EventBus.subscribe(this, rawMouseDraggedListener);

        BusEventListener rawMousePressedListener = event -> {
            if (event instanceof shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMousePressed checked && isInteractionEnabled()) {
                MouseEvent me = checked.mouseEvent();
                if ((me.isControlDown() || collisionRadiusHotkeyPressed) && javax.swing.SwingUtilities.isLeftMouseButton(me)) {
                    updateCollisionRadiusFromMouseEvent(me);
                }
            }
        };
        EventBus.subscribe(this, rawMousePressedListener);

        // Set point position on LMB click (without Ctrl)
        BusEventListener pointCreationListener = event -> {
            if (event instanceof PointCreationQueued checked && isInteractionEnabled()) {
                if (collisionRadiusHotkeyPressed) return;
                Point2D position = checked.position();
                if (this.centerPoint != null) {
                    EditDispatch.postPointDragged(this.centerPoint, position);
                    this.centerPoint.setPosition(position);
                    EventBus.publish(new InstrumentRepaintQueued(EditorInstrument.COLLISION));
                    EventBus.publish(new ViewerRepaintQueued());
                }
            }
        };
        EventBus.subscribe(this, pointCreationListener);
    }

    private void initHotkeys() {
        EventBus.subscribe(this, event -> {
            if (!this.getParentLayer().isLayerActive()) {
                return;
            }
            if (event instanceof shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyPressed pressedEvent) {
                int keyCode = pressedEvent.keyEvent().getKeyCode();
                boolean isCollisionHotkey = (keyCode == DRAG_COLLISION_RADIUS_HOTKEY);
                if (isCollisionHotkey) {
                    this.collisionRadiusHotkeyPressed = true;
                    EventBus.publish(new ViewerRepaintQueued());
                }
            } else if (event instanceof shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyReleased releasedEvent) {
                int keyCode = releasedEvent.keyEvent().getKeyCode();
                boolean isCollisionHotkey = (keyCode == DRAG_COLLISION_RADIUS_HOTKEY);
                if (isCollisionHotkey) {
                    this.collisionRadiusHotkeyPressed = false;
                    EventBus.publish(new ViewerRepaintQueued());
                }
            }
        });
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
        org.joml.Vector4f colorVec = new org.joml.Vector4f(
            moduleColor.getRed() / 255.0f,
            moduleColor.getGreen() / 255.0f,
            moduleColor.getBlue() / 255.0f,
            opacity
        );

        shapeRenderer.drawLine(horizStart, horizEnd, colorVec);
        shapeRenderer.drawLine(vertStart, vertEnd, colorVec);

        Point2D toDisplay = Utility.getPointCoordinatesForDisplay(resultAnchorLocation);
        String coords = StringManager.getString("MODULE_ANCHOR") + " (" + toDisplay.getX() + ", " + toDisplay.getY() + ")";

        // Conditionally paint coords text using drawTextGL (replaces drawWithConditionalOpacity)
        double zoomLevel = StaticController.getZoomLevel();
        if (zoomLevel > 20) {
            float alpha = (float) ((zoomLevel - 20.0) / 20.0);
            alpha = Math.min(alpha, 1.0f);
            Font font = Utility.getOrbitron(14);
            shapeRenderer.end();
            TextRenderer.drawTextGL(spriteRenderer, projection, coords, font, Color.WHITE, resultAnchorLocation, alpha);
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