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

    SuppressedModsPanel() {
        this.setLayout(new BorderLayout());
        ComponentUtilities.outfitPanelWithTitle(this, "Suppressed Mods");

        listModel = new DefaultListModel<>();
        modsList = new JList<>(listModel);
        modsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modsList.setBorder(new LineBorder(Themes.getBorderColor()));
        modsList.setToolTipText("Press Delete to remove selected suppressed hullmod");
        modsList.setEnabled(false);
        modsList.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE
                        || e.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE) {
                    String selected = modsList.getSelectedValue();
                    if (selected != null) {
                        listModel.removeElement(selected);
                        syncBackToVariant();
                    }
                }
            }
        });
        this.add(new JScrollPane(modsList), BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new BorderLayout(4, 0));
        addField = new JTextField();
        addField.setToolTipText(StringManager.getString("ENTER_HULLMOD_ID_TO_SUPPRESS_THEN_PRESS_ADD_OR_ENTER"));
        addField.putClientProperty("JTextField.placeholderText", "Hullmod ID (or click Add to browse)...");
        addField.addActionListener(e -> addFromField());
        addField.setEnabled(false);
        controlPanel.add(addField, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));

        addButton = new JButton(StringManager.getString("ADD"),
                FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 14, Themes.getIconColor()));
        addButton.setMargin(new java.awt.Insets(1, 6, 1, 6));
        addButton.setToolTipText("Add typed hullmod ID, or browse to pick if field is empty (press Delete on list to remove)");
        addButton.setEnabled(false);
        addButton.addActionListener(e -> {
            String text = addField.getText();
            if (text != null && !text.isBlank()) {
                addFromField();
                return;
            }
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
        buttonsPanel.add(addButton);

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

        List<String> updatedList = new java.util.ArrayList<>(listModel.getSize());
        for (int i = 0; i < listModel.getSize(); i++) {
            updatedList.add(listModel.getElementAt(i));
        }

        ShipVariant variant = shipeditor.utility.overseers.StaticController.getOrCreateEditableVariant(shipLayer);
        if (variant == null || variant.isEmpty()) return;

        List<String> oldList = variant.getSuppressedMods();
        if (oldList == null) {
            oldList = new java.util.ArrayList<>();
        } else {
            oldList = new java.util.ArrayList<>(oldList);
        }

        shipeditor.undo.edits.features.FeatureEdits.SuppressedModsEdit edit =
                new shipeditor.undo.edits.features.FeatureEdits.SuppressedModsEdit(variant, activeLayer, oldList, updatedList);
        shipeditor.undo.UndoOverseer.post(edit);
        edit.redo();
    }

    void refreshListModel(ViewerLayer selected) {
        DefaultListModel<String> newModel = new DefaultListModel<>();
        if (!(selected instanceof ShipLayer checkedLayer)) {
            this.listModel = newModel;
            this.modsList.setModel(newModel);
            this.modsList.setEnabled(false);
            if (addField != null) addField.setEnabled(false);
            if (addButton != null) addButton.setEnabled(false);
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
            }
            this.modsList.setEnabled(true);
        } else {
            this.modsList.setEnabled(false);
        }
        this.listModel = newModel;
        this.modsList.setModel(newModel);
        boolean enabled = this.modsList.isEnabled();
        if (addField != null) addField.setEnabled(enabled);
        if (addButton != null) addButton.setEnabled(enabled);
    }

}
