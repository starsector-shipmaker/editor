package shipeditor.components.instrument.ship.skins;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.components.datafiles.entities.WingCSVEntry;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ActiveShipSpec;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.undo.UndoOverseer;
import shipeditor.undo.edits.features.SkinOverrideEdits.SkinListOverrideEdit;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.UIConstants;
import shipeditor.utility.themes.Themes;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class SkinRemovalsPanel extends JPanel {

    private final JComboBox<ShipSkin> skinChooser;
    private final JLabel statusLabel;
    private final JPanel contentContainer;
    private ShipPainter cachedPainter;
    private boolean isUpdatingSkinChooser;

    public SkinRemovalsPanel() {
        this.setLayout(new BorderLayout());

        JPanel headerPanel = new JPanel(new BorderLayout(6, 0));
        headerPanel.setBorder(new EmptyBorder(4, 6, 2, 6));
        JLabel skinLabel = new JLabel("Skin:");
        skinChooser = new JComboBox<>();
        skinChooser.addActionListener(e -> {
            if (isUpdatingSkinChooser || cachedPainter == null || cachedPainter.isUninitialized()) {
                return;
            }
            ShipSkin chosen = (ShipSkin) skinChooser.getSelectedItem();
            ActiveShipSpec spec = (chosen != null && !chosen.isBase()) ? ActiveShipSpec.SKIN : ActiveShipSpec.HULL;
            cachedPainter.setActiveSpec(spec, chosen);
        });
        headerPanel.add(skinLabel, BorderLayout.LINE_START);
        headerPanel.add(skinChooser, BorderLayout.CENTER);

        statusLabel = new JLabel(StringManager.getString("NO_SKIN_ACTIVE"));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(headerPanel, BorderLayout.PAGE_START);
        topContainer.add(statusLabel, BorderLayout.CENTER);

        contentContainer = new JPanel();
        contentContainer.setLayout(new BoxLayout(contentContainer, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(contentContainer);
        scrollPane.setBorder(UIConstants.EMPTY_BORDER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        this.add(topContainer, BorderLayout.PAGE_START);
        this.add(scrollPane, BorderLayout.CENTER);

        this.initEventListening();
    }

    private void initEventListening() {
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerWasSelected checked) {
                handleLayerChange(checked.selected());
            } else if (event instanceof ActiveLayerUpdated checked) {
                handleLayerChange(checked.updated());
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.SKIN_REMOVALS) {
                    refreshContent();
                }
            }
        });
    }

    private void handleLayerChange(ViewerLayer layer) {
        if (layer instanceof ShipLayer shipLayer) {
            ShipPainter painter = shipLayer.getPainter();
            if (painter != null && !painter.isUninitialized()) {
                this.cachedPainter = painter;
                refreshContent();
                return;
            }
        }
        this.cachedPainter = null;
        refreshContent();
    }

    private void updateSkinChooser() {
        isUpdatingSkinChooser = true;
        skinChooser.removeAllItems();
        if (cachedPainter != null && !cachedPainter.isUninitialized()) {
            ShipLayer parentLayer = cachedPainter.getParentLayer();
            if (parentLayer != null && parentLayer.getSkins() != null) {
                for (ShipSkin skin : parentLayer.getSkins()) {
                    skinChooser.addItem(skin);
                }
                skinChooser.setSelectedItem(cachedPainter.getActiveSkin());
                skinChooser.setEnabled(skinChooser.getItemCount() > 0);
            } else {
                skinChooser.setEnabled(false);
            }
        } else {
            skinChooser.setEnabled(false);
        }
        isUpdatingSkinChooser = false;
    }

    private void refreshContent() {
        updateSkinChooser();
        contentContainer.removeAll();

        if (cachedPainter == null || cachedPainter.isUninitialized()) {
            statusLabel.setText(StringManager.getString("NO_SHIP_LAYER_SELECTED"));
            contentContainer.revalidate();
            contentContainer.repaint();
            return;
        }

        ShipSkin activeSkin = cachedPainter.getActiveSkin();
        boolean isSkinActive = activeSkin != null && !activeSkin.isBase();

        if (isSkinActive) {
            statusLabel.setText(StringManager.getString("SKIN") + activeSkin);
        } else {
            statusLabel.setText(StringManager.getString("NO_SKIN_ACTIVE_SELECT_A_SKIN_IN_THE_SKIN_DATA_TAB"));
        }

        contentContainer.add(createRemovalListPanel("Remove Weapon Slots", activeSkin,
                isSkinActive ? activeSkin::getRemoveWeaponSlots : () -> null,
                isSkinActive ? activeSkin::setRemoveWeaponSlots : list -> {},
                String.class, "Enter Slot ID", isSkinActive));

        contentContainer.add(createRemovalListPanel("Remove Engine Slots", activeSkin,
                isSkinActive ? activeSkin::getRemoveEngineSlots : () -> null,
                isSkinActive ? activeSkin::setRemoveEngineSlots : list -> {},
                Integer.class, "Enter Engine Index (e.g. 0)", isSkinActive));

        contentContainer.add(createRemovalListPanel("Remove Built-in Weapons", activeSkin,
                isSkinActive ? activeSkin::getRemoveBuiltInWeapons : () -> null,
                isSkinActive ? activeSkin::setRemoveBuiltInWeapons : list -> {},
                String.class, "Enter Slot ID", isSkinActive));

        contentContainer.add(createRemovalListPanel("Remove Built-in Hullmods", activeSkin,
                isSkinActive ? activeSkin::getRemoveBuiltInMods : () -> null,
                isSkinActive ? activeSkin::setRemoveBuiltInMods : list -> {},
                HullmodCSVEntry.class, "Enter Hullmod ID", isSkinActive));

        contentContainer.add(createRemovalListPanel("Remove Built-in Wings", activeSkin,
                isSkinActive ? activeSkin::getRemoveBuiltInWings : () -> null,
                isSkinActive ? activeSkin::setRemoveBuiltInWings : list -> {},
                WingCSVEntry.class, "Enter Wing ID", isSkinActive));

        contentContainer.add(Box.createVerticalGlue());
        contentContainer.revalidate();
        contentContainer.repaint();
    }

    private <T> JPanel createRemovalListPanel(String title, ShipSkin skin,
                                              Supplier<List<T>> getter, Consumer<List<T>> setter,
                                              Class<T> typeClass, String inputHint,
                                              boolean isSkinActive) {
        JPanel panel = new JPanel(new BorderLayout());
        ComponentUtilities.outfitPanelWithTitle(panel, title);

        List<T> currentList = getter.get();
        if (currentList == null) currentList = new ArrayList<>();

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (T item : currentList) {
            if (item instanceof HullmodCSVEntry entry) {
                listModel.addElement(entry.getID());
            } else if (item instanceof WingCSVEntry entry) {
                listModel.addElement(entry.getID());
            } else {
                listModel.addElement(String.valueOf(item));
            }
        }

        JList<String> jList = new JList<>(listModel);
        jList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jList.setVisibleRowCount(4);
        jList.setEnabled(isSkinActive);
        JScrollPane scrollPane = new JScrollPane(jList);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new BorderLayout(4, 0));
        JTextField inputField = new JTextField();
        inputField.setToolTipText(inputHint);
        inputField.putClientProperty("JTextField.placeholderText", inputHint);
        inputField.setEnabled(isSkinActive);
        JButton addButton = new JButton(StringManager.getString("ADD_1"),
                FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 16, Themes.getIconColor()));
        addButton.setEnabled(isSkinActive);
        JButton removeButton = new JButton(StringManager.getString("REMOVE"),
                FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        removeButton.setEnabled(isSkinActive && !listModel.isEmpty());

        addButton.addActionListener(e -> {
            if (!isSkinActive || skin == null) return;
            String text = inputField.getText().trim();
            if (text.isEmpty() || listModel.contains(text)) return;

            T item = parseInput(text, typeClass);
            if (item != null) {
                List<T> newList = new ArrayList<>(getter.get() != null ? getter.get() : new ArrayList<>());
                newList.add(item);

                var edit = new SkinListOverrideEdit<>(setter, getter.get(), newList, EditorInstrument.SKIN_REMOVALS, skin);
                UndoOverseer.post(edit);
                edit.redo();
            }
        });

        removeButton.addActionListener(e -> {
            if (!isSkinActive || skin == null) return;
            int selected = jList.getSelectedIndex();
            if (selected != -1) {
                List<T> newList = new ArrayList<>(getter.get());
                newList.remove(selected);
                if (newList.isEmpty()) newList = null;

                var edit = new SkinListOverrideEdit<>(setter, getter.get(), newList, EditorInstrument.SKIN_REMOVALS, skin);
                UndoOverseer.post(edit);
                edit.redo();
            }
        });

        jList.addListSelectionListener(e -> {
            if (isSkinActive) {
                removeButton.setEnabled(jList.getSelectedIndex() != -1);
            }
        });

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 4, 0));
        btnPanel.add(addButton);
        btnPanel.add(removeButton);

        controlPanel.add(inputField, BorderLayout.CENTER);
        controlPanel.add(btnPanel, BorderLayout.LINE_END);
        controlPanel.setBorder(new EmptyBorder(4, 0, 0, 0));

        panel.add(controlPanel, BorderLayout.PAGE_END);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        return panel;
    }

    @SuppressWarnings("unchecked")
    private <T> T parseInput(String input, Class<T> typeClass) {
        if (typeClass == String.class) {
            return (T) input;
        } else if (typeClass == Integer.class) {
            try {
                return (T) Integer.valueOf(input);
            } catch (NumberFormatException e) {
                return null;
            }
        } else if (typeClass == HullmodCSVEntry.class) {
            HullmodCSVEntry entry = GameDataRepository.retrieveHullmodCSVEntryByID(input);
            if (entry == null) {
                JOptionPane.showMessageDialog(this, StringManager.getString("UNKNOWN_HULLMOD_ID_MSG") + input, "Error", JOptionPane.ERROR_MESSAGE);
            }
            return (T) entry;
        } else if (typeClass == WingCSVEntry.class) {
            WingCSVEntry entry = GameDataRepository.retrieveWingCSVEntryByID(input);
            if (entry == null) {
                JOptionPane.showMessageDialog(this, StringManager.getString("UNKNOWN_WING_ID_MSG") + input, "Error", JOptionPane.ERROR_MESSAGE);
            }
            return (T) entry;
        }
        return null;
    }
}
