package shipeditor.parsing;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import shipeditor.PrimaryWindow;
import shipeditor.components.viewer.layers.LayerFactory;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.parsing.serialize.CustomSerializers.ColorArrayRGBASerializer;
import shipeditor.persistence.BasicPrettyPrinter;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.VariantFile;
import shipeditor.utility.Errors;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.StringConstants;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class FileUtilities {

    private static final ObjectMapper MAPPER;

    public static final String OPEN_COMMAND_CANCELLED_BY_USER = "Open command cancelled by user.";

    @Getter @Setter
    private static File lastSpriteDirectory;

    @Getter @Setter
    private static File lastShipDirectory;

    @Getter @Setter
    private static File lastVariantDirectory;

    @Getter @Setter
    private static File lastGeneralDirectory;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
        MAPPER.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        MAPPER.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        
        MAPPER.configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true);
        MAPPER.configure(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS.mappedFeature(), true);
        MAPPER.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        MAPPER.configure(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS.mappedFeature(), true);
        MAPPER.configure(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS.mappedFeature(), true);

        MAPPER.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        MAPPER.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        MAPPER.coercionConfigFor(LogicalType.Collection).setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);

        DefaultPrettyPrinter prettyPrinter = new BasicPrettyPrinter().createInstance();
        MAPPER.setDefaultPrettyPrinter(prettyPrinter);

        MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);

        SimpleModule module = new SimpleModule();
        module.addSerializer(Color.class, new ColorArrayRGBASerializer());
        module.addDeserializer(String.class, new JsonDeserializer<String>() {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = StringDeserializer.instance.deserialize(p, ctxt);
                return text != null ? text.intern() : null;
            }
        });
        MAPPER.registerModule(module);
    }

    private FileUtilities() {}

    public static void updateActionStates(ViewerLayer currentlySelected) {
        if (!(currentlySelected instanceof ShipLayer layer)) {
            FileLoading.OPEN_SPRITE.setEnabled(true);
            FileLoading.OPEN_SHIP.setEnabled(false);
            return;
        }
        ShipPainter painter = layer.getPainter();
        if (painter == null) {
            FileLoading.OPEN_SPRITE.setEnabled(true);
            FileLoading.OPEN_SHIP.setEnabled(false);
        } else {
            boolean hullState = painter.getSprite() != null;
            FileLoading.OPEN_SPRITE.setEnabled(true);
            FileLoading.OPEN_SHIP.setEnabled(hullState);
        }
    }
    public static void openPathInDesktop(Path toOpen) {
        if (toOpen == null) {
            log.warn("Attempted to open null path in desktop.");
            return;
        }
        FileUtilities.openPathInDesktop(toOpen.toFile());
    }

    public static void openPathInDesktop(File toOpen) {
        if (toOpen == null) {
            log.warn("Attempted to open null file in desktop.");
            return;
        }
        try {
            Desktop.getDesktop().open(toOpen);
        } catch (IOException | IllegalArgumentException ex) {
            Errors.showFileOpeningError(toOpen, ex);
        }
    }

    public static ShipLayer createShipLayerWithSprite(File spriteFile) {
        Sprite sprite = FileLoading.loadSprite(spriteFile);

        var manager = StaticController.getLayerManager();
        ShipLayer layer = null;
        if (manager != null) {
            layer = manager.createShipLayer();
            manager.setActiveLayer(layer);
            var viewer = StaticController.getViewer();
            viewer.loadLayer(layer, sprite);
        }

        return layer;
    }

    /**
     * @param targetFile can be either file (CSV table, usually) or a directory.
     * @return map of entries where key is package folder and value is file instance of target in package.
     */
    public static Map<Path, File> getFileFromPackages(Path targetFile) {
        Settings settings = SettingsManager.getSettings();

        List<Path> allModFolders = SettingsManager.getAllModFolders();

        Path coreFolderPath = Path.of(settings.getCoreFolderPath());

        Path coreFilePath = coreFolderPath.resolve(targetFile);

        Map<Path, File> matchingFiles = new LinkedHashMap<>();
        matchingFiles.put(coreFolderPath, coreFilePath.toFile());

        try (Stream<Path> modDirectories = allModFolders.stream()) {
            modDirectories.forEach(modDir -> {
                Path fileNamePath = modDir.getFileName();
                if (fileNamePath == null || !SettingsManager.isModActive(fileNamePath.toString())) {
                    return;
                }
                Path targetFilePath = modDir.resolve(targetFile);
                if (Files.exists(targetFilePath)) {
                    matchingFiles.put(modDir, targetFilePath.toFile());
                }
            });
        }
        return matchingFiles;
    }

    public static ObjectMapper getConfigured() {
        return MAPPER;
    }

    public static JFileChooser getFileChooser() {
        return FileUtilities.getFileChooser(null);
    }

    public static JFileChooser getFileChooser(FileFilter fileFilter) {
        Path coreFolderPath = SettingsManager.getCoreFolderPath();
        JFileChooser fileChooser = new JFileChooser(coreFolderPath.toString());

        File directory = FileUtilities.getLastGeneralDirectory();
        if (directory != null) {
            fileChooser.setCurrentDirectory(directory);
        }
        if (fileFilter != null) {
            fileChooser.setFileFilter(fileFilter);
        }
        return fileChooser;
    }

    public static JFileChooser getImageChooser() {
        FileNameExtensionFilter pngFilter = new FileNameExtensionFilter(
                "PNG Image", "png");

        JFileChooser fileChooser = FileUtilities.getFileChooser(pngFilter);

        File directory = FileUtilities.getLastSpriteDirectory();
        if (directory != null) {
            fileChooser.setCurrentDirectory(directory);
        }

        return fileChooser;
    }

    public static JFileChooser getHullFileChooser() {
        FileNameExtensionFilter shipFilter = new FileNameExtensionFilter(
                StringManager.getString("JSON_SHIP_FILES"), "ship");

        JFileChooser fileChooser = FileUtilities.getFileChooser(shipFilter);

        File directory = FileUtilities.getLastShipDirectory();
        if (directory != null) {
            fileChooser.setCurrentDirectory(directory);
        }

        return fileChooser;
    }

    public static File resolveDefaultVariantDirectory(
            shipeditor.components.viewer.layers.ship.data.ShipVariant variant,
            ShipLayer shipLayer) {
        log.debug("Resolving default variant directory for variant={}", variant != null ? variant.getVariantId() : "null");
        if (variant != null && variant.getVariantFilePath() != null) {
            Path parent = variant.getVariantFilePath().getParent();
            if (parent != null && Files.exists(parent)) {
                return parent.toFile();
            }
        }

        if (variant != null && variant.getVariantId() != null) {
            shipeditor.representation.ship.VariantFile existing =
                    shipeditor.representation.GameDataRepository.getVariantByID(variant.getVariantId());
            if (existing != null && existing.getVariantFilePath() != null) {
                Path parent = existing.getVariantFilePath().getParent();
                if (parent != null && Files.exists(parent)) {
                    return parent.toFile();
                }
            }
        }

        String hullId = null;
        if (variant != null && variant.getShipHullId() != null && !variant.getShipHullId().isBlank()) {
            hullId = variant.getShipHullId();
        } else if (shipLayer != null) {
            hullId = shipLayer.getShipID();
        }

        if (hullId != null && !hullId.isBlank()) {
            shipeditor.representation.ship.ShipSpecFile spec =
                    shipeditor.representation.GameDataRepository.retrieveSpecByID(hullId);
            if (spec instanceof shipeditor.representation.ship.HullSpecFile hullSpec && hullSpec.getFilePath() != null) {
                Path parent = hullSpec.getFilePath().getParent();
                Path parentName = parent != null ? parent.getFileName() : null;
                if (parent != null && parentName != null && "hulls".equalsIgnoreCase(parentName.toString())) {
                    Path grandparent = parent.getParent();
                    if (grandparent != null) {
                        Path variantsDir = grandparent.resolve("variants");
                        if (Files.exists(variantsDir)) {
                            return variantsDir.toFile();
                        }
                    }
                }
            } else if (spec instanceof shipeditor.representation.ship.SkinSpecFile skinSpec && skinSpec.getFilePath() != null) {
                Path ancestor = skinSpec.getFilePath().getParent();
                while (ancestor != null) {
                    Path ancestorName = ancestor.getFileName();
                    if (ancestorName != null && "data".equalsIgnoreCase(ancestorName.toString())) {
                        Path variantsDir = ancestor.resolve("variants");
                        if (Files.exists(variantsDir)) {
                            return variantsDir.toFile();
                        }
                        break;
                    }
                    ancestor = ancestor.getParent();
                }
            }

            shipeditor.components.datafiles.entities.ShipCSVEntry csvEntry =
                    shipeditor.representation.GameDataRepository.retrieveShipCSVEntryByID(hullId);
            if (csvEntry == null) {
                String baseHullId = shipeditor.representation.GameDataRepository.getBaseHullID(hullId);
                if (baseHullId != null) {
                    csvEntry = shipeditor.representation.GameDataRepository.retrieveShipCSVEntryByID(baseHullId);
                }
            }
            if (csvEntry != null && csvEntry.getPackageFolderPath() != null) {
                Path variantsDir = csvEntry.getPackageFolderPath().resolve("data").resolve("variants");
                if (Files.exists(variantsDir)) {
                    return variantsDir.toFile();
                }
            }
        }

        if (lastVariantDirectory != null && lastVariantDirectory.exists()) {
            return lastVariantDirectory;
        }

        if (lastShipDirectory != null
                && "hulls".equalsIgnoreCase(lastShipDirectory.getName())
                && lastShipDirectory.getParentFile() != null) {
            File variantsDir = new File(lastShipDirectory.getParentFile(), "variants");
            if (variantsDir.exists()) {
                return variantsDir;
            }
        }

        return lastGeneralDirectory;
    }

    public static JFileChooser getVariantFileChooser(
            shipeditor.components.viewer.layers.ship.data.ShipVariant variant,
            ShipLayer shipLayer) {
        FileNameExtensionFilter variantFileFilter = new FileNameExtensionFilter(
                "JSON variant files", StringConstants.VARIANT);
        JFileChooser fileChooser = FileUtilities.getFileChooser(variantFileFilter);
        File directory = resolveDefaultVariantDirectory(variant, shipLayer);
        if (directory != null) {
            fileChooser.setCurrentDirectory(directory);
        }
        return fileChooser;
    }

    /**
     * Shared "Load Variant..." flow used by the File menu and the variant chooser panel.
     * Lets the user pick a .variant file (starting at the ship's data/variants directory), registers it
     * in the game data and attaches it to the active ship layer if the hull matches; otherwise offers to
     * open it as a new layer.
     *
     * @param activeLayer currently active layer (may be null or a non-ship layer)
     */
    public static void loadVariantFromDisk(ViewerLayer activeLayer) {
        ShipLayer targetLayer = activeLayer instanceof ShipLayer checked ? checked : null;
        JFileChooser fileChooser = FileUtilities.getVariantFileChooser(null, targetLayer);
        fileChooser.setDialogTitle("Load Variant");
        int returnVal = fileChooser.showOpenDialog(PrimaryWindow.getInstance());

        File currentDir = fileChooser.getCurrentDirectory();
        if (currentDir != null) {
            FileUtilities.setLastVariantDirectory(currentDir);
            FileUtilities.setLastGeneralDirectory(currentDir);
        }
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File selectedFile = fileChooser.getSelectedFile();
        if (selectedFile == null || !selectedFile.isFile()) {
            return;
        }

        log.debug("Loading variant file from disk: {}", selectedFile);
        try {
            VariantFile loadedFile = FileLoading.loadVariantFile(selectedFile);
            if (loadedFile == null) {
                JOptionPane.showMessageDialog(PrimaryWindow.getInstance(),
                        "Failed to parse variant file: " + selectedFile.getName(),
                        "Variant Load Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            if ((loadedFile.getHullId() == null || loadedFile.getHullId().isBlank()) && targetLayer != null) {
                loadedFile.setHullId(targetLayer.getShipID());
            }
            GameDataRepository.putVariant(loadedFile);

            boolean hullMatches = targetLayer != null
                    && targetLayer.getPainter() != null
                    && Objects.equals(targetLayer.getShipID(), loadedFile.getHullId());
            if (hullMatches) {
                targetLayer.getPainter().selectVariant(loadedFile);
                return;
            }

            int choice = JOptionPane.showConfirmDialog(PrimaryWindow.getInstance(),
                    "Variant '" + loadedFile.getVariantId() + "' is for hull '" + loadedFile.getHullId()
                            + "', which does not match the active layer.\nOpen it as a new ship layer?",
                    "Load Variant",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            ShipLayer newLayer = LayerFactory.createLayerFromVariant(loadedFile);
            LayerManager layerManager = StaticController.getLayerManager();
            if (newLayer != null && layerManager != null) {
                layerManager.setActiveLayer(newLayer);
            } else {
                JOptionPane.showMessageDialog(PrimaryWindow.getInstance(),
                        "Could not create a layer for hull '" + loadedFile.getHullId() + "'.",
                        "Variant Load Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (RuntimeException ex) {
            log.error("Failed to load variant file: {}", selectedFile, ex);
            JOptionPane.showMessageDialog(PrimaryWindow.getInstance(),
                    "Error loading variant file: " + ex.getMessage(),
                    "Variant Load Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public static File ensureFileExtension(JFileChooser fileChooser, String extension) {
        File selectedFile = fileChooser.getSelectedFile();
        File result;

        String fileName = selectedFile.getName();
        boolean alreadyHasExtension = fileName.endsWith("." + extension);
        if (alreadyHasExtension) {
            result = selectedFile;
        } else {
            result = new File(selectedFile + "." + extension);
        }
        return result;
    }

    public static String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(StringConstants.EXTENSION_DOT);
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }

    public static boolean isFileWithinGamePackages(File file) {
        Path filePath = file.toPath();

        if (filePath.startsWith(SettingsManager.getCoreFolderPath())) {
            return true;
        }
        for (Path modFolder : SettingsManager.getAllModFolders()) {
            if (filePath.startsWith(modFolder)) {
                return true;
            }
        }

        return false;
    }

    public static String extractFolderName(String path) {
        FileSystem fileSystem = FileSystems.getDefault();
        Path fullPath = fileSystem.getPath(path);
        Path fileNamePath = fullPath.getFileName();
        if (fileNamePath == null) return "";
        return fileNamePath.toString();
    }

}
