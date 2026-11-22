package shipeditor.components.viewer.layers;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import shipeditor.utility.graphics.opengl.OpenGLPainter;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.control.ControlEvents.LayerAnchorDragged;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerRotationQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.ViewerLayerRemovalConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.AnchorOffsetQueued;
import shipeditor.components.viewer.control.ControlPredicates;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.undo.UndoOverseer;
import shipeditor.utility.Utility;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.overseers.StaticController;

import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base abstraction for drawing entities (like Ships or Weapons) in the OpenGL viewer.
 *
 * <h2>Coordinate System Contract</h2>
 * <ul>
 *   <li><b>World Space:</b> The canvas coordinate system. Y increases <em>downward</em> (matches AWT
 *       and the OpenGL orthographic projection configured in {@code PrimaryViewer}).</li>
 *   <li><b>Screen Space:</b> Window pixel coordinates after camera zoom/pan transforms.</li>
 * </ul>
 *
 * <h2>Anchor &amp; Center Definitions</h2>
 * This class exposes several coordinate queries. Each serves a distinct purpose:
 * <table>
 *   <tr><th>Method</th><th>Returns</th><th>Use for</th></tr>
 *   <tr><td>{@link #getAnchor()}</td>
 *       <td>Top-left corner of the unrotated sprite rectangle in world space.</td>
 *       <td>Positioning the sprite quad, bounding-box hit tests.</td></tr>
 *   <tr><td>{@link #getSpriteCenter()}</td>
 *       <td>{@code anchor + (width/2, height/2)} — geometric center of the image.</td>
 *       <td>Camera centering, guide crosshair rendering.</td></tr>
 *   <tr><td>{@link #getEntityCenter()}</td>
 *       <td>Logical center defined by the entity data (e.g. ship center point, weapon center offset).</td>
 *       <td>Mirroring, serialization, entity-specific logic.</td></tr>
 *   <tr><td>{@link #getRotationAnchor()}</td>
 *       <td>The world-space pivot point around which the sprite actually rotates.</td>
 *       <td><b>All rotation math</b> — {@code SpriteRenderer}, {@code AffineTransform},
 *           selection highlights, hit detection inverse transforms.</td></tr>
 * </table>
 *
 * <h2>Rotation</h2>
 * Rotation is stored in <b>radians, clockwise-positive</b>, applied around
 * {@link #getRotationAnchor()}. When the rotation changes, all child
 * {@link BaseWorldPoint} positions are explicitly rotated by the delta
 * (see {@link #setRotationRadians(double)}).
 *
 * @see #getRotationTransform()
 * @see #getWithRotation(java.awt.geom.AffineTransform)
 */
@Getter
@SuppressWarnings("ClassWithTooManyMethods")
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public abstract class LayerPainter implements OpenGLPainter {

    private final List<AbstractPointPainter> allPainters;

    /**
     * Top-left corner of the unrotated sprite rectangle in world space.
     * <p>
     * This is <b>not</b> the center or pivot — it is the position where the sprite quad
     * begins drawing. For the rotation pivot, use {@link #getRotationAnchor()}.
     *
     * @see #getRotationAnchor()
     * @see #getSpriteCenter()
     */
    private Point2D anchor = new Point2D.Double(0, 0);

    private float spriteOpacity = 1.0f;

    private double rotationRadians;

    public void setRotationRadians(double newRotation) {
        double delta = newRotation - this.getRotationRadians();

        if (delta != 0.0 && this.allPainters != null && !this.allPainters.isEmpty()) {
            Point2D rotationAnchor = this.getRotationAnchor();
            
            AffineTransform rotationTransform = AffineTransform.getRotateInstance(delta, rotationAnchor.getX(), rotationAnchor.getY());

            for (AbstractPointPainter pointPainter : this.allPainters) {
                for (Object pointObj : pointPainter.getPointsIndex()) {
                    if (pointObj instanceof shipeditor.components.viewer.entities.BaseWorldPoint point) {
                        Point2D oldPos = point.getPosition();
                        Point2D newPos = rotationTransform.transform(oldPos, null);
                        point.setPosition(newPos.getX(), newPos.getY());
                    }
                    if (pointObj instanceof shipeditor.components.viewer.entities.AngledPoint angledPoint) {
                        angledPoint.setAngle(angledPoint.getAngle() - Math.toDegrees(delta));
                    }
                }

                if (pointPainter instanceof shipeditor.components.viewer.painters.points.ship.LaunchBayPainter bayPainter) {
                    for (shipeditor.components.viewer.entities.bays.LaunchBay bay : bayPainter.getBaysList()) {
                        bay.setAngle(bay.getAngle() - Math.toDegrees(delta));
                    }
                }
            }
        }
        
        this.rotationRadians = newRotation;
    }

    private final ViewerLayer parentLayer;

    @Setter
    private Sprite sprite;

    @Setter(AccessLevel.PROTECTED)
    private boolean uninitialized = true;

    @Setter
    private boolean shouldDrawPainter = true;

    private final Rectangle2D.Double spriteRectCache = new Rectangle2D.Double();
    
    private final Point2D.Double unrotatedCache = new Point2D.Double();

    private final org.joml.Vector2f paintPosition = new org.joml.Vector2f();
    private final org.joml.Vector2f paintSize = new org.joml.Vector2f();
    private final org.joml.Vector2f paintRotAnchor = new org.joml.Vector2f();
    private final org.joml.Vector4f paintColor = new org.joml.Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    protected LayerPainter(ViewerLayer layer) {
        this.parentLayer = layer;
        this.allPainters = new ArrayList<>();
        this.initLayerListeners();
    }

    public Dimension getSpriteSize() {
        Sprite spriteContainer = getSprite();
        if (spriteContainer != null) {
            var spriteImage = spriteContainer.getImage();
            if (spriteImage != null) {
                return new Dimension(spriteImage.getWidth(), spriteImage.getHeight());
            }
        }
        return new Dimension(0, 0);
    }

    public BufferedImage getSpriteImage() {
        Sprite currentSprite = getSprite();
        return currentSprite != null ? currentSprite.getImage() : null;
    }

    private void initLayerListeners() {
        BusEventListener removalListener = event -> {
            if (event instanceof ViewerLayerRemovalConfirmed checked) {
                if (checked.removed() != this.getParentLayer())
                    return;
                this.cleanupForRemoval();
            }
        };
        EventBus.subscribe(this, removalListener);
        BusEventListener anchorDragListener = event -> {
            if (event instanceof LayerAnchorDragged checked && checked.selected() == this) {
                AffineTransform screenToWorld = checked.screenToWorld();
                Point2D difference = checked.difference();
                Point2D wP = screenToWorld.transform(difference, null);
                double roundedX = Math.round(wP.getX() * 2) / 2.0;
                double roundedY = Math.round(wP.getY() * 2) / 2.0;
                Point2D corrected = new Point2D.Double(roundedX, roundedY);
                updateAnchorOffset(corrected);
            }
        };
        EventBus.subscribe(this, anchorDragListener);

        BusEventListener rotationListener = event -> {
            if (event instanceof LayerRotationQueued checked) {
                if (checked.layer() != this)
                    return;
                this.rotateToTarget(checked.worldTarget());
            }
        };
        EventBus.subscribe(this, rotationListener);
    }

    /**
     * Returns the logical center of this entity as defined by its data model.
     * <p>
     * For ships, this is the ship center point ({@code ShipCenterPoint.getPosition()}).
     * For weapons, this delegates to the weapon center offset.
     * <p>
     * <b>Do not use for rotation math.</b> Use {@link #getRotationAnchor()} instead.
     * This method is primarily for mirroring operations and serialization.
     *
     * @return the entity's logical center in world space.
     * @see #getRotationAnchor()
     * @see #getSpriteCenter()
     */
    public abstract Point2D getEntityCenter();

    public void setAnchor(Point2D inputAnchor) {
        Point2D oldAnchor = this.getAnchor();

        Point2D difference = new Point2D.Double(oldAnchor.getX() - inputAnchor.getX(),
                oldAnchor.getY() - inputAnchor.getY());
        EventBus.publish(new AnchorOffsetQueued(this, difference));

        this.anchor = inputAnchor;
    }

    /**
     * Is expected to contain a setSprite() call as well.
     * 
     * @param updated new sprite to be used.
     */
    public void reconfigureSpriteCircumstance(Sprite updated) {
        BufferedImage previous = this.getSpriteImage();
        var spriteHeight = previous.getHeight();

        BufferedImage updatedImage = updated.getImage();
        var updatedHeight = updatedImage.getHeight();

        int heightDifference = spriteHeight - updatedHeight;

        var painterList = this.getAllPainters();
        painterList.forEach(abstractPointPainter -> {
            List<? extends BaseWorldPoint> pointsIndex = abstractPointPainter.getPointsIndex();
            pointsIndex.forEach((Consumer<BaseWorldPoint>) baseWorldPoint -> {
                Point2D oldPosition = baseWorldPoint.getPosition();
                baseWorldPoint.setPosition(oldPosition.getX(), oldPosition.getY() - heightDifference);
            });
        });

        this.setSprite(updated);
    }

    public boolean isLayerActive() {
        ViewerLayer layer = this.getParentLayer();
        if (layer == null)
            return false;
        return StaticController.getActiveLayer() == layer;
    }

    public void cleanupForRemoval() {
        cleanupPointPainters();
        EventBus.unsubscribeByParent(this);
        UndoOverseer.cleanupRemovedLayer(this);
    }

    protected void cleanupPointPainters() {
        List<AbstractPointPainter> painters = this.getAllPainters();
        for (AbstractPointPainter pointPainter : painters) {
            pointPainter.cleanupPointPainter();
        }
        this.allPainters.clear();
    }

    public void setSpriteOpacity(float opacity) {
        if (opacity < 0.0f) {
            this.spriteOpacity = 0.0f;
        } else
            this.spriteOpacity = Math.min(opacity, 1.0f);
    }

    @Override
    public String toString() {
        Class<? extends LayerPainter> identity = this.getClass();
        return identity.getSimpleName() + " #" + this.hashCode();
    }

    private void rotateToTarget(Point2D worldTarget) {
        Point2D center = getRotationAnchor();
        double deltaX = worldTarget.getX() - center.getX();
        double deltaY = worldTarget.getY() - center.getY();

        double radians = -Math.atan2(deltaX, deltaY);

        double rotationDegrees = Math.toDegrees(radians) + 180;
        double result = rotationDegrees;
        if (ControlPredicates.isRotationRoundingEnabled()) {
            result = Math.round(rotationDegrees);
        }
        this.rotateLayer(result);
    }

    /**
     * Returns the world-space pivot point around which this layer rotates.
     * <p>
     * This is the <b>single source of truth</b> for all rotation math:
     * {@link #setRotationRadians(double)}, {@link #getRotationTransform()},
     * {@link #paintContent}, and selection highlight rendering all use this.
     * <p>
     * The base implementation returns {@link #getSpriteCenter()} (image geometric center).
     * Subclasses override to use entity-specific pivots (e.g. ship center point,
     * weapon turret center, module anchor offset).
     *
     * @return the rotation pivot in world space.
     * @see #getSpriteCenter()
     * @see #getEntityCenter()
     */
    public Point2D getRotationAnchor() {
        return this.getSpriteCenter();
    }

    public void rotateLayer(double rotationDegrees) {
        EditDispatch.postLayerRotated(this, this.getRotationRadians(), Math.toRadians(rotationDegrees));
    }

    /**
     * Applies this layer's rotation on top of a base World-to-Screen transform.
     * Use this when drawing components (like weapon arcs or bounding boxes) that need to rotate with the sprite.
     * 
     * @param worldToScreen The parent camera's affine transform.
     * @return A new transform combining camera translation and layer rotation.
     */
    public AffineTransform getWithRotation(AffineTransform worldToScreen) {
        AffineTransform transform = new AffineTransform(worldToScreen);
        transform.concatenate(getRotationTransform());
        return transform;
    }

    public AffineTransform getWithRotation(AffineTransform worldToScreen, AffineTransform transformCache) {
        transformCache.setTransform(worldToScreen);
        transformCache.concatenate(getRotationTransform());
        return transformCache;
    }

    /**
     * Creates an AffineTransform that applies the layer's rotation around its visual center anchor.
     * 
     * @return The rotation transform instance.
     */
    public AffineTransform getRotationTransform() {
        double rotation = this.getRotationRadians();
        Point2D center = this.getRotationAnchor();
        double centerX = center.getX();
        double centerY = center.getY();
        return AffineTransform.getRotateInstance(rotation, centerX, centerY);
    }

    /**
     * Creates an inverse transform (Screen-to-World reversing this layer's rotation).
     * Useful for hit detection or mouse click coordinate resolution where the cursor's
     * screen position needs to map back to an unrotated point on the sprite.
     * 
     * @param worldToScreen The parent camera's affine transform.
     * @return The inverted transform matrix.
     */
    public AffineTransform getWithRotationInverse(AffineTransform worldToScreen) {
        AffineTransform transform;
        AffineTransform worldToScreenCopy = new AffineTransform(worldToScreen);
        try {
            AffineTransform inverseRotation = getRotationTransform();
            worldToScreenCopy.concatenate(inverseRotation);
            transform = worldToScreenCopy.createInverse();
        } catch (NoninvertibleTransformException e) {
            throw new RuntimeException("Non-invertible rotation transform of layer!", e);
        }
        return transform;
    }

    public int getSpriteWidth() {
        Sprite spriteContainer = getSprite();
        var spriteImage = spriteContainer.getImage();
        return spriteImage.getWidth();
    }

    public int getSpriteHeight() {
        Sprite spriteContainer = getSprite();
        var spriteImage = spriteContainer.getImage();
        return spriteImage.getHeight();
    }

    /**
     * Note: if called programmatically outside of usual user input flow,
     * {@link shipeditor.undo.UndoOverseer} needs to finish all edits
     * programmatically as well,
     * for consistent undo/redo behaviour.
     * 
     * @param updated new position of the anchor offset.
     */
    public void updateAnchorOffset(Point2D updated) {
        EditDispatch.postAnchorOffsetChanged(this, updated);
    }

    /**
     * Returns the geometric center of the sprite image in world space:
     * {@code anchor + (width/2, height/2)}.
     * <p>
     * This is a purely geometric calculation based on the image dimensions.
     * It does <b>not</b> account for entity-specific center offsets (use
     * {@link #getEntityCenter()} for that) or module anchor adjustments
     * (use {@link #getRotationAnchor()} for rotation math).
     *
     * @return the sprite's geometric center in world space.
     * @see #getRotationAnchor()
     * @see #getEntityCenter()
     */
    public Point2D getSpriteCenter() {
        Point2D difference = this.getSpriteCenterDifferenceToAnchor();
        Point2D currentAnchor = this.getAnchor();
        return new Point2D.Double((currentAnchor.getX() + difference.getX()),
                (currentAnchor.getY() + difference.getY()));
    }

    /**
     * Determines whether a given cursor coordinate (in World Space) falls inside the bounding box of this Layer's sprite.
     * This method automatically accounts for the layer's rotation by inverse-transforming the cursor coordinate
     * against the layer's rotation matrix before testing the standard axis-aligned bounding box.
     * 
     * @param worldCursor The mouse cursor coordinate in world space.
     * @return True if the cursor intersects the sprite visual bounds, false otherwise.
     */
    public boolean isWorldCursorInsideSprite(Point2D worldCursor) {
        try {
            Point2D currentAnchor = this.getAnchor();
            int width = this.getSpriteWidth();
            int height = this.getSpriteHeight();
            spriteRectCache.setRect(currentAnchor.getX(), currentAnchor.getY(), width, height);

            double rotation = this.getRotationRadians();
            if (rotation == 0.0) {
                return spriteRectCache.contains(worldCursor);
            }

            AffineTransform inverseRotation = this.getRotationTransform().createInverse();
            Point2D unrotated = inverseRotation.transform(worldCursor, unrotatedCache);

            return spriteRectCache.contains(unrotated);
        } catch (NoninvertibleTransformException e) {
            return false;
        }
    }

    public Point2D getSpriteCenter(Point2D cachingTarget) {
        Point2D difference = this.getSpriteCenterDifferenceToAnchor();

        Point2D currentAnchor = this.getAnchor();
        double x = currentAnchor.getX() + difference.getX();
        double y = currentAnchor.getY() + difference.getY();
        cachingTarget.setLocation(x, y);

        return cachingTarget;
    }

    /**
     * Returns the offset vector from the anchor (top-left) to the sprite's geometric center:
     * {@code (width/2, height/2)}.
     * <p>
     * This is a local-space offset, not a world position. Add it to {@link #getAnchor()}
     * to get the world-space sprite center (which is what {@link #getSpriteCenter()} does).
     *
     * @return the offset from anchor to sprite center.
     */
    public Point2D getSpriteCenterDifferenceToAnchor() {
        Sprite spriteContainer = getSprite();
        if (spriteContainer == null) {
            return new Point2D.Double(0, 0);
        }
        var spriteImage = spriteContainer.getImage();
        return Utility.getSpriteCenterDifferenceToAnchor(spriteImage);
    }

    protected void paintContent(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        Sprite spriteContainer = getSprite();
        if (spriteContainer == null) return;
        int textureId = spriteContainer.getTextureId();
        if (textureId == 0) return;

        Point2D currentAnchor = this.getAnchor();
        paintPosition.set((float) currentAnchor.getX(), (float) currentAnchor.getY());
        paintSize.set((float) this.getSpriteWidth(), (float) this.getSpriteHeight());
        Point2D rotationAnchor2D = this.getRotationAnchor();
        paintRotAnchor.set((float) rotationAnchor2D.getX(), (float) rotationAnchor2D.getY());
        float rotation = (float) this.getRotationRadians();
        float opacity = this.getSpriteOpacity();
        paintColor.w = opacity;

        spriteRenderer.drawSprite(textureId, paintPosition, paintSize, paintRotAnchor, rotation, paintColor, projection, view);
    }

    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        if (!this.isShouldDrawPainter())
            return;
        this.paintContent(spriteRenderer, shapeRenderer, projection, view);
    }

    public Rectangle2D getVisualBounds() {
        if (isUninitialized()) {
            return new Rectangle2D.Double();
        }
        Point2D currentAnchor = this.getAnchor();
        int width = this.getSpriteWidth();
        int height = this.getSpriteHeight();
        
        Point2D[] corners = new Point2D[] {
            new Point2D.Double(currentAnchor.getX(), currentAnchor.getY()),
            new Point2D.Double(currentAnchor.getX() + width, currentAnchor.getY()),
            new Point2D.Double(currentAnchor.getX(), currentAnchor.getY() + height),
            new Point2D.Double(currentAnchor.getX() + width, currentAnchor.getY() + height)
        };
        
        double rotation = this.getRotationRadians();
        if (rotation != 0.0) {
            AffineTransform transform = this.getRotationTransform();
            for (int i = 0; i < corners.length; i++) {
                transform.transform(corners[i], corners[i]);
            }
        }
        
        double minX = corners[0].getX();
        double minY = corners[0].getY();
        double maxX = corners[0].getX();
        double maxY = corners[0].getY();
        for (int i = 1; i < corners.length; i++) {
            minX = Math.min(minX, corners[i].getX());
            minY = Math.min(minY, corners[i].getY());
            maxX = Math.max(maxX, corners[i].getX());
            maxY = Math.max(maxY, corners[i].getY());
        }
        
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

}
