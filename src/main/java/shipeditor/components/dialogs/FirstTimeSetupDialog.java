package shipeditor.components.dialogs;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import shipeditor.PrimaryWindow;
import shipeditor.persistence.Initializations;
import shipeditor.persistence.Settings;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.JComboBox;

@Log4j2
public class FirstTimeSetupDialog extends JDialog {

    private String confirmedPath = null;

    private FirstTimeSetupDialog(String detectedPath, java.util.List<String> candidatePaths, Settings settings) {
        super(PrimaryWindow.getInstance(), "First-Time Setup", true);
        this.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        infoPanel.add(new JLabel(StringManager.getString("WELCOME_TO_STARSECTOR_SHIP_EDITOR")));
        infoPanel.add(new JLabel(StringManager.getString("PLEASE_SELECT_YOUR_STARSECTOR_INSTALLATION_FOLDER_CONTAINS_STARSECTOR_CORE_AND_MODS")));
        mainPanel.add(infoPanel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        JComboBox<String> pathComboBox = new JComboBox<>();
        pathComboBox.setEditable(true);
        pathComboBox.setPrototypeDisplayValue("                                                                                ");
        pathComboBox.setMaximumRowCount(8);
        
        java.awt.Component editorComponent = pathComboBox.getEditor().getEditorComponent();
        if (editorComponent instanceof JTextField textField) {
            textField.putClientProperty("JTextField.placeholderText", "Select or type path to Starsector folder...");
        }

        if (candidatePaths != null) {
            for (String cand : candidatePaths) {
                if (cand != null && !cand.isEmpty()) {
                    addItemIfAbsent(pathComboBox, cand);
                }
            }
        }

        if (detectedPath != null && !detectedPath.isEmpty()) {
            addItemIfAbsent(pathComboBox, detectedPath);
            pathComboBox.setSelectedItem(detectedPath);
        } else if (pathComboBox.getItemCount() > 0) {
            pathComboBox.setSelectedIndex(0);
        }

        Object initialSel = pathComboBox.getSelectedItem();
        if (initialSel != null) {
            pathComboBox.setToolTipText(initialSel.toString());
        }
        pathComboBox.addActionListener(e -> {
            Object sel = pathComboBox.getSelectedItem();
            if (sel != null) {
                pathComboBox.setToolTipText(sel.toString());
            }
        });

        inputPanel.add(pathComboBox, BorderLayout.CENTER);

        JButton browseButton = new JButton(StringManager.getString("BROWSE"));
        browseButton.addActionListener(e -> {
            JFileChooser folderChooser = new JFileChooser();
            folderChooser.setDialogTitle("Choose folder containing installed game");
            folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            Object curSel = pathComboBox.getSelectedItem();
            String startPath = curSel != null ? curSel.toString() : detectedPath;
            if (startPath != null && !startPath.isEmpty()) {
                folderChooser.setCurrentDirectory(new File(startPath));
            }
            int returnVal = folderChooser.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                String selected = folderChooser.getSelectedFile().getAbsolutePath();
                addItemIfAbsent(pathComboBox, selected);
                pathComboBox.setSelectedItem(selected);
            }
        });
        inputPanel.add(browseButton, BorderLayout.EAST);
        mainPanel.add(inputPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton confirmButton = new JButton(StringManager.getString("CONFIRM"));
        confirmButton.addActionListener(e -> {
            Object selectedItem = pathComboBox.getSelectedItem();
            String currentPath = selectedItem != null ? selectedItem.toString().trim() : "";
            if (currentPath.isEmpty()) {
                JOptionPane.showMessageDialog(this, StringManager.getString("PATH_CANNOT_BE_EMPTY_MSG"), "Invalid Path", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Path path = Paths.get(currentPath);
            if (Initializations.checkGameFolderEligibility(path, settings)) {
                confirmedPath = path.toAbsolutePath().toString();
                this.dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        StringManager.getString("SELECTED_FOLDER_DOES_NOT_CONTAIN_CORE_AN_MSG"),
                        "Invalid folder",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton exitButton = new JButton(StringManager.getString("EXIT"));
        exitButton.addActionListener(e -> this.dispose());

        buttonPanel.add(exitButton);
        buttonPanel.add(confirmButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        this.add(mainPanel);

        this.pack();
        this.setMinimumSize(new Dimension(500, 200));
        this.setSize(Math.max(500, this.getWidth()), Math.max(200, this.getHeight()));
        this.setLocationRelativeTo(null);
        
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmedPath = null;
            }
        });
    }

    private static void addItemIfAbsent(JComboBox<String> comboBox, String item) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            if (item.equals(comboBox.getItemAt(i))) {
                return;
            }
        }
        comboBox.addItem(item);
    }

    public static String promptForGameFolder(String detectedPath, Settings settings) {
        return promptForGameFolder(detectedPath, java.util.Collections.emptyList(), settings);
    }

    public static String promptForGameFolder(String detectedPath, java.util.List<String> candidatePaths, Settings settings) {
        FirstTimeSetupDialog dialog = new FirstTimeSetupDialog(detectedPath, candidatePaths, settings);
        dialog.setVisible(true);
        if (dialog.confirmedPath == null) {
            log.info("Game folder selection cancelled by user, exiting.");
            System.exit(0);
            return null;
        }
        return dialog.confirmedPath;
    }
}
