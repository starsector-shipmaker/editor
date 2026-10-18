package shipeditor.components.viewer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.ViewerBackgroundChanged;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerGuidesToggled;
import shipeditor.communication.events.viewer.layers.LayerEvents.ViewerLayerRemovalConfirmed;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerSpriteLoadQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerSpriteLoadConfirmed;
import shipeditor.components.viewer.control.LayerViewerControls;
import shipeditor.components.viewer.control.ViewerControl;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.components.viewer.layers.weapon.WeaponPainter;
import shipeditor.components.viewer.layers.weapon.WeaponSprites;
import shipeditor.undo.EditDispatch;
import shipeditor.undo.UndoOverseer;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ViewerTransform;
import shipeditor.utility.overseers.StaticController;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import shipeditor.communication.events.components.ComponentEvents.ViewerFocusRequestQueued;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerTransformsReset;
/** * This one is a conceptual root of the whole app.
 * It is responsible for the foundation of editing workflow - visual display of ships and its point features.*/
@Getter
@SuppressWarnings("OverlyCoupledClass")
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class PrimaryViewer extends JPanel implements LayerViewer {

    private static final Dimension MINIMUM_PANEL_SIZE = new Dimension(240, 120);

    private final LayerManager layerManager;

    private PaintOrderController paintOrderController;

    @Setter
    private boolean cursorInViewer;

    private ViewerControl viewerControls;

    private final ViewerTransform viewerTransform = new ViewerTransform();
    private AWTGLCanvas glCanvas;
    private SpriteRenderer spriteRenderer;
    private ShapeRenderer shapeRenderer;
    private final Matrix4f projectionMatrix = new Matrix4f();

    private final Queue<Runnable> glRunnables = new ConcurrentLinkedQueue<>();

    public void queueGLTask(Runnable task) {
        glRunnables.add(task);
        setRepaintQueued();
    }

    public PrimaryViewer() {
        this.setLayout(new BorderLayout());
        this.setMinimumSize(MINIMUM_PANEL_SIZE);
        this.setBackground(Color.GRAY);

        this.layerManager = new LayerManager();
        this.layerManager.initListeners();
    }

    @SuppressWarnings("OverlyComplexAnonymousInnerClass")
    public PrimaryViewer commenceInitialization() {
        System.setProperty("org.lwjgl.opengl.contextAPI", "native");
        try {
            org.lwjgl.system.Configuration.OPENGL_CONTEXT_API.set("native");
        } catch (Throwable ignored) {
        }

        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        data.swapInterval = 0; // Explicitly 0 to prevent WGL VSync blocking on Windows EDT

        glCanvas = new AWTGLCanvas(data) {
            @Override
            public void initGL() {
                try {
                    GL.createCapabilities();
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    spriteRenderer = new SpriteRenderer();
                    shapeRenderer = new ShapeRenderer();
                } catch (Throwable t) {
                    log.error("Failed to initialize OpenGL capabilities!", t);
                }
            }

            @Override
            public void paintGL() {
                try {
                    Color bg = getBackground();
                    GL11.glViewport(0, 0, getWidth(), getHeight());
                    GL11.glClearColor(bg.getRed() / 255.0f, bg.getGreen() / 255.0f, bg.getBlue() / 255.0f, 1.0f);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

                    projectionMatrix.setOrtho(0.0f, getWidth(), getHeight(), 0.0f, -1.0f, 1.0f);
                    Matrix4f viewMatrix = ViewerTransform.convertToMatrix4f(viewerTransform.getWorldToScreen());

                    if (paintOrderController != null) {
                        paintOrderController.paint(spriteRenderer, shapeRenderer, projectionMatrix, viewMatrix, getWidth(), getHeight());
                    }

                    while (!glRunnables.isEmpty()) {
                        Runnable task = glRunnables.poll();
                        if (task != null) {
                            task.run();
                        }
                    }
                    
                    swapBuffers();
                } catch (Throwable t) {
                    log.error("Error during OpenGL painting!", t);
                } finally {
                    if (shipeditor.utility.Utility.isLinux()) {
                        java.awt.Toolkit.getDefaultToolkit().sync();
                    }
                }
            }
        };

        // Tab / Shift+Tab are canvas shortcuts (slot cycling), not focus traversal.
        glCanvas.setFocusTraversalKeysEnabled(false);
        this.add(glCanvas, BorderLayout.CENTER);

        this.paintOrderController = new PaintOrderController(this);

        EventBus.publish(new ViewerGuidesToggled(true, true,
                true));

        glCanvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (cursorInViewer) return;
                glCanvas.requestFocusInWindow();
                cursorInViewer = true;
                setRepaintQueued();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!cursorInViewer) return;
                cursorInViewer = false;
                setRepaintQueued();
            }
        });

        viewerControls = LayerViewerControls.create(this);
        glCanvas.addMouseListener(viewerControls);
        glCanvas.addMouseMotionListener(viewerControls);
        glCanvas.addMouseWheelListener(viewerControls);
        
        this.initViewerStateListeners();
        this.initLayerListening();
        this.setDropTarget(new ViewerDropReceiver(this));
        StaticController.setViewer(this);
        return this;
    }

    public void setRepaintQueued() {
        if (this.paintOrderController != null) {
            this.paintOrderController.queueRepaint();
        }
    }

    public void renderImmediately() {
        if (glCanvas != null && glCanvas.isDisplayable()) {
            glCanvas.render();
        }
    }

    private void initViewerStateListeners() {
        EventBus.subscribe(this, event -> {
            if(event instanceof ViewerRepaintQueued || event instanceof LayerWasSelected) {
                setRepaintQueued();
            } else if(event instanceof ViewerFocusRequestQueued) {
                this.requestFocusInWindow();
            } else if(event instanceof ViewerTransformsReset) {
                this.resetTransform();
                this.centerViewpoint();
            } else if(event instanceof ViewerBackgroundChanged checked) {
                Color background = checked.newColor();
                Color opaque = new Color(background.getRed(),
                        background.getGreen(), background.getBlue(), 255);
                this.setBackground(opaque);
                setRepaintQueued();
            }
        });
    }

    private void initLayerListening() {
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerSpriteLoadQueued checked) {
                ViewerLayer layer = checked.updated();
                Sprite sprite = checked.sprite();
                this.loadSpriteToLayer(layer, sprite);
            } else if (event instanceof ViewerLayerRemovalConfirmed checked) {
                PrimaryViewer.unloadLayer(checked.removed());
                setRepaintQueued();
            }
        });
    }

    public void loadSpriteToLayer(ViewerLayer layer, Sprite sprite) {
        if (layer.getPainter() == null && sprite != null) {
            this.loadLayer(layer, sprite);
        } else if (sprite != null) {
            LayerPainter painter = layer.getPainter();
            Sprite oldSprite = painter.getSprite();
            EditDispatch.postLayerSpriteSwapped(painter, oldSprite, sprite);
        }
    }

    public AffineTransform getWorldToScreen() {
        return viewerTransform.getWorldToScreen();
    }

    public AffineTransform getScreenToWorld() {
        return viewerTransform.getScreenToWorld();
    }

    public void translate(double dx, double dy) {
        viewerTransform.translate(dx, dy);
    }

    public void zoom(double x, double y, double factorX, double factorY) {
        viewerTransform.zoom(x, y, factorX, factorY);
    }

    public void rotate(double x, double y, double angleRadians) {
        viewerTransform.rotate(x, y, angleRadians);
    }

    public void resetTransform() {
        viewerTransform.resetTransform();
    }

    @Override
    public AffineTransform getTransformWorldToScreen() {
        return this.getWorldToScreen();
    }

    public ViewerControl getViewerControls() {
        return this.viewerControls;
    }

    /**
     * @return layer that is currently active in viewer; might be null, in which case caller is expected to handle that.
     */
    @Override
    public LayerPainter getSelectedLayer() {
        ViewerLayer activeLayer = layerManager.getActiveLayer();
        if (activeLayer == null) {
            return null;
        }
        return activeLayer.getPainter();
    }

    @SuppressWarnings("ChainOfInstanceofChecks")
    @Override
    public ViewerLayer loadLayer(ViewerLayer layer, Sprite sprite) {
        LayerPainter newPainter = null;

        if (layer.getPainter() == null) {
            if (layer instanceof ShipLayer checkedLayer) {
                ShipPainter shipPainter = new ShipPainter(checkedLayer);
                shipPainter.setBaseHullSprite(sprite);
                newPainter = shipPainter;
            } else if (layer instanceof WeaponLayer checkedLayer) {
                WeaponPainter weaponPainter = new WeaponPainter(checkedLayer);
                WeaponSprites weaponSprites = weaponPainter.getWeaponSprites();
                weaponSprites.setTurretSprite(sprite);
                newPainter = weaponPainter;
            }
            layer.setPainter(newPainter);
            if (newPainter != null) {
                newPainter.setSprite(sprite);
            }
        } else {
            newPainter = layer.getPainter();
        }

        layerManager.setActiveLayer(layer);

        if (newPainter != null) {
            List<ViewerLayer> layers = layerManager.getLayers();
            int idx = layers.indexOf(layer);
            if (idx > 0) {
                // Then get anchor of that and place new painter anchor next to it.
                var prevLayer = layers.get(idx - 1);
                var layerPainter = prevLayer.getPainter();
                if (layerPainter != null) {
                    var layerAnchor = layerPainter.getAnchor();
                    var prevLayerWidth = layerPainter.getSpriteSize();
                    Point2D widthPoint = new Point2D.Double(layerAnchor.getX() + prevLayerWidth.width, layerAnchor.getY());

                    newPainter.updateAnchorOffset(widthPoint);
                    UndoOverseer.finishAllEdits();
                }
            }
        }
        EventBus.publish(new LayerSpriteLoadConfirmed(layer, sprite));
        EventBus.publish(new ActiveLayerUpdated(layer));
        if (newPainter != null && this.getWidth() > 0 && this.getHeight() > 0) {
            this.centerViewpoint();
        }
        return layer;
    }

    private static void unloadLayer(ViewerLayer layer) {
        LayerPainter mainPainter = layer.getPainter();
        UndoOverseer.cleanupRemovedLayer(mainPainter);
    }

    public void centerViewpoint() {
        ViewerLayer activeLayer = this.layerManager.getActiveLayer();
        if (activeLayer == null) return;
        AffineTransform worldToScreen = this.getWorldToScreen();
        // Get the center of the sprite in screen coordinates.
        LayerPainter activePainter = activeLayer.getPainter();
        if (activePainter == null) return;
        Point2D spriteCenter = activePainter.getSpriteCenter();
        Point2D centerScreen = worldToScreen.transform(spriteCenter, null);
        // Calculate the delta values to center the sprite.
        Point2D midpoint = this.getViewerMidpoint();
        double dx = midpoint.getX() - centerScreen.getX();
        double dy = midpoint.getY() - centerScreen.getY();
        this.translate(dx, dy);
        setRepaintQueued();
    }

    public Point2D getViewerMidpoint() {
        double x = (this.getWidth() / 2.0f);
        double y = (this.getHeight() / 2.0f);
        return new Point2D.Double(x, y);
    }

}
