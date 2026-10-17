package shipeditor.components.settings;

import shipeditor.utility.text.StringManager;

import shipeditor.Main;
import shipeditor.parsing.FileUtilities;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.UtilityEnums.Theme;
import shipeditor.utility.themes.Themes;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

public class PreferencesDialog extends JDialog {

    public PreferencesDialog(Frame owner) {
        super(owner, StringManager.getString("PREFERENCES_TITLE"), true);
        this.initUI();
    }

    private void initUI() {
        this.setLayout(new BorderLayout());
        this.setSize(680, 560);
        this.setMinimumSize(new Dimension(600, 480));
        this.setLocationRelativeTo(this.getOwner());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBorder(new EmptyBorder(6, 6, 0, 6));

        tabbedPane.addTab(StringManager.getString("TAB_GENERAL"),
                FontIcon.of(BoxiconsRegular.COG, 16, Themes.getIconColor()),
                new JScrollPane(this.createGeneralPanel()));
        tabbedPane.addTab(StringManager.getString("TAB_THEME"),
                FontIcon.of(BoxiconsRegular.PALETTE, 16, Themes.getIconColor()),
                this.createThemePanel());
        tabbedPane.addTab(StringManager.getString("TAB_ABOUT"),
                FontIcon.of(BoxiconsRegular.INFO_CIRCLE, 16, Themes.getIconColor()),
                this.createAboutPanel());

        this.add(tabbedPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomPanel.setBorder(new CompoundBorder(
                new LineBorder(Themes.getBorderColor(), 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));

        JButton closeButton = new JButton(StringManager.getString("CLOSE"),
                FontIcon.of(BoxiconsRegular.CHECK, 16, Themes.getIconColor()));
        closeButton.setPreferredSize(new Dimension(100, 30));
        closeButton.addActionListener(e -> this.dispose());
        bottomPanel.add(closeButton);
        this.add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createGeneralPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        Settings settings = SettingsManager.getSettings();

        // 1. Startup & Data Loading Section
        JPanel startupPanel = createSectionPanel("Startup & Loading", BoxiconsRegular.ROCKET);
        
        JCheckBox autoLoadData = new JCheckBox(StringManager.getString("AUTO_LOAD_DATA_AT_START"));
        autoLoadData.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoLoadData.setToolTipText(StringManager.getString("AUTOMATICALLY_LOAD_INDEXED_MOD_DATA_WHEN_THE_EDITOR_STARTS"));
        autoLoadData.setSelected(SettingsManager.isDataAutoloadEnabled());
        autoLoadData.addActionListener(event -> settings.setLoadDataAtStart(autoLoadData.isSelected()));
        startupPanel.add(autoLoadData);
        startupPanel.add(Box.createVerticalStrut(4));

        JCheckBox togglePromptMods = new JCheckBox(StringManager.getString("ALWAYS_SHOW_MOD_SELECTION_AT_STARTUP"));
        togglePromptMods.setAlignmentX(Component.LEFT_ALIGNMENT);
        togglePromptMods.setToolTipText(StringManager.getString("FORCE_THE_MOD_SELECTION_DIALOG_TO_APPEAR_EVERY_TIME_YOU_OPEN_THE_EDITOR"));
        togglePromptMods.setSelected(settings.isPromptForModsAtStart());
        togglePromptMods.addActionListener(event -> settings.setPromptForModsAtStart(togglePromptMods.isSelected()));
        startupPanel.add(togglePromptMods);
        startupPanel.add(Box.createVerticalStrut(4));

        JCheckBox toggleFileErrorPopups = new JCheckBox(StringManager.getString("ENABLE_FILE_ERROR_POP_UPS"));
        toggleFileErrorPopups.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggleFileErrorPopups.setToolTipText(StringManager.getString("SHOW_ERROR_DIALOGS_WHEN_CORRUPTED_FILES_ARE_ENCOUNTERED_DURING_LOADING"));
        toggleFileErrorPopups.setSelected(SettingsManager.areFileErrorPopupsEnabled());
        toggleFileErrorPopups.addActionListener(event -> settings.setShowLoadingErrors(toggleFileErrorPopups.isSelected()));
        startupPanel.add(toggleFileErrorPopups);

        mainPanel.add(startupPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 2. Mod Packages & Indexing Section
        JPanel modPanel = createSectionPanel("Mod Management & Indexing", BoxiconsRegular.FOLDER);

        JButton openModSelectionBtn = new JButton(StringManager.getString("SELECT_MOD_PACKAGES"),
                FontIcon.of(BoxiconsRegular.LIST_CHECK, 16, Themes.getIconColor()));
        openModSelectionBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        openModSelectionBtn.setToolTipText(StringManager.getString("OPEN_THE_MOD_SELECTION_DIALOG_TO_CHANGE_INDEXED_MODS"));
        openModSelectionBtn.addActionListener(e -> {
            shipeditor.components.dialogs.ModSelectionDialog dialog =
                    new shipeditor.components.dialogs.ModSelectionDialog(
                            (Frame) javax.swing.SwingUtilities.getWindowAncestor(this)
                    );
            if (dialog.showDialog()) {
                shipeditor.parsing.loading.FileLoading.forceReindexAndLoadGameData();
            }
        });
        modPanel.add(openModSelectionBtn);
        modPanel.add(Box.createVerticalStrut(8));

        JLabel blacklistDesc = new JLabel(StringManager.getString("COMMA_SEPARATED_LIST_OF_MOD_FOLDER_NAMES_OR_PREFIXES_TO_IGNORE"));
        blacklistDesc.setFont(blacklistDesc.getFont().deriveFont(11f));
        blacklistDesc.setForeground(Themes.getDisabledTextColor());
        blacklistDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        modPanel.add(blacklistDesc);
        modPanel.add(Box.createVerticalStrut(4));

        JTextArea blacklistArea = new JTextArea(3, 20);
        blacklistArea.setLineWrap(true);
        blacklistArea.setWrapStyleWord(true);
        if (settings.getBlacklistedMods() != null) {
            blacklistArea.setText(String.join(", ", settings.getBlacklistedMods()));
        }
        blacklistArea.getDocument().addDocumentListener(new BlacklistDocumentListener(blacklistArea, settings));
        JScrollPane blacklistScroll = new JScrollPane(blacklistArea);
        blacklistScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        blacklistScroll.setBorder(new LineBorder(Themes.getBorderColor(), 1, true));
        modPanel.add(blacklistScroll);

        mainPanel.add(modPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 3. Configuration & Paths Section
        JPanel pathsPanel = createSectionPanel("Files & Directories", BoxiconsRegular.DATA);

        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttonsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton openSettings = new JButton(StringManager.getString("OPEN_SETTINGS_FILE"),
                FontIcon.of(BoxiconsRegular.FILE, 16, Themes.getIconColor()));
        openSettings.addActionListener(e -> {
            File settingsPath = SettingsManager.getSettingsPath();
            FileUtilities.openPathInDesktop(settingsPath);
        });
        buttonsRow.add(openSettings);

        JButton openEditorFolder = new JButton(StringManager.getString("OPEN_EDITOR_FOLDER"),
                FontIcon.of(BoxiconsRegular.FOLDER_OPEN, 16, Themes.getIconColor()));
        openEditorFolder.addActionListener(e -> {
            File settingsPath = SettingsManager.getSettingsPath();
            File editorFolder = settingsPath != null ? settingsPath.getParentFile() : null;
            if (editorFolder != null) {
                FileUtilities.openPathInDesktop(editorFolder);
            }
        });
        buttonsRow.add(openEditorFolder);

        pathsPanel.add(buttonsRow);
        mainPanel.add(pathsPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 4. Diagnostics Section
        JPanel diagPanel = createSectionPanel("Diagnostics & Logging", BoxiconsRegular.BUG);

        JCheckBox toggleDeveloperMode = new JCheckBox(StringManager.getString("ENABLE_DEVELOPER_MESSAGES_LOGS"));
        toggleDeveloperMode.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggleDeveloperMode.setToolTipText(StringManager.getString("ENABLE_EXTENSIVE_DIAGNOSTIC_LOGGING_AND_DEVELOPER_ONLY_WARNINGS"));
        toggleDeveloperMode.setSelected(SettingsManager.isDeveloperModeEnabled());
        toggleDeveloperMode.addActionListener(event -> settings.setDeveloperMode(toggleDeveloperMode.isSelected()));
        diagPanel.add(toggleDeveloperMode);

        mainPanel.add(diagPanel);

        return mainPanel;
    }

    private JPanel createSectionPanel(String title, org.kordamp.ikonli.Ikon icon) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(new CompoundBorder(
                BorderFactory.createTitledBorder(
                        new LineBorder(Themes.getBorderColor(), 1, true),
                        title,
                        TitledBorder.LEFT,
                        TitledBorder.TOP,
                        section.getFont().deriveFont(Font.BOLD, 12f),
                        Themes.getIconColor()
                ),
                new EmptyBorder(8, 10, 8, 10)
        ));
        return section;
    }

    private JPanel createThemePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel section = createSectionPanel(StringManager.getString("TAB_THEME"), BoxiconsRegular.PALETTE);

        JLabel themeNotice = new JLabel(StringManager.getString("SELECT_APPLICATION_THEME_WILL_TAKE_EFFECT_AFTER_RESTART"));
        themeNotice.setFont(themeNotice.getFont().deriveFont(12f));
        themeNotice.setForeground(Themes.getDisabledTextColor());
        themeNotice.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(themeNotice);
        section.add(Box.createVerticalStrut(12));

        Settings settings = SettingsManager.getSettings();
        Theme[] themes = Theme.values();
        ButtonGroup buttonGroup = new ButtonGroup();

        for (Theme theme : themes) {
            JRadioButton setTheme = new JRadioButton(theme.getDisplayedName());
            setTheme.setFont(setTheme.getFont().deriveFont(13f));
            setTheme.setAlignmentX(Component.LEFT_ALIGNMENT);
            setTheme.addActionListener(e -> settings.setTheme(theme));

            buttonGroup.add(setTheme);

            if (settings.getTheme() == theme) {
                setTheme.setSelected(true);
            }

            section.add(setTheme);
            section.add(Box.createVerticalStrut(6));
        }

        panel.add(section);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private JPanel createAboutPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel card = createSectionPanel("Starsector Shipmaker", BoxiconsRegular.INFO_CIRCLE);

        JLabel appTitle = new JLabel("Starsector Shipmaker");
        appTitle.setFont(appTitle.getFont().deriveFont(Font.BOLD, 18f));
        appTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(appTitle);
        card.add(Box.createVerticalStrut(4));

        String projectVersion = Main.VERSION;
        JLabel versionBadge = new JLabel("Version " + projectVersion);
        versionBadge.setFont(versionBadge.getFont().deriveFont(Font.BOLD, 12f));
        versionBadge.setForeground(new Color(90, 180, 255));
        versionBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(versionBadge);
        card.add(Box.createVerticalStrut(12));

        JLabel authorsLabel = new JLabel(StringManager.getString("AUTHORS_THEVOLKFLOWER"));
        authorsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(authorsLabel);
        card.add(Box.createVerticalStrut(4));

        JLabel startedLabel = new JLabel(StringManager.getString("STARTED_MAY_2026"));
        startedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(startedLabel);
        card.add(Box.createVerticalStrut(12));

        // System & Runtime Diagnostics
        JPanel sysInfo = new JPanel(new GridBagLayout());
        sysInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        sysInfo.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(Themes.getBorderColor(), 1, true), "Runtime Environment"
        ));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addSysInfoRow(sysInfo, gbc, row++, "Java Version:", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        addSysInfoRow(sysInfo, gbc, row++, "Operating System:", System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        File coreFolder = SettingsManager.getCoreFolderPath() != null ? SettingsManager.getCoreFolderPath().toFile() : null;
        addSysInfoRow(sysInfo, gbc, row++, "Core Path:", coreFolder != null ? coreFolder.getAbsolutePath() : "Not Configured");

        card.add(sysInfo);

        panel.add(card);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    private void addSysInfoRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JLabel val = new JLabel(value);
        val.setFont(val.getFont().deriveFont(11f));
        val.setForeground(Themes.getDisabledTextColor());
        panel.add(val, gbc);
    }

    private static final class BlacklistDocumentListener implements javax.swing.event.DocumentListener {
        private final JTextArea blacklistArea;
        private final Settings settings;

        BlacklistDocumentListener(JTextArea blacklistArea, Settings settings) {
            this.blacklistArea = blacklistArea;
            this.settings = settings;
        }

        private void update() {
            String text = blacklistArea.getText();
            java.util.List<String> list = java.util.Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            settings.setBlacklistedMods(list);
        }

        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
    }
}
