package shipeditor.components.instrument.ship.shared;

import shipeditor.utility.text.StringManager;

import shipeditor.components.instrument.LayerPropertiesPanel;
import shipeditor.components.viewer.entities.weapon.SlotData;
import shipeditor.components.viewer.entities.weapon.SlotPoint;
import shipeditor.components.viewer.entities.weapon.WeaponSlotOverride;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.widgets.Spinners;
import shipeditor.utility.objects.Pair;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractSlotValuesPanel extends LayerPropertiesPanel {
    /**
     * Registers clear/refresh behavior for a slot widget whose value can be locked by a skin override.
     *
     * @param clearValue    resets the widget value when nothing is selected.
     * @param showValue     displays the selected slot's value in the widget.
     * @param overrideCheck returns non-null if the skin override defines the property this widget edits.
     */
    private void registerSkinOverrideWidget(JComponent widget, Runnable clearValue,
                                            Consumer<SlotData> showValue,
                                            java.util.function.Function<WeaponSlotOverride, Object> overrideCheck,
                                            String lockedTooltipKey) {
        registerWidgetListeners(widget, layerPainter -> {
            clearValue.run();
            widget.setToolTipText(null);
            widget.setEnabled(false);
        }, layerPainter -> {
            var selectedSlot = getSelectedFromLayer(layerPainter);

            if (selectedSlot != null) {
                WeaponSlotOverride skinOverride = null;
                if (selectedSlot instanceof SlotPoint checked) {
                    skinOverride = checked.getSkinOverride();
                }

                if (skinOverride != null && overrideCheck.apply(skinOverride) != null) {
                    widget.setToolTipText(StringManager.getString(lockedTooltipKey));
                    widget.setEnabled(false);
                } else {
                    showValue.accept(selectedSlot);
                    widget.setEnabled(true);
                }
            } else {
                clearValue.run();
                widget.setToolTipText(null);
                widget.setEnabled(false);
            }
        });
    }

    private void registerSkinOverrideSpinner(JSpinner spinner, java.util.function.Function<WeaponSlotOverride, Object> overrideCheck, java.util.function.Function<SlotData, Double> valueGetter, String lockedTooltipKey) {
        registerSkinOverrideWidget(spinner, () -> spinner.setValue(0.0d),
                slot -> spinner.setValue(valueGetter.apply(slot)), overrideCheck, lockedTooltipKey);
    }

    private <V> void registerSkinOverrideComboBox(JComboBox<V> comboBox, java.util.function.Function<WeaponSlotOverride, Object> overrideCheck, java.util.function.Function<SlotData, V> valueGetter, String lockedTooltipKey) {
        registerSkinOverrideWidget(comboBox, () -> comboBox.setSelectedItem(null),
                slot -> comboBox.setSelectedItem(valueGetter.apply(slot)), overrideCheck, lockedTooltipKey);
    }

    private final boolean multiSelectionAllowed;

    protected AbstractSlotValuesPanel(boolean multiSelection) {
        this.multiSelectionAllowed = multiSelection;
    }

    protected abstract String getEntityName();

    protected abstract SlotData getSelectedFromLayer(LayerPainter layerPainter);

    @Override
    public ShipPainter getCachedLayerPainter() {
        return (ShipPainter) super.getCachedLayerPainter();
    }

    /**
     * @return ID from painter of cached layer that is not yet assigned to any slot.
     */
    protected abstract String getNextUniqueID();

    protected abstract Consumer<String> getIDSetter();

    protected abstract Consumer<WeaponType> getTypeSetter();

    protected abstract Consumer<WeaponMount> getMountSetter();

    protected abstract Consumer<WeaponSize> getSizeSetter();

    protected abstract Consumer<Double> getAngleSetter();

    protected abstract Consumer<Double> getArcSetter();

    protected abstract Consumer<Double> getRenderOrderSetter();

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        if (layerPainter == null || layerPainter.isUninitialized()) {
            fireClearingListeners(null);
            return;
        }

        fireRefresherListeners(layerPainter);
    }

    protected boolean shouldIncludeTypeSelector() {
        return true;
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BorderLayout());

        if (shouldIncludeTypeSelector()) {
            JPanel suffixesPanel = AbstractSlotValuesPanel.createSuffixesWidgetPanel();

            this.add(suffixesPanel, BorderLayout.PAGE_START);
        }

        Map<JLabel, JComponent> widgets = new LinkedHashMap<>();

        var slotIdWidget = createIDWidget();
        widgets.put(slotIdWidget.getFirst(), slotIdWidget.getSecond());

        if (shouldIncludeTypeSelector()) {
            var slotTypeWidget = createTypeSelector();
            widgets.put(slotTypeWidget.getFirst(), slotTypeWidget.getSecond());
        }

        var slotMountWidget = createMountSelector();
        widgets.put(slotMountWidget.getFirst(), slotMountWidget.getSecond());

        var slotSizeSelector = createSizeSelector();
        widgets.put(slotSizeSelector.getFirst(), slotSizeSelector.getSecond());

        var angleController = createAngleController();
        widgets.put(angleController.getFirst(), angleController.getSecond());

        var arcController = createArcController();
        widgets.put(arcController.getFirst(), arcController.getSecond());

        var renderOrderController = createRenderOrderController();
        widgets.put(renderOrderController.getFirst(), renderOrderController.getSecond());

        JPanel widgetsPanel = createWidgetsPanel(widgets);
        this.add(widgetsPanel, BorderLayout.CENTER);
    }

    private static JPanel createSuffixesWidgetPanel() {
        JPanel suffixesPanel = new JPanel();
        suffixesPanel.setLayout(new BoxLayout(suffixesPanel, BoxLayout.LINE_AXIS));

        JCheckBox numericSuffixesWidget = new JCheckBox(StringManager.getString("APPEND_NUMERIC_SUFFIXES_TO_IDS"));
        numericSuffixesWidget.setSelected(SettingsManager.isNumericSuffixesForSlotsEnabled());
        Settings settings = SettingsManager.getSettings();
        numericSuffixesWidget.addActionListener(
                e -> settings.setNumericSuffixesForSlots(numericSuffixesWidget.isSelected())
        );
        suffixesPanel.add(numericSuffixesWidget);
        return suffixesPanel;
    }

    @Override
    protected void addWidgetRow(JPanel contentContainer, JLabel label, JComponent component, int ordering) {
        ComponentUtilities.addLabelAndComponent(contentContainer,
                label, component, 2, 2, 0, ordering);
    }

    private Pair<JLabel, JComponent> createIDWidget() {
        JLabel label = new JLabel(getEntityName() + " ID:");

        if (multiSelectionAllowed) {
            label.setToolTipText(StringManager.getString("CHANGE_APPLIES_TO_ALL_SELECTED_SLOTS"));
        }

        JTextField editor = new JTextField();
        editor.putClientProperty("JTextField.placeholderText", getEntityName() + " ID...");
        editor.setColumns(10);
        editor.addActionListener(e -> {
            if (isWidgetsReadyForInput()) {
                String currentText = editor.getText();
                Consumer<String> setter = getIDSetter();
                setter.accept(currentText);
            }
        });

        JPopupMenu contextMenu = getIDMenu(editor);
        String confirmHint = StringManager.getString("ENTER_TO_SAVE_CHANGES");
        String menuHint = StringManager.getString("RIGHT_CLICK_TO_GENERATE");
        editor.setToolTipText(Utility.getWithLinebreaks(confirmHint, menuHint));
        editor.addMouseListener(new EditorMouseListener(contextMenu));

        registerWidgetListeners(editor, layerPainter -> {
            editor.setText("");
            editor.setEnabled(false);
        }, layerPainter -> {
            var selectedSlot = getSelectedFromLayer(layerPainter);
            if (selectedSlot != null) {
                editor.setText(selectedSlot.getId());
                editor.setEnabled(true);
            } else {
                editor.setText("");
                editor.setEnabled(false);
            }
        });

        return new Pair<>(label, editor);
    }

    private JPopupMenu getIDMenu(JTextField editor) {
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem createNextUniqueId = new JMenuItem(StringManager.getString("CREATE_NEXT_UNIQUE_ID"));
        createNextUniqueId.addActionListener(e -> {
            String nextUniqueID = getNextUniqueID();
            if (nextUniqueID != null) {
                editor.setText(nextUniqueID);
            }
        });
        contextMenu.add(createNextUniqueId);
        return contextMenu;
    }

    private Pair<JLabel, JComponent> createTypeSelector() {
        JComboBox<WeaponType> typeSelector = new JComboBox<>(WeaponType.values());
        typeSelector.removeItem(WeaponType.LAUNCH_BAY);

        return createEnumSelector(" type:", typeSelector, WeaponType.class, this::getTypeSetter,
                WeaponSlotOverride::getWeaponType, SlotData::getWeaponType, "LOCKED_TYPE_OVERRIDDEN_BY_SKIN");
    }

    private Pair<JLabel, JComponent> createMountSelector() {
        JComboBox<WeaponMount> mountSelector = new JComboBox<>(WeaponMount.values());

        return createEnumSelector(" mount:", mountSelector, WeaponMount.class, this::getMountSetter,
                WeaponSlotOverride::getWeaponMount, SlotData::getWeaponMount, "LOCKED_MOUNT_OVERRIDDEN_BY_SKIN");
    }

    private Pair<JLabel, JComponent> createSizeSelector() {
        JComboBox<WeaponSize> sizeSelector = new JComboBox<>(WeaponSize.values());

        return createEnumSelector(" size:", sizeSelector, WeaponSize.class, this::getSizeSetter,
                WeaponSlotOverride::getWeaponSize, SlotData::getWeaponSize, "LOCKED_SIZE_OVERRIDDEN_BY_SKIN");
    }

    /**
     * Builds a label + combo box pair editing an enum property of the selected slots.
     *
     * @param labelSuffix text appended to the entity name for the label.
     */
    private <V> Pair<JLabel, JComponent> createEnumSelector(String labelSuffix, JComboBox<V> selector,
                                                            Class<V> valueType,
                                                            Supplier<Consumer<V>> setterSupplier,
                                                            java.util.function.Function<WeaponSlotOverride, Object> overrideCheck,
                                                            java.util.function.Function<SlotData, V> valueGetter,
                                                            String lockedTooltipKey) {
        JLabel selectorLabel = new JLabel(getEntityName() + labelSuffix);
        if (multiSelectionAllowed) {
            selectorLabel.setToolTipText(StringManager.getString("CHANGE_APPLIES_TO_ALL_SELECTED_SLOTS"));
        }

        selector.addActionListener(e -> {
            Object selectedItem = selector.getSelectedItem();
            if (isWidgetsReadyForInput() && valueType.isInstance(selectedItem)) {
                Consumer<V> setter = setterSupplier.get();
                setter.accept(valueType.cast(selectedItem));
            }
        });

        registerSkinOverrideComboBox(selector, overrideCheck, valueGetter, lockedTooltipKey);

        return new Pair<>(selectorLabel, selector);
    }

    private Pair<JLabel, JComponent> createAngleController() {
        JLabel selectorLabel = new JLabel(getEntityName() + " angle:");

        return createSpinnerController(selectorLabel, -360, 360, 0.5d, this::getAngleSetter,
                WeaponSlotOverride::getBoxedAngle, SlotData::getAngle, "LOCKED_ANGLE_OVERRIDDEN_BY_SKIN");
    }

    private Pair<JLabel, JComponent> createArcController() {
        JLabel selectorLabel = new JLabel(getEntityName() + " arc:");

        return createSpinnerController(selectorLabel, 0, 360, 1.0d, this::getArcSetter,
                WeaponSlotOverride::getBoxedArc, SlotData::getArc, "LOCKED_ARC_OVERRIDDEN_BY_SKIN");
    }

    private Pair<JLabel, JComponent> createRenderOrderController() {
        JLabel selectorLabel = new JLabel(StringManager.getString("RENDER_ORDER"));

        return createSpinnerController(selectorLabel, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, 1.0d,
                this::getRenderOrderSetter, WeaponSlotOverride::getRenderOrderModBoxed,
                slot -> (double) slot.getRenderOrderMod(), "LOCKED_RENDER_ORDER_OVERRIDDEN_BY_SKIN");
    }

    /**
     * Builds the wheelable spinner for a numeric slot property, tooltipping the label and wiring skin override locking.
     */
    private Pair<JLabel, JComponent> createSpinnerController(JLabel selectorLabel, double minValue, double maxValue,
                                                             double step, Supplier<Consumer<Double>> setterSupplier,
                                                             java.util.function.Function<WeaponSlotOverride, Object> overrideCheck,
                                                             java.util.function.Function<SlotData, Double> valueGetter,
                                                             String lockedTooltipKey) {
        String tooltip;
        if (multiSelectionAllowed) {
            tooltip = Utility.getWithLinebreaks(StringManager.getString("CHANGE_APPLIES_TO_FIRST_SELECTED_SLOT"), StringManager.getString("MOUSEWHEEL_TO_CHANGE"));
        } else {
            tooltip = StringManager.getString("MOUSEWHEEL_TO_CHANGE");
        }
        selectorLabel.setToolTipText(tooltip);

        SpinnerNumberModel spinnerNumberModel = new SpinnerNumberModel(
                0.0d, minValue, maxValue, step
        );
        JSpinner spinner =  Spinners.createWheelable(spinnerNumberModel);

        spinner.addChangeListener(e -> {
            if (isWidgetsReadyForInput()) {
                Number modelNumber = spinnerNumberModel.getNumber();
                double current = modelNumber.doubleValue();

                Consumer<Double> setter = setterSupplier.get();
                setter.accept(current);
            }
        });

        registerSkinOverrideSpinner(spinner, overrideCheck, valueGetter, lockedTooltipKey);

        return new Pair<>(selectorLabel, spinner);
    }

    private static class EditorMouseListener extends MouseAdapter {
        private final JPopupMenu contextMenu;

        public EditorMouseListener(JPopupMenu contextMenu) {
            this.contextMenu = contextMenu;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getSource() instanceof JTextField editor) {
                if (SwingUtilities.isRightMouseButton(e) && editor.isEnabled()) {
                    contextMenu.show(editor, e.getX(), e.getY());
                }
            }
        }
    }

}
