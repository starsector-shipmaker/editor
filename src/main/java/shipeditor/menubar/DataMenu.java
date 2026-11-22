package shipeditor.menubar;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.parsing.FileUtilities;
import shipeditor.parsing.JsonProcessor;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.utility.Utility;
import shipeditor.utility.themes.Themes;

import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Log4j2
class DataMenu extends JMenu {

    DataMenu() {
        super(StringManager.getString("MENU_DATA"));
        this.setMnemonic(KeyEvent.VK_D);
    }

    void initialize() {
        JMenuItem reloadAllGameData = new JMenuItem(StringManager.getString("RELOAD_GAME_DATA"));
        reloadAllGameData.setIcon(FontIcon.of(BoxiconsRegular.REFRESH, 16, Themes.getIconColor()));
        reloadAllGameData.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        reloadAllGameData.addActionListener(event -> FileLoading.loadGameData());
        if (FileLoading.isLoadingInProgress()) {
            reloadAllGameData.setEnabled(false);
        }
        this.add(reloadAllGameData);

        JMenuItem reindexData = new JMenuItem(StringManager.getString("RE_INDEX_MOD_FOLDERS_RELOAD"));
        reindexData.setIcon(FontIcon.of(BoxiconsRegular.REFRESH, 16, Themes.getIconColor()));
        reindexData.addActionListener(event -> {
            if (FileLoading.isLoadingInProgress()) {
                JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                        StringManager.getString("CANNOT_RE_INDEX_WHILE_DATA_IS_CURRENTLY_MSG"),
                        StringManager.getString("LOADING_IN_PROGRESS_TITLE"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            FileLoading.forceReindexAndLoadGameData();
        });
        if (FileLoading.isLoadingInProgress()) {
            reindexData.setEnabled(false);
        }
        this.add(reindexData);

        this.addSeparator();

        JMenuItem selectMods = new JMenuItem(StringManager.getString("SELECT_MODS_TO_LOAD"));
        selectMods.setIcon(FontIcon.of(BoxiconsRegular.LIST_CHECK, 16, Themes.getIconColor()));
        selectMods.addActionListener(event -> {
            if (FileLoading.isLoadingInProgress()) {
                JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                        StringManager.getString("CANNOT_MODIFY_MOD_SELECTION_WHILE_DATA_I_MSG"),
                        StringManager.getString("LOADING_IN_PROGRESS_TITLE"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            shipeditor.components.dialogs.ModSelectionDialog modDialog = new shipeditor.components.dialogs.ModSelectionDialog(shipeditor.PrimaryWindow.getInstance());
            boolean shouldLoad = modDialog.showDialog();
            if (shouldLoad) {
                FileLoading.forceReindexAndLoadGameData();
            }
        });
        this.add(selectMods);

        JMenuItem dataFilters = new JMenuItem(StringManager.getString("DATA_FILTERS_1"));
        dataFilters.setIcon(FontIcon.of(BoxiconsRegular.FILTER, 16, Themes.getIconColor()));
        dataFilters.addActionListener(event -> shipeditor.components.datafiles.trees.FilterDialogs.showCombinedFilters(null));
        this.add(dataFilters);

        this.addSeparator();

        JMenuItem jsonCorrector = DataMenu.getJSONCorrector();
        this.add(jsonCorrector);

        JMenu qaSubmenu = new JMenu("QA Reports");
        qaSubmenu.setIcon(FontIcon.of(BoxiconsRegular.SHIELD_QUARTER, 16, Themes.getIconColor()));

        JMenuItem qaReport = new JMenuItem(StringManager.getString("WEAPON_OFFSET_QA_REPORT"));
        qaReport.setIcon(FontIcon.of(BoxiconsRegular.SHIELD_QUARTER, 16, Themes.getIconColor()));
        qaReport.addActionListener(event -> {
            shipeditor.utility.components.dialog.WeaponQAReportDialog dialog = new shipeditor.utility.components.dialog.WeaponQAReportDialog();
            dialog.setVisible(true);
        });
        qaSubmenu.add(qaReport);

        JMenuItem hullQaReport = new JMenuItem(StringManager.getString("HULL_QA_REPORT"));
        hullQaReport.setIcon(FontIcon.of(BoxiconsRegular.SHIELD_QUARTER, 16, Themes.getIconColor()));
        hullQaReport.addActionListener(event -> {
            shipeditor.utility.components.dialog.HullQAReportDialog dialog = new shipeditor.utility.components.dialog.HullQAReportDialog();
            dialog.setVisible(true);
        });
        qaSubmenu.add(hullQaReport);

        this.add(qaSubmenu);
    }

    private static JMenuItem getJSONCorrector() {
        JMenuItem jsonCorrector = new JMenuItem(StringManager.getString("REPAIR_MALFORMED_JSON"));
        jsonCorrector.setIcon(FontIcon.of(BoxiconsRegular.WRENCH, 16, Themes.getIconColor()));
        jsonCorrector.setToolTipText(StringManager.getString("FIXES_SEMANTICALLY_INCORRECT_JSON_THEN_SAVES_A_CORRECTED_COPY_TO_THE_SAME_LOCATION"));
        jsonCorrector.addActionListener(e -> {
            JFileChooser fileChooser = FileUtilities.getFileChooser();

            File directory = FileUtilities.getLastGeneralDirectory();
            if (directory != null) {
                fileChooser.setCurrentDirectory(directory);
            }

            int returnVal = fileChooser.showOpenDialog(shipeditor.PrimaryWindow.getInstance());
            File currentDirectory = fileChooser.getCurrentDirectory();
            FileUtilities.setLastGeneralDirectory(currentDirectory);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                DataMenu.correctJSON(fileChooser, currentDirectory);
            }
        });
        return jsonCorrector;
    }

    private static void correctJSON(JFileChooser fileChooser, File currentDirectory) {
        File file = fileChooser.getSelectedFile();
        if (file == null) return;

        try {
            String result = JsonProcessor.straightenMalformed(file);
            String fixedFileName = Utility.getFilenameWithoutExtension(file.getName()) + "_corrected.json";
            String targetFilePath = new File(currentDirectory, fixedFileName).getPath();
            try (PrintWriter out = new PrintWriter(targetFilePath, StandardCharsets.UTF_8)) {
                out.println(result);
            }
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("CORRECTED_FILE_SAVED_AS_N_MSG") + targetFilePath,
                    StringManager.getString("JSON_CORRECTED_TITLE"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            log.error("Failed to correct malformed JSON file: " + file.getPath(), ex);
            JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("FAILED_TO_PROCESS_JSON_FILE_N_MSG") + ex.getMessage(),
                    StringManager.getString("ERROR_CORRECTING_JSON_TITLE"), JOptionPane.ERROR_MESSAGE);
        }
    }

}
