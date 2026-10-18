package shipeditor.components.instrument.ship.slots;

import shipeditor.utility.text.StringManager;

import javax.swing.ListModel;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.SlotPointsSorted;
import shipeditor.components.datafiles.trees.WeaponFilterPanel;

import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.utility.components.containers.PointList;
import shipeditor.utility.components.rendering.WeaponSlotCellRenderer;

import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.utility.components.dialog.DialogUtilities;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;
import java.util.function.Consumer;

public class WeaponSlotList extends PointList<WeaponSlotPoint> {

    private static final DataFlavor SLOT_FLAVOR = new DataFlavor(WeaponSlotPoint.class, "Slot");

    private final Consumer<WeaponSlotPoint> selectAction;

    WeaponSlotList(ListModel<WeaponSlotPoint> dataModel, Consumer<WeaponSlotPoint> pointSelectAction) {
        super(dataModel);
        this.setCellRenderer(new WeaponSlotCellRenderer());
        this.selectAction = pointSelectAction;
        initCopyPasteKeyBindings();
    }

    private void initCopyPasteKeyBindings() {
        EventBus.subscribe(this, event -> {
            if (event instanceof shipeditor.communication.events.viewer.control.ControlEvents.ViewerRawKeyPressed pressedEvent) {
                int keyCode = pressedEvent.keyEvent().getKeyCode();
                boolean isCtrlDown = pressedEvent.keyEvent().isControlDown();
                
                java.awt.Component focusOwner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                boolean isFocused = this.hasFocus() || (focusOwner != null && javax.swing.SwingUtilities.isDescendingFrom(focusOwner, this));
                // Only handle these if we actually have focus (to allow LayerViewerControls to handle it if viewer is focused)
                if (isFocused) {
                    if (keyCode == java.awt.event.KeyEvent.VK_C && isCtrlDown) {
                        java.util.List<WeaponSlotPoint> selected = getSelectedValuesList();
                        if (!selected.isEmpty()) {
                            WeaponSlotClipboard.copy(selected);
                        }
                    } else if (keyCode == java.awt.event.KeyEvent.VK_V && isCtrlDown) {
                        WeaponSlotPainter painter = shipeditor.utility.overseers.StaticController.getSelectedSlotPainter();
                        if (painter != null && WeaponSlotClipboard.hasData()) {
                            painter.pasteSlots(WeaponSlotClipboard.getClipboard(), null);
                        }
                    }
                }
            }
        });
    }

    @Override
    protected java.awt.event.MouseListener createContextMenuListener() {
        return new java.awt.event.MouseAdapter() {
            private javax.swing.JPopupMenu getContextMenu() {
                javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

                javax.swing.JMenuItem removePoint = new javax.swing.JMenuItem("Remove point");
                removePoint.addActionListener(event -> {
                    for (WeaponSlotPoint point : getSelectedValuesList()) {
                        EventBus.publish(new shipeditor.communication.events.viewer.points.PointEvents.PointRemoveQueued(point, true));
                    }
                });
                menu.add(removePoint);
                menu.addSeparator();

                javax.swing.JMenuItem adjustPosition = new javax.swing.JMenuItem(StringManager.getString("ADJUST_POSITION"));
                adjustPosition.addActionListener(event -> {
                    WeaponSlotPoint firstSelected = getSelectedValue();
                    if (firstSelected != null) {
                        DialogUtilities.showAdjustPointDialog(firstSelected);
                    }
                });
                menu.add(adjustPosition);
                menu.addSeparator();

                javax.swing.JMenuItem copyItem = new javax.swing.JMenuItem("Copy selected");
                copyItem.addActionListener(event -> {
                    java.util.List<WeaponSlotPoint> selected = getSelectedValuesList();
                    WeaponSlotClipboard.copy(selected);
                });
                menu.add(copyItem);

                javax.swing.JMenuItem pasteItem = new javax.swing.JMenuItem("Paste");
                pasteItem.setEnabled(WeaponSlotClipboard.hasData());
                pasteItem.addActionListener(event -> {
                    WeaponSlotPainter painter = shipeditor.utility.overseers.StaticController.getSelectedSlotPainter();
                    if (painter != null) {
                        painter.pasteSlots(WeaponSlotClipboard.getClipboard(), null);
                    }
                });
                menu.add(pasteItem);

                return menu;
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
                    int index = locationToIndex(e.getPoint());
                    if (index != -1) {
                        if (!isSelectedIndex(index)) {
                            setSelectedIndex(index);
                        }
                    }
                    javax.swing.JPopupMenu menu = getContextMenu();
                    menu.show(WeaponSlotList.this, e.getPoint().x, e.getPoint().y);
                }
            }
        };
    }

    @Override
    protected void handlePointSelection(WeaponSlotPoint point) {
        WeaponFilterPanel.updateSelectedSlot(point);
        this.selectAction.accept(point);
    }

    @Override
    protected void publishPointsSorted(List<WeaponSlotPoint> rearrangedPoints) {
        EventBus.publish(new SlotPointsSorted(rearrangedPoints));
    }

    @Override
    protected Transferable createTransferableFromEntry(WeaponSlotPoint entry) {
        return createSingleEntryTransferable(entry, SLOT_FLAVOR);
    }

    @Override
    protected boolean isSupported(Transferable transferable) {
        return transferable.isDataFlavorSupported(SLOT_FLAVOR);
    }


}
