package shipeditor.components.viewer.control;

import shipeditor.components.viewer.ViewerEnums.PointSelectionMode;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMousePressed;
import shipeditor.communication.events.viewer.control.ControlEvents.FeatureInstallQueued;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerCursorMoved;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMouseMoved;
import shipeditor.communication.events.viewer.control.ControlEvents.LayerAnchorDragged;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMouseDragged;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerRotationQueued;
import shipeditor.communication.events.viewer.points.PointEvents.PointDragQueued;
import shipeditor.components.viewer.PrimaryViewer;
import shipeditor.components.viewer.ViewerDropReceiver;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.Utility;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.slots.WeaponSlotClipboard;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;

import javax.swing.Timer;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import shipeditor.communication.events.components.ComponentEvents.DeleteButtonPressed;
import shipeditor.communication.events.viewer.points.PointEvents.PointCreationQueued;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectedConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.PointRemoveQueued;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerMouseReleased;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerZoomChanged;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerTransformChanged;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRotationToggled;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRotationSet;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerTransformsReset;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerTransformRotated;

/**
 * * should not publish a plethora of different events that are opinionated as
 * to what their receivers should do.
 * Instead, its sole purpose should be collecting input control data and
 * publishing it on event bus;
 * Interested classes like painters and viewer entities should listen for that
 * input.
 */

@SuppressWarnings({ "OverlyCoupledClass", "OverlyComplexClass" })
@Log4j2
@SuppressFBWarnings({ "EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP" })
public final class LayerViewerControls implements ViewerControl {

    private final PrimaryViewer parentViewer;

    @Getter
    private boolean rotationEnabled;

    /**
     * Previous mouse position
     */
    private final Point previousPoint = new Point();

    /**
     * Position where the mouse was previously pressed
     */
    private final Point pressPoint = new Point();

    private boolean pointDragActive = false;
    private boolean handCursorActive = false;
    private boolean marqueeSelectionActive = false;
    private Point marqueeStartPoint = null;
    private Point marqueeEndPoint = null;

    public boolean isMarqueeSelectionActive() {
        return this.marqueeSelectionActive;
    }

    public Point getMarqueeStartPoint() {
        return this.marqueeStartPoint;
    }

    public Point getMarqueeEndPoint() {
        return this.marqueeEndPoint;
    }

    /**
     * Used for layer dragging functionality.
     */
    private final Point layerDragPoint = new Point();

    private static final int LAYER_DRAG_HOTKEY = KeyEvent.VK_SHIFT;

    @Getter
    private Point mousePoint = new Point();

    @Getter
    private double zoomLevel = 1;

    @Getter
    private double rotationDegree;

    private final SmoothZoomHandler zoomHandler = new SmoothZoomHandler();

    /**
     * @param parent Viewer which is manipulated via this instance of controls
     *               class.
     */
    private LayerViewerControls(PrimaryViewer parent) {
        this.parentViewer = parent;
        this.rotationEnabled = true;
        this.initListeners();
        this.initKeystrokeListener();
    }



    /**
     * @param parent Viewer which is manipulated via this instance of controls
     *               class.
     * @return instance of controls via factory method.
     */
    public static LayerViewerControls create(PrimaryViewer parent) {
        return new LayerViewerControls(parent);
    }

    private void initListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof ViewerTransformsReset) {
                this.setZoomLevel(1);
                this.rotationDegree = 0;
                StaticController.setRotationRadians(0);
                EventBus.publish(new ViewerTransformRotated());
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof ViewerRotationToggled checked) {
                this.rotationEnabled = checked.isSelected();
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof ViewerRotationSet checked) {
                this.rotateExact(checked.degrees());
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof shipeditor.communication.events.viewer.control.ControlEvents.CycleWeaponFrameQueued) {
                LayerPainter active = parentViewer.getSelectedLayer();
                if (active instanceof shipeditor.components.viewer.layers.ship.ShipPainter shipPainter) {
                    shipPainter.cycleWeaponFrames();
                    EventBus.publish(new ViewerRepaintQueued());
                } else if (active instanceof shipeditor.components.viewer.layers.weapon.WeaponPainter weaponPainter) {
                    weaponPainter.cycleNextFrame();
                    EventBus.publish(new ViewerRepaintQueued());
                }
            }
        });
    }

    private void initKeystrokeListener() {
        EventBus.subscribe(this, event -> {
            switch (event.getClass().getSimpleName()) {
                case "ViewerRawKeyPressed" -> {
                    var pressedEvent = (shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyPressed) event;
                    int keyCode = pressedEvent.keyEvent().getKeyCode();
                    boolean isCtrlDown = pressedEvent.keyEvent().isControlDown();
                    java.awt.Component focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    boolean isTextFieldFocused = focusOwner instanceof javax.swing.text.JTextComponent;

                    if (keyCode == LAYER_DRAG_HOTKEY) {
                        this.handCursorActive = false;
                        this.parentViewer.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        this.parentViewer.setRepaintQueued();
                    } else if (!isTextFieldFocused) {
                        KeyEvent keyEvent = pressedEvent.keyEvent();
                        boolean modifierDown = isCtrlDown || keyEvent.isAltDown() || keyEvent.isMetaDown();
                        CanvasInteractionPolicy.KeyAction keyAction = CanvasInteractionPolicy.resolveKey(
                                StaticController.getEditorMode(), keyCode, keyEvent.isShiftDown(),
                                modifierDown, this.isCanvasFocused(focusOwner));
                        if (keyAction != CanvasInteractionPolicy.KeyAction.NONE) {
                            log.debug("Key {} resolved to canvas action {}", KeyEvent.getKeyText(keyCode), keyAction);
                            keyEvent.consume();
                            this.handleCanvasKeyAction(keyAction);
                        } else {
                            switch (keyCode) {
                                case KeyEvent.VK_BACK_SPACE -> {
                                    EventBus.publish(new PointRemoveQueued(null, false));
                                    EventBus.publish(new DeleteButtonPressed());
                                }
                                case KeyEvent.VK_C -> {
                                    if (isCtrlDown && StaticController.getEditorMode() == EditorInstrument.WEAPON_SLOTS) {
                                        WeaponSlotPainter painter = StaticController.getSelectedSlotPainter();
                                        if (painter != null) {
                                            WeaponSlotPoint selected = painter.getSelected();
                                            if (selected != null) {
                                                WeaponSlotClipboard.copy(java.util.List.of(selected));
                                            }
                                        }
                                    }
                                }
                                case KeyEvent.VK_V -> {
                                    if (isCtrlDown && StaticController.getEditorMode() == EditorInstrument.WEAPON_SLOTS) {
                                        WeaponSlotPainter painter = StaticController.getSelectedSlotPainter();
                                        if (painter != null && WeaponSlotClipboard.hasData()) {
                                            Point2D target = StaticController.getFinalWorldCursor();
                                            painter.pasteSlots(WeaponSlotClipboard.getClipboard(), target);
                                        }
                                    }
                                }
                                case KeyEvent.VK_F6 -> {
                                    EventBus.publish(
                                            new shipeditor.communication.events.viewer.control.ControlEvents
                                                    .CycleWeaponFrameQueued());
                                }
                                default -> {}
                            }
                        }
                    }
                }
                case "ViewerRawKeyReleased" -> {
                    var releasedEvent = (shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyReleased) event;
                    int keyCode = releasedEvent.keyEvent().getKeyCode();
                    if (keyCode == LAYER_DRAG_HOTKEY) {
                        this.handCursorActive = false;
                        this.parentViewer.setCursor(Cursor.getDefaultCursor());
                        this.parentViewer.setRepaintQueued();
                    }
                }
                default -> {}
            }
        });
    }

    /**
     * @param focusOwner component that currently owns keyboard focus, may be null.
     * @return whether the focus is inside the viewer (the GL canvas).
     */
    private boolean isCanvasFocused(java.awt.Component focusOwner) {
        return focusOwner != null && javax.swing.SwingUtilities.isDescendingFrom(focusOwner, this.parentViewer);
    }

    private void handleCanvasKeyAction(CanvasInteractionPolicy.KeyAction action) {
        if (!(parentViewer.getSelectedLayer() instanceof shipeditor.components.viewer.layers.ship.ShipPainter shipPainter)
                || shipPainter.isUninitialized()) {
            return;
        }
        WeaponSlotPainter slotPainter = shipPainter.getWeaponSlotPainter();
        if (slotPainter == null) {
            return;
        }
        switch (action) {
            case OPEN_WEAPON_PICKER -> {
                WeaponSlotPoint primary = slotPainter.getSelected();
                if (primary != null && primary.isFittable()) {
                    // The picker is modal; open it outside of the key dispatcher.
                    javax.swing.SwingUtilities.invokeLater(() -> this.pickAndInstallWeapon(slotPainter, primary));
                }
            }
            case CLEAR_WEAPONS -> this.clearSelectedWeapons(slotPainter);
            case NEXT_SLOT -> this.cycleSelectedSlot(slotPainter, false);
            case PREVIOUS_SLOT -> this.cycleSelectedSlot(slotPainter, true);
            default -> { }
        }
    }

    private void clearSelectedWeapons(WeaponSlotPainter slotPainter) {
        WeaponSlotPoint primary = slotPainter.getSelected();
        var layer = StaticController.getActiveLayer();
        if (primary == null || !primary.isFittable()
                || !(layer instanceof shipeditor.components.viewer.layers.ship.ShipLayer shipLayer)) {
            return;
        }
        List<WeaponSlotPoint> targets = this.collectSelectedFittableSlots(slotPainter, primary);
        log.debug("Clearing weapons on {} selected slot(s)", targets.size());
        for (WeaponSlotPoint slot : targets) {
            shipLayer.getFeaturesOverseer().uninstallWeapon(slot);
        }
        EventBus.publish(new ViewerRepaintQueued());
    }

    private void cycleSelectedSlot(WeaponSlotPainter slotPainter, boolean backwards) {
        List<WeaponSlotPoint> candidates = slotPainter.getEligibleForSelection();
        WeaponSlotPoint next = CanvasInteractionPolicy.cycle(candidates, slotPainter.getSelected(), backwards);
        if (next == null) {
            return;
        }
        slotPainter.setSelected(next);
        EventBus.publish(new PointSelectedConfirmed(next));
        EventBus.publish(new ViewerRepaintQueued());
    }

    private boolean isHoveringFittableSlot() {
        if (!(parentViewer.getSelectedLayer() instanceof shipeditor.components.viewer.layers.ship.ShipPainter shipPainter)
                || shipPainter.isUninitialized()) {
            return false;
        }
        WeaponSlotPainter slotPainter = shipPainter.getWeaponSlotPainter();
        if (slotPainter == null) {
            return false;
        }
        for (WeaponSlotPoint slot : slotPainter.getEligibleForSelection()) {
            if (slot.isFittable() && slot.isCursorInBounds()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Shows the hand cursor over fittable slots in VARIANT_WEAPONS mode and restores
     * the default cursor otherwise. Does nothing while Shift owns the cursor (layer dragging).
     */
    private void updateHoverCursor(MouseEvent event) {
        if (event.isShiftDown()) {
            return;
        }
        EditorInstrument mode = StaticController.getEditorMode();
        boolean wantHand = mode == EditorInstrument.VARIANT_WEAPONS
                && CanvasInteractionPolicy.shouldShowHandCursor(mode, this.isHoveringFittableSlot());
        if (wantHand == this.handCursorActive) {
            return;
        }
        this.handCursorActive = wantHand;
        this.parentViewer.setCursor(wantHand ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        EditorInstrument mode = StaticController.getEditorMode();
        CanvasInteractionPolicy.DoubleClickAction action = CanvasInteractionPolicy.resolveDoubleClick(
                mode, javax.swing.SwingUtilities.isLeftMouseButton(e), e.getClickCount());
        if (action != CanvasInteractionPolicy.DoubleClickAction.OPEN_WEAPON_PICKER) {
            return;
        }
        log.debug("Double-click resolved to {} in mode {}", action, mode);
        LayerPainter activePainter = parentViewer.getSelectedLayer();
        if (activePainter instanceof shipeditor.components.viewer.layers.ship.ShipPainter shipPainter) {
            var slotPainter = shipPainter.getWeaponSlotPainter();
            WeaponSlotPoint slot = slotPainter.getSelected();
            if (slot == null || !slot.isCursorInBounds()) {
                BaseWorldPoint closest = slotPainter.findClosestPointToScreen(
                        e.getPoint(), parentViewer.getWorldToScreen(), 20.0);
                if (closest instanceof WeaponSlotPoint candidate) {
                    slot = candidate;
                    slotPainter.setSelected(slot);
                    EventBus.publish(new PointSelectedConfirmed(slot));
                }
            }
            if (slot != null && slot.isFittable()) {
                this.pickAndInstallWeapon(slotPainter, slot);
            }
        }
    }

    /**
     * Opens the weapon picker for the primary slot and installs the chosen weapon.
     * When several fittable slots are selected the weapon is applied to all of them.
     *
     * @param slotPainter painter owning the selection.
     * @param primary     primary selected slot, used to configure the picker.
     */
    private void pickAndInstallWeapon(WeaponSlotPainter slotPainter, WeaponSlotPoint primary) {
        var layer = StaticController.getActiveLayer();
        if (!(layer instanceof shipeditor.components.viewer.layers.ship.ShipLayer shipLayer)) {
            return;
        }
        List<WeaponSlotPoint> targets = this.collectSelectedFittableSlots(slotPainter, primary);
        log.debug("Opening weapon picker for slot {} with {} target slot(s)", primary.getId(), targets.size());
        shipeditor.components.datafiles.entities.WeaponCSVEntry picked =
                shipeditor.utility.components.dialog.DialogUtilities.showWeaponPickerDialog(primary);
        if (picked == null) {
            return;
        }
        if (targets.size() > 1) {
            int installed = shipLayer.getFeaturesOverseer().installWeaponOnSlots(targets, picked);
            log.debug("Installed weapon on {} of {} selected slots", installed, targets.size());
        } else {
            shipLayer.getFeaturesOverseer().installWeapon(primary, picked);
        }
    }

    /**
     * @return selected fittable slots, always containing the primary slot first.
     */
    private List<WeaponSlotPoint> collectSelectedFittableSlots(WeaponSlotPainter slotPainter,
                                                               WeaponSlotPoint primary) {
        List<WeaponSlotPoint> result = new ArrayList<>();
        result.add(primary);
        for (BaseWorldPoint point : slotPainter.getSelectedPoints()) {
            if (point != primary && point instanceof WeaponSlotPoint slot && slot.isFittable()) {
                result.add(slot);
            }
        }
        return result;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Point point = e.getPoint();
        this.pressPoint.setLocation(point);
        // This layer dragging feature took a long time to figure out; careful here in
        // the future.
        // Should any difficulties arise, employ logging liberally.
        if (this.parentViewer.getSelectedLayer() != null) {
            LayerPainter selected = this.parentViewer.getSelectedLayer();
            AffineTransform worldToScreen = this.parentViewer.getTransformWorldToScreen();
            Point2D anchor = selected.getAnchor();
            // Layer anchor needs to be transformed because all mouse events are evaluated
            // in screen coordinates.
            Point2D transformed = worldToScreen.transform(anchor, null);
            this.layerDragPoint.setLocation(e.getX() - transformed.getX(), e.getY() - transformed.getY());
        }

        boolean hoveredOnPoint = false;
        BaseWorldPoint clickedPoint = null;
        AbstractPointPainter clickedPainter = null;
        LayerPainter activePainter = parentViewer.getSelectedLayer();
        if (activePainter != null) {
            for (AbstractPointPainter pointPainter : activePainter.getAllPainters()) {
                if (CanvasInteractionPolicy.canSelectPoints(pointPainter)) {
                    for (BaseWorldPoint p : pointPainter.getPointsIndex()) {
                        if (p.isCursorInBounds()) {
                            clickedPoint = p;
                            clickedPainter = pointPainter;
                            hoveredOnPoint = true;
                            break;
                        }
                    }
                }
                if (hoveredOnPoint) break;
            }
        }
        this.pointDragActive = CanvasInteractionPolicy.canDragPoints(clickedPainter);

        if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
            CanvasInteractionPolicy.PressAction pressAction = CanvasInteractionPolicy.resolveLeftPress(
                    clickedPoint != null, e.isShiftDown(), e.isControlDown(), StaticController.getEditorMode());
            log.debug("Left press resolved to {} (drag allowed: {})", pressAction, this.pointDragActive);
            switch (pressAction) {
                case TOGGLE_SELECTION -> {
                    if (clickedPainter.getSelectedPoints().contains(clickedPoint)) {
                        clickedPainter.removePointFromSelection(clickedPoint);
                    } else {
                        clickedPainter.addPointToSelection(clickedPoint);
                        if (e.isControlDown()) {
                            clickedPainter.setSelected(clickedPoint);
                        }
                    }
                    this.pointDragActive = false; // toggle selection, don't drag
                    EventBus.publish(new PointSelectedConfirmed(clickedPainter.getSelected()));
                    EventBus.publish(new ViewerRepaintQueued());
                }
                case SELECT_POINT -> {
                    if (!clickedPainter.getSelectedPoints().contains(clickedPoint) || clickedPainter.getSelected() != clickedPoint) {
                        clickedPainter.setSelected(clickedPoint);
                    }
                    EventBus.publish(new PointSelectedConfirmed(clickedPoint));
                    EventBus.publish(new ViewerRepaintQueued());
                }
                case START_MARQUEE -> {
                    // Clicked on empty space: save marquee start
                    this.marqueeStartPoint = e.getPoint();
                    this.marqueeEndPoint = e.getPoint();
                }
                default -> { }
            }
        }

        // Only publish creation event if not left clicking on a point
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (clickedPoint == null) {
                this.publishMousePressWithPosition(e, point);
            } else if (e.isControlDown()) {
                AffineTransform screenToWorld = StaticController.getScreenToWorld();
                Point2D position = screenToWorld.transform(point, null);
                if (ControlPredicates.isCursorSnappingEnabled()) {
                    Point2D screenPoint = this.getAdjustedCursor();
                    position = Utility.correctAdjustedCursor(screenPoint, screenToWorld);
                }
                EventBus.publish(new FeatureInstallQueued(position));
            }
        }
        if (ControlPredicates.REMOVE_POINT_PREDICATE.test(e)) {
            EventBus.publish(new PointRemoveQueued(null, false));
        }
        // Publish raw mouse pressed for painters to evaluate their own predicates.
        EventBus.publish(new ViewerRawMousePressed(e));
        if (!ControlPredicates.SELECT_POINT_PREDICATE.test(e))
            return;
        if (ControlPredicates.getSelectionMode() == PointSelectionMode.STRICT) {
            EventBus.publish(new PointSelectQueued(null));
        }
    }

    /**
     * Respective hotkey checks are being done in points painter itself.
     */
    private void publishMousePressWithPosition(MouseEvent event, Point2D point) {
        AffineTransform screenToWorld = StaticController.getScreenToWorld();
        Point2D position = screenToWorld.transform(point, null);
        if (ControlPredicates.isCursorSnappingEnabled()) {
            Point2D screenPoint = this.getAdjustedCursor();
            position = Utility.correctAdjustedCursor(screenPoint, screenToWorld);
        }
        if (!event.isControlDown()) {
            EventBus.publish(new PointCreationQueued(position));
        } else {
            EventBus.publish(new FeatureInstallQueued(position));
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        EventBus.publish(new ViewerMouseReleased());
        this.pointDragActive = false;
        if (this.marqueeSelectionActive && this.marqueeStartPoint != null && this.marqueeEndPoint != null) {
            this.marqueeSelectionActive = false;
            int x = Math.min(this.marqueeStartPoint.x, this.marqueeEndPoint.x);
            int y = Math.min(this.marqueeStartPoint.y, this.marqueeEndPoint.y);
            int w = Math.abs(this.marqueeStartPoint.x - this.marqueeEndPoint.x);
            int h = Math.abs(this.marqueeStartPoint.y - this.marqueeEndPoint.y);
            Rectangle rect = new Rectangle(x, y, w, h);
            
            LayerPainter activePainter = parentViewer.getSelectedLayer();
            if (activePainter != null) {
                AffineTransform worldToScreen = parentViewer.getWorldToScreen();
                boolean cumulative = e.isShiftDown() || e.isControlDown();
                for (AbstractPointPainter pointPainter : activePainter.getAllPainters()) {
                    pointPainter.selectPointsInRect(rect, worldToScreen, cumulative);
                }
            }
        } else if (this.marqueeStartPoint != null) {
            // A clean click on empty space (no drag)
            LayerPainter activePainter = parentViewer.getSelectedLayer();
            if (activePainter != null) {
                for (AbstractPointPainter pointPainter : activePainter.getAllPainters()) {
                    boolean clear = CanvasInteractionPolicy.shouldClearSelectionOnEmptyClick(
                            CanvasInteractionPolicy.canSelectPoints(pointPainter), e.isShiftDown(),
                            e.isControlDown(), e.isAltDown(), javax.swing.SwingUtilities.isLeftMouseButton(e));
                    if (clear) {
                        pointPainter.clearSelection();
                        EventBus.publish(new PointSelectedConfirmed(null));
                    }
                }
            }
        }
        this.marqueeStartPoint = null;
        this.marqueeEndPoint = null;
        this.parentViewer.setRepaintQueued();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (this.handCursorActive) {
            this.handCursorActive = false;
            this.parentViewer.setCursor(Cursor.getDefaultCursor());
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        LayerPainter selected = this.parentViewer.getSelectedLayer();
        AffineTransform screenToWorld = this.parentViewer.getScreenToWorld();
        if (ControlPredicates.TRANSLATE_PREDICATE.test(e)) {
            int dx = x - this.previousPoint.x;
            int dy = y - this.previousPoint.y;
            this.parentViewer.translate(dx, dy);
            EventBus.publish(new ViewerTransformChanged());
        } else if (ControlPredicates.LAYER_MOVE_PREDICATE.test(e)) {
            int dx = x - this.layerDragPoint.x;
            int dy = y - this.layerDragPoint.y;
            if (selected != null) {
                Point2D snappedDifference = this.snapPointToGrid(new Point2D.Double(dx, dy), 1.0f);
                EventBus.publish(new LayerAnchorDragged(screenToWorld, selected, snappedDifference));
            }
        } else if (ControlPredicates.LAYER_ROTATE_PREDICATE.test(e)) {
            if (selected != null) {
                Point2D worldTarget = screenToWorld.transform(e.getPoint(), null);
                EventBus.publish(new LayerRotationQueued(selected, worldTarget));
            }
        } else if (javax.swing.SwingUtilities.isLeftMouseButton(e) && !this.pointDragActive && this.marqueeStartPoint != null) {
            double dist = this.marqueeStartPoint.distance(e.getPoint());
            if (dist > 5) {
                this.marqueeSelectionActive = true;
                this.marqueeEndPoint = e.getPoint();
                this.parentViewer.setRepaintQueued();
            }
        }
        this.previousPoint.setLocation(x, y);
        this.refreshCursorPosition(e);
        if (!this.marqueeSelectionActive) {
            EventBus.publish(new ViewerRawMouseDragged(e));
        }
    }

    // publishAngleRotation and tryRadiusDrag have been removed.
    // Their logic is now owned by the individual painters (WeaponSlotPainter,
    // EngineSlotPainter, ShieldPointPainter, CenterPointPainter) which subscribe
    // to ViewerRawMouseDragged/ViewerRawMouseMoved/ViewerRawMousePressed.

    @Override
    public void mouseMoved(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        this.previousPoint.setLocation(x, y);
        boolean selectionHoldActive = e.isControlDown() && ControlPredicates.isSelectionHoldingEnabled();
        if (ControlPredicates.getSelectionMode() == PointSelectionMode.CLOSEST && !selectionHoldActive &&
                !ControlPredicates.LAYER_SELECT_PREDICATE.test(e)) {
            EventBus.publish(new PointSelectQueued(null));
        }
        this.refreshCursorPosition(e);
        // Publish raw mouse moved for painters to evaluate their own predicates.
        EventBus.publish(new ViewerRawMouseMoved(e));
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double wheelRotation = e.getPreciseWheelRotation();
        if (ControlPredicates.ROTATE_PREDICATE.test(e) && this.rotationEnabled) {
            double toRadians = Math.toRadians(wheelRotation);
            double resultRadians = toRadians * ControlPredicates.ROTATION_SPEED;
            rotateViewer(resultRadians);
            this.rotationDegree -= Math.toDegrees(resultRadians);
            if (this.rotationDegree >= 360) {
                this.rotationDegree -= 360;
            }
            rotationDegree = (rotationDegree + 360) % 360;
            StaticController.updateViewerRotation(-resultRadians, rotationDegree);
            EventBus.publish(new ViewerTransformRotated());
        } else {
            double speed = ControlPredicates.ZOOMING_SPEED * 0.10d;
            double d = Math.pow(1 + speed, -wheelRotation) - 1;
            double factor = 1.0 + d;
            double max = ControlPredicates.MAXIMUM_ZOOM;
            double min = ControlPredicates.MINIMUM_ZOOM;
            int x = e.getX();
            int y = e.getY();
            if (this.zoomLevel * factor >= max) {
                this.setZoomAtLimit(x, y, max);
            } else if (this.zoomLevel * factor <= min) {
                this.setZoomAtLimit(x, y, min);
            } else {
                zoomHandler.startZoom(x, y, this.zoomLevel * factor);
            }
        }
        this.refreshCursorPosition(e);
    }

    private void setZoomAtLimit(int x, int y, double limit) {
        zoomHandler.startZoom(x, y, limit);
    }

    @Override
    public void setZoomExact(double level) {
        Point2D viewerMidPoint = parentViewer.getViewerMidpoint();
        zoomHandler.startZoom(viewerMidPoint.getX(), viewerMidPoint.getY(), level);
    }

    private void setZoomLevel(double level) {
        this.zoomLevel = level;
        StaticController.setZoomLevel(level);
        EventBus.publish(new ViewerZoomChanged());
    }

    public void rotateExact(double desiredDegrees) {
        double desiredRadians = Math.toRadians(desiredDegrees);
        double current = StaticController.getRotationRadians();
        double radiansChange = current - desiredRadians;
        rotateViewer(radiansChange);
        this.rotationDegree = desiredDegrees;
        StaticController.updateViewerRotation(-radiansChange, desiredDegrees);
        EventBus.publish(new ViewerTransformRotated());
    }

    private void rotateViewer(double angleRadians) {
        Point2D midpoint = parentViewer.getViewerMidpoint();
        this.parentViewer.rotate(midpoint.getX(), midpoint.getY(), angleRadians);
    }

    public Point2D getAdjustedCursor() {
        Point mouse = new Point(this.getMousePoint());
        return this.snapPointToGrid(mouse, 2.0f);
    }

    /**
     * @param input           Point that will be snapped to grid.
     * @param snappingDivisor value that will determine the size of snapping grid.
     *                        E.g. value of 2.0f means position snapping to 0.5
     *                        scaled pixel, while 1.0f will snap to the whole pixel.
     * @return Snapped point instance.
     */
    private Point2D snapPointToGrid(Point2D input, float snappingDivisor) {
        AffineTransform worldToScreen = parentViewer.getWorldToScreen();
        Point2D anchor = worldToScreen.transform(new Point(0, 0), null);
        // Calculate cursor position relative to anchor.
        double scale = this.zoomLevel;
        double cursorRelX = (input.getX() - anchor.getX()) / scale;
        double cursorRelY = (input.getY() - anchor.getY()) / scale;
        // Align cursor position to nearest 0.5 scaled pixel.
        double alignedCursorRelX = Math.round(cursorRelX * snappingDivisor) / snappingDivisor;
        double alignedCursorRelY = Math.round(cursorRelY * snappingDivisor) / snappingDivisor;
        // Calculate cursor position in scaled pixels.
        double cursorX = (anchor.getX() + alignedCursorRelX * scale);
        double cursorY = (anchor.getY() + alignedCursorRelY * scale);
        input.setLocation(cursorX, cursorY);
        return input;
    }

    private void updateHoverStatesForAllPainters() {
        LayerPainter activePainter = parentViewer.getSelectedLayer();
        if (activePainter != null) {
            AffineTransform worldToScreen = parentViewer.getWorldToScreen();
            for (AbstractPointPainter pointPainter : activePainter.getAllPainters()) {
                pointPainter.updateHoverStates(this.mousePoint, worldToScreen);
            }
        }
    }

    @Override
    public void refreshCursorPosition(MouseEvent event) {
        this.mousePoint = event.getPoint();
        AffineTransform screenToWorld = StaticController.getScreenToWorld();
        Point2D adjusted = this.getAdjustedCursor();
        Point2D corrected = Utility.correctAdjustedCursor(adjusted, screenToWorld);
        EventBus.publish(new ViewerCursorMoved(this.mousePoint, adjusted, corrected));
        this.updateHoverStatesForAllPainters();
        this.updateHoverCursor(event);
        if (ControlPredicates.SELECT_POINT_PREDICATE.test(event)) {
            Point2D cursor = mousePoint;
            if (ControlPredicates.isCursorSnappingEnabled()) {
                cursor = adjusted;
            }
            EventBus.publish(new PointDragQueued(screenToWorld, cursor));
        } else if (ControlPredicates.LAYER_SELECT_PREDICATE.test(event)) {
            this.tryMouseLayerSelection(mousePoint);
        }
        updateViewerCursorState();
        this.parentViewer.setRepaintQueued();
    }

    private void tryMouseLayerSelection(Point2D targetPoint) {
        LayerManager layerManager = parentViewer.getLayerManager();
        java.util.List<ViewerLayer> layers = layerManager.getLayers();

        if (layers.size() > 1) {
            ViewerLayer closestLayer = null;
            double closestDistance = Double.MAX_VALUE;

            Point2D cachedTransformedCenter = new Point2D.Double();
            AffineTransform worldToScreen = parentViewer.getWorldToScreen();
            AffineTransform transformCache = new AffineTransform();

            Point2D spriteCenterCached = new Point2D.Double();

            for (ViewerLayer layer : layers) {
                LayerPainter painter = layer.getPainter();
                AffineTransform transform = painter.getWithRotation(worldToScreen, transformCache);

                Point2D layerCenter = painter.getSpriteCenter(spriteCenterCached);
                Point2D transformed = transform.transform(layerCenter, cachedTransformedCenter);

                double checkedDistance = transformed.distance(targetPoint);
                if (checkedDistance < closestDistance) {
                    closestLayer = layer;
                    closestDistance = checkedDistance;
                }
            }

            if (closestLayer != null && !closestLayer.equals(layerManager.getActiveLayer())) {
                layerManager.setActiveLayer(closestLayer);
            }
        }
    }

    private void updateViewerCursorState() {
        Rectangle viewerBounds = parentViewer.getBounds();
        parentViewer.setCursorInViewer(viewerBounds.contains(this.mousePoint));
    }

    @Override
    public void notifyCursorState(Point cursorLocation) {
        this.mousePoint = cursorLocation;
        AffineTransform screenToWorld = StaticController.getScreenToWorld();
        Point2D adjusted = this.getAdjustedCursor();
        Point2D corrected = Utility.correctAdjustedCursor(adjusted, screenToWorld);
        EventBus.publish(new ViewerCursorMoved(this.mousePoint, adjusted, corrected));
        this.updateHoverStatesForAllPainters();

        boolean dragInProgress = ViewerDropReceiver.isDragToViewerInProgress();
        boolean closestMode = ControlPredicates.getSelectionMode() == PointSelectionMode.CLOSEST;
        if (dragInProgress && closestMode) {
            updateViewerCursorState();
            EventBus.publish(new PointSelectQueued(null));
        }
    }

    private final class SmoothZoomHandler {

        private static final int DELAY_MS = 6;
        private static final double SNAP_RATIO = 0.005; // Snap when difference is less than 0.5% of target

        private final Timer zoomTimer;
        private double targetZoomLevel;
        private double currentZoomStep = 1.0;

        private double targetX;
        private double targetY;

        private SmoothZoomHandler() {
            zoomTimer = new Timer(DELAY_MS, e -> updateZoom());
        }

        void startZoom(double x, double y, double targetZoom) {
            this.currentZoomStep = LayerViewerControls.this.zoomLevel;

            if (zoomTimer.isRunning()) {
                double ratio = targetZoom / LayerViewerControls.this.zoomLevel;
                this.targetZoomLevel = this.targetZoomLevel * ratio;
            } else {
                this.targetZoomLevel = targetZoom;
            }

            this.targetZoomLevel = Math.max(ControlPredicates.MINIMUM_ZOOM,
                    Math.min(ControlPredicates.MAXIMUM_ZOOM, this.targetZoomLevel));
            this.targetX = x;
            this.targetY = y;

            double snapThreshold = this.targetZoomLevel * SNAP_RATIO;
            if (Math.abs(currentZoomStep - targetZoomLevel) < snapThreshold) {
                currentZoomStep = targetZoomLevel;
                setZoomLevel(currentZoomStep);
            } else {
                zoomTimer.start();
            }
        }

        private void updateZoom() {
            // Logarithmic interpolation for a visually uniform, smoother curve
            currentZoomStep *= Math.pow(targetZoomLevel / currentZoomStep, 0.12);
            setZoomLevel(currentZoomStep);

            double snapThreshold = this.targetZoomLevel * SNAP_RATIO;
            if (Math.abs(currentZoomStep - targetZoomLevel) < snapThreshold) {
                currentZoomStep = targetZoomLevel;
                setZoomLevel(currentZoomStep);
                zoomTimer.stop();
            }
        }

        private void setZoomLevel(double level) {
            double factor = level / LayerViewerControls.this.zoomLevel;
            LayerViewerControls.this.parentViewer.zoom(targetX, targetY, factor, factor);
            LayerViewerControls.this.setZoomLevel(level);
            LayerViewerControls.this.parentViewer.setRepaintQueued();
        }
    }



}
