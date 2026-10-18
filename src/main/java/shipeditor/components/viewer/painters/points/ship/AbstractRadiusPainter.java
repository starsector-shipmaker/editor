package shipeditor.components.viewer.painters.points.ship;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyPressed;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyReleased;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMouseDragged;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMousePressed;
import shipeditor.communication.events.viewer.points.PointEvents.InstrumentModeChanged;
import shipeditor.communication.events.viewer.points.PointEvents.PointCreationQueued;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.overseers.StaticController;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * Shared behaviour of single-point painters that own a radius circle (collision and shield):
 * instrument mode listening, Ctrl + LMB radius dragging, point repositioning on click
 * and the radius-drag hotkey tracking.
 */
@Log4j2
abstract class AbstractRadiusPainter extends SinglePointPainter {

    private static final int DRAG_RADIUS_HOTKEY = KeyEvent.VK_CONTROL;

    private boolean radiusHotkeyPressed;

    AbstractRadiusPainter(ShipPainter parent) {
        super(parent);
    }

    /**
     * @return the instrument this painter is interactive for.
     */
    protected abstract EditorInstrument getRadiusInstrument();

    /**
     * @return the point owning the radius, or null when not initialized yet.
     */
    protected abstract BaseWorldPoint getRadiusPoint();

    /**
     * Dispatches the undoable edit changing the radius of the owned point.
     */
    protected abstract void postRadiusChanged(float radius);

    private void updateRadiusFromMouseEvent(MouseEvent me) {
        BaseWorldPoint radiusPoint = this.getRadiusPoint();
        if (radiusPoint == null) return;
        AffineTransform screenToWorld = StaticController.getScreenToWorld();
        Point2D transformed = screenToWorld.transform(me.getPoint(), null);
        if (ControlPredicates.isCursorSnappingEnabled()) {
            transformed = screenToWorld.transform(StaticController.getAdjustedCursor(), null);
        }
        Point2D pointPosition = radiusPoint.getPosition();
        float radius = (float) pointPosition.distance(transformed);
        float result = radius;
        if (ControlPredicates.isCursorSnappingEnabled()) {
            result = Math.round(radius * 2) / 2.0f;
        }
        log.debug("Radius drag on {}: {}", getRadiusInstrument(), result);
        this.postRadiusChanged(result);
    }

    protected final void initModeListening() {
        BusEventListener modeListener = event -> {
            if (event instanceof InstrumentModeChanged checked) {
                EditorInstrument editorInstrument = this.getRadiusInstrument();
                this.setInteractionEnabled(checked.newMode() == editorInstrument);
                EventBus.publish(new InstrumentRepaintQueued(editorInstrument));
            }
        };
        EventBus.subscribe(this, modeListener);

        // Adjust radius on Ctrl + LMB (press or drag)
        BusEventListener rawMouseDraggedListener = event -> {
            if (event instanceof ViewerRawMouseDragged checked && isInteractionEnabled()) {
                MouseEvent me = checked.mouseEvent();
                if ((me.isControlDown() || radiusHotkeyPressed) && javax.swing.SwingUtilities.isLeftMouseButton(me)) {
                    updateRadiusFromMouseEvent(me);
                }
            }
        };
        EventBus.subscribe(this, rawMouseDraggedListener);

        BusEventListener rawMousePressedListener = event -> {
            if (event instanceof ViewerRawMousePressed checked && isInteractionEnabled()) {
                MouseEvent me = checked.mouseEvent();
                if ((me.isControlDown() || radiusHotkeyPressed) && javax.swing.SwingUtilities.isLeftMouseButton(me)) {
                    updateRadiusFromMouseEvent(me);
                }
            }
        };
        EventBus.subscribe(this, rawMousePressedListener);

        // Set point position on LMB click (without Ctrl)
        BusEventListener pointCreationListener = event -> {
            if (event instanceof PointCreationQueued checked && isInteractionEnabled()) {
                if (radiusHotkeyPressed) return;
                Point2D position = checked.position();
                BaseWorldPoint radiusPoint = this.getRadiusPoint();
                if (radiusPoint != null) {
                    EditDispatch.postPointDragged(radiusPoint, position);
                    radiusPoint.setPosition(position);
                    EventBus.publish(new InstrumentRepaintQueued(this.getRadiusInstrument()));
                    EventBus.publish(new ViewerRepaintQueued());
                }
            }
        };
        EventBus.subscribe(this, pointCreationListener);
    }

    protected final void initHotkeys() {
        EventBus.subscribe(this, event -> {
            if (!this.getParentLayer().isLayerActive()) {
                return;
            }
            if (event instanceof ViewerRawKeyPressed pressedEvent) {
                int keyCode = pressedEvent.keyEvent().getKeyCode();
                if (keyCode == DRAG_RADIUS_HOTKEY) {
                    this.radiusHotkeyPressed = true;
                    EventBus.publish(new ViewerRepaintQueued());
                }
            } else if (event instanceof ViewerRawKeyReleased releasedEvent) {
                int keyCode = releasedEvent.keyEvent().getKeyCode();
                if (keyCode == DRAG_RADIUS_HOTKEY) {
                    this.radiusHotkeyPressed = false;
                    EventBus.publish(new ViewerRepaintQueued());
                }
            }
        });
    }

}
