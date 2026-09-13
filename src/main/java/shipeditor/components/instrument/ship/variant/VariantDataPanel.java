package shipeditor.components.instrument.ship.variant;

import shipeditor.utility.text.StringManager;

import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.Map;
import java.util.function.Consumer;

public class VariantDataPanel extends JPanel {
    private final JLabel shipHullIDLabel;
    private final JTextField variantIDEditor;
    private final JTextField variantDisplayNameEditor;
    private final DataSpinnerContainer<Integer> ventsSpinner;
    private final DataSpinnerContainer<Integer> capacitorsSpinner;
    private final DataSpinnerContainer<Double> qualitySpinner;
    private final JCheckBox goalVariantCheckbox;

    private Consumer<String> variantIDSetter;
    private Consumer<String> variantDisplayNameSetter;
    private Consumer<Boolean> goalVariantSetter;

    private ShipLayer selectedLayer;
    private ShipVariant cachedVariant;

    public VariantDataPanel(Runnable onRefreshOrdnance) {
        this.setLayout(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(this, "Variant data");

        JLabel shipHullIDConstLabel = new JLabel(StringManager.getString("SHIP_HULL_ID"));
        shipHullIDConstLabel.setBorder(new EmptyBorder(2, 0, 5, 0));
        shipHullIDLabel = new JLabel(StringManager.getString("NOT_INITIALIZED"));
        ComponentUtilities.addLabelAndComponent(this, shipHullIDConstLabel, shipHullIDLabel, 0);

        JLabel variantIDLabel = new JLabel(StringManager.getString("VARIANT_ID"));
        variantIDEditor = new JTextField();
        String editorIDTooltip = Utility.getWithLinebreaks(StringManager.getString("TYPE_AND_PRESS_ENTER_TO_EDIT_ID"),
                "Original variant will be copied with new ID, old entry reloaded");
        variantIDEditor.setToolTipText(editorIDTooltip);
        variantIDEditor.putClientProperty("JTextField.placeholderText", "Variant ID...");

        variantIDEditor.addActionListener(e -> {
            if (selectedLayer == null || cachedVariant == null) return;
            Map<String, ShipVariant> loadedVariants = selectedLayer.getLoadedVariants();
            String variantId = cachedVariant.getVariantId();
            ShipVariant renamed = loadedVariants.remove(variantId);

            String currentText = variantIDEditor.getText();
            variantIDSetter.accept(currentText);

            loadedVariants.put(currentText, renamed);
            renamed.setLoadedFromFile(false);
            ShipPainter shipPainter = selectedLayer.getPainter();
            shipPainter.selectVariant(renamed);
        });
        ComponentUtilities.addLabelAndComponent(this, variantIDLabel, variantIDEditor, 1);

        JLabel variantDisplayNameLabel = new JLabel(StringManager.getString("DISPLAY_NAME"));
        variantDisplayNameEditor = new JTextField();
        String editorNameTooltip = Utility.getWithLinebreaks(StringManager.getString("TYPE_AND_PRESS_ENTER_TO_EDIT_ID"));
        variantDisplayNameEditor.setToolTipText(editorNameTooltip);
        variantDisplayNameEditor.putClientProperty("JTextField.placeholderText", "Display name...");

        variantDisplayNameEditor.addActionListener(e -> {
            if (selectedLayer == null || cachedVariant == null) return;
            String currentText = variantDisplayNameEditor.getText();
            variantDisplayNameSetter.accept(currentText);

            ShipPainter shipPainter = selectedLayer.getPainter();
            shipPainter.selectVariant(cachedVariant);
        });
        ComponentUtilities.addLabelAndComponent(this, variantDisplayNameLabel, variantDisplayNameEditor, 2);

        SpinnerNumberModel ventsModel = new SpinnerNumberModel(0, 0, 0, 1);
        JSpinner ventsSpinnerComponent = new JSpinner(ventsModel);
        ventsSpinner = new DataSpinnerContainer<>(ventsModel, ventsSpinnerComponent);
        this.addDataSpinner(this, "Flux vents:", false, ventsSpinner, 3, onRefreshOrdnance);

        SpinnerNumberModel capacitorsModel = new SpinnerNumberModel(0, 0, 0, 1);
        JSpinner capacitorSpinnerComponent = new JSpinner(capacitorsModel);
        capacitorsSpinner = new DataSpinnerContainer<>(capacitorsModel, capacitorSpinnerComponent);
        this.addDataSpinner(this, "Flux capacitors:", false, capacitorsSpinner, 4, onRefreshOrdnance);

        SpinnerNumberModel qualityModel = new SpinnerNumberModel(0.0d, -1.0d, 0.0d, 0.05d);
        JSpinner qualitySpinnerComponent = new JSpinner(qualityModel);
        qualitySpinnerComponent.setToolTipText(StringManager.getString("VALUES_LESS_THAN_0_INDICATE_THE_FIELD_IS_OMITTED_FROM_VARIANT_FILE"));
        qualitySpinner = new DataSpinnerContainer<>(qualityModel, qualitySpinnerComponent);
        this.addDataSpinner(this, "Variant quality:", true, qualitySpinner, 5, onRefreshOrdnance);

        goalVariantCheckbox = new JCheckBox(StringManager.getString("GOAL_VARIANT"));
        goalVariantCheckbox.addItemListener(e -> {
            boolean enableGoal = goalVariantCheckbox.isSelected();
            if (goalVariantSetter != null) {
                goalVariantSetter.accept(enableGoal);
            }
        });

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 3, 0, 3);
        constraints.gridx = 0;
        constraints.gridy = 6;
        constraints.weightx = 0.0;
        constraints.anchor = GridBagConstraints.LINE_START;
        this.add(goalVariantCheckbox, constraints);
    }

    public void disableDataSpinners() {
        ventsSpinner.disableSpinner();
        capacitorsSpinner.disableSpinner();
        qualitySpinner.disableSpinner();
    }

    public void installPlaceholders() {
        disableDataSpinners();
        shipHullIDLabel.setText(StringManager.getString("NOT_INITIALIZED"));
        variantIDEditor.setEnabled(false);
        variantIDEditor.setText("");
        variantDisplayNameEditor.setEnabled(false);
        variantDisplayNameEditor.setText("");
        goalVariantCheckbox.setSelected(false);
        goalVariantCheckbox.setEnabled(false);
        variantIDSetter = null;
        variantDisplayNameSetter = null;
        goalVariantSetter = null;
        selectedLayer = null;
        cachedVariant = null;
    }

    public void refresh(ShipLayer layer, ShipVariant variant) {
        installPlaceholders();
        if (layer == null || variant == null || variant.isEmpty()) return;

        selectedLayer = layer;
        cachedVariant = variant;

        ShipHull shipHull = layer.getHull();
        if (shipHull == null) return;

        HullSize hullSize = shipHull.getHullSize();
        int maxFluxRegulators = hullSize.getMaxFluxRegulators();

        ventsSpinner.enableSpinner(layer, variant.getFluxVents(), maxFluxRegulators, val -> variant.setFluxVents(val != null ? val : 0));
        capacitorsSpinner.enableSpinner(layer, variant.getFluxCapacitors(), maxFluxRegulators, val -> variant.setFluxCapacitors(val != null ? val : 0));
        qualitySpinner.enableSpinner(layer, variant.getQuality(), 1.0d, val -> variant.setQuality(val != null ? val : 0.0d));

        shipHullIDLabel.setText(variant.getShipHullId());

        variantIDEditor.setText(variant.getVariantId());
        variantIDEditor.setEnabled(true);
        variantIDSetter = variant::setVariantId;

        variantDisplayNameEditor.setText(variant.getDisplayName());
        variantDisplayNameEditor.setEnabled(true);
        variantDisplayNameSetter = variant::setDisplayName;

        goalVariantSetter = val -> variant.setGoalVariant(val != null && val);
        goalVariantCheckbox.setSelected(variant.isGoalVariant());
        goalVariantCheckbox.setEnabled(true);
    }

    @SuppressWarnings("unchecked")
    private <T extends Number> void addDataSpinner(JPanel target, String labelText, boolean useFloatingPoint,
                                                   DataSpinnerContainer<T> spinnerContainer, int row, Runnable onRefreshOrdnance) {
        JLabel label = new JLabel(labelText);
        SpinnerNumberModel spinnerNumberModel = spinnerContainer.getModel();
        JSpinner spinner = spinnerContainer.getSpinner();

        spinner.addChangeListener(e -> {
            Number modelNumber = spinnerNumberModel.getNumber();
            T result = (T) modelNumber;
            var currentSetter = spinnerContainer.getCurrentSetter();
            if (currentSetter != null) {
                currentSetter.accept(result);
            }
            if (onRefreshOrdnance != null) onRefreshOrdnance.run();
        });

        MouseWheelListener wheelListener = e -> {
            if (e.getScrollType() != MouseWheelEvent.WHEEL_UNIT_SCROLL || spinnerContainer.getCurrentSetter() == null) {
                return;
            }
            Number newValue;
            if (useFloatingPoint) {
                double value = (double) spinner.getValue();
                double newValueInt = value - (e.getUnitsToScroll() * 0.05d);
                double minValue = (double) spinnerContainer.getMinValue();
                double maxValue = (double) spinnerContainer.getMaxValue();
                newValue = Math.min(maxValue, Math.max(minValue, newValueInt));
            } else {
                int value = (int) spinner.getValue();
                int newValueInt = value - e.getUnitsToScroll();
                int minValue = (int) spinnerContainer.getMinValue();
                int maxValue = (int) spinnerContainer.getMaxValue();
                newValue = Math.min(maxValue, Math.max(minValue, newValueInt));
            }
            spinner.setValue(newValue);
        };
        spinner.addMouseWheelListener(wheelListener);

        ComponentUtilities.addLabelAndComponent(target, label, spinner, row);
    }
}
