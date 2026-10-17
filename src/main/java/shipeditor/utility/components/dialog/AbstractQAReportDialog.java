package shipeditor.utility.components.dialog;

import shipeditor.PrimaryWindow;
import shipeditor.utility.text.StringManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractQAReportDialog extends JDialog {

    public record QAIssue(String entityId, String issueType, String details) {}

    protected final JTable table;
    protected final QATableModel model;
    protected final JLabel statusLabel;
    private final String passMessage;
    private final String issuesSuffix;

    protected AbstractQAReportDialog(String title, String idColumnName, String scanningMessage,
                                     String passMessage, String issuesSuffix) {
        super(PrimaryWindow.getInstance(), title, false);
        this.passMessage = passMessage;
        this.issuesSuffix = issuesSuffix;

        this.setLayout(new BorderLayout());
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.setSize(700, 500);
        this.setLocationRelativeTo(PrimaryWindow.getInstance());

        model = new QATableModel(idColumnName);
        table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(450);

        JScrollPane scrollPane = new JScrollPane(table);
        this.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel(scanningMessage);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton(StringManager.getString("CLOSE"));
        closeButton.addActionListener(e -> this.dispose());
        buttonPanel.add(closeButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        this.add(bottomPanel, BorderLayout.SOUTH);
    }

    protected void runQAAnalysis() {
        SwingWorker<List<QAIssue>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<QAIssue> doInBackground() throws Exception {
                return performAnalysis();
            }

            @Override
            protected void done() {
                try {
                    List<QAIssue> issues = get();
                    model.setIssues(issues);
                    if (issues.isEmpty()) {
                        statusLabel.setText(passMessage);
                    } else {
                        statusLabel.setText(StringManager.getString("FOUND") + issues.size() + issuesSuffix);
                    }
                } catch (Exception e) {
                    statusLabel.setText(StringManager.getString("ERROR_DURING_QA_ANALYSIS"));
                }
            }
        };
        worker.execute();
    }

    protected abstract List<QAIssue> performAnalysis();

    protected static class QATableModel extends AbstractTableModel {
        private final String[] columns;
        private List<QAIssue> issues = new ArrayList<>();

        public QATableModel(String idColumnName) {
            this.columns = new String[]{idColumnName, "Issue", "Details"};
        }

        public void setIssues(List<QAIssue> issues) {
            this.issues = issues;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return issues.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            QAIssue issue = issues.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> issue.entityId();
                case 1 -> issue.issueType();
                case 2 -> issue.details();
                default -> "";
            };
        }
    }
}
