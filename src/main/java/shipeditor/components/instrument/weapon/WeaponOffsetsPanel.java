package shipeditor.components.instrument.weapon;

import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.LayerWasSelected;
import shipeditor.components.viewer.entities.weapon.OffsetPoint;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.components.viewer.layers.weapon.WeaponPainter;
import shipeditor.components.viewer.painters.points.weapon.WeaponOffsetPainter;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JCheckBox;
import javax.swing.JSlider;
import javax.swing.JTextField;
import shipeditor.utility.overseers.StaticController;
import shipeditor.communication.events.components.ComponentEvents.LayerTabUpdated;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/** * Panel for editing weapon offset points (turret / hardpoint firing positions and angles).*/
class WeaponOffsetsPanel extends JPanel {

    private OffsetsTableModel tableModel;
    private JTable offsetsTable;
    private WeaponPainter cachedPainter;
    private WeaponLayer cachedLayer;
    
    private JTextField visualRecoilEditor;
    private JCheckBox separateRecoilCheckbox;
    private JSlider recoilPreviewSlider;
    private boolean readyForInput;

    WeaponOffsetsPanel() {
        this.setLayout(new BorderLayout());

        JPanel infoPanel = createInfoPanel();
        this.add(infoPanel, BorderLayout.PAGE_START);

        tableModel = new OffsetsTableModel();
        offsetsTable = new JTable(tableModel);
        offsetsTable.setFillsViewportHeight(true);
        offsetsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        offsetsTable.setPreferredScrollableViewportSize(new java.awt.Dimension(260, 200));

        JScrollPane scrollPane = new JScrollPane(offsetsTable);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        this.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = createButtonPanel();
        this.add(buttonPanel, BorderLayout.PAGE_END);

        initLayerListeners();
    }

    private JPanel createInfoPanel() {
        JPanel infoPanel = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(infoPanel, "Weapon offsets");

        int row = 0;

        JLabel description = new JLabel(StringManager.getString("FIRING_POSITIONS_AND_ANGLES_FOR_CURRENT_MOUNT_MODE"));
        description.setBorder(new EmptyBorder(4, 4, 4, 4));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row++;
        constraints.weightx = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.LINE_START;
        infoPanel.add(description, constraints);

        visualRecoilEditor = new JTextField();
        visualRecoilEditor.setColumns(10);
        visualRecoilEditor.setToolTipText(StringManager.getString("VISUAL_RECOIL_DISTANCE_IN_PIXELS"));
        visualRecoilEditor.addActionListener(e -> {
            if (readyForInput && cachedLayer != null && cachedLayer.getSpecFile() != null) {
                try {
                    cachedLayer.getSpecFile().setVisualRecoil(Double.parseDouble(visualRecoilEditor.getText()));
                    processChange();
                } catch (NumberFormatException ex) {
                    javax.swing.JOptionPane.showMessageDialog(this, StringManager.getString("INVALID_VISUAL_RECOIL_VALUE_PLEASE_ENTER_MSG"), "Invalid Input", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        ComponentUtilities.addLabelAndComponent(infoPanel, new JLabel(StringManager.getString("VISUAL_RECOIL")), visualRecoilEditor, row++);

        separateRecoilCheckbox = new JCheckBox(StringManager.getString("SEPARATE_RECOIL_FOR_LINKED_BARRELS"));
        separateRecoilCheckbox.setToolTipText(StringManager.getString("WHETHER_EACH_BARREL_RECOILS_INDEPENDENTLY"));
        separateRecoilCheckbox.addActionListener(e -> {
            if (readyForInput && cachedLayer != null && cachedLayer.getSpecFile() != null) {
                cachedLayer.getSpecFile().setSeparateRecoilForLinkedBarrels(separateRecoilCheckbox.isSelected());
                processChange();
            }
        });
        ComponentUtilities.addLabelAndComponent(infoPanel, new JLabel(), separateRecoilCheckbox, row++);

        recoilPreviewSlider = new JSlider(0, 100, 0);
        recoilPreviewSlider.setToolTipText(StringManager.getString("PREVIEW_VISUAL_RECOIL_IN_THE_VIEWER"));
        recoilPreviewSlider.addChangeListener(e -> {
            if (cachedPainter != null) {
                cachedPainter.setRecoilPreviewFraction(recoilPreviewSlider.getValue() / 100.0);
                StaticController.getScheduler().queueViewerRepaint();
            }
        });
        ComponentUtilities.addLabelAndComponent(infoPanel, new JLabel(StringManager.getString("RECOIL_PREVIEW")), recoilPreviewSlider, row++);

        return infoPanel;
    }

    private void processChange() {
        if (cachedLayer != null) {
            EventBus.publish(new LayerTabUpdated(cachedLayer));
            StaticController.getScheduler().queueViewerRepaint();
        }
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton addButton = new JButton(StringManager.getString("ADD_OFFSET"));
        addButton.setToolTipText(StringManager.getString("ADD_A_NEW_FIRING_OFFSET_POINT"));
        addButton.addActionListener(e -> {
            if (cachedPainter == null) return;
            WeaponOffsetPainter offsetPainter = cachedPainter.getOffsetPainter();
            Point2D center = cachedPainter.getEntityCenter();
            OffsetPoint newPoint = new OffsetPoint(
                    new Point2D.Double(center.getX(), center.getY()), cachedPainter);
            EditDispatch.postPointAdded(offsetPainter, newPoint);
            refreshTableModel();
        });

        JButton removeButton = new JButton(StringManager.getString("REMOVE"));
        removeButton.setToolTipText(StringManager.getString("REMOVE_SELECTED_FIRING_OFFSET_POINT"));
        removeButton.setEnabled(false);
        offsetsTable.getSelectionModel().addListSelectionListener(e -> {
            removeButton.setEnabled(offsetsTable.getSelectedRow() >= 0);
        });
        removeButton.addActionListener(e -> {
            if (cachedPainter == null) return;
            int selectedRow = offsetsTable.getSelectedRow();
            if (selectedRow < 0) return;
            WeaponOffsetPainter offsetPainter = cachedPainter.getOffsetPainter();
            List<OffsetPoint> points = offsetPainter.getOffsetPoints();
            if (selectedRow >= points.size()) return;
            OffsetPoint toRemove = points.get(selectedRow);
            EditDispatch.postPointRemoved(offsetPainter, toRemove);
            refreshTableModel();
        });

        panel.add(addButton);
        panel.add(removeButton);

        return panel;
    }

    private void initLayerListeners() {
        EventBus.subscribe(this, event -> {
            if (event instanceof LayerWasSelected checked) {
                ViewerLayer selected = checked.selected();
                refreshPanel(selected);
            } else if (event instanceof shipeditor.communication.events.viewer.points.PointEvents.PointAddConfirmed ||
                       event instanceof shipeditor.communication.events.viewer.points.PointEvents.PointRemovedConfirmed) {
                refreshTableModel();
            } else if (event instanceof shipeditor.communication.events.viewer.ViewerRepaintQueued) {
                if (cachedPainter != null && offsetsTable != null) {
                    offsetsTable.repaint();
                }
            }
        });
    }

    private void refreshPanel(ViewerLayer selected) {
        readyForInput = false;
        if (selected instanceof WeaponLayer weaponLayer) {
            cachedPainter = weaponLayer.getPainter();
            cachedLayer = weaponLayer;
            
            shipeditor.representation.weapon.WeaponSpecFile spec = weaponLayer.getSpecFile();
            if (spec != null) {
                visualRecoilEditor.setText(String.valueOf(spec.getVisualRecoil()));
                separateRecoilCheckbox.setSelected(spec.isSeparateRecoilForLinkedBarrels());
            } else {
                visualRecoilEditor.setText("");
                separateRecoilCheckbox.setSelected(false);
            }
        } else {
            cachedPainter = null;
            cachedLayer = null;
            visualRecoilEditor.setText("");
            separateRecoilCheckbox.setSelected(false);
        }
        
        if (cachedPainter != null) {
            recoilPreviewSlider.setValue((int) (cachedPainter.getRecoilPreviewFraction() * 100));
        } else {
            recoilPreviewSlider.setValue(0);
        }
        
        refreshTableModel();
        readyForInput = true;
    }

    private void refreshTableModel() {
        if (cachedPainter != null) {
            WeaponOffsetPainter offsetPainter = cachedPainter.getOffsetPainter();
            tableModel.setOffsetPoints(offsetPainter.getOffsetPoints());
            offsetsTable.setEnabled(true);
        } else {
            tableModel.setOffsetPoints(new ArrayList<>());
            offsetsTable.setEnabled(false);
        }
        tableModel.fireTableDataChanged();
    }

    /**
     * Simple table model for displaying/editing offset point positions and angles.
     */
    private static class OffsetsTableModel extends AbstractTableModel {

        private static final String[] COLUMN_NAMES = {"#", "X", "Y", "Angle"};
        private List<OffsetPoint> offsetPoints = new ArrayList<>();

        void setOffsetPoints(List<OffsetPoint> points) {
            this.offsetPoints = points;
        }

        @Override
        public int getRowCount() {
            return offsetPoints.size();
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
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case 0 -> Integer.class;
                case 1, 2, 3 -> Double.class;
                default -> Object.class;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // Index column is not editable.
            return columnIndex > 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            OffsetPoint point = offsetPoints.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> rowIndex;
                case 1 -> point.getPosition().getX();
                case 2 -> point.getPosition().getY();
                case 3 -> point.getAngle();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            OffsetPoint point = offsetPoints.get(rowIndex);
            double val = ((Number) aValue).doubleValue();
            switch (columnIndex) {
                case 1 -> point.setPosition(val, point.getPosition().getY());
                case 2 -> point.setPosition(point.getPosition().getX(), val);
                case 3 -> point.setAngle(val);
                default -> {}
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

    }

}
