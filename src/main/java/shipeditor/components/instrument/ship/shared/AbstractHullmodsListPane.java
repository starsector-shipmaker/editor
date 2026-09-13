package shipeditor.components.instrument.ship.shared;

import shipeditor.utility.text.StringManager;
import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.components.dialog.DialogUtilities;
import shipeditor.utility.themes.Themes;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class AbstractHullmodsListPane<T> extends JPanel {
    private final HullmodsList modsList;
    private DefaultListModel<HullmodCSVEntry> modsModel;
    private final Function<T, List<HullmodCSVEntry>> modsGetter;
    private final JButton addButton;
    private final JButton removeButton;

    protected AbstractHullmodsListPane(Function<T, List<HullmodCSVEntry>> getter,
                                       BiConsumer<T, List<HullmodCSVEntry>> sortSetter) {
        this.modsModel = new DefaultListModel<>();
        this.modsGetter = getter;

        Consumer<HullmodCSVEntry> removeAction = entry ->
                actOnTarget((layer, target) -> {
                    var entryList = modsGetter.apply(target);
                    EditDispatch.postHullmodRemoved(entryList, layer, entry);
                });

        Consumer<List<HullmodCSVEntry>> sortAction = updatedList ->
                actOnTarget((layer, target) -> {
                    var oldMods = modsGetter.apply(target);
                    EditDispatch.postHullmodsSorted(oldMods, updatedList, layer,
                            list -> sortSetter.accept(target, list));
                });

        this.modsList = new HullmodsList(removeAction, modsModel, sortAction);
        modsList.setBorder(new LineBorder(Themes.getBorderColor()));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 2));
        addButton = new JButton(StringManager.getString("ADD"),
                FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 16, Themes.getIconColor()));
        removeButton = new JButton(StringManager.getString("REMOVE"),
                FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        addButton.setEnabled(false);
        removeButton.setEnabled(false);

        addButton.addActionListener(e -> {
            var activeLayer = StaticController.getActiveLayer();
            if (!(activeLayer instanceof ShipLayer shipLayer)) return;
            var shipPainter = shipLayer.getPainter();
            if (shipPainter == null || shipPainter.isUninitialized()) return;
            var shipHull = shipLayer.getHull();
            var shipSize = shipHull != null ? shipHull.getHullSize() : null;

            HullmodCSVEntry picked = DialogUtilities.showHullmodPickerDialog(shipSize, shipLayer);
            if (picked != null) {
                actOnTarget((layer, target) -> {
                    var entryList = modsGetter.apply(target);
                    if (entryList != null && !entryList.contains(picked)) {
                        EditDispatch.postHullmodAdded(entryList, layer, picked);
                        refreshListModel(layer);
                    }
                });
            }
        });

        removeButton.addActionListener(e -> {
            HullmodCSVEntry selected = modsList.getSelectedValue();
            if (selected == null) return;
            actOnTarget((layer, target) -> {
                var entryList = modsGetter.apply(target);
                if (entryList != null && entryList.contains(selected)) {
                    EditDispatch.postHullmodRemoved(entryList, layer, selected);
                    refreshListModel(layer);
                }
            });
        });

        modsList.addListSelectionListener(e -> {
            removeButton.setEnabled(modsList.getSelectedValue() != null);
        });

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);

        this.setLayout(new BorderLayout());
        this.add(modsList, BorderLayout.CENTER);
        this.add(buttonPanel, BorderLayout.PAGE_END);
    }

    protected abstract void actOnTarget(BiConsumer<ShipLayer, T> action);

    protected abstract T getTarget(ShipLayer checkedLayer);

    protected boolean isValidTarget(T target) {
        return target != null;
    }

    public void refreshListModel(ViewerLayer selected) {
        DefaultListModel<HullmodCSVEntry> newModel = new DefaultListModel<>();
        if (!(selected instanceof ShipLayer checkedLayer)) {
            this.modsModel = newModel;
            this.modsList.setModel(newModel);
            this.modsList.setEnabled(false);
            this.addButton.setEnabled(false);
            this.removeButton.setEnabled(false);
            return;
        }

        T target = getTarget(checkedLayer);

        if (isValidTarget(target)) {
            List<HullmodCSVEntry> entries = modsGetter.apply(target);
            if (entries != null) {
                newModel.addAll(entries);
                this.modsList.setEnabled(true);
                this.addButton.setEnabled(true);
            } else {
                this.modsList.setEnabled(false);
                this.addButton.setEnabled(false);
            }
        } else {
            this.modsList.setEnabled(false);
            this.addButton.setEnabled(false);
        }
        this.removeButton.setEnabled(false);
        this.modsModel = newModel;
        this.modsList.setModel(newModel);
    }
}
