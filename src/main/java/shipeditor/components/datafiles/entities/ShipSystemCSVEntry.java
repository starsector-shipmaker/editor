package shipeditor.components.datafiles.entities;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.utility.Utility;
import java.nio.file.Path;
import java.util.Map;

@Log4j2
@Getter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class ShipSystemCSVEntry implements CSVEntry {

    private final Map<String, String> rowData;

    private final Path packageFolderPath;

    private final Path tableFilePath;

    private String shipSystemID;

    public ShipSystemCSVEntry(Map<String, String> row, Path folder, Path tablePath) {
        this.rowData = row;
        packageFolderPath = folder;
        this.tableFilePath = tablePath;
        shipSystemID = this.rowData.get("id");
    }

    @Override
    public String getMultilineTooltip() {
        String entryID = "Shipsystem ID: " + this.getShipSystemID();
        return Utility.getWithLinebreaks(entryID);
    }

    @Override
    public String getID() {
        return shipSystemID;
    }

    public void setShipSystemID(String newID) {
        this.shipSystemID = newID;
        this.rowData.put("id", newID);
    }

    @Override
    public String toString() {
        return CSVEntry.nameOrUntitled(rowData);
    }

}
