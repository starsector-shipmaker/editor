package shipeditor.components.instrument.ship.variant.modules;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.control.ControlEvents.FeatureInstallQueued;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.components.instrument.LayerPropertiesPanel;
import shipeditor.components.instrument.ship.centers.ModuleAnchorPanel;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.ViewerEnums.PainterVisibility;
import shipeditor.components.viewer.painters.points.AbstractPointPainter;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.RepresentationEnums.ShipTypeHints;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.ship.VariantFile;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.objects.Pair;
import shipeditor.utility.themes.Themes;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class ModuleControlPanel extends LayerPropertiesPanel {

    private final ModuleList moduleList;

    private ModuleAnchorPanel moduleAnchorWidget;

    private JComboBox<VariantFile> variantPicker;

    private boolean updatingVariants = false;

    private Runnable refreshVariantsAction;

    ModuleControlPanel(ModuleList list) {
        this.moduleList = list;
    }

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        if (refreshVariantsAction != null && variantPicker != null && variantPicker.getItemCount() == 0) {
            refreshVariantsAction.run();
        }
        if (!(layerPainter instanceof ShipPainter shipPainter) || shipPainter.isUninitialized()) {
            fireClearingListeners(layerPainter);

            moduleAnchorWidget.setCenterPainter(null);
            moduleAnchorWidget.refresh(null);
            return;
        }

        fireRefresherListeners(layerPainter);

        moduleAnchorWidget.setCenterPainter(shipPainter.getCenterPointPainter());
        moduleAnchorWidget.refresh(layerPainter);
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BorderLayout());

        JPanel topContainer = new JPanel(new BorderLayout());

        Map<JLabel, JComponent> topWidgets = new LinkedHashMap<>();

        var moduleOpacityWidget = createModuleOpacitySlider();
        topWidgets.put(moduleOpacityWidget.getFirst(), moduleOpacityWidget.getSecond());

        var collisionVisibilityWidget = createCollisionVisibilityWidget();
        topWidgets.put(collisionVisibilityWidget.getFirst(), collisionVisibilityWidget.getSecond());

        var boundsVisibilityWidget = createBoundsVisibilityWidget();
        topWidgets.put(boundsVisibilityWidget.getFirst(), boundsVisibilityWidget.getSecond());

        var slotsVisibilityWidget = createSlotsVisibilityWidget();
        topWidgets.put(slotsVisibilityWidget.getFirst(), slotsVisibilityWidget.getSecond());

        Border bottomPadding = new EmptyBorder(0, 0, 4, 0);

        JPanel topWidgetsPanel = createWidgetsPanel(topWidgets);
        topWidgetsPanel.setBorder(bottomPadding);
        topContainer.add(topWidgetsPanel, BorderLayout.PAGE_START);
        moduleAnchorWidget = new ModuleAnchorPanel();

        String tooltip = Utility.getWithLinebreaks("Mockup editing, changes are not saved to file",
                "Create separate ship layer from module to edit module anchor offset");
        moduleAnchorWidget.setToolTipText(tooltip);

        topContainer.add(moduleAnchorWidget, BorderLayout.CENTER);

        JPanel moduleActionsPanel = createModuleActionsPanel();
        topContainer.add(moduleActionsPanel, BorderLayout.PAGE_END);

        this.add(topContainer, BorderLayout.PAGE_START);
    }

    private Pair<JLabel, JComboBox<PainterVisibility>> createCollisionVisibilityWidget() {
        Function<LayerPainter, AbstractPointPainter> painterGetter = layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                return shipPainter.getCenterPointPainter();
            }
            return null;
        };

        Consumer<PainterVisibility> additionalAction = painterVisibility -> actOnSelectedModules(shipPainter -> {
            var pointPainter = shipPainter.getCenterPointPainter();
            pointPainter.setVisibilityMode(painterVisibility);
        });
        var opacityWidget = createVisibilityWidget(painterGetter, additionalAction);

        JLabel opacityLabel = opacityWidget.getFirst();
        opacityLabel.setText(StringManager.getString("COLLISION_VIEW"));

        return opacityWidget;
    }

    private Pair<JLabel, JComboBox<PainterVisibility>> createBoundsVisibilityWidget() {
        Function<LayerPainter, AbstractPointPainter> painterGetter = layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                return shipPainter.getBoundsPainter();
            }
            return null;
        };

        Consumer<PainterVisibility> additionalAction = painterVisibility -> actOnSelectedModules(shipPainter -> {
            var pointPainter = shipPainter.getBoundsPainter();
            pointPainter.setVisibilityMode(painterVisibility);
        });
        var opacityWidget = createVisibilityWidget(painterGetter, additionalAction);

        JLabel opacityLabel = opacityWidget.getFirst();
        opacityLabel.setText(StringManager.getString("BOUNDS_VIEW"));

        return opacityWidget;
    }

    private Pair<JLabel, JComboBox<PainterVisibility>> createSlotsVisibilityWidget() {
        Function<LayerPainter, AbstractPointPainter> painterGetter = layerPainter -> {
            if (layerPainter instanceof ShipPainter shipPainter) {
                return shipPainter.getWeaponSlotPainter();
            }
            return null;
        };

        Consumer<PainterVisibility> additionalAction = painterVisibility -> actOnSelectedModules(shipPainter -> {
            var pointPainter = shipPainter.getWeaponSlotPainter();
            pointPainter.setVisibilityMode(painterVisibility);
        });
        var opacityWidget = createVisibilityWidget(painterGetter, additionalAction);

        JLabel opacityLabel = opacityWidget.getFirst();
        opacityLabel.setText(StringManager.getString("SLOTS_VIEW"));

        return opacityWidget;
    }

    private Pair<JLabel, JSlider> createModuleOpacitySlider() {
        Consumer<Float> opacitySetter = changedValue -> {
            actOnSelectedModules(shipPainter -> {
                if (shipPainter == null) return;
                shipPainter.setSpriteOpacity(changedValue);
                ShipVariant moduleVariant = shipPainter.getActiveVariant();
                if (moduleVariant == null || moduleVariant.isEmpty()) return;
                moduleVariant.setOpacityForAllFitted(changedValue);
            });
            processChange();
        };

        Function<LayerPainter, Float> opacityGetter = a -> a.getSpriteOpacity();

        Pair<JLabel, JSlider> opacityWidget = super.createOpacityWidget(opacityGetter, opacitySetter);

        JLabel opacityLabel = opacityWidget.getFirst();
        opacityLabel.setText(StringManager.getString("MODULE_OPACITY"));

        return opacityWidget;
    }

    private void actOnSelectedModules(Consumer<ShipPainter> action) {
        if (moduleList == null) return;
        List<InstalledFeature> selectedValuesList = moduleList.getSelectedValuesList();
        if (selectedValuesList != null && !selectedValuesList.isEmpty()) {
            for (InstalledFeature selectedModule : selectedValuesList) {
                if (selectedModule != null && selectedModule.getFeaturePainter() instanceof ShipPainter sp) {
                    action.accept(sp);
                }
            }
        }
    }

    public static boolean isStationModuleVariant(VariantFile variant) {
        if (variant == null || variant.isEmpty()) {
            return false;
        }
        String hullId = variant.getHullId();
        if (hullId == null || hullId.isEmpty()) {
            return false;
        }

        // 1. Check HullSpecFile for moduleAnchor and hullSize
        var spec = GameDataRepository.retrieveSpecByID(hullId);
        if (spec instanceof HullSpecFile hullSpec) {
            if (hullSpec.getModuleAnchor() != null) {
                return true;
            }
            String hullSize = hullSpec.getHullSize();
            if (hullSize != null && "MODULE".equalsIgnoreCase(hullSize)) {
                return true;
            }
        }

        // 2. Check ShipCSVEntry for hints and tags
        ShipCSVEntry csvEntry = GameDataRepository.retrieveShipCSVEntryByID(hullId);
        if (csvEntry != null) {
            List<ShipTypeHints> hints = csvEntry.getBaseHullHints();
            if (hints != null) {
                for (ShipTypeHints hint : hints) {
                    if (hint == ShipTypeHints.MODULE || hint == ShipTypeHints.STATION
                            || hint == ShipTypeHints.UNDER_PARENT || hint == ShipTypeHints.INDEPENDENT_ROTATION) {
                        return true;
                    }
                }
            }
            Map<String, String> row = csvEntry.getRowData();
            if (row != null) {
                String tags = row.get("tags");
                if (tags != null) {
                    String lowerTags = tags.toLowerCase(Locale.ROOT);
                    if (lowerTags.contains("station") || lowerTags.contains("module")) {
                        return true;
                    }
                }
                String hintsStr = row.get("hints");
                if (hintsStr != null) {
                    String lowerHints = hintsStr.toLowerCase(Locale.ROOT);
                    if (lowerHints.contains("module") || lowerHints.contains("station")) {
                        return true;
                    }
                }
            }
        }

        // 3. Check variant ID / hull ID naming heuristics
        String lowerHull = hullId.toLowerCase(Locale.ROOT);
        String lowerVar = variant.getVariantId() != null ? variant.getVariantId().toLowerCase(Locale.ROOT) : "";
        return lowerHull.contains("module") || lowerHull.contains("station") || lowerVar.contains("module") || lowerVar.contains("station");
    }

    private JPanel createModuleActionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(new EmptyBorder(6, 0, 4, 0));

        JPanel headerRow = new JPanel(new BorderLayout());
        JLabel pickerLabel = new JLabel(StringManager.getString("MODULE_VARIANT"));
        pickerLabel.setFont(pickerLabel.getFont().deriveFont(Font.BOLD, 12f));
        headerRow.add(pickerLabel, BorderLayout.WEST);

        JLabel countLabel = new JLabel();
        countLabel.setFont(countLabel.getFont().deriveFont(11f));
        countLabel.setForeground(Themes.getDisabledTextColor());
        headerRow.add(countLabel, BorderLayout.EAST);
        panel.add(headerRow, BorderLayout.NORTH);

        // Filter Controls: Search box + "Station only" checkbox
        JPanel filterRow = new JPanel(new BorderLayout(4, 0));
        JTextField searchField = new JTextField();
        searchField.setToolTipText(StringManager.getString("FILTER_MODULES_BY_NAME"));
        searchField.putClientProperty("JTextField.placeholderText", "Filter modules...");

        JCheckBox stationOnlyCheckBox = new JCheckBox(StringManager.getString("STATION_MODULES_ONLY"), true);
        stationOnlyCheckBox.setToolTipText("Filter out variants that are not designated as station modules");

        filterRow.add(searchField, BorderLayout.CENTER);
        filterRow.add(stationOnlyCheckBox, BorderLayout.EAST);

        // Action row: Variant Combobox + Install Button + Clear Button
        JPanel controlsRow = new JPanel(new BorderLayout(4, 0));

        variantPicker = new JComboBox<>();
        variantPicker.setEnabled(false);

        Runnable refreshVariants = () -> {
            updatingVariants = true;
            try {
                String search = searchField.getText().toLowerCase(Locale.ROOT).trim();
                boolean stationOnly = stationOnlyCheckBox.isSelected();

                VariantFile prevSelected = (VariantFile) variantPicker.getSelectedItem();
                variantPicker.removeAllItems();

                GameDataRepository dataRepository = SettingsManager.getGameData();
                int totalCount = 0;
                if (dataRepository != null) {
                    Map<String, VariantFile> allVariants = dataRepository.getAllVariants();
                    if (allVariants != null) {
                        for (VariantFile variant : allVariants.values()) {
                            if (stationOnly && !isStationModuleVariant(variant)) {
                                continue;
                            }
                            if (!search.isEmpty()) {
                                String displayName = variant.getDisplayName() != null ? variant.getDisplayName().toLowerCase(Locale.ROOT) : "";
                                String varId = variant.getVariantId() != null ? variant.getVariantId().toLowerCase(Locale.ROOT) : "";
                                String hullId = variant.getHullId() != null ? variant.getHullId().toLowerCase(Locale.ROOT) : "";
                                String toString = variant.toString().toLowerCase(Locale.ROOT);
                                if (!displayName.contains(search) && !varId.contains(search) && !hullId.contains(search) && !toString.contains(search)) {
                                    continue;
                                }
                            }
                            variantPicker.addItem(variant);
                            totalCount++;
                        }
                    }
                }

                variantPicker.setEnabled(totalCount > 0);
                if (totalCount > 0) {
                    if (prevSelected != null && variantPicker.getItemCount() > 0) {
                        variantPicker.setSelectedItem(prevSelected);
                    }
                    countLabel.setText("(" + totalCount + " available)");
                } else {
                    countLabel.setText("(0 available)");
                }
            } finally {
                updatingVariants = false;
            }
        };
        this.refreshVariantsAction = refreshVariants;

        searchField.getDocument().addDocumentListener(new SimpleDocumentListener(refreshVariants));

        stationOnlyCheckBox.addActionListener(e -> refreshVariants.run());

        variantPicker.addActionListener(e -> {
            VariantFile selected = (VariantFile) variantPicker.getSelectedItem();
            if (!updatingVariants && selected != null) {
                EventBus.publish(new shipeditor.communication.events.components.ComponentEvents.ShipEntryPicked(selected));
            }
        });

        JButton installButton = new JButton(StringManager.getString("INSTALL_1"),
                FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 16, Themes.getIconColor()));
        installButton.setToolTipText(StringManager.getString("INSTALL_SELECTED_VARIANT_AS_MODULE_TO_THE_SELECTED_SLOT"));
        installButton.addActionListener(e -> {
            VariantFile selected = (VariantFile) variantPicker.getSelectedItem();
            if (selected != null) {
                EventBus.publish(new shipeditor.communication.events.components.ComponentEvents.ShipEntryPicked(selected));
                EventBus.publish(new FeatureInstallQueued(null));
            }
        });

        JButton clearButton = new JButton(StringManager.getString("CLEAR"),
                FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        clearButton.setToolTipText(StringManager.getString("REMOVE_SELECTED_MODULE_FROM_VARIANT"));
        clearButton.addActionListener(e -> {
            if (moduleList == null) return;
            InstalledFeature selectedFeature = moduleList.getSelectedValue();
            if (selectedFeature == null) return;
            LayerPainter painter = getCachedLayerPainter();
            if (!(painter instanceof ShipPainter shipPainter)) return;
            ShipVariant variant = shipPainter.getActiveVariant();
            if (variant == null || variant.isEmpty()) return;
            var modules = variant.getFittedModules();
            String slotID = selectedFeature.getSlotID();
            InstalledFeature toRemove = modules.get(slotID);
            if (toRemove != null) {
                EditDispatch.postFeatureUninstalled(modules, slotID, toRemove, null);
            }
        });

        controlsRow.add(variantPicker, BorderLayout.CENTER);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttonsPanel.add(installButton);
        buttonsPanel.add(clearButton);
        controlsRow.add(buttonsPanel, BorderLayout.LINE_END);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.add(filterRow);
        contentPanel.add(Box.createVerticalStrut(4));
        contentPanel.add(controlsRow);

        panel.add(contentPanel, BorderLayout.CENTER);

        // Initial population
        refreshVariants.run();

        return panel;
    }

    private record SimpleDocumentListener(Runnable callback) implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            callback.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            callback.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            callback.run();
        }
    }

}
