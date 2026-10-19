package shipeditor.utility.components.containers;

import shipeditor.utility.text.StringManager;

import javax.swing.ListModel;

import shipeditor.components.datafiles.entities.OrdnancedCSVEntry;
import shipeditor.utility.components.rendering.OrdnancedEntryCellRenderer;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class OrdnancedEntryList<T extends OrdnancedCSVEntry> extends SortableList<T>{

    private final Consumer<List<T>> sorter;

    protected OrdnancedEntryList(ListModel<T> dataModel, Consumer<List<T>> sortAction) {
        super(dataModel);
        this.sorter = sortAction;
        this.setCellRenderer(new OrdnancedEntryCellRenderer());
        this.addMouseListener(new ContextMenuListener());
        this.setDragEnabled(true);
        this.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE
                        || e.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                    Consumer<T> action = getRemoveAction();
                    if (action != null) {
                        actOnSelectedEntry(action);
                    }
                }
            }
        });
    }

    @Override
    protected void sortListModel() {
        ListModel<T> model = this.getModel();
        List<T> rearranged = new ArrayList<>(model.getSize());
        for (int i = 0; i < model.getSize(); i++) {
            T point = model.getElementAt(i);
            rearranged.add(point);
        }
        this.sorter.accept(rearranged);
    }

    protected abstract Consumer<T> getRemoveAction();

    protected JPopupMenu getContextMenu() {
        T selected = getSelectedValue();
        if (selected == null) return null;

        JPopupMenu menu = new JPopupMenu();
        JMenuItem remove = new JMenuItem(StringManager.getString("REMOVE_ENTRY"));
        remove.addActionListener(event -> actOnSelectedEntry(getRemoveAction()));
        menu.add(remove);

        return menu;
    }

    private class ContextMenuListener extends MouseAdapter {

        public void mousePressed(MouseEvent e) {
            if ( SwingUtilities.isRightMouseButton(e) ) {
                setSelectedIndex(locationToIndex(e.getPoint()));
                JPopupMenu menu = getContextMenu();
                if (menu != null) {
                    menu.show(OrdnancedEntryList.this, e.getPoint().x, e.getPoint().y);
                }
            }
        }
    }

}
