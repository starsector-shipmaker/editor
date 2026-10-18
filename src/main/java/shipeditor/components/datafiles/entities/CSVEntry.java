package shipeditor.components.datafiles.entities;

import shipeditor.utility.text.StringConstants;
import shipeditor.utility.text.StringManager;

import java.nio.file.Path;
import java.util.Map;

public interface CSVEntry {

    Map<String, String> getRowData();

    String getID();

    String getMultilineTooltip();

    Path getPackageFolderPath();

    Path getTableFilePath();

    /**
     * Returns the row's display name, or the localized "untitled" label when it has none.
     */
    static String nameOrUntitled(Map<String, String> rowData) {
        String displayedName = rowData.get(StringConstants.NAME);
        if (displayedName == null || displayedName.isEmpty()) {
            displayedName = StringManager.getString("UNTITLED");
        }
        return displayedName;
    }

}
