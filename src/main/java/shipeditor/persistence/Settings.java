package shipeditor.persistence;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import shipeditor.parsing.deserialize.CustomDeserializers.ColorArrayRGBADeserializer;
import shipeditor.parsing.serialize.CustomSerializers.ColorArrayRGBASerializer;
import shipeditor.utility.objects.SimpleRectangle;
import shipeditor.utility.UtilityEnums.Theme;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ClassWithTooManyFields")
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class Settings {

    public Settings() {}

    @JsonProperty("backgroundColor")
    @JsonDeserialize(using = ColorArrayRGBADeserializer.class)
    @JsonSerialize(using = ColorArrayRGBASerializer.class)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Color backgroundColor = Color.GRAY;

    @JsonProperty("gameFolderPath")
    String gameFolderPath;

    @JsonProperty("coreFolderPath")
    String coreFolderPath;

    @JsonProperty("modFolderPath")
    String modFolderPath;

    @JsonProperty("showLoadingErrors")
    boolean showLoadingErrors;

    @JsonProperty("developerMode")
    boolean developerMode;

    @JsonProperty("loadDataAtStart")
    boolean loadDataAtStart = true;

    @JsonProperty("numericSuffixesForSlots")
    boolean numericSuffixesForSlots = true;

    @JsonProperty("theme")
    Theme theme = Theme.FLAT_INTELLIJ;

    @JsonProperty("windowBounds")
    SimpleRectangle windowBounds;

    @JsonProperty("windowMaximized")
    boolean windowMaximized;

    @JsonProperty("promptForModsAtStart")
    boolean promptForModsAtStart = true;

    @JsonProperty("blacklistedMods")
    private List<String> blacklistedMods = new ArrayList<>(List.of(
            "magiclib",
            "lazylib",
            "graphicslib",
            "lunalib",
            "console commands",
            "lw_lazylib",
            "lw_console",
            "shaderlib"
    ));

    @JsonProperty("dataPackages")
    private List<GameDataPackage> dataPackages = new ArrayList<>();

    public void setBackgroundColor(Color color) {
        if (color != null) {
            this.backgroundColor = color;
        } else {
            this.backgroundColor = Color.GRAY;
        }
        SettingsManager.updateFileFromRuntime();
    }

    public void setWindowBounds(SimpleRectangle inputBounds) {
        this.windowBounds = inputBounds;
    }

    public void setWindowMaximized(boolean maximized) {
        this.windowMaximized = maximized;
    }

    public void setTheme(Theme inputTheme) {
        this.theme = inputTheme;
        SettingsManager.updateFileFromRuntime();
    }

    void setGameFolderPath(String path) {
        this.gameFolderPath = path;
        SettingsManager.updateFileFromRuntime();
    }

    void setCoreFolderPath(String path) {
        this.coreFolderPath = path;
        SettingsManager.updateFileFromRuntime();
    }

    void setModFolderPath(String path) {
        this.modFolderPath = path;
        SettingsManager.updateFileFromRuntime();
    }

    public void setShowLoadingErrors(boolean showErrors) {
        this.showLoadingErrors = showErrors;
        SettingsManager.updateFileFromRuntime();
    }

    public void setDeveloperMode(boolean devMode) {
        this.developerMode = devMode;
        SettingsManager.updateFileFromRuntime();
    }

    public void setLoadDataAtStart(boolean loadData) {
        this.loadDataAtStart = loadData;
        SettingsManager.updateFileFromRuntime();
    }

    public void setPromptForModsAtStart(boolean promptForMods) {
        this.promptForModsAtStart = promptForMods;
        SettingsManager.updateFileFromRuntime();
    }

    public void setNumericSuffixesForSlots(boolean numericSuffixes) {
        this.numericSuffixesForSlots = numericSuffixes;
        SettingsManager.updateFileFromRuntime();
    }

    public void setBlacklistedMods(List<String> blacklistedMods) {
        this.blacklistedMods = blacklistedMods;
        SettingsManager.updateFileFromRuntime();
    }

    public void addDataPackage(Path folder) {
        Path fileNamePath = folder.getFileName();
        if (fileNamePath == null) return;
        String folderName = fileNamePath.toString();
        addDataPackage(folderName);
    }

    public void addDataPackage(String folderName) {
        if (getPackage(folderName) != null) {
            return;
        }
        GameDataPackage dataPackage = new GameDataPackage(folderName, false, false);
        dataPackages.add(dataPackage);
    }

    public GameDataPackage getPackage(Path folder) {
        Path fileNamePath = folder.getFileName();
        if (fileNamePath == null) return null;
        String folderName = fileNamePath.toString();
        return getPackage(folderName);
    }

    public GameDataPackage getPackage(String folderName) {
        if (dataPackages == null || folderName == null) return null;
        if (SettingsManager.isCoreFolder(folderName)) {
            return SettingsManager.getCorePackage();
        }
        for (GameDataPackage gameDataPackage : dataPackages) {
            String packageFolderName = gameDataPackage.getFolderName();
            if (packageFolderName != null && packageFolderName.equalsIgnoreCase(folderName)) {
                return gameDataPackage;
            }
        }
        return null;
    }

    public void deduplicateDataPackages() {
        if (dataPackages == null) return;
        List<GameDataPackage> uniquePackages = new ArrayList<>();
        List<String> seenFolderNames = new ArrayList<>();
        for (GameDataPackage pkg : dataPackages) {
            String folderName = pkg.getFolderName();
            if (!seenFolderNames.contains(folderName)) {
                seenFolderNames.add(folderName);
                uniquePackages.add(pkg);
            }
        }
        if (uniquePackages.size() != dataPackages.size()) {
            dataPackages.clear();
            dataPackages.addAll(uniquePackages);
            SettingsManager.updateFileFromRuntime();
        }
    }

}
