package shipeditor.components.instrument.ship.shared;

import shipeditor.components.datafiles.entities.WingCSVEntry;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.text.StringManager;
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

public abstract class AbstractWingListPane<T> extends JPanel {

    private final WingsList wingsList;
    private DefaultListModel<WingCSVEntry> wingsModel;
    private final Function<T, List<WingCSVEntry>> wingsGetter;
    private final JButton removeButton;

    protected AbstractWingListPane(Function<T, List<WingCSVEntry>> getter,
                                   BiConsumer<T, List<WingCSVEntry>> sortSetter) {
        this.setLayout(new BorderLayout());

        this.wingsGetter = getter;
        this.wingsModel = new DefaultListModel<>();

        BiConsumer<Integer, WingCSVEntry> removeAction = (entryIndex, wingCSVEntry) ->
                actOnTarget((layer, target) -> {
                    var entryList = wingsGetter.apply(target);
                    EditDispatch.postWingRemoved(entryList, layer, wingCSVEntry, entryIndex);
                });

        Consumer<List<WingCSVEntry>> sortAction = updatedList ->
                actOnTarget((layer, target) -> {
                    var oldWings = wingsGetter.apply(target);
                    EditDispatch.postWingsSorted(oldWings, updatedList, layer,
                            list -> sortSetter.accept(target, list));
                });

        this.wingsList = new WingsList(removeAction, wingsModel, sortAction);
        wingsList.setBorder(new LineBorder(Themes.getBorderColor()));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 2));
        removeButton = new JButton(StringManager.getString("REMOVE"),
                FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> {
            WingCSVEntry selected = wingsList.getSelectedValue();
            int selectedIndex = wingsList.getSelectedIndex();
            if (selected == null || selectedIndex < 0) return;
            actOnTarget((layer, target) -> {
                var entryList = wingsGetter.apply(target);
                EditDispatch.postWingRemoved(entryList, layer, selected, selectedIndex);
                refreshListModel(layer);
            });
        });
        wingsList.addListSelectionListener(e -> {
            removeButton.setEnabled(wingsList.getSelectedValue() != null);
        });
        buttonPanel.add(removeButton);

        this.add(wingsList, BorderLayout.CENTER);
        this.add(buttonPanel, BorderLayout.PAGE_END);
    }

    protected abstract void actOnTarget(BiConsumer<ShipLayer, T> action);

    protected abstract T getTarget(ShipLayer checkedLayer);

    protected boolean isValidTarget(T target) {
        return target != null;
    }

    public void refreshListModel(ViewerLayer selected) {
        DefaultListModel<WingCSVEntry> newModel = new DefaultListModel<>();
        if (!(selected instanceof ShipLayer checkedLayer)) {
            this.wingsModel = newModel;
            this.wingsList.setModel(newModel);
            this.wingsList.setEnabled(false);
            return;
        }

        T target = getTarget(checkedLayer);
        if (isValidTarget(target)) {
            List<WingCSVEntry> entries = wingsGetter.apply(target);
            if (entries != null) {
                newModel.addAll(entries);
                this.wingsList.setEnabled(true);
            } else {
                this.wingsList.setEnabled(false);
            }
        } else {
            this.wingsList.setEnabled(false);
        }
        this.removeButton.setEnabled(false);
        this.wingsModel = newModel;
        this.wingsList.setModel(newModel);
    }
}
