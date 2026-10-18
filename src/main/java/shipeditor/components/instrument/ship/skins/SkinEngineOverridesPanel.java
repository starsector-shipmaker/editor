package shipeditor.components.instrument.ship.skins;

import shipeditor.utility.text.StringManager;

import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.engine.EngineDataOverride;
import shipeditor.components.viewer.entities.engine.EnginePoint;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.components.viewer.painters.points.ship.EngineSlotPainter;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.EngineStyle;
import shipeditor.utility.themes.Themes;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class SkinEngineOverridesPanel extends AbstractSkinOverridesPanel<SkinEngineOverridesPanel.EngineOverrideTableModel> {

    public SkinEngineOverridesPanel() {
        super(new EngineOverrideTableModel(), EditorInstrument.SKIN_ENGINES, "Selected Engine Override");
    }

    @Override
    protected void refreshContent() {
        updateSkinChooser();
        tableModel.clear();

        if (cachedPainter == null || cachedPainter.isUninitialized()) {
            statusLabel.setText(StringManager.getString("NO_SHIP_LAYER_SELECTED"));
            editorPanel.setVisible(false);
            return;
        }

        EngineSlotPainter slotPainter = cachedPainter.getEnginePainter();
        List<EnginePoint> slots = slotPainter != null ? slotPainter.getPointsIndex() : List.of();

        if (slots.isEmpty()) {
            statusLabel.setText(StringManager.getString("NO_ENGINE_SLOTS_DEFINED_ON_THIS_HULL"));
            editorPanel.setVisible(false);
            return;
        }

        ShipSkin activeSkin = cachedPainter.getActiveSkin();
        boolean isSkinActive = activeSkin != null && !activeSkin.isBase();

        Map<Integer, EngineDataOverride> overrides = isSkinActive ? activeSkin.getEngineSlotChanges() : null;
        int overrideCount = (overrides != null) ? overrides.size() : 0;

        if (isSkinActive) {
            statusLabel.setText(StringManager.getString("SKIN") + activeSkin + "  —  " + overrideCount + " engine override(s)");
        } else {
            statusLabel.setText(StringManager.getString("NO_SKIN_ACTIVE_SELECT_A_SKIN_IN_THE_SKIN_DATA_TAB"));
        }

        tableModel.populate(slots, overrides != null ? overrides : Map.of());
        editorPanel.setVisible(true);
        refreshEditorPanel();
    }


    protected void refreshEditorPanel() {
        refreshEditorForSelection("Select an engine slot from the table to view/edit its override",
                (panel, rowIndex) -> populateEditorForRow(panel, tableModel.getRow(rowIndex)));
    }

    private void populateEditorForRow(JPanel panel, EngineOverrideRow row) {
        GridBagConstraints labelGbc = createLabelConstraints();
        GridBagConstraints fieldGbc = createFieldConstraints();

        int gridRow = 0;

        addReadOnlyField(panel, "Engine Index:", String.valueOf(row.index), labelGbc, fieldGbc, gridRow++);

        addOverrideStatusRow(panel, row.hasOverride, Themes.getSuccessColor(), labelGbc, fieldGbc, gridRow++);

        SpinnerNumberModel angleModel = new SpinnerNumberModel(
                row.hasOverride && row.override.getAngle() != null ? row.override.getAngle().doubleValue() : row.baseAngle,
                -360.0, 360.0, 1.0);
        JSpinner angleSpinner = new JSpinner(angleModel);

        SpinnerNumberModel lengthModel = new SpinnerNumberModel(
                row.hasOverride && row.override.getLength() != null ? row.override.getLength().doubleValue() : row.baseLength,
                0.0, 1000.0, 1.0);
        JSpinner lengthSpinner = new JSpinner(lengthModel);

        SpinnerNumberModel widthModel = new SpinnerNumberModel(
                row.hasOverride && row.override.getWidth() != null ? row.override.getWidth().doubleValue() : row.baseWidth,
                0.0, 1000.0, 1.0);
        JSpinner widthSpinner = new JSpinner(widthModel);

        GameDataRepository gameData = SettingsManager.getGameData();
        Map<String, EngineStyle> allStyles = gameData != null ? gameData.getAllEngineStyles() : null;
        Vector<String> styleIds = new Vector<>();
        styleIds.add(""); // Empty means no override
        if (allStyles != null) {
            styleIds.addAll(allStyles.keySet());
        }
        JComboBox<String> styleBox = new JComboBox<>(styleIds);
        if (row.hasOverride && row.override.getStyleID() != null) {
            styleBox.setSelectedItem(row.override.getStyleID());
        } else {
            styleBox.setSelectedIndex(0);
        }

        addField(panel, "Override Angle:", angleSpinner, labelGbc, fieldGbc, gridRow++);
        addField(panel, "Override Length:", lengthSpinner, labelGbc, fieldGbc, gridRow++);
        addField(panel, "Override Width:", widthSpinner, labelGbc, fieldGbc, gridRow++);
        addField(panel, "Override Style:", styleBox, labelGbc, fieldGbc, gridRow++);

        addOverrideButtons(panel, gridRow, row.hasOverride, () -> {
            EngineDataOverride.EngineDataOverrideBuilder builder = EngineDataOverride.builder();
            builder.index(row.index);
            builder.angle(((Number) angleModel.getValue()).doubleValue());
            builder.length(((Number) lengthModel.getValue()).doubleValue());
            builder.width(((Number) widthModel.getValue()).doubleValue());
            String selectedStyle = (String) styleBox.getSelectedItem();
            if (selectedStyle != null && !selectedStyle.isEmpty()) {
                builder.styleID(selectedStyle);
            }

            commitOverride(row.index, builder.build());
        }, () -> commitOverride(row.index, null));
    }

    private void commitOverride(Integer index, EngineDataOverride override) {
        commitOverride(index, override, ShipSkin::getEngineSlotChanges, ShipSkin::setEngineSlotChanges);
    }

    private static final class EngineOverrideRow {
        Integer index;
        double baseAngle;
        double baseLength;
        double baseWidth;
        boolean hasOverride;
        EngineDataOverride override;

        EngineOverrideRow(EnginePoint slot, Integer index, EngineDataOverride override) {
            this.index = index;
            this.baseAngle = slot.getAngle();
            this.baseLength = slot.getLength();
            this.baseWidth = slot.getWidth();
            this.hasOverride = (override != null);
            this.override = override;
        }
    }

    static final class EngineOverrideTableModel extends AbstractTableModel {
        private static final String[] COLUMN_NAMES = {
                "Index", "Base Angle", "Base Length", "Base Width", "Override?"
        };

        private final List<EngineOverrideRow> rows = new ArrayList<>();

        void clear() {
            int oldSize = rows.size();
            rows.clear();
            if (oldSize > 0) {
                fireTableRowsDeleted(0, oldSize - 1);
            }
        }

        void populate(List<EnginePoint> slots, Map<Integer, EngineDataOverride> overrides) {
            rows.clear();
            for (int i = 0; i < slots.size(); i++) {
                EnginePoint slot = slots.get(i);
                EngineDataOverride override = null;
                if (overrides != null) {
                    override = overrides.get(i);
                }
                rows.add(new EngineOverrideRow(slot, i, override));
            }
            fireTableDataChanged();
        }

        EngineOverrideRow getRow(int rowIndex) {
            return rows.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            EngineOverrideRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0: return row.index;
                case 1: return row.baseAngle;
                case 2: return row.baseLength;
                case 3: return row.baseWidth;
                case 4: return row.hasOverride ? "✓" : "";
                default: return "";
            }
        }
    }
}
