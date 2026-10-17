package shipeditor.components.viewer.painters.points.ship;

import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.BusEvent;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectedConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.SlotPointsSorted;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.slots.SlotCreationPane;
import shipeditor.components.instrument.ship.slots.WeaponSlotClipboard;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.WorldPoint;
import shipeditor.components.viewer.entities.weapon.SlotData;
import shipeditor.components.viewer.entities.weapon.SlotDrawer;
import shipeditor.components.viewer.entities.weapon.WeaponSlotOverride;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.components.viewer.painters.points.AngledPointPainter;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.overseers.StaticController;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;

import static shipeditor.components.viewer.ViewerEnums.PainterVisibility.ALWAYS_SHOWN;
import static shipeditor.components.viewer.ViewerEnums.PainterVisibility.SHOWN_WHEN_EDITED;
import shipeditor.communication.events.viewer.points.PointEvents.PointCreationQueued;
import shipeditor.communication.events.viewer.points.PointEvents.WeaponSlotInsertedConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued;

/** * Is not supposed to handle launch bays - bays deserialize to different points and painter.*/
@SuppressWarnings({"OverlyCoupledClass", "OverlyComplexClass", "ClassWithTooManyMethods"})
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class WeaponSlotPainter extends AbstractSlotPainter {

    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

    @Getter @Setter
    private List<WeaponSlotPoint> slotPoints;

    private final Map<String, WeaponSlotPoint> slotsByID = new HashMap<>();

    private final SlotDrawer slotMockDrawer = new SlotDrawer(null);

    private final SlotDrawer counterpartMockDrawer = new SlotDrawer(null);

    private transient shipeditor.components.viewer.painters.points.ship.features.InstalledFeature weaponPreviewFeature = null;
    
    private transient shipeditor.components.datafiles.entities.WeaponCSVEntry lastPreviewedEntry = null;

    public WeaponSlotPainter(ShipPainter parent) {
        super(parent);
        this.slotPoints = new ArrayList<>();
    }

    @Override
    public WeaponSlotPoint getSelected() {
        return (WeaponSlotPoint) super.getSelected();
    }

    public boolean hasSlotsOfType(WeaponType type) {
        for (WeaponSlotPoint slotPoint : this.getSlotPoints()) {
            WeaponType slotPointType = slotPoint.getWeaponType();
            if (slotPointType == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected EditorInstrument getInstrumentType() {
        return EditorInstrument.WEAPON_SLOTS;
    }

    private void invalidateCaches() {
        this.slotsByID.clear();
    }

    public WeaponSlotPoint getSlotByID(String slotID) {
        WeaponSlotPoint result = this.slotsByID.get(slotID);
        if (result == null) {
            for (WeaponSlotPoint slotPoint : this.slotPoints) {
                String slotPointId = slotPoint.getId();
                if (slotPointId.equals(slotID)) {
                    result = slotPoint;
                    break;
                }
            }
            if (result != null) {
                this.slotsByID.put(slotID, result);
            }
        }
        return result;
    }

    public boolean isSlotDecorative(String slotID) {
        WeaponSlotPoint slotPoint = this.getSlotByID(slotID);
        return slotPoint != null && slotPoint.isDecorative();
    }

    @Override
    protected void handleSizeOrArcChange(Point2D worldTarget) {
        this.changeArcByTarget(worldTarget);
    }

    @Override
    protected void initSortingListeners() {
        BusEventListener slotSortingListener = event -> {
            if (event instanceof SlotPointsSorted checked) {
                if (!isInteractionEnabled()) return;
                EditDispatch.postSlotsRearranged(this, this.slotPoints, checked.rearranged());
            }
        };
        EventBus.subscribe(this, slotSortingListener);
    }

    @Override
    protected void initInteractionListeners() {
        super.initInteractionListeners();
        
        EventBus.subscribe(this, event -> {
            if (event instanceof shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMousePressed checked) {
                java.awt.event.MouseEvent me = checked.mouseEvent();
                boolean isModuleRightClick = javax.swing.SwingUtilities.isRightMouseButton(me)
                        && StaticController.getEditorMode() == EditorInstrument.VARIANT_MODULES
                        && getParentLayer() != null && getParentLayer().isLayerActive();
                if (!isInteractionEnabled() && !isModuleRightClick) return;
                if (isModuleRightClick) {
                    Point2D worldTarget = computeWorldTarget(me);
                    WeaponSlotPoint closest = (WeaponSlotPoint) findClosestPoint(worldTarget);
                    
                    if (closest != null && closest.getWeaponType() == WeaponType.STATION_MODULE) {
                        Point2D screenLoc = StaticController.getViewer().getWorldToScreen().transform(closest.getPosition(), null);
                        if (me.getPoint().distanceSq(screenLoc) <= 144.0) {
                            javax.swing.JPopupMenu contextMenu = createModuleContextMenu(closest);
                            contextMenu.show(me.getComponent(), me.getX(), me.getY());
                            me.consume();
                        }
                    }
                }
            }
        });
    }

    private javax.swing.JPopupMenu createModuleContextMenu(WeaponSlotPoint slotPoint) {
        return WeaponSlotContextMenu.createModuleContextMenu(slotPoint, this.getParentLayer().getActiveVariant());
    }

    @Override
    public ShipPainter getParentLayer() {
        return (ShipPainter) super.getParentLayer();
    }

    @Override
    protected void handleCreation(PointCreationQueued event) {
        if (!isCreationHotkeyPressed()) return;
        WeaponSlotCreationController.handleCreation(this, event.position());
    }

    public void pasteSlots(List<WeaponSlotClipboard.CopiedSlotData> copiedSlots, Point2D targetPosition) {
        WeaponSlotCreationController.pasteSlots(this, copiedSlots, targetPosition);
    }

    @Override
    protected void selectPointClosest() {
        Point2D cursor = StaticController.getCorrectedCursor();

        ShipPainter layer = this.getParentLayer();
        boolean insideSprite = false;
        if (layer != null) {
            insideSprite = layer.isWorldCursorInsideSprite(cursor);
        }

        BaseWorldPoint toSelect = null;
        if (insideSprite) {
            toSelect = super.findClosestPoint(cursor);
        }

        WorldPoint selectedPoint = this.getSelected();
        if (selectedPoint == toSelect) return;

        if (selectedPoint != null) {
            selectedPoint.setPointSelected(false);
        }
        this.setSelected(toSelect);
        if (toSelect != null) {
            toSelect.setPointSelected(true);
        }
        EventBus.publish(new PointSelectedConfirmed(toSelect));
        EventBus.publish(new ViewerRepaintQueued());
    }

    public String generateUniqueSlotID() {
        return this.generateUniqueSlotID("WS");
    }

    private String generateUniqueSlotID(String prefix) {
        ShipPainter parentLayer = getParentLayer();
        return parentLayer.generateUniqueSlotID(prefix);
    }

    @Override
    public BaseWorldPoint getMirroredCounterpart(WorldPoint inputPoint) {
        if (!(inputPoint instanceof WeaponSlotPoint inputSlot)) {
            return super.getMirroredCounterpart(inputPoint);
        }
        Point2D pointPosition = inputSlot.getPosition();
        Point2D counterpartPosition = this.createCounterpartPosition(pointPosition);
        double threshold = ControlPredicates.getMirrorPointLinkageTolerance();

        WeaponSlotPoint bestMatch = null;
        double bestDist = Double.MAX_VALUE;

        for (WeaponSlotPoint slot : this.getPointsIndex()) {
            if (slot == inputSlot) {
                continue;
            }
            if (slot.getWeaponType() != inputSlot.getWeaponType()) {
                continue;
            }
            if (slot.getWeaponSize() != inputSlot.getWeaponSize()) {
                continue;
            }

            double dist = counterpartPosition.distance(slot.getPosition());
            if (dist <= threshold && dist < bestDist) {
                bestDist = dist;
                bestMatch = slot;
            }
        }
        return bestMatch;
    }

    private Set<WeaponSlotPoint> getSlotsWithCounterparts(Iterable<WeaponSlotPoint> slots) {
        Set<WeaponSlotPoint> resultSet = new HashSet<>();
        for (WeaponSlotPoint point : slots) {
            resultSet.add(point);

            boolean mirrorMode = ControlPredicates.isMirrorModeEnabled();
            BaseWorldPoint mirroredCounterpart = getMirroredCounterpart(point);
            if (mirrorMode && mirroredCounterpart instanceof WeaponSlotPoint checkedSlot) {
                resultSet.add(checkedSlot);
            }
        }
        return resultSet;
    }

    public void changeSlotsIDWithMirrorCheck(String inputIDText, Iterable<WeaponSlotPoint> slots) {
        ShipPainter parentLayer = getParentLayer();
        Set<String> existingIDs = parentLayer.getAllSlotIDs();
        if (SettingsManager.isNumericSuffixesForSlotsEnabled()) {
            Collection<WeaponSlotPoint> slotsWithCounterparts = this.getSlotsWithCounterparts(slots);
            for (WeaponSlotPoint slot : slotsWithCounterparts) {
                String newID = inputIDText;
                if (inputIDText.isEmpty()) {
                    newID = parentLayer.generateUniqueSlotID("WS", existingIDs);
                } else if (SettingsManager.isNumericSuffixesForSlotsEnabled()) {
                    newID = parentLayer.generateUniqueSlotID(inputIDText, existingIDs);
                }
                EditDispatch.postSlotIDChanged(slot, newID);
            }
        } else {
            for (WeaponSlotPoint slot : slots) {
                String newID = inputIDText;
                if (newID.isEmpty()) {
                    newID = parentLayer.generateUniqueSlotID("WS", existingIDs);
                }
                if (!parentLayer.isGeneratedIDUnassigned(newID)) {
                    shipeditor.utility.components.dialog.DialogHelper.showDuplicateIDError();
                    continue;
                }

                boolean mirrorMode = ControlPredicates.isMirrorModeEnabled();
                BaseWorldPoint mirroredCounterpart = getMirroredCounterpart(slot);
                if (mirrorMode && mirroredCounterpart instanceof WeaponSlotPoint checkedSlot) {
                    String mirroredID = newID + " [Mirrored]";
                    EditDispatch.postSlotIDChanged(checkedSlot, mirroredID);
                }

                EditDispatch.postSlotIDChanged(slot, newID);
            }
        }
    }

    public void changeSlotsTypeWithMirrorCheck(WeaponType inputType, Iterable<WeaponSlotPoint> slots) {
        Collection<WeaponSlotPoint> slotsWithCounterparts = this.getSlotsWithCounterparts(slots);
        for (WeaponSlotPoint slot : slotsWithCounterparts) {
            slot.changeSlotType(inputType);
        }
    }

    public void changeSlotsMountWithMirrorCheck(WeaponMount inputMount, Iterable<WeaponSlotPoint> slots) {
        Collection<WeaponSlotPoint> slotsWithCounterparts = this.getSlotsWithCounterparts(slots);
        for (WeaponSlotPoint slot : slotsWithCounterparts) {
            slot.changeSlotMount(inputMount);
        }
    }

    public void changeSlotsSizeWithMirrorCheck(WeaponSize inputSize, Iterable<WeaponSlotPoint> slots) {
        Collection<WeaponSlotPoint> slotsWithCounterparts = this.getSlotsWithCounterparts(slots);
        for (WeaponSlotPoint slot : slotsWithCounterparts) {
            slot.changeSlotSize(inputSize);
        }
    }

    private void changeArcByTarget(Point2D worldTarget) {
        WorldPoint selected = getSelected();
        if (selected instanceof WeaponSlotPoint checked) {
            double directionAngle = checked.getAngle();
            double targetAngle = AngledPointPainter.getTargetRotation(checked, worldTarget);

            double angleDifference = targetAngle - directionAngle;
            // Normalize the angle difference to the range from -180 to 180 degrees.
            if (angleDifference > 180) {
                angleDifference -= 360;
            } else if (angleDifference < -180) {
                angleDifference += 360;
            }

            // Calculate the arc extent based on the normalized angle difference.
            double arcExtent = Math.abs(angleDifference) * 2;
            this.changeArcWithMirrorCheck(checked, arcExtent);
        }
        else {
            throwIllegalPoint();
        }
    }

    public void changeArcWithMirrorCheck(WeaponSlotPoint slotPoint, double arcExtentDegrees) {
        slotPoint.changeSlotArc(arcExtentDegrees);
        actOnCounterpart(point -> point.changeSlotArc(arcExtentDegrees), slotPoint);
    }

    public void changeRenderOrderWithMirrorCheck(WeaponSlotPoint slotPoint, int renderOrder) {
        slotPoint.changeRenderOrder(renderOrder);
        actOnCounterpart(point -> point.changeRenderOrder(renderOrder), slotPoint);
    }

    public void insertPoint(BaseWorldPoint toInsert, int precedingIndex) {
        if (!(toInsert instanceof WeaponSlotPoint checked)) {
            throw new IllegalStateException("Attempted to insert incompatible point to WeaponSlotPainter!");
        }
        slotPoints.add(precedingIndex, checked);
        EventBus.publish(new WeaponSlotInsertedConfirmed(checked, precedingIndex));
        log.trace("Weapon slot inserted to painter: {}", checked);
    }

    public void resetSkinSlotOverride() {
        this.slotPoints.forEach(weaponSlotPoint -> weaponSlotPoint.setSkinOverride(null));
    }

    public void toggleSkinSlotOverride(ShipSkin skin) {
        this.slotPoints.forEach(weaponSlotPoint -> WeaponSlotPainter.setSlotOverrideFromSkin(weaponSlotPoint, skin));
    }

    public static void setSlotOverrideFromSkin(WeaponSlotPoint weaponSlotPoint, ShipSkin skin) {
        if (skin == null || skin.isBase()) {
            weaponSlotPoint.setSkinOverride(null);
            return;
        }
        String slotID = weaponSlotPoint.getId();
        Map<String, WeaponSlotOverride> weaponSlotChanges = skin.getWeaponSlotChanges();
        WeaponSlotOverride matchingOverride = weaponSlotChanges != null ? weaponSlotChanges.get(slotID) : null;
        weaponSlotPoint.setSkinOverride(matchingOverride);
    }

    @Override
    public List<WeaponSlotPoint> getPointsIndex() {
        return slotPoints;
    }

    @Override
    protected void addPointToIndex(BaseWorldPoint point) {
        if (point instanceof WeaponSlotPoint checked) {
            slotPoints.add(checked);
            invalidateCaches();
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    protected void removePointFromIndex(BaseWorldPoint point) {
        if (point instanceof WeaponSlotPoint checked) {
            slotPoints.remove(checked);
            invalidateCaches();
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    public int getIndexOfPoint(BaseWorldPoint point) {
        if (point instanceof WeaponSlotPoint checked) {
            return slotPoints.indexOf(checked);
        } else {
            throwIllegalPoint();
            return -1;
        }
    }

    @Override
    protected void paintDelegates(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        List<WeaponSlotPoint> allPoints = this.getPointsIndex();
        this.paintSlots(spriteRenderer, shapeRenderer, projection, view, allPoints);
    }

    private void paintSlots(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer,
                            Matrix4f projection, Matrix4f view, Iterable<WeaponSlotPoint> slotPointList) {
        slotPointList.forEach(slotPoint -> {
            this.paintDelegate(spriteRenderer, shapeRenderer, projection, view, slotPoint);
            slotPoint.setPaintSizeMultiplier(1);
        });
    }

    @Override
    protected void handleSelectionHighlight() {
        WorldPoint selection = this.getSelected();
        double full = 1.5d;
        if (selection != null && isSlotSelectionEnabled()) {
            this.setSlotPaintSize(selection, full);
            WorldPoint counterpart = this.getMirroredCounterpart(selection);
            if (counterpart != null && ControlPredicates.isMirrorModeEnabled()) {
                this.setSlotPaintSize(counterpart, full);
            }
        }
    }

    @Override
    protected void paintPainterContent(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        if (isInteractionEnabled() && isCreationHotkeyPressed()) {
            Point2D finalWorldCursor = StaticController.getFinalWorldCursor();
            Point2D worldCounterpart = this.createCounterpartPosition(finalWorldCursor);
            boolean mirrorMode = ControlPredicates.isMirrorModeEnabled();

            switch (SlotCreationPane.getMode()) {
                case BY_CLOSEST -> {
                    SlotData closest = (SlotData) findClosestPoint(finalWorldCursor);
                    if (closest != null) {
                        slotMockDrawer.setType(closest.getWeaponType());
                        slotMockDrawer.setMount(closest.getWeaponMount());
                        slotMockDrawer.setSize(closest.getWeaponSize());
                        slotMockDrawer.setAngle(closest.getAngle());
                        slotMockDrawer.setArc(closest.getArc());
                    } else {
                        slotMockDrawer.setType(SlotCreationPane.getDefaultType());
                        slotMockDrawer.setMount(SlotCreationPane.getDefaultMount());
                        slotMockDrawer.setSize(SlotCreationPane.getDefaultSize());
                        slotMockDrawer.setAngle(SlotCreationPane.getDefaultAngle());
                        slotMockDrawer.setArc(SlotCreationPane.getDefaultArc());
                    }
                }
                case BY_DEFAULT -> {
                    slotMockDrawer.setType(SlotCreationPane.getDefaultType());
                    slotMockDrawer.setMount(SlotCreationPane.getDefaultMount());
                    slotMockDrawer.setSize(SlotCreationPane.getDefaultSize());
                    slotMockDrawer.setAngle(SlotCreationPane.getDefaultAngle());
                    slotMockDrawer.setArc(SlotCreationPane.getDefaultArc());
                }
            }

            slotMockDrawer.setPointPosition(finalWorldCursor);
            slotMockDrawer.paintSlotVisuals(spriteRenderer, shapeRenderer, projection, view);
            if (mirrorMode) {
                counterpartMockDrawer.setType(slotMockDrawer.getType());
                counterpartMockDrawer.setMount(slotMockDrawer.getMount());
                counterpartMockDrawer.setSize(slotMockDrawer.getSize());

                double flipAngle = Utility.flipAngle(slotMockDrawer.getAngle());
                counterpartMockDrawer.setAngle(flipAngle);
                counterpartMockDrawer.setArc(slotMockDrawer.getArc());

                counterpartMockDrawer.setPointPosition(worldCounterpart);
                counterpartMockDrawer.paintSlotVisuals(spriteRenderer, shapeRenderer, projection, view);
            }
        }
    }
    private void paintWeaponPreview(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        if (StaticController.getEditorMode() != EditorInstrument.VARIANT_WEAPONS) return;
        
        shipeditor.components.datafiles.entities.WeaponCSVEntry pickedWeapon = null;
        if (StaticController.getActiveLayer() instanceof shipeditor.components.viewer.layers.ship.ShipLayer layer) {
            pickedWeapon = layer.getFeaturesOverseer().getWeaponForInstall();
        }
        if (pickedWeapon == null) return;
        
        Point2D cursor = StaticController.getCorrectedCursor();
        WeaponSlotPoint hoveredSlot = (WeaponSlotPoint) findClosestPoint(cursor);
        if (hoveredSlot == null) return;
        
        if (!WeaponType.isWeaponFitting(hoveredSlot, pickedWeapon)) return;
        
        if (lastPreviewedEntry != pickedWeapon || weaponPreviewFeature == null) {
            shipeditor.components.viewer.layers.weapon.WeaponPainter painter = pickedWeapon.createPainterFromEntry(null, pickedWeapon.getSpecFile());
            weaponPreviewFeature = shipeditor.components.viewer.painters.points.ship.features.InstalledFeature.of(hoveredSlot.getId(), pickedWeapon.getWeaponID(), painter, pickedWeapon);
            lastPreviewedEntry = pickedWeapon;
        }
        
        weaponPreviewFeature.refreshPaintCircumstance(hoveredSlot);
        
        shipeditor.components.viewer.layers.LayerPainter featurePainter = weaponPreviewFeature.getFeaturePainter();
        if (featurePainter != null) {
            featurePainter.setSpriteOpacity(0.5f);
        }
        
        weaponPreviewFeature.paint(spriteRenderer, shapeRenderer, projection, view);
    }

    private void setSlotPaintSize(WorldPoint point, double value) {
        if (point instanceof WeaponSlotPoint checked) {
            checked.setPaintSizeMultiplier(value);
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    protected Class<WeaponSlotPoint> getTypeReference() {
        return WeaponSlotPoint.class;
    }

    @SuppressWarnings("WeakerAccess")
    protected BusEventListener createSelectionListener() {
        return new SharedSelectionListener();
    }

    @Override
    public List<WeaponSlotPoint> getEligibleForSelection() {
        List<WeaponSlotPoint> eligibleForSelection = this.getSlotPoints();
        List<WeaponSlotPoint> result;
        EditorInstrument mode = StaticController.getEditorMode();
        switch (mode) {
            case VARIANT_WEAPONS ->
                    result = eligibleForSelection.stream().filter(a -> a.isFittable()).toList();
            case VARIANT_MODULES ->
                    result = eligibleForSelection.stream().filter(a -> a.isModule()).toList();
            default -> result = eligibleForSelection;
        }
        return result;
    }

    @Override
    public void updateHoverStates(Point2D rawCursor, java.awt.geom.AffineTransform worldToScreen) {
        if (!this.isSlotSelectionEnabled()) {
            for (BaseWorldPoint point : this.getPointsIndex()) {
                point.setCursorInBounds(false);
            }
            return;
        }
        for (BaseWorldPoint point : this.getPointsIndex()) {
            point.updateCursorHitState(rawCursor, worldToScreen);
        }
    }

    private void paintSlotLabels(SpriteRenderer spriteRenderer, Matrix4f projection) {
        double zoomLevel = StaticController.getZoomLevel();
        java.awt.Font font = Utility.getOrbitron(12);

        for (WeaponSlotPoint slot : this.getPointsIndex()) {
            boolean isSelected = slot.isPointSelected();
            boolean isHovered = slot.isCursorInBounds();

            if (isSelected || isHovered || zoomLevel > 22) {
                float alpha = (isSelected || isHovered) ? 1.0f : (float) Math.min(1.0, (zoomLevel - 22.0) / 10.0);
                if (alpha <= 0.05f) continue;

                String text = slot.getId();
                String builtIn = slot.getBuiltInWeaponName();
                if (builtIn != null && (isSelected || isHovered)) {
                    text = text + " [" + builtIn + "]";
                }

                java.awt.Color textColor = isSelected ? java.awt.Color.WHITE : (isHovered ? new java.awt.Color(255, 235, 130) : slot.getWeaponType().getColor());
                Point2D pos = slot.getPosition();
                Point2D labelPos = new Point2D.Double(pos.getX(), pos.getY() + 0.35);
                shipeditor.utility.graphics.opengl.TextRenderer.drawTextGL(spriteRenderer, projection, text, font, textColor, labelPos, alpha);
            }
        }
    }

    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        var visibility = this.getVisibilityMode();
        boolean isRightMode = visibility == ALWAYS_SHOWN || visibility == SHOWN_WHEN_EDITED;
        boolean visibleForRelatedMode = isVisibleForRelatedMode() && isRightMode;
        if (checkVisibility()) {
            shapeRenderer.begin(projection, IDENTITY_MATRIX);
            this.paintPainterContent(spriteRenderer, shapeRenderer, projection, view);
            this.handleSelectionHighlight();
            this.paintDelegates(spriteRenderer, shapeRenderer, projection, view);
            shapeRenderer.end();
            this.paintSlotLabels(spriteRenderer, projection);
            this.paintWeaponPreview(spriteRenderer, shapeRenderer, projection, view);
        } else if (visibleForRelatedMode) {
            shapeRenderer.begin(projection, IDENTITY_MATRIX);
            this.handleSelectionHighlight();
            List<WeaponSlotPoint> activePoints = this.getEligibleForSelection();
            this.paintSlots(spriteRenderer, shapeRenderer, projection, view, activePoints);
            shapeRenderer.end();
            this.paintSlotLabels(spriteRenderer, projection);
            this.paintWeaponPreview(spriteRenderer, shapeRenderer, projection, view);
        }
    }

    private boolean isVisibleForRelatedMode() {
        return isParentLayerActive() && SharedSelectionListener.isRelatedEditorModeActive();
    }

    private boolean isSlotSelectionEnabled() {
        return this.isInteractionEnabled() || isVisibleForRelatedMode();
    }

    private class SharedSelectionListener implements BusEventListener {
        @Override
        public void handleEvent(BusEvent event) {
            if (event instanceof PointSelectQueued checked && WeaponSlotPainter.this.isPointEligible(checked.point())) {
                if (!isSlotSelectionEnabled()) return;
                WeaponSlotPainter.this.handlePointSelectionEvent((BaseWorldPoint) checked.point());
            }
        }

        private static boolean isRelatedEditorModeActive() {
            EditorInstrument editorMode = StaticController.getEditorMode();
            switch (editorMode) {
                case VARIANT_WEAPONS, VARIANT_MODULES -> {
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
    }

}
