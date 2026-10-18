package shipeditor.components.viewer.painters.points.ship;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.jhlabs.image.HSBAdjustFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.EnginePointsSorted;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.WorldPoint;
import shipeditor.components.viewer.entities.engine.EngineDataOverride;
import shipeditor.components.viewer.entities.engine.EnginePoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.representation.ship.EngineStyle;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.objects.Size2D;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.StringConstants;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import shipeditor.communication.events.viewer.points.PointEvents.PointCreationQueued;
import shipeditor.communication.events.viewer.points.PointEvents.EngineInsertedConfirmed;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class EngineSlotPainter extends AbstractSlotPainter {

    @Getter @Setter
    private List<EnginePoint> enginePoints;

    private int previewTextureId = 0;

    private Color lastFlameColor = null;

    public EngineSlotPainter(ShipPainter parent) {
        super(parent);
        this.enginePoints = new ArrayList<>();
    }

    @Override
    public EnginePoint getSelected() {
        return (EnginePoint) super.getSelected();
    }

    @Override
    protected void handleSizeOrArcChange(Point2D worldTarget) {
        this.handleSizeChange(worldTarget);
    }

    @Override
    protected void initSortingListeners() {
        BusEventListener enginesSortingListener = event -> {
            if (event instanceof EnginePointsSorted checked) {
                if (!isInteractionEnabled()) return;
                EditDispatch.postEnginesRearranged(this, this.enginePoints, checked.rearranged());
            }
        };
        EventBus.subscribe(this, enginesSortingListener);
    }

    private void handleSizeChange(Point2D worldTarget) {
        WorldPoint selected = this.getSelected();
        if (selected instanceof EnginePoint checked) {
            Point2D position = checked.getPosition();

            AffineTransform rotateInstance = AffineTransform.getRotateInstance(Math.toRadians(checked.getAngle()),
                    position.getX(), position.getY());
            Point2D rotatedTarget = rotateInstance.transform(worldTarget, null);

            double halfWidth = Math.abs(rotatedTarget.getX() - position.getX());
            double length = Math.abs(rotatedTarget.getY() - position.getY());

            if (ControlPredicates.isCursorSnappingEnabled()) {
                length = Math.round(length * 2.0d) / 2.0d;
                halfWidth = Math.round(halfWidth * 2.0d) / 2.0d;
            }

            double fullWidth = halfWidth * 2.0d;

            double minimumValue = 0.5d;
            if (fullWidth < minimumValue) {
                fullWidth = minimumValue;
            }
            if (length < minimumValue) {
                length = minimumValue;
            }

            this.changeEngineSizeWithMirrorCheck(checked, new Size2D(fullWidth, length));
        }
    }

    public void changeEngineSizeWithMirrorCheck(EnginePoint point, Size2D size) {
        point.changeSize(size);
        actOnCounterpart(enginePoint -> enginePoint.changeSize(size), point);
    }

    public void changeEngineContrailWithMirrorCheck(EnginePoint point, int contrailSize) {
        point.changeContrailSize(contrailSize);
        actOnCounterpart(enginePoint -> enginePoint.changeContrailSize(contrailSize), point);
    }

    public void changeEngineStyleWithMirrorCheck(EnginePoint point, EngineStyle style) {
        point.changeStyle(style);
        actOnCounterpart(enginePoint -> enginePoint.changeStyle(style), point);
    }

    public void resetSkinSlotOverride() {
        this.enginePoints.forEach(enginePoint -> enginePoint.setSkinOverride(null));
    }

    public void toggleSkinSlotOverride(ShipSkin skin) {
        this.enginePoints.forEach(enginePoint -> this.setEngineOverrideFromSkin(enginePoint, skin));
    }

    private void setEngineOverrideFromSkin(EnginePoint enginePoint, ShipSkin skin) {
        if (skin == null || skin.isBase()) {
            enginePoint.setSkinOverride(null);
            return;
        }
        int slotIndex = this.enginePoints.indexOf(enginePoint);
        Map<Integer, EngineDataOverride> engineSlotChanges = skin.getEngineSlotChanges();
        EngineDataOverride matchingOverride = engineSlotChanges != null ? engineSlotChanges.get(slotIndex) : null;
        enginePoint.setSkinOverride(matchingOverride);
    }

    @Override
    protected EditorInstrument getInstrumentType() {
        return EditorInstrument.ENGINES;
    }

    private static EnginePoint createDefaultEngine(Point2D position, ShipPainter parentLayer) {
        log.debug("Creating engine at {} with default dimensions and low tech style", position);
        EnginePoint created = new EnginePoint(position, parentLayer);
        created.setAngle(180);
        created.setLength(10);
        created.setWidth(2);
        created.setContrailSize(12);
        created.setStyleID(StringConstants.LOW_TECH);
        return created;
    }

    @Override
    protected void handleCreation(PointCreationQueued event) {
        if (!isCreationHotkeyPressed()) return;

        ShipPainter parentLayer = (ShipPainter) this.getParentLayer();
        Point2D position = event.position();
        boolean mirrorMode = ControlPredicates.isMirrorModeEnabled();

        EnginePoint created;
        EnginePoint counterpart = null;

        if (enginePoints.isEmpty()) {
            created = createDefaultEngine(position, parentLayer);

        } else {
            EnginePoint closest = (EnginePoint) findClosestPoint(position);
            if (closest != null) {
                created = new EnginePoint(position, parentLayer, closest);
            } else {
                created = createDefaultEngine(position, parentLayer);
            }
        }

        if (mirrorMode) {
            if (getMirroredCounterpart(created) == null) {
                Point2D counterpartPosition = createCounterpartPosition(position);
                counterpart = new EnginePoint(counterpartPosition, parentLayer, created);
                double flipAngle = Utility.flipAngle(counterpart.getAngle());
                counterpart.setAngle(flipAngle);
            }
        }

        EditDispatch.postPointAdded(this, created);
        if (counterpart != null) {
            EditDispatch.postPointAdded(this, counterpart);
        }
    }

    @Override
    protected void paintPainterContent(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        if (isInteractionEnabled() && isCreationHotkeyPressed()) {
            Point2D finalWorldCursor = StaticController.getFinalWorldCursor();
            Point2D worldCounterpart = this.createCounterpartPosition(finalWorldCursor);
            boolean mirrorMode = ControlPredicates.isMirrorModeEnabled();

            Color flameColor = new Color(255, 125, 25);
            double rawAngle = 180;
            double engineWidth = 2;
            double engineLength = 10;

            if (!enginePoints.isEmpty()) {
                EnginePoint closest = (EnginePoint) findClosestPoint(finalWorldCursor);
                if (closest != null) {
                    EngineStyle style = closest.getStyle();
                    if (style != null) {
                        flameColor = style.getEngineColor();
                    }
                    rawAngle = closest.getAngle();
                    engineWidth = closest.getWidth();
                    engineLength = closest.getLength();
                }
            }

            if (previewTextureId == 0 || !flameColor.equals(lastFlameColor)) {
                if (previewTextureId != 0) {
                    org.lwjgl.opengl.GL11.glDeleteTextures(previewTextureId);
                    previewTextureId = 0;
                }
                float[] hue = Color.RGBtoHSB(flameColor.getRed(), flameColor.getGreen(), flameColor.getBlue(), null);
                BufferedImageOp filter = new HSBAdjustFilter(hue[0], hue[1], hue[2]);
                BufferedImage flameColored = filter.filter(EnginePoint.getBaseFlameTexture(), null);
                previewTextureId = shipeditor.utility.graphics.opengl.TextureLoader.loadTexture(flameColored);
                lastFlameColor = flameColor;
            }

            int coreTextureId = EnginePoint.getBaseFlameCoreTextureId();

            shapeRenderer.end();

            EnginePoint.drawRectangleStaticallyGL(shapeRenderer, projection, view, finalWorldCursor,
                    rawAngle, engineWidth, engineLength);
            EnginePoint.drawFlameStaticallyGL(spriteRenderer, projection, view, finalWorldCursor,
                    rawAngle, engineWidth, engineLength, previewTextureId, coreTextureId);

            if (mirrorMode) {
                double flipAngle = Utility.flipAngle(rawAngle);

                EnginePoint.drawRectangleStaticallyGL(shapeRenderer, projection, view, worldCounterpart,
                        flipAngle, engineWidth, engineLength);
                EnginePoint.drawFlameStaticallyGL(spriteRenderer, projection, view, worldCounterpart,
                        flipAngle, engineWidth, engineLength, previewTextureId, coreTextureId);
            }

            shapeRenderer.begin(projection, new Matrix4f());
        }
    }

    @Override
    public List<EnginePoint> getPointsIndex() {
        return enginePoints;
    }

    @Override
    protected void addPointToIndex(BaseWorldPoint point) {
        if (point instanceof EnginePoint checked) {
            enginePoints.add(checked);
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    protected void removePointFromIndex(BaseWorldPoint point) {
        if (point instanceof EnginePoint checked) {
            enginePoints.remove(checked);
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    public int getIndexOfPoint(BaseWorldPoint point) {
        if (point instanceof EnginePoint checked) {
            return enginePoints.indexOf(checked);
        } else {
            throwIllegalPoint();
            return -1;
        }
    }

    @Override
    protected Class<EnginePoint> getTypeReference() {
        return EnginePoint.class;
    }

    @Override
    public void insertPoint(BaseWorldPoint toInsert, int precedingIndex) {
        if (toInsert instanceof EnginePoint checked) {
            enginePoints.add(precedingIndex, checked);
            EventBus.publish(new EngineInsertedConfirmed(checked, precedingIndex));
            log.info("Engine inserted to painter: {}", checked);
        }
        else {
            throwIllegalPoint();
        }

    }

}
