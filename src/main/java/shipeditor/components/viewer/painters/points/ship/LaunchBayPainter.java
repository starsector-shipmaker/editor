package shipeditor.components.viewer.painters.points.ship;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.PrimaryViewer;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.WorldPoint;
import shipeditor.components.viewer.entities.bays.LaunchBay;
import shipeditor.components.viewer.entities.bays.LaunchPortPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.MirrorablePointPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;

import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import shipeditor.utility.graphics.GraphicConstants;
import shipeditor.communication.BusEventListener;
import shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawMousePressed;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import shipeditor.communication.events.viewer.points.PointEvents.PointCreationQueued;
import shipeditor.communication.events.viewer.points.PointEvents.LaunchBayRemoveConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.LaunchBayAddConfirmed;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class LaunchBayPainter extends MirrorablePointPainter {

    private final List<LaunchPortPoint> portsIndex;

    @Getter
    private final List<LaunchBay> baysList;

    @Getter
    private static boolean addPortHotkeyPressed;

    @Getter
    private static boolean addBayHotkeyPressed;

    private static final int ADD_PORT_HOTKEY = KeyEvent.VK_SHIFT;
    private static final int ADD_BAY_HOTKEY = KeyEvent.VK_CONTROL;


    public LaunchBayPainter(ShipPainter parent) {
        super(parent);
        this.portsIndex = new ArrayList<>();
        this.baysList = new ArrayList<>();
        this.initHotkeys();
    }

    @Override
    public LaunchPortPoint getSelected() {
        return (LaunchPortPoint) super.getSelected();
    }

    @Override
    protected EditorInstrument getInstrumentType() {
        return EditorInstrument.LAUNCH_BAYS;
    }

    @Override
    public void cleanupListeners() {
        super.cleanupListeners();
    }

    @Override
    protected void handleCreation(PointCreationQueued event) {
        ShipPainter parentLayer = (ShipPainter) this.getParentLayer();
        Point2D position = event.position();
        String generatedID = this.generateUniqueBayID();
        if (addPortHotkeyPressed) {
            LaunchPortPoint selected = this.getSelected();
            if (selected != null) {
                LaunchBay selectedBay = selected.getParentBay();
                LaunchPortPoint newPort = new LaunchPortPoint(position, parentLayer, selectedBay);
                EditDispatch.postPointAdded(this, newPort);
            } else {
                LaunchBay newBay = new LaunchBay(generatedID, this);
                LaunchPortPoint newPort = new LaunchPortPoint(position, parentLayer, newBay);
                EditDispatch.postPointAdded(this, newPort);
                this.setSelected(newPort);
            }
        } else if (addBayHotkeyPressed) {
            LaunchBay newBay = new LaunchBay(generatedID, this);
            LaunchPortPoint newPort = new LaunchPortPoint(position, parentLayer, newBay);
            EditDispatch.postPointAdded(this, newPort);
        }
    }

    public String generateUniqueBayID() {
        ShipPainter parentLayer = (ShipPainter) getParentLayer();
        return parentLayer.generateUniqueSlotID("WS");
    }

    private void initHotkeys() {
        this.subscribeHotkeyPair(ADD_PORT_HOTKEY, ADD_BAY_HOTKEY, LaunchBayPainter::setHotkeyState);
    }

    private static void setHotkeyState(boolean isPortHotkey, boolean state) {
        if (isPortHotkey) {
            addPortHotkeyPressed = state;
        } else {
            addBayHotkeyPressed = state;
        }
    }

    @Override
    protected void handlePointSelectionEvent(BaseWorldPoint point) {
        if (addPortHotkeyPressed) return;
        super.handlePointSelectionEvent(point);
    }

    @Override
    public List<LaunchPortPoint> getPointsIndex() {
        return portsIndex;
    }

    public void addBay(LaunchBay bay) {
        baysList.add(bay);
        EventBus.publish(new LaunchBayAddConfirmed(bay, -1));
    }

    public void insertBay(LaunchBay bay, int index) {
        baysList.add(index, bay);
        EventBus.publish(new LaunchBayAddConfirmed(bay, index));
    }

    public void removeBay(LaunchBay bay) {
        baysList.remove(bay);
        EventBus.publish(new LaunchBayRemoveConfirmed(bay));
    }

    /**
     * Assumes that all UI-side preparations are already made and firing events is not necessary.
     */
    public LaunchBay transferPointToNewBay(LaunchPortPoint portPoint) {
        LaunchBay newBay = new LaunchBay(this.generateUniqueBayID(), this);
        baysList.add(newBay);
        return newBay;
    }

    @Override
    protected void addPointToIndex(BaseWorldPoint point) {
        if (point instanceof LaunchPortPoint checked) {
            LaunchBay targetBay = checked.getParentBay();
            if (!baysList.contains(targetBay)) {
                this.addBay(targetBay);
            }
            List<LaunchPortPoint> portPoints = targetBay.getPortPoints();
            portPoints.add(checked);
            portsIndex.add(checked);
            this.setSelected(checked);
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    protected void removePointFromIndex(BaseWorldPoint point) {
        if (point instanceof LaunchPortPoint checked) {
            portsIndex.remove(checked);
            LaunchBay parentBay = checked.getParentBay();
            List<LaunchPortPoint> portPoints = parentBay.getPortPoints();
            portPoints.remove(checked);
            if (portPoints.isEmpty()) {
                this.removeBay(parentBay);
            }
        } else {
            throwIllegalPoint();
        }
    }

    @Override
    public int getIndexOfPoint(BaseWorldPoint point) {
        if (point instanceof LaunchPortPoint checked) {
            return portsIndex.indexOf(checked);
        } else {
            throwIllegalPoint();
            return -1;
        }
    }

    @Override
    protected Class<? extends BaseWorldPoint> getTypeReference() {
        return LaunchPortPoint.class;
    }

    @Override
    public void insertPoint(BaseWorldPoint toInsert, int precedingIndex) {
        throwIllegalPoint();
    }

    @Override
    protected void selectPointConditionally() {
        // Do not implement sticking on hover.
    }

    @Override
    protected void initInteractionListeners() {
        super.initInteractionListeners();
        BusEventListener rawMouseListener = event -> {
            if (!isInteractionEnabled()) return;
            if (event instanceof ViewerRawMousePressed checked) {
                MouseEvent me = checked.mouseEvent();
                if (SwingUtilities.isLeftMouseButton(me) && !me.isControlDown() && !me.isShiftDown() && !me.isAltDown()) {
                    this.selectPointClosest();
                }
            }
        };
        EventBus.subscribe(this, rawMouseListener);
    }

    @Override
    protected void paintPainterContent(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        if (!isInteractionEnabled()) return;
        
        AffineTransform worldToScreen = StaticController.getViewer().getWorldToScreen();
        Point2D finalWorldCursor = StaticController.getFinalWorldCursor();
        WorldPoint selected = this.getSelected();
        PrimaryViewer viewer = StaticController.getViewer();

        // Draw connections between ports of the same bay and arc/angle visuals
        for (LaunchBay bay : this.baysList) {
            List<LaunchPortPoint> ports = bay.getPortPoints();
            if (ports.isEmpty()) continue;

            boolean hasSelectedPort = selected instanceof LaunchPortPoint && ((LaunchPortPoint) selected).getParentBay() == bay;

            // Draw connection lines
            if (ports.size() > 1) {
                org.lwjgl.opengl.GL11.glLineWidth(2.0f);
                for (int i = 0; i < ports.size() - 1; i++) {
                    Point2D p1 = worldToScreen.transform(ports.get(i).getPosition(), null);
                    Point2D p2 = worldToScreen.transform(ports.get(i + 1).getPosition(), null);
                    org.joml.Vector2f start = new org.joml.Vector2f((float) p1.getX(), (float) p1.getY());
                    org.joml.Vector2f end = new org.joml.Vector2f((float) p2.getX(), (float) p2.getY());
                    
                    org.joml.Vector4f color = hasSelectedPort ? new org.joml.Vector4f(1.0f, 0.5f, 0.0f, 1.0f) : new org.joml.Vector4f(0.5f, 0.5f, 0.5f, 0.7f);
                    shapeRenderer.drawLine(start, end, color);
                }
                org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
            }

            // Draw direction arrow and arc for the bay (centered on the first port)
            Point2D firstPort = ports.get(0).getPosition();
            
            shipeditor.components.viewer.entities.weapon.SlotDrawer drawer = new shipeditor.components.viewer.entities.weapon.SlotDrawer(null);
            drawer.setPointPosition(firstPort);
            drawer.setType(bay.getWeaponType());
            drawer.setMount(bay.getWeaponMount());
            drawer.setSize(bay.getWeaponSize());
            drawer.setAngle(bay.getAngle());
            drawer.setArc(bay.getArc());
            drawer.setDrawMount(false);
            drawer.paintSlotVisuals(spriteRenderer, shapeRenderer, projection, view);
        }

        if (selected != null && viewer.isCursorInViewer() && addPortHotkeyPressed) {
            Point2D startScreen = worldToScreen.transform(selected.getPosition(), null);
            Point2D endScreen = worldToScreen.transform(finalWorldCursor, null);
            org.joml.Vector2f start = new org.joml.Vector2f((float) startScreen.getX(), (float) startScreen.getY());
            org.joml.Vector2f end = new org.joml.Vector2f((float) endScreen.getX(), (float) endScreen.getY());

            org.lwjgl.opengl.GL11.glLineWidth(4.0f);
            shapeRenderer.drawLine(start, end, new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 1.0f));

            org.lwjgl.opengl.GL11.glLineWidth(2.0f);
            shapeRenderer.drawLine(start, end, new org.joml.Vector4f(0.75f, 0.75f, 0.75f, 1.0f));

            org.lwjgl.opengl.GL11.glLineWidth(GraphicConstants.LINE_WIDTH_DEFAULT);
        }
    }

}
