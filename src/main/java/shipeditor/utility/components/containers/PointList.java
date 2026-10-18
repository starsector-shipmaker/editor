package shipeditor.utility.components.containers;

import shipeditor.utility.text.StringManager;

import javax.swing.ListModel;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectedConfirmed;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.utility.components.dialog.DialogUtilities;
import shipeditor.utility.components.rendering.PointCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued;
import shipeditor.communication.events.viewer.points.PointEvents.PointRemoveQueued;

public abstract class PointList<T extends BaseWorldPoint> extends SortableList<T> {

    private boolean propagationBlock;

    protected PointList(ListModel<T> dataModel) {
        super(dataModel);
        this.addListSelectionListener(e -> {
            this.actOnSelectedPoint(this::handlePointSelection);
            if (propagationBlock) {
                propagationBlock = false;
                return;
            }
            this.actOnSelectedPoint(point -> {
                EventBus.publish(new PointSelectQueued(point));
                EventBus.publish(new ViewerRepaintQueued());
            });
        });
        this.addMouseListener(createContextMenuListener());
        this.setCellRenderer(createCellRenderer());
        int margin = 3;
        this.setBorder(new EmptyBorder(margin, margin, margin, margin));
        this.initListeners();
    }

    @SuppressWarnings("WeakerAccess")
    protected MouseListener createContextMenuListener() {
        return new PointContextMenuListener();
    }

    @SuppressWarnings("WeakerAccess")
    protected ListCellRenderer<? super T> createCellRenderer() {
        return new PointCellRenderer();
    }

    protected abstract void handlePointSelection(T point);

    @Override
    protected void sortListModel() {
        ListModel<T> model = this.getModel();
        List<T> rearrangedPoints = new ArrayList<>(model.getSize());
        for (int i = 0; i < model.getSize(); i++) {
            T point = model.getElementAt(i);
            rearrangedPoints.add(point);
        }
        this.publishPointsSorted(rearrangedPoints);
    }

    protected abstract void publishPointsSorted(List<T> rearrangedPoints);

    /**
     * Compares the model against the current points by identity. If they differ, a replacement model is installed
     * into this list and the previous selection is restored; otherwise the list is only repainted.
     */
    public DefaultListModel<T> synchronizeModel(DefaultListModel<T> model, List<? extends T> currentPoints) {
        boolean modelsEqual = true;
        if (model.getSize() == currentPoints.size()) {
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i) != currentPoints.get(i)) {
                    modelsEqual = false;
                    break;
                }
            }
        } else {
            modelsEqual = false;
        }

        if (modelsEqual) {
            this.repaint();
            return model;
        }

        int[] cachedSelected = this.getSelectedIndices();
        DefaultListModel<T> newModel = new DefaultListModel<>();
        newModel.addAll(currentPoints);
        this.setModel(newModel);
        this.setSelectedIndices(cachedSelected);
        if (!newModel.isEmpty() && cachedSelected.length > 0) {
            this.ensureIndexIsVisible(cachedSelected[0]);
        }
        return newModel;
    }

    private void actOnSelectedPoint(Consumer<T> action) {
        int index = this.getSelectedIndex();
        if (index != -1) {
            ListModel<T> listModel = this.getModel();
            T point = listModel.getElementAt(index);
            action.accept(point);
        }
    }

    private void initListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof PointSelectedConfirmed checked) {
                DefaultListModel<T> model = (DefaultListModel<T>) this.getModel();
                if (!model.contains(checked.point())) return;
                propagationBlock = true;
                BaseWorldPoint point = (BaseWorldPoint) checked.point();
                this.setSelectedValue(point, true);
            }
        });
    }

    private class PointContextMenuListener extends MouseAdapter {

        private JPopupMenu getContextMenu() {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem removePoint = new JMenuItem(StringManager.getString("REMOVE_POINT"));
            removePoint.addActionListener(event -> actOnSelectedPoint(point ->
                    EventBus.publish(new PointRemoveQueued(point, true))));
            menu.add(removePoint);
            menu.addSeparator();
            JMenuItem adjustPosition = new JMenuItem(StringManager.getString("ADJUST_POSITION"));
            adjustPosition.addActionListener(event ->
                    actOnSelectedPoint(DialogUtilities::showAdjustPointDialog));
            menu.add(adjustPosition);
            return menu;
        }

        public void mousePressed(MouseEvent e) {
            if ( SwingUtilities.isRightMouseButton(e) ) {
                setSelectedIndex(locationToIndex(e.getPoint()));
                JPopupMenu menu = getContextMenu();
                menu.show(PointList.this, e.getPoint().x, e.getPoint().y);
            }
        }

    }

}
