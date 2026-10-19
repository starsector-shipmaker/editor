package shipeditor.components.instrument.ship.skins;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ActiveShipSpec;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.UIConstants;
import shipeditor.utility.text.StringManager;
import shipeditor.utility.themes.Themes;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public abstract class AbstractSkinOverridesPanel<M extends AbstractTableModel> extends JPanel {

    protected final JComboBox<ShipSkin> skinChooser;
    protected final JLabel statusLabel;
    protected final JTable overridesTable;
    protected final M tableModel;
    protected final JPanel editorPanel;
    protected ShipPainter cachedPainter;
    private final EditorInstrument editorMode;
    private boolean isUpdatingSkinChooser;

    protected AbstractSkinOverridesPanel(EditorInstrument editorMode) {
        this.editorMode = editorMode;
        this.tableModel = null;
        this.overridesTable = null;
        this.editorPanel = null;

        skinChooser = createSkinChooserComboBox();
        statusLabel = createStatusLabel();
    }

    public AbstractSkinOverridesPanel(M tableModel, EditorInstrument editorMode, String editorTitle) {
        this.editorMode = editorMode;
        this.tableModel = tableModel;
        this.skinChooser = createSkinChooserComboBox();
        this.statusLabel = createStatusLabel();

        this.setLayout(new BorderLayout());

        JPanel chooserPanel = createChooserPanel();

        overridesTable = new JTable(tableModel);
        overridesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        overridesTable.setRowHeight(24);
        overridesTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        overridesTable.getTableHeader().setReorderingAllowed(false);
        overridesTable.setPreferredScrollableViewportSize(new java.awt.Dimension(260, 200));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        overridesTable.setDefaultRenderer(Object.class, centerRenderer);

        JScrollPane scrollPane = new JScrollPane(overridesTable);
        scrollPane.setBorder(UIConstants.EMPTY_BORDER);

        editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        
        JPanel titlePanel = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(titlePanel, editorTitle);
        editorPanel.add(titlePanel, BorderLayout.PAGE_START);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(chooserPanel, BorderLayout.PAGE_START);
        topContainer.add(statusLabel, BorderLayout.CENTER);
        topContainer.add(editorPanel, BorderLayout.PAGE_END);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topContainer, scrollPane);
        splitPane.setResizeWeight(0.35);
        splitPane.setDividerSize(4);
        this.add(splitPane, BorderLayout.CENTER);

        overridesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshEditorPanel();
            }
        });

        this.initEventListening();
    }

    private JComboBox<ShipSkin> createSkinChooserComboBox() {
        JComboBox<ShipSkin> combo = new JComboBox<>();
        combo.addActionListener(e -> {
            if (isUpdatingSkinChooser || cachedPainter == null || cachedPainter.isUninitialized()) {
                return;
            }
            applyChosenSkin(cachedPainter, (ShipSkin) combo.getSelectedItem());
        });
        return combo;
    }

    private static JLabel createStatusLabel() {
        JLabel label = new JLabel(StringManager.getString("NO_SKIN_ACTIVE"));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new EmptyBorder(4, 8, 4, 8));
        return label;
    }

    protected final EditorInstrument getEditorMode() {
        return editorMode;
    }

    protected static boolean isSkinActive(ShipSkin skin) {
        return skin != null && !skin.isBase();
    }

    static void applyChosenSkin(ShipPainter painter, ShipSkin chosen) {
        ActiveShipSpec spec = isSkinActive(chosen) ? ActiveShipSpec.SKIN : ActiveShipSpec.HULL;
        painter.setActiveSpec(spec, chosen);
    }

    protected JPanel createChooserPanel() {
        JPanel chooserPanel = new JPanel(new BorderLayout(6, 0));
        chooserPanel.setBorder(new EmptyBorder(4, 6, 2, 6));
        JLabel skinLabel = new JLabel("Skin:");
        chooserPanel.add(skinLabel, BorderLayout.LINE_START);
        chooserPanel.add(skinChooser, BorderLayout.CENTER);
        return chooserPanel;
    }

    protected void updateSkinChooser() {
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

    protected abstract void refreshContent();

    protected void refreshEditorPanel() {
    }

    protected final void initEventListening() {
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerWasSelected checked) {
                handleLayerChange(checked.selected());
            } else if (event instanceof ActiveLayerUpdated checked) {
                handleLayerChange(checked.updated());
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == this.editorMode) {
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
        log.debug("Skin panel {} has no initialized ship painter for the changed layer", editorMode);
        this.cachedPainter = null;
        refreshContent();
    }

    /**
     * Shared body of {@link #refreshEditorPanel()}: clears the inner editor and either shows the hint when no row
     * is selected or lets the subclass populate the editor for the selected row.
     *
     * @param noSelectionHint text shown when no valid row is selected.
     * @param rowPopulator    receives the inner editor panel and the selected row index.
     */
    protected void refreshEditorForSelection(String noSelectionHint,
                                             java.util.function.BiConsumer<JPanel, Integer> rowPopulator) {
        JPanel innerEditor = (JPanel) editorPanel.getComponent(0);
        innerEditor.removeAll();

        int selectedRow = overridesTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= tableModel.getRowCount()) {
            showNoSelectionHint(innerEditor, noSelectionHint);
            return;
        }

        log.debug("Populating override editor for row {}", selectedRow);
        rowPopulator.accept(innerEditor, selectedRow);

        innerEditor.revalidate();
        innerEditor.repaint();
    }

    protected <K, V> void commitOverride(K key, V override,
                                         java.util.function.Function<ShipSkin, java.util.Map<K, V>> mapGetter,
                                         java.util.function.BiConsumer<ShipSkin, java.util.Map<K, V>> mapSetter) {
        if (cachedPainter == null || cachedPainter.isUninitialized()) return;
        ShipSkin activeSkin = cachedPainter.getActiveSkin();
        if (activeSkin == null || activeSkin.isBase()) return;

        java.util.Map<K, V> oldMap = mapGetter.apply(activeSkin);
        if (oldMap == null) {
            oldMap = new java.util.LinkedHashMap<>();
        } else {
            oldMap = new java.util.LinkedHashMap<>(oldMap);
        }

        java.util.Map<K, V> newMap = new java.util.LinkedHashMap<>(oldMap);
        if (override == null) {
            newMap.remove(key);
        } else {
            newMap.put(key, override);
        }

        if (newMap.isEmpty()) {
            newMap = null;
        }

        var edit = new shipeditor.undo.edits.features.SkinOverrideEdits.SkinMapOverrideEdit<>(
                map -> mapSetter.accept(activeSkin, map),
                mapGetter.apply(activeSkin),
                newMap,
                getEditorMode(),
                activeSkin
        );
        shipeditor.undo.UndoOverseer.post(edit);
        edit.redo();
    }

    protected java.awt.GridBagConstraints createLabelConstraints() {
        java.awt.GridBagConstraints labelGbc = new java.awt.GridBagConstraints();
        labelGbc.anchor = java.awt.GridBagConstraints.LINE_START;
        labelGbc.insets = new java.awt.Insets(2, 4, 2, 4);
        return labelGbc;
    }

    protected java.awt.GridBagConstraints createFieldConstraints() {
        java.awt.GridBagConstraints fieldGbc = new java.awt.GridBagConstraints();
        fieldGbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1;
        fieldGbc.insets = new java.awt.Insets(2, 4, 2, 4);
        return fieldGbc;
    }

    private void addLabel(JPanel panel, String labelText, java.awt.GridBagConstraints labelGbc,
                          java.awt.GridBagConstraints fieldGbc, int row) {
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        panel.add(new JLabel(labelText), labelGbc);

        fieldGbc.gridx = 1;
        fieldGbc.gridy = row;
    }

    protected void addField(JPanel panel, String labelText, java.awt.Component comp,
                            java.awt.GridBagConstraints labelGbc, java.awt.GridBagConstraints fieldGbc, int row) {
        addLabel(panel, labelText, labelGbc, fieldGbc, row);
        panel.add(comp, fieldGbc);
    }

    protected void addReadOnlyField(JPanel panel, String labelText, String value,
                                    java.awt.GridBagConstraints labelGbc, java.awt.GridBagConstraints fieldGbc, int row) {
        addLabel(panel, labelText, labelGbc, fieldGbc, row);
        JTextField field = new JTextField(value);
        field.setEditable(false);
        field.setColumns(12);
        panel.add(field, fieldGbc);
    }

    protected void addColoredField(JPanel panel, String labelText, String value, java.awt.Color color,
                                  java.awt.GridBagConstraints labelGbc, java.awt.GridBagConstraints fieldGbc, int row) {
        addLabel(panel, labelText, labelGbc, fieldGbc, row);
        JLabel valueLabel = new JLabel(value);
        valueLabel.setForeground(color);
        panel.add(valueLabel, fieldGbc);
    }

    /**
     * Adds the "Status:" row showing whether the selected entry has a skin override.
     *
     * @param overrideColor color used for the status text when an override is present.
     */
    protected void addOverrideStatusRow(JPanel panel, boolean hasOverride, java.awt.Color overrideColor,
                                        java.awt.GridBagConstraints labelGbc, java.awt.GridBagConstraints fieldGbc,
                                        int row) {
        String overrideStatus = hasOverride ? "✓ Has Override" : "✗ No Override";
        java.awt.Color statusColor = hasOverride
                ? overrideColor
                : UIManager.getColor("Label.disabledForeground");
        addColoredField(panel, "Status:", overrideStatus, statusColor, labelGbc, fieldGbc, row);
    }

    /**
     * Adds the Clear / Apply override button row at the given grid row.
     *
     * @param onApply  invoked when the apply button is pressed.
     * @param onClear  invoked when the clear button is pressed.
     */
    protected void addOverrideButtons(JPanel panel, int gridRow, boolean hasOverride, Runnable onApply,
                                      Runnable onClear) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));
        JButton applyButton = new JButton(StringManager.getString("APPLY_OVERRIDE"),
                FontIcon.of(BoxiconsRegular.CHECK_CIRCLE, 16, Themes.getIconColor()));
        applyButton.addActionListener(e -> onApply.run());

        ShipSkin activeSkin = cachedPainter != null ? cachedPainter.getActiveSkin() : null;
        boolean isSkinActive = isSkinActive(activeSkin);

        applyButton.setEnabled(isSkinActive);
        if (!isSkinActive) {
            applyButton.setToolTipText("Select a skin in the chooser above to apply overrides");
        }

        JButton clearButton = new JButton(StringManager.getString("CLEAR_OVERRIDE"),
                FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        clearButton.setEnabled(isSkinActive && hasOverride);
        clearButton.addActionListener(e -> onClear.run());

        buttonPanel.add(clearButton);
        buttonPanel.add(applyButton);

        java.awt.GridBagConstraints btnGbc = new java.awt.GridBagConstraints();
        btnGbc.gridx = 0;
        btnGbc.gridy = gridRow;
        btnGbc.gridwidth = 2;
        btnGbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        btnGbc.insets = new java.awt.Insets(8, 4, 4, 4);
        panel.add(buttonPanel, btnGbc);
    }

    protected void showNoSelectionHint(JPanel panel, String hintText) {
        JLabel hint = new JLabel(hintText);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new java.awt.Insets(8, 4, 8, 4);
        panel.add(hint, gbc);
        panel.revalidate();
        panel.repaint();
    }
}
