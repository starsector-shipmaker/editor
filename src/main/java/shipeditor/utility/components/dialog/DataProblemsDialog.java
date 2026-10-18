package shipeditor.utility.components.dialog;

import lombok.extern.log4j.Log4j2;
import shipeditor.utility.DataProblemsRegistry;
import shipeditor.utility.DataProblemsRegistry.Category;
import shipeditor.utility.DataProblemsRegistry.Problem;
import shipeditor.utility.components.ToastManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-modal dialog listing the data problems collected in {@link DataProblemsRegistry}, filterable by category,
 * with copy-to-clipboard support.
 */
@Log4j2
public final class DataProblemsDialog extends JDialog {

    private static final String ALL_CATEGORIES = "All categories";
    private static final String[] COLUMNS = {"Category", "Source", "Package", "Message"};

    private final ProblemsTableModel tableModel = new ProblemsTableModel();
    private final JTable table = new JTable(tableModel);
    private final JComboBox<Object> categoryFilter = new JComboBox<>();
    private final JLabel summaryLabel = new JLabel();

    public DataProblemsDialog(Frame owner) {
        super(owner, "Data Problems", false);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        categoryFilter.addItem(ALL_CATEGORIES);
        for (Category category : Category.values()) {
            categoryFilter.addItem(category);
        }
        categoryFilter.addActionListener(e -> refresh());

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh());
        JButton copyButton = new JButton("Copy to clipboard");
        copyButton.addActionListener(e -> copyToClipboard());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Filter:"));
        top.add(categoryFilter);
        top.add(refreshButton);
        top.add(copyButton);

        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(420);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bottom.add(summaryLabel, BorderLayout.WEST);

        this.setLayout(new BorderLayout());
        this.add(top, BorderLayout.NORTH);
        this.add(new JScrollPane(table), BorderLayout.CENTER);
        this.add(bottom, BorderLayout.SOUTH);
        this.setSize(960, 520);
        this.setLocationRelativeTo(owner);

        refresh();
    }

    private void refresh() {
        Object selected = categoryFilter.getSelectedItem();
        Category category = selected instanceof Category selectedCategory ? selectedCategory : null;
        tableModel.setRows(DataProblemsRegistry.getProblems(category));
        summaryLabel.setText(tableModel.getRowCount() + " problem(s) shown, "
                + DataProblemsRegistry.size() + " total; "
                + DataProblemsRegistry.getSkippedRowsTotal() + " blank/comment CSV row(s) skipped");
    }

    private void copyToClipboard() {
        StringBuilder text = new StringBuilder();
        int[] selectedRows = table.getSelectedRows();
        List<Problem> rows = new ArrayList<>();
        if (selectedRows.length > 0) {
            for (int viewRow : selectedRows) {
                rows.add(tableModel.getRow(table.convertRowIndexToModel(viewRow)));
            }
        } else {
            for (int i = 0; i < table.getRowCount(); i++) {
                rows.add(tableModel.getRow(table.convertRowIndexToModel(i)));
            }
        }
        text.append(String.join("\t", COLUMNS)).append('\n');
        for (Problem problem : rows) {
            text.append(problem.category().getDisplayName()).append('\t')
                    .append(problem.source()).append('\t')
                    .append(problem.packageName()).append('\t')
                    .append(problem.message()).append('\n');
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
            ToastManager.show("Copied " + rows.size() + " problem(s) to clipboard",
                    ToastManager.ToastLevel.SUCCESS);
        } catch (IllegalStateException e) {
            log.warn("Failed to copy data problems to clipboard: {}", e.getMessage());
            ToastManager.show("Could not access the clipboard", ToastManager.ToastLevel.ERROR);
        }
    }

    private static final class ProblemsTableModel extends AbstractTableModel {

        private transient List<Problem> rows = new ArrayList<>();

        void setRows(List<Problem> newRows) {
            this.rows = newRows;
            fireTableDataChanged();
        }

        Problem getRow(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Problem problem = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> problem.category().getDisplayName();
                case 1 -> problem.source();
                case 2 -> problem.packageName();
                default -> problem.message();
            };
        }
    }

}
