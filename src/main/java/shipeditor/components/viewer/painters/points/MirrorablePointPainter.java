package shipeditor.components.viewer.painters.points;

import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyPressed;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyReleased;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.WorldPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.ViewerEnums.PainterVisibility;
import shipeditor.utility.overseers.StaticController;

import java.awt.geom.Point2D;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import shipeditor.communication.events.viewer.points.PointEvents.PointCreationQueued;
import shipeditor.communication.events.viewer.points.PointEvents.InstrumentModeChanged;

@Getter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public abstract class MirrorablePointPainter extends AbstractPointPainter {

    private final LayerPainter parentLayer;

    protected MirrorablePointPainter(LayerPainter parent) {
        this.parentLayer = parent;
        initModeListener();
        this.setInteractionEnabled(StaticController.getEditorMode() == getInstrumentType());
        this.initInteractionListeners();
    }

    protected void initInteractionListeners() {
        BusEventListener slotCreationListener = event -> {
            if (event instanceof PointCreationQueued checked) {
                if (!isInteractionEnabled()) return;
                if (!hasPointAtCoords(checked.position())) {
                    this.handleCreation(checked);
                }
            }
        };
        EventBus.subscribe(this, slotCreationListener);
    }

    /**
     * Tracks a pair of creation hotkeys while the parent layer is active, requesting a repaint on every change.
     * @param firstHotkey key code reported to the setter with {@code isFirstHotkey == true}.
     * @param secondHotkey key code reported to the setter with {@code isFirstHotkey == false}.
     * @param stateSetter receives (isFirstHotkey, pressed) whenever one of the two keys is pressed or released.
     */
    protected final void subscribeHotkeyPair(int firstHotkey, int secondHotkey,
                                             BiConsumer<Boolean, Boolean> stateSetter) {
        EventBus.subscribe(this, event -> {
            if (!this.getParentLayer().isLayerActive()) {
                return;
            }
            if (event instanceof ViewerRawKeyPressed pressedEvent) {
                int keyCode = pressedEvent.keyEvent().getKeyCode();
                if (keyCode == firstHotkey || keyCode == secondHotkey) {
                    stateSetter.accept(keyCode == firstHotkey, true);
                    EventBus.publish(new ViewerRepaintQueued());
                }
            } else if (event instanceof ViewerRawKeyReleased releasedEvent) {
                int keyCode = releasedEvent.keyEvent().getKeyCode();
                if (keyCode == firstHotkey || keyCode == secondHotkey) {
                    stateSetter.accept(keyCode == firstHotkey, false);
                    EventBus.publish(new ViewerRepaintQueued());
                }
            }
        });
    }

    private void initModeListener() {
        BusEventListener modeListener = event -> {
            if (event instanceof InstrumentModeChanged checked) {
                setInteractionEnabled(checked.newMode() == getInstrumentType());
            }
        };
        EventBus.subscribe(this, modeListener);
    }

    protected abstract EditorInstrument getInstrumentType();

    protected abstract void handleCreation(PointCreationQueued event);

    @Override
    protected boolean isParentLayerActive() {
        return this.parentLayer.isLayerActive();
    }

    @Override
    public Point2D createCounterpartPosition(Point2D toMirror) {
        Point2D entityCenter = parentLayer.getEntityCenter();
        double counterpartX = 2 * entityCenter.getX() - toMirror.getX();
        double counterpartY = toMirror.getY(); // Y-coordinate remains the same.
        return new Point2D.Double(counterpartX, counterpartY);
    }

    @Override
    protected boolean checkVisibility() {
        PainterVisibility visibilityMode = getVisibilityMode();
        boolean parentCheck = super.checkVisibility();
        if (visibilityMode == PainterVisibility.SHOWN_WHEN_SELECTED && !parentLayer.isLayerActive()) return false;
        return parentCheck;
    }

    @Override
    public boolean isMirrorable() {
        return true;
    }

    public abstract void insertPoint(BaseWorldPoint toInsert, int precedingIndex);

    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        if (!checkVisibility()) return;

        shapeRenderer.begin(projection, new Matrix4f());
        this.paintPainterContent(spriteRenderer, shapeRenderer, projection, view);
        this.handleSelectionHighlight();
        this.paintDelegates(spriteRenderer, shapeRenderer, projection, view);
        shapeRenderer.end();
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    protected void paintPainterContent(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {}

    protected void handleSelectionHighlight() {
        WorldPoint selection = this.getSelected();
        if (selection != null && isInteractionEnabled()) {
            MirrorablePointPainter.enlargePoint(selection);
            this.actOnCounterpart(MirrorablePointPainter::enlargePoint, selection);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends WorldPoint> void actOnCounterpart(Consumer<T> action, T point) {
        boolean mirrorMode = ControlPredicates.isMirrorModeEnabled();
        BaseWorldPoint mirroredCounterpart = getMirroredCounterpart(point);
        Class<? extends WorldPoint> pointClass = point.getClass();
        if (mirrorMode && pointClass.isInstance(mirroredCounterpart)) {
            T checkedCounterpart = (T) pointClass.cast(mirroredCounterpart);
            action.accept(checkedCounterpart);
        }
    }

    private static void enlargePoint(WorldPoint point) {
        point.setPaintSizeMultiplier(1.5);
    }

    // @Override
    protected void paintDelegates(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        super.paintDelegates(spriteRenderer, shapeRenderer, projection, view);
        for (BaseWorldPoint point : getPointsIndex()) {
            point.setPaintSizeMultiplier(1);
        }
    }

    @Override
    public BaseWorldPoint getMirroredCounterpart(WorldPoint inputPoint) {
        Point2D pointPosition = inputPoint.getPosition();
        Point2D counterpartPosition = this.createCounterpartPosition(pointPosition);
        BaseWorldPoint closestPoint = this.findClosestPoint(counterpartPosition);
        double threshold = ControlPredicates.getMirrorPointLinkageTolerance();

        if (closestPoint != null && closestPoint != inputPoint) {
            double closestDistance = counterpartPosition.distance(closestPoint.getPosition());
            if (closestDistance <= threshold) {
                return closestPoint;
            }
        }

        return null;
    }

}
