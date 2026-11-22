package shipeditor.components.viewer.painters.points.weapon;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMouseDragged;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMouseMoved;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMousePressed;
import shipeditor.communication.events.viewer.points.PointEvents.PointCreationQueued;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.weapon.OffsetPoint;
import shipeditor.components.viewer.layers.weapon.WeaponPainter;
import shipeditor.components.viewer.painters.points.AngledPointPainter;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.overseers.StaticController;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Point painter managing weapon offset (barrel) points for a single mount type.
 * <p>
 * Each {@link WeaponPainter} owns three instances of this painter —
 * one per {@link WeaponMount} (TURRET, HARDPOINT, HIDDEN). Only the instance
 * matching the weapon's current mount mode is painted and interactive.
 */
@Getter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class WeaponOffsetPainter extends AngledPointPainter {

    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

    @Setter
    private List<OffsetPoint> offsetPoints;

    private final WeaponMount designatedType;

    private boolean controlHotkeyPressed;

    @Setter
    private boolean creationHotkeyPressed;

    public WeaponOffsetPainter(WeaponPainter parent, WeaponMount mount) {
        super(parent);
        this.offsetPoints = new ArrayList<>();
        this.designatedType = mount;
    }

    // ---- Index management ----

    @Override
    public List<OffsetPoint> getPointsIndex() {
        return offsetPoints;
    }

    @Override
    protected void addPointToIndex(BaseWorldPoint point) {
        if (point instanceof OffsetPoint checked) {
            offsetPoints.add(checked);
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    protected void removePointFromIndex(BaseWorldPoint point) {
        if (point instanceof OffsetPoint checked) {
            offsetPoints.remove(checked);
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    public int getIndexOfPoint(BaseWorldPoint point) {
        if (point instanceof OffsetPoint checked) {
            return offsetPoints.indexOf(checked);
        } else {
            throwIllegalPoint();
            return -1;
        }
    }

    @Override
    public WeaponPainter getParentLayer() {
        return (WeaponPainter) super.getParentLayer();
    }

    @Override
    protected Class<OffsetPoint> getTypeReference() {
        return OffsetPoint.class;
    }

    // ---- Visibility ----

    /**
     * Offset points are visible whenever their parent layer is active,
     * regardless of which instrument tab is selected.
     */
    @Override
    protected boolean checkVisibility() {
        return isParentLayerActive();
    }

    @Override
    public boolean isInteractionEnabled() {
        WeaponPainter parentLayer = getParentLayer();
        return super.isInteractionEnabled() && parentLayer != null && parentLayer.getMount() == designatedType;
    }

    // ---- Rendering ----

    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer,
                      Matrix4f projection, Matrix4f view) {
        if (!checkVisibility()) return;

        WeaponPainter parentLayer = getParentLayer();
        if (parentLayer.getMount() != designatedType) return;

        shapeRenderer.begin(projection, IDENTITY_MATRIX);
        this.handleSelectionHighlight();
        this.paintDelegates(spriteRenderer, shapeRenderer, projection, view);
        shapeRenderer.end();
    }

    // ---- Hotkeys ----

    @Override
    public void setControlHotkeyPressed(boolean pressed) {
        this.controlHotkeyPressed = pressed;
    }

    @Override
    protected int getControlHotkey() {
        return KeyEvent.VK_ALT;
    }

    @Override
    protected int getCreationHotkey() {
        return KeyEvent.VK_SHIFT;
    }

    // ---- Interaction ----

    @Override
    protected void handlePointSelectionEvent(BaseWorldPoint point) {
        if (this.controlHotkeyPressed) return;
        super.handlePointSelectionEvent(point);
    }

    @Override
    protected void initInteractionListeners() {
        super.initInteractionListeners();

        BusEventListener rawMouseListener = event -> {
            if (!isInteractionEnabled() || !isControlHotkeyPressed()) return;

            MouseEvent me = extractMouseEvent(event);
            if (me != null && ControlPredicates.CHANGE_ANGLE_PREDICATE.test(me)) {
                Point2D worldTarget = computeWorldTarget(me);
                super.changePointAngleByTarget(worldTarget);
            }
        };
        EventBus.subscribe(this, rawMouseListener);
    }

    private static MouseEvent extractMouseEvent(Object event) {
        if (event instanceof ViewerRawMouseDragged checked) {
            return checked.mouseEvent();
        } else if (event instanceof ViewerRawMouseMoved checked) {
            return checked.mouseEvent();
        } else if (event instanceof ViewerRawMousePressed checked) {
            return checked.mouseEvent();
        }
        return null;
    }

    private Point2D computeWorldTarget(MouseEvent me) {
        AffineTransform screenToWorld = StaticController.getScreenToWorld();
        Point2D target = me.getPoint();
        if (ControlPredicates.isRotationRoundingEnabled()) {
            target = StaticController.getAdjustedCursor();
        }
        return screenToWorld.transform(target, null);
    }

    // ---- Instrument & Creation ----

    @Override
    protected EditorInstrument getInstrumentType() {
        return EditorInstrument.WEAPON_OFFSETS;
    }

    @Override
    protected void handleCreation(PointCreationQueued event) {
        if (!isCreationHotkeyPressed()) return;
        OffsetPoint newPoint = new OffsetPoint(event.position(), getParentLayer());
        shipeditor.undo.EditDispatch.postPointAdded(this, newPoint);
    }

    @Override
    public void insertPoint(BaseWorldPoint toInsert, int precedingIndex) {
        if (toInsert instanceof OffsetPoint checked) {
            offsetPoints.add(precedingIndex, checked);
        } else {
            throwIllegalPoint();
        }
    }

}
