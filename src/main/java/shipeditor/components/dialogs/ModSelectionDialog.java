package shipeditor.components.dialogs;

import shipeditor.utility.text.StringManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log4j2
public class ModSelectionDialog extends JDialog {

    @Getter
    public static class ModInfo {
        private final String folderName;
        private final Path folderPath;
        private String id = "";
        private String name = "";
        private String version = "";
        private String author = "";
        private String description = "";

        public ModInfo(String folderName, Path folderPath) {
            this.folderName = folderName;
            this.folderPath = folderPath;
            this.name = folderName;
            parseModInfoJson();
        }

        private void parseModInfoJson() {
            File modInfoFile = folderPath.resolve("mod_info.json").toFile();
            if (!modInfoFile.exists()) {
                return;
            }

            try {
                JsonNode root = shipeditor.parsing.loading.JsonSpecLoader.parseCorrectableJSON(modInfoFile, JsonNode.class);
                if (root != null) {
                    if (root.has("id")) id = root.get("id").asText().trim();
                    else if (root.has("ID")) id = root.get("ID").asText().trim();

                    if (root.has("name")) name = root.get("name").asText().trim();
                    if (root.has("version")) version = root.get("version").asText().trim();
                    if (root.has("author")) author = root.get("author").asText().trim();
                    if (root.has("description")) description = root.get("description").asText().trim();
                } else {
                    fallbackRegexParse(modInfoFile);
                }
            } catch (Exception e) {
                log.warn("Standard JSON parse failed for {}, attempting regex fallback", folderName, e);
                fallbackRegexParse(modInfoFile);
            }

            if (name.isEmpty()) {
                name = folderName;
            }
        }

        private void fallbackRegexParse(File modInfoFile) {
            try {
                String content = Files.readString(modInfoFile.toPath());
                id = extractRegex(content, "[\"']?(?:id|ID)[\"']?\\s*:\\s*[\"']([^\"']+)[\"']");
                name = extractRegex(content, "[\"']?name[\"']?\\s*:\\s*[\"']([^\"']+)[\"']");
                version = extractRegex(content, "[\"']?version[\"']?\\s*:\\s*[\"']?([^\"',\\s]+)[\"']?");
                author = extractRegex(content, "[\"']?author[\"']?\\s*:\\s*[\"']([^\"']+)[\"']");
            } catch (java.io.IOException ignored) {
                log.warn("Regex fallback failed to read file {}", modInfoFile.getName(), ignored);
            }
        }

        private String extractRegex(String text, String regex) {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            return matcher.find() ? matcher.group(1).trim() : "";
        }

        public boolean matchesQuery(String query) {
            if (query == null || query.isBlank()) return true;
            String q = query.toLowerCase(java.util.Locale.ROOT);
            return folderName.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || name.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || id.toLowerCase(java.util.Locale.ROOT).contains(q)
                    || author.toLowerCase(java.util.Locale.ROOT).contains(q);
        }
    }

    private final Map<String, JCheckBox> modCheckboxes = new HashMap<>();
    private final Map<String, JPanel> modCardPanels = new HashMap<>();
    private final Map<String, ModInfo> modMetadataMap = new HashMap<>();
    private final Settings settings = SettingsManager.getSettings();
    private boolean resultIsLoad = false;

    // Visible for testing
    SwingWorker<List<String>, Void> fetchWorker;

    public ModSelectionDialog(Frame owner) {
        super(owner, "Select Mods to Index", true);
        initUI();
    }

    public boolean showDialog() {
        setVisible(true);
        return resultIsLoad;
    }

    private JLabel statusLabel;
    private JTextField searchField;
    private JPanel checkboxContainer;

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        setMinimumSize(new Dimension(400, 500));
        setSize(560, 680);
        setLocationRelativeTo(getOwner());

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout(5, 5));
        headerPanel.setBorder(new EmptyBorder(12, 16, 8, 16));
        headerPanel.setBackground(UIManager.getColor("Panel.background"));

        JLabel titleLabel = new JLabel(StringManager.getString("SELECT_MODS_TO_INDEX"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        JLabel subtitleLabel = new JLabel(StringManager.getString("CHOOSE_WHICH_MOD_PACKAGES_TO_PARSE_INTO_THE_EDITOR_DATABASE"));
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(11f));
        subtitleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 2, 2));
        titleBox.setOpaque(false);
        titleBox.add(titleLabel);
        titleBox.add(subtitleLabel);
        headerPanel.add(titleBox, BorderLayout.NORTH);

        // Search Filter Field
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search by name, ID, author, or folder...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterCheckboxes(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterCheckboxes(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterCheckboxes(); }
        });
        headerPanel.add(searchField, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);

        // Checkbox Panel inside ScrollPane
        checkboxContainer = new JPanel();
        checkboxContainer.setLayout(new BoxLayout(checkboxContainer, BoxLayout.Y_AXIS));
        checkboxContainer.setBorder(new EmptyBorder(8, 12, 8, 12));

        rebuildModList();

        JScrollPane scrollPane = new JScrollPane(checkboxContainer);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Controls Panel
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(new EmptyBorder(8, 16, 12, 16));

        // Quick Tools Bar (Select All / Deselect All / Sync)
        JPanel toolsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton selectAllBtn = new JButton(StringManager.getString("SELECT_ALL_1"));
        selectAllBtn.addActionListener(e -> {
            setAllModsSelected(true);
            updateStatusLabel();
        });
        toolsBar.add(selectAllBtn);

        JButton deselectAllBtn = new JButton(StringManager.getString("DESELECT_ALL_1"));
        deselectAllBtn.addActionListener(e -> {
            setAllModsSelected(false);
            updateStatusLabel();
        });
        toolsBar.add(deselectAllBtn);

        // Action Buttons Bar
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        JButton saveButton = new JButton(StringManager.getString("INDEX_SELECTED_MODS"));
        saveButton.setFont(saveButton.getFont().deriveFont(Font.BOLD, 13f));
        saveButton.setForeground(new Color(0, 120, 215));
        saveButton.addActionListener(e -> {
            applySelection();
            saveButton.setEnabled(false);
            saveButton.setText(StringManager.getString("SCANNING"));
            
            // The actual scanning is delegated to FileLoading.loadGameData() 
            // which is triggered by the caller when this dialog returns true.
            resultIsLoad = true;
            dispose();
        });

        JButton syncGameBtn = new JButton(StringManager.getString("SYNC_FROM_GAME"));
        syncGameBtn.setToolTipText(StringManager.getString("IMPORT_ENABLED_MODS_FROM_STARSECTOR_S_ENABLED_MODS_JSON"));
        syncGameBtn.addActionListener(e -> fetchFromEnabledMods(saveButton));
        toolsBar.add(syncGameBtn);

        JButton refreshDiskBtn = new JButton(StringManager.getString("REFRESH_DISK"));
        refreshDiskBtn.setToolTipText(StringManager.getString("RESCAN_THE_MODS_FOLDER_FOR_NEWLY_ADDED_OR_DELETED_MODS"));
        refreshDiskBtn.addActionListener(e -> {
            SettingsManager.invalidateModCache();
            rebuildModList();
            filterCheckboxes();
        });
        toolsBar.add(refreshDiskBtn);

        buttonPanel.add(saveButton);

        JButton closeButton = new JButton(StringManager.getString("CANCEL"));
        closeButton.addActionListener(e -> {
            resultIsLoad = false;
            dispose();
        });
        buttonPanel.add(closeButton);

        statusLabel = new JLabel();
        updateStatusLabel();

        JPanel southContent = new JPanel(new BorderLayout(5, 8));
        southContent.add(toolsBar, BorderLayout.NORTH);

        JPanel actionRow = new JPanel(new BorderLayout());
        actionRow.add(statusLabel, BorderLayout.WEST);
        actionRow.add(buttonPanel, BorderLayout.EAST);
        southContent.add(actionRow, BorderLayout.SOUTH);

        bottomPanel.add(southContent, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        if (settings.getDataPackages() == null || settings.getDataPackages().isEmpty()) {
            fetchFromEnabledMods(saveButton);
        }
    }

    private void rebuildModList() {
        checkboxContainer.removeAll();
        modCheckboxes.clear();
        modCardPanels.clear();
        modMetadataMap.clear();
        
        SettingsManager.invalidateModCache();
        List<Path> modFolders = SettingsManager.getAllModFolders();
        for (Path modFolder : modFolders) {
            if (shipeditor.parsing.loading.LibModFilter.isLibMod(modFolder)) continue;
            Path fileNamePath = modFolder.getFileName();
            if (fileNamePath == null) continue;
            String folderName = fileNamePath.toString();
            if (SettingsManager.isCoreFolder(folderName)) continue;

            ModInfo info = new ModInfo(folderName, modFolder);
            modMetadataMap.put(folderName, info);

            GameDataPackage pkg = settings.getPackage(folderName);
            boolean isEnabled = (pkg == null) || !pkg.isDisabled();

            JPanel cardPanel = createModCardPanel(info, isEnabled);
            modCardPanels.put(folderName, cardPanel);
            checkboxContainer.add(cardPanel);
            checkboxContainer.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        
        checkboxContainer.revalidate();
        checkboxContainer.repaint();
        updateStatusLabel();
    }

    private JPanel createModCardPanel(ModInfo info, boolean isEnabled) {
        JPanel card = new JPanel(new BorderLayout(8, 0));
        card.setBorder(new CompoundBorder(
                new LineBorder(UIManager.getColor("Component.borderColor") != null ? UIManager.getColor("Component.borderColor") : Color.LIGHT_GRAY, 1, true),
                new EmptyBorder(6, 8, 6, 8)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox checkBox = new JCheckBox("", isEnabled);
        checkBox.addActionListener(e -> updateStatusLabel());
        modCheckboxes.put(info.getFolderName(), checkBox);
        card.add(checkBox, BorderLayout.WEST);

        StringBuilder sb = new StringBuilder();
        sb.append("<html><b><font size='+1'>").append(escapeHtml(info.getName())).append("</font></b>");
        if (!info.getVersion().isEmpty()) {
            sb.append(" <font color='#0078D7'>v").append(escapeHtml(info.getVersion())).append("</font>");
        }
        if (!info.getAuthor().isEmpty()) {
            sb.append(" <font color='#777777'>by ").append(escapeHtml(info.getAuthor())).append("</font>");
        }
        sb.append("<br><font size='-1' color='#777777'>");
        if (!info.getId().isEmpty()) {
            sb.append("ID: <b>").append(escapeHtml(info.getId())).append("</b> | ");
        }
        sb.append("Folder: <i>").append(escapeHtml(info.getFolderName())).append("</i></font></html>");

        JLabel detailsLabel = new JLabel(sb.toString());
        card.add(detailsLabel, BorderLayout.CENTER);

        return card;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void filterCheckboxes() {
        String text = searchField.getText().trim();
        for (Map.Entry<String, ModInfo> entry : modMetadataMap.entrySet()) {
            String folderName = entry.getKey();
            ModInfo info = entry.getValue();
            boolean visible = info.matchesQuery(text);

            JPanel card = modCardPanels.get(folderName);
            if (card != null) {
                card.setVisible(visible);
            }
        }
        checkboxContainer.revalidate();
        checkboxContainer.repaint();
    }

    private void setAllModsSelected(boolean selected) {
        modCheckboxes.values().forEach(cb -> cb.setSelected(selected));
    }

    private void updateStatusLabel() {
        if (statusLabel == null) return;
        long selectedCount = modCheckboxes.values().stream().filter(cb -> cb.isSelected()).count();
        statusLabel.setText(String.format("Selected: %d / %d mods", selectedCount, modCheckboxes.size()));
    }

    private void applySelection() {
        for (Map.Entry<String, JCheckBox> entry : modCheckboxes.entrySet()) {
            String folderName = entry.getKey();
            boolean isEnabled = entry.getValue().isSelected();

            GameDataPackage pkg = settings.getPackage(folderName);
            if (pkg == null) {
                settings.addDataPackage(folderName);
                pkg = settings.getPackage(folderName);
            }
            if (pkg != null) {
                pkg.setDisabled(!isEnabled);
            }
        }

        // Ensure core is always loaded
        String coreFolderName = SettingsManager.getCoreFolderName();
        if (coreFolderName != null) {
            GameDataPackage corePkg = settings.getPackage(coreFolderName);
            if (corePkg != null) {
                corePkg.setDisabled(false);
            }
        }

        SettingsManager.updateFileFromRuntime();
    }

    private void fetchFromEnabledMods(JButton saveBtn) {
        String modFolderPath = settings.getModFolderPath();
        if (modFolderPath == null || modFolderPath.isEmpty()) return;

        File enabledModsFile = Paths.get(modFolderPath, "enabled_mods.json").toFile();
        if (!enabledModsFile.exists()) {
            return;
        }

        saveBtn.setEnabled(false);
        saveBtn.setText(StringManager.getString("SYNCING"));

        fetchWorker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                List<String> foldersToEnable = new ArrayList<>();
                try {
                    ObjectMapper mapper = SettingsManager.getMapperForSettingsFile();
                    JsonNode rootNode = mapper.readTree(enabledModsFile);
                    JsonNode enabledModsNode = rootNode.get("enabledMods");

                    if (enabledModsNode != null && enabledModsNode.isArray()) {
                        java.util.Set<String> enabledSet = new java.util.HashSet<>();
                        for (JsonNode modNode : enabledModsNode) {
                            if (modNode != null && modNode.isTextual()) {
                                enabledSet.add(modNode.asText().trim().toLowerCase(java.util.Locale.ROOT));
                            }
                        }

                        for (Map.Entry<String, ModInfo> entry : modMetadataMap.entrySet()) {
                            String folderName = entry.getKey();
                            ModInfo info = entry.getValue();

                            boolean isEnabledInGame = enabledSet.contains(folderName.toLowerCase(java.util.Locale.ROOT))
                                    || (!info.getId().isEmpty() && enabledSet.contains(info.getId().toLowerCase(java.util.Locale.ROOT)));

                            if (isEnabledInGame) {
                                foldersToEnable.add(folderName);
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.error("Failed to read enabled_mods.json on dialog init", ex);
                }
                return foldersToEnable;
            }

            @Override
            protected void done() {
                try {
                    List<String> foldersToEnable = get();
                    setAllModsSelected(false);
                    for (String folderName : foldersToEnable) {
                        JCheckBox cb = modCheckboxes.get(folderName);
                        if (cb != null) {
                            cb.setSelected(true);
                        }
                    }
                    updateStatusLabel();
                } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                    log.error("Error updating mod checkboxes", e);
                } finally {
                    saveBtn.setEnabled(true);
                    saveBtn.setText(StringManager.getString("INDEX_SELECTED_MODS"));
                }
            }
        };
        fetchWorker.execute();
    }
}
