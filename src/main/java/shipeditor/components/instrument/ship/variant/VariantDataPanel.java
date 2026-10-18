package shipeditor.components.instrument.ship.variant;

import lombok.extern.log4j.Log4j2;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.StringManager;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Log4j2
public class VariantDataPanel extends JPanel {
    private static final Pattern ILLEGAL_ID_CHARS = Pattern.compile("[^A-Za-z0-9_.-]");
    private static final String VARIANT_FILE_EXTENSION = ".variant";
    private static final String OUTLINE_PROPERTY = "JComponent.outline";
    private static final String OUTLINE_ERROR = "error";
    private static final String OUTLINE_WARNING = "warning";

    private final JLabel shipHullIDLabel;
    private final JTextField variantIDEditor;
    private final JTextField variantDisplayNameEditor;
    private final DataSpinnerContainer<Integer> ventsSpinner;
    private final DataSpinnerContainer<Integer> capacitorsSpinner;
    private final DataSpinnerContainer<Double> qualitySpinner;
    private final JCheckBox goalVariantCheckbox;

    private final String defaultIDTooltip;

    private Consumer<String> variantIDSetter;
    private Consumer<String> variantDisplayNameSetter;
    private Consumer<Boolean> goalVariantSetter;

    private ShipLayer selectedLayer;
    private ShipVariant cachedVariant;
    private boolean refreshing;

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
                "Changes apply on Enter or when leaving the field");
        variantIDEditor.setToolTipText(editorIDTooltip);
        defaultIDTooltip = editorIDTooltip;
        variantIDEditor.putClientProperty("JTextField.placeholderText", "Variant ID...");

        variantIDEditor.addActionListener(e -> commitVariantID());
        variantIDEditor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitVariantID();
            }
        });
        variantIDEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validateIfNotRefreshing();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validateIfNotRefreshing();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validateIfNotRefreshing();
            }
        });
        ComponentUtilities.addLabelAndComponent(this, variantIDLabel, variantIDEditor, 1);

        JLabel variantDisplayNameLabel = new JLabel(StringManager.getString("DISPLAY_NAME"));
        variantDisplayNameEditor = new JTextField();
        String editorNameTooltip = Utility.getWithLinebreaks(StringManager.getString("TYPE_AND_PRESS_ENTER_TO_EDIT_ID"),
                "Changes apply on Enter or when leaving the field");
        variantDisplayNameEditor.setToolTipText(editorNameTooltip);
        variantDisplayNameEditor.putClientProperty("JTextField.placeholderText", "Display name...");

        variantDisplayNameEditor.addActionListener(e -> commitDisplayName());
        variantDisplayNameEditor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitDisplayName();
            }
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
                markVariantUnsaved();
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

    private void commitVariantID() {
        if (refreshing || selectedLayer == null || cachedVariant == null || variantIDSetter == null) {
            return;
        }
        String newId = variantIDEditor.getText() != null ? variantIDEditor.getText().trim() : "";
        String oldId = cachedVariant.getVariantId();
        if (newId.isEmpty()) {
            variantIDEditor.setText(oldId != null ? oldId : "");
            return;
        }
        if (Objects.equals(newId, oldId)) {
            return;
        }
        Map<String, ShipVariant> loadedVariants = selectedLayer.getLoadedVariants();
        if (oldId != null) {
            loadedVariants.remove(oldId);
        }
        variantIDSetter.accept(newId);
        loadedVariants.put(newId, cachedVariant);
        cachedVariant.setLoadedFromFile(false);
        markVariantUnsaved();

        ShipPainter shipPainter = selectedLayer.getPainter();
        if (shipPainter != null) {
            shipPainter.selectVariant(cachedVariant);
        }
    }

    private void validateIfNotRefreshing() {
        if (!refreshing) {
            validateVariantID();
        }
    }

    /**
     * Inline validation of the variant ID field. Errors (blank, illegal characters, duplicates) and warnings
     * (ID differs from the loaded file name) are shown via the FlatLaf outline and a tooltip.
     * This does not alter what gets committed.
     */
    private void validateVariantID() {
        if (selectedLayer == null || cachedVariant == null) {
            clearVariantIDValidation();
            return;
        }
        String text = variantIDEditor.getText() != null ? variantIDEditor.getText().trim() : "";
        log.debug("Validating variant ID '{}' (current '{}')", text, cachedVariant.getVariantId());

        String error = findVariantIDError(text);
        if (error != null) {
            applyVariantIDValidation(OUTLINE_ERROR, error);
            return;
        }
        String warning = findVariantIDWarning(text);
        if (warning != null) {
            applyVariantIDValidation(OUTLINE_WARNING, warning);
            return;
        }
        clearVariantIDValidation();
    }

    private String findVariantIDError(String id) {
        if (id.isEmpty()) {
            return "Variant ID must not be blank.";
        }
        if (ILLEGAL_ID_CHARS.matcher(id).find()) {
            return "Variant ID may only contain letters, digits, '_', '.' and '-'.";
        }
        Map<String, ShipVariant> loadedVariants = selectedLayer.getLoadedVariants();
        if (loadedVariants != null) {
            ShipVariant existing = loadedVariants.get(id);
            if (existing != null && existing != cachedVariant) {
                return "Another loaded variant already uses the ID '" + id + "'.";
            }
        }
        return null;
    }

    private String findVariantIDWarning(String id) {
        Path filePath = cachedVariant.getVariantFilePath();
        Path fileNamePath = filePath != null ? filePath.getFileName() : null;
        if (fileNamePath == null) {
            return null;
        }
        String stem = fileNamePath.toString();
        if (stem.endsWith(VARIANT_FILE_EXTENSION)) {
            stem = stem.substring(0, stem.length() - VARIANT_FILE_EXTENSION.length());
        }
        if (!stem.equals(id)) {
            return "Variant ID differs from the loaded file name '" + fileNamePath + "'.";
        }
        return null;
    }

    private void applyVariantIDValidation(String outline, String message) {
        variantIDEditor.putClientProperty(OUTLINE_PROPERTY, outline);
        variantIDEditor.setToolTipText(message);
    }

    private void clearVariantIDValidation() {
        variantIDEditor.putClientProperty(OUTLINE_PROPERTY, null);
        variantIDEditor.setToolTipText(defaultIDTooltip);
    }

    private void commitDisplayName() {
        if (refreshing || selectedLayer == null || cachedVariant == null || variantDisplayNameSetter == null) {
            return;
        }
        String newName = variantDisplayNameEditor.getText() != null ? variantDisplayNameEditor.getText().trim() : "";
        if (Objects.equals(newName, cachedVariant.getDisplayName())) {
            return;
        }
        variantDisplayNameSetter.accept(newName);
        markVariantUnsaved();

        ShipPainter shipPainter = selectedLayer.getPainter();
        if (shipPainter != null) {
            shipPainter.selectVariant(cachedVariant);
        }
    }

    private void markVariantUnsaved() {
        if (refreshing || selectedLayer == null) {
            return;
        }
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager != null) {
            layerManager.markUnsaved(selectedLayer, "variant");
        }
    }

    public void disableDataSpinners() {
        ventsSpinner.disableSpinner();
        capacitorsSpinner.disableSpinner();
        qualitySpinner.disableSpinner();
    }

    public void installPlaceholders() {
        variantIDSetter = null;
        variantDisplayNameSetter = null;
        goalVariantSetter = null;
        selectedLayer = null;
        cachedVariant = null;
        disableDataSpinners();
        shipHullIDLabel.setText(StringManager.getString("NOT_INITIALIZED"));
        variantIDEditor.setEnabled(false);
        variantIDEditor.setText("");
        clearVariantIDValidation();
        variantDisplayNameEditor.setEnabled(false);
        variantDisplayNameEditor.setText("");
        goalVariantCheckbox.setSelected(false);
        goalVariantCheckbox.setEnabled(false);
    }

    public void refresh(ShipLayer layer, ShipVariant variant) {
        refreshing = true;
        try {
            installPlaceholders();
            if (layer == null || variant == null || variant.isEmpty()) return;

            selectedLayer = layer;
            cachedVariant = variant;

            ShipHull shipHull = layer.getHull();
            if (shipHull == null) return;

            HullSize hullSize = shipHull.getHullSize();
            int maxFluxRegulators = hullSize != null ? hullSize.getMaxFluxRegulators() : 0;
            int fluxVents = variant.getFluxVents();
            int fluxCapacitors = variant.getFluxCapacitors();

            ventsSpinner.enableSpinner(layer, fluxVents, Math.max(maxFluxRegulators, fluxVents), val -> variant.setFluxVents(val != null ? val : 0));
            capacitorsSpinner.enableSpinner(layer, fluxCapacitors, Math.max(maxFluxRegulators, fluxCapacitors), val -> variant.setFluxCapacitors(val != null ? val : 0));
            qualitySpinner.enableSpinner(layer, variant.getQuality(), 1.0d, val -> variant.setQuality(val != null ? val : 0.0d));

            shipHullIDLabel.setText(variant.getShipHullId());

            variantIDEditor.setText(variant.getVariantId());
            variantIDEditor.setEnabled(true);
            variantIDSetter = variant::setVariantId;
            validateVariantID();

            variantDisplayNameEditor.setText(variant.getDisplayName());
            variantDisplayNameEditor.setEnabled(true);
            variantDisplayNameSetter = variant::setDisplayName;

            goalVariantSetter = val -> variant.setGoalVariant(val != null && val);
            goalVariantCheckbox.setSelected(variant.isGoalVariant());
            goalVariantCheckbox.setEnabled(true);
        } finally {
            refreshing = false;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Number> void addDataSpinner(JPanel target, String labelText, boolean useFloatingPoint,
                                                   DataSpinnerContainer<T> spinnerContainer, int row, Runnable onRefreshOrdnance) {
        JLabel label = new JLabel(labelText);
        SpinnerNumberModel spinnerNumberModel = spinnerNumberModelFromContainer(spinnerContainer);
        JSpinner spinner = spinnerContainer.getSpinner();

        spinner.addChangeListener(e -> {
            Number modelNumber = spinnerNumberModel.getNumber();
            T result = (T) modelNumber;
            var currentSetter = spinnerContainer.getCurrentSetter();
            if (currentSetter != null) {
                currentSetter.accept(result);
                markVariantUnsaved();
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

    private static <T extends Number> SpinnerNumberModel spinnerNumberModelFromContainer(DataSpinnerContainer<T> spinnerContainer) {
        return spinnerContainer.getModel();
    }
}
