package shipeditor.components.instrument.ship.variant.hullmods;

import shipeditor.utility.text.StringManager;

import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.utility.components.ComponentUtilities;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.utility.themes.Themes;

/** * Simple list editor for variant suppressedMods (raw hullmod ID strings).
 * Follows the same visual pattern as {@link VariantHullmodsListPane}.*/
class SuppressedModsPanel extends JPanel {

    private DefaultListModel<String> listModel;
    private JList<String> modsList;
    private JTextField addField;
    private JButton addButton;
    private JButton pickButton;
    private JButton removeButton;

    SuppressedModsPanel() {
        this.setLayout(new BorderLayout());
        ComponentUtilities.outfitPanelWithTitle(this, "Suppressed Mods");

        listModel = new DefaultListModel<>();
        modsList = new JList<>(listModel);
        modsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modsList.setBorder(new LineBorder(Themes.getBorderColor()));
        modsList.setEnabled(false);
        this.add(new JScrollPane(modsList), BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new BorderLayout(4, 0));
        addField = new JTextField();
        addField.setToolTipText(StringManager.getString("ENTER_HULLMOD_ID_TO_SUPPRESS_THEN_PRESS_ADD_OR_ENTER"));
        addField.putClientProperty("JTextField.placeholderText", "Hullmod ID...");
        addField.addActionListener(e -> addFromField());
        addField.setEnabled(false);
        controlPanel.add(addField, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        addButton = new JButton(StringManager.getString("ADD_1"),
                FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 16, Themes.getIconColor()));
        addButton.setEnabled(false);
        addButton.addActionListener(e -> addFromField());
        buttonsPanel.add(addButton);

        pickButton = new JButton(StringManager.getString("PICK"),
                FontIcon.of(BoxiconsRegular.SEARCH, 16, Themes.getIconColor()));
        pickButton.setToolTipText(StringManager.getString("BROWSE_AND_PICK_A_HULLMOD_TO_SUPPRESS"));
        pickButton.setEnabled(false);
        pickButton.addActionListener(e -> {
            var activeLayer = shipeditor.utility.overseers.StaticController.getActiveLayer();
            if (!(activeLayer instanceof ShipLayer shipLayer)) return;
            var shipHull = shipLayer.getHull();
            var shipSize = shipHull != null ? shipHull.getHullSize() : null;

            shipeditor.components.datafiles.entities.HullmodCSVEntry picked = 
                    shipeditor.utility.components.dialog.DialogUtilities.showHullmodPickerDialog(shipSize, shipLayer);
            if (picked != null) {
                String id = picked.getID();
                if (!listModel.contains(id)) {
                    listModel.addElement(id);
                    syncBackToVariant();
                }
            }
        });
        buttonsPanel.add(pickButton);

        removeButton = new JButton(StringManager.getString("REMOVE"),
                FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        removeButton.setEnabled(false);
        removeButton.addActionListener(e -> {
            String selected = modsList.getSelectedValue();
            if (selected == null) return;
            listModel.removeElement(selected);
            syncBackToVariant();
        });
        buttonsPanel.add(removeButton);

        modsList.addListSelectionListener(e -> {
            removeButton.setEnabled(modsList.getSelectedValue() != null);
        });

        controlPanel.add(buttonsPanel, BorderLayout.LINE_END);
        this.add(controlPanel, BorderLayout.PAGE_END);
    }

    private void addFromField() {
        String text = addField.getText();
        if (text == null || text.isBlank()) return;
        String trimmed = text.trim();
        if (listModel.contains(trimmed)) return;
        listModel.addElement(trimmed);
        addField.setText("");
        syncBackToVariant();
    }

    /**
     * Push the current list model contents back to the active variant's suppressedMods list.
     */
    private void syncBackToVariant() {
        ViewerLayer activeLayer = shipeditor.utility.overseers.StaticController.getActiveLayer();
        if (!(activeLayer instanceof ShipLayer shipLayer)) return;
        ShipPainter painter = shipLayer.getPainter();
        if (painter == null || painter.isUninitialized()) return;
        ShipVariant variant = painter.getActiveVariant();
        if (variant == null || variant.isEmpty()) return;

        List<String> updatedList = new java.util.ArrayList<>(listModel.getSize());
        for (int i = 0; i < listModel.getSize(); i++) {
            updatedList.add(listModel.getElementAt(i));
        }
        
        List<String> oldList = variant.getSuppressedMods();
        if (oldList == null) {
            oldList = new java.util.ArrayList<>();
        } else {
            oldList = new java.util.ArrayList<>(oldList);
        }
        
        shipeditor.undo.edits.features.FeatureEdits.SuppressedModsEdit edit = new shipeditor.undo.edits.features.FeatureEdits.SuppressedModsEdit(variant, activeLayer, oldList, updatedList);
        shipeditor.undo.UndoOverseer.post(edit);
        edit.redo();
    }

    void refreshListModel(ViewerLayer selected) {
        DefaultListModel<String> newModel = new DefaultListModel<>();
        if (!(selected instanceof ShipLayer checkedLayer)) {
            this.listModel = newModel;
            this.modsList.setModel(newModel);
            this.modsList.setEnabled(false);
            return;
        }
        ShipPainter painter = checkedLayer.getPainter();
        if (painter != null && !painter.isUninitialized()) {
            ShipVariant active = painter.getActiveVariant();
            if (active != null && !active.isEmpty()) {
                List<String> entries = active.getSuppressedMods();
                if (entries != null) {
                    newModel.addAll(entries);
                }
                this.modsList.setEnabled(true);
            } else {
                this.modsList.setEnabled(false);
            }
        } else {
            this.modsList.setEnabled(false);
        }
        this.listModel = newModel;
        this.modsList.setModel(newModel);
        boolean enabled = this.modsList.isEnabled();
        if (addField != null) addField.setEnabled(enabled);
        if (addButton != null) addButton.setEnabled(enabled);
        if (pickButton != null) pickButton.setEnabled(enabled);
        if (removeButton != null) removeButton.setEnabled(false);
    }

}
