package shipeditor.persistence;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.icons.FlatAbstractIcon;
import lombok.extern.log4j.Log4j2;
import shipeditor.PrimaryWindow;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.ViewerBackgroundChanged;
import shipeditor.parsing.FileUtilities;
import shipeditor.utility.Errors;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Log4j2
public final class Initializations {

    public static final String FILE_CHOOSER_SHORTCUTS_FILES_FUNCTION = "FileChooser.shortcuts.filesFunction";
    public static final String SHELL_FOLDER_0_X_12 = "ShellFolder: 0x12";

    private Initializations() {
    }

    @SuppressWarnings({"ProhibitedExceptionThrown", "CallToPrintStackTrace"})
    public static void updateStateFromSettings(PrimaryWindow window) {
        Settings settings = SettingsManager.getSettings();
        EventBus.publish(new ViewerBackgroundChanged(settings.getBackgroundColor()));
        try {
            Initializations.installGameFolderShortcut(window);
            Initializations.installWindowIcon(window);
        } catch (URISyntaxException | IOException e) {
            log.error("Customization of file chooser failed!", e);
            throw new RuntimeException("Customization of file chooser failed!", e);
        }
    }

    private static void installGameFolderShortcut(PrimaryWindow window) throws URISyntaxException, IOException {
        Class<? extends PrimaryWindow> windowClass = window.getClass();
        ClassLoader classLoader = windowClass.getClassLoader();
        String iconName = "gamefolder_icon64.png";
        BufferedImage iconImage = loadResourceImage(iconName, classLoader, "Loading game folder icon...");

        Settings settings = SettingsManager.getSettings();
        String folderPath = settings != null ? settings.getGameFolderPath() : null;
        if (folderPath != null && !folderPath.isEmpty() && iconImage != null) {
            BufferedImage finalIconImage = iconImage;
            UIManager.put(FILE_CHOOSER_SHORTCUTS_FILES_FUNCTION, (Function<File[], File[]>) files -> {
                ArrayList<File> list = new ArrayList<>(Arrays.asList(files != null ? files : new File[0]));
                list.removeIf(next -> next != null && SHELL_FOLDER_0_X_12.equals(next.getPath()));
                list.add(0, new File(folderPath));
                return list.toArray(new File[0]);
            });
            UIManager.put("FileChooser.shortcuts.displayNameFunction", (Function<File, String>) file -> {
                if (file != null && file.getAbsolutePath().equals(folderPath)) {
                    return "Game folder";
                }
                return null;
            });
            UIManager.put("FileChooser.shortcuts.iconFunction", (Function<File, Icon>) file -> {
                if (file != null && file.getAbsolutePath().equals(folderPath)) {
                    return new GameFolderIcon(finalIconImage);
                }
                return null;
            });
        }
    }

    private static void installWindowIcon(PrimaryWindow window) throws URISyntaxException, IOException {
        if (window == null) {
            return;
        }
        Class<? extends PrimaryWindow> windowClass = window.getClass();
        ClassLoader classLoader = windowClass.getClassLoader();
        String iconName = "icon.png";

        BufferedImage iconImage = loadResourceImage(iconName, classLoader, "Loading window icon...");

        if (iconImage != null) {
            ImageIcon icon = new ImageIcon(iconImage);
            window.setIconImage(icon.getImage());
        }
    }

    private static BufferedImage loadResourceImage(String resourceName, ClassLoader classLoader, String logMessage)
            throws URISyntaxException, IOException {
        URL iconPath = classLoader != null ? classLoader.getResource(resourceName)
                : Initializations.class.getResource("/" + resourceName);
        if (iconPath == null) {
            return null;
        }
        URI checked = iconPath.toURI();
        if (checked.isOpaque()) {
            try (InputStream inputStream = Initializations.class.getResourceAsStream("/" + resourceName)) {
                if (inputStream != null) {
                    return ImageIO.read(inputStream);
                }
            }
        } else {
            File iconFile = new File(checked);
            if (logMessage != null) {
                log.info(logMessage);
            }
            return ImageIO.read(iconFile);
        }
        return null;
    }

    private static class GameFolderIcon extends FlatAbstractIcon {

        private final BufferedImage iconImage;

        GameFolderIcon(BufferedImage image) {
            super(64, 64, Color.WHITE);
            this.iconImage = image;
        }

        @Override
        protected void paintIcon(Component c, Graphics2D g2) {
            g2.drawImage(iconImage, 0, 0, width, height, null);
        }

    }

    public static void initializeSettingsFile() {
        ObjectMapper mapper = SettingsManager.getMapperForSettingsFile();
        Settings loaded;
        Path workingDirectory = Paths.get("").toAbsolutePath();
        log.info("Current folder: {}", workingDirectory);
        File settingsFile = SettingsManager.getSettingsPath();
        try {
            if (settingsFile.exists()) {
                log.info("Reading existing settings file...");
                loaded = mapper.readValue(settingsFile, Settings.class);
                if (loaded != null) {
                    String coreFolder = loaded.getCoreFolderPath();
                    if (coreFolder != null && !coreFolder.isEmpty()) {
                        SettingsManager.setCoreFolderName(FileUtilities.extractFolderName(coreFolder));
                    }
                    log.info("Settings read successful.");
                }
            } else {
                log.info("Settings file not found, creating default...");
                loaded = SettingsManager.createDefault();
                mapper.writeValue(settingsFile, loaded);
                if (settingsFile.exists()) {
                    log.info("Default settings file creation successful.");
                }
            }
        } catch (IOException e) {
            Errors.printToStream(e);
            log.error("Failed to resolve settings file, writing default one.", e);
            loaded = SettingsManager.createDefault();
            SettingsManager.writeSettingsToFile(mapper, settingsFile, loaded);
        }
        if (loaded != null) {
            loaded.deduplicateDataPackages();
        }
        SettingsManager.setSettings(loaded);
        SettingsManager.getCorePackage();
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    private static List<Path> getPotentialGameFolders() {
        List<Path> paths = new ArrayList<>();
        for (Path local : getLocalCandidateFolders()) {
            if (!paths.contains(local)) {
                paths.add(local);
            }
        }

        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        boolean isWindows = os.contains("win");
        boolean isMac = os.contains("mac");

        if (isWindows) {
            paths.add(Paths.get("C:\\Games\\Starsector"));
            paths.add(Paths.get("C:\\Program Files (x86)\\Fractal Softworks\\Starsector"));
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isEmpty()) {
            paths.add(Paths.get(userHome, "Games", "Starsector"));
            paths.add(Paths.get(userHome, "Games", "starsector"));
            paths.add(Paths.get(userHome, ".local", "share", "starsector"));
            paths.add(Paths.get(userHome, ".local", "share", "Starsector"));
            paths.add(Paths.get(userHome, "starsector"));
            paths.add(Paths.get(userHome, "Starsector"));
        }

        if (!isWindows && !isMac) {
            paths.add(Paths.get("/opt/starsector"));
            paths.add(Paths.get("/opt/Starsector"));
        }

        if (isMac) {
            paths.add(Paths.get("/Applications/Starsector.app"));
        }

        if (isWindows) {
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    try {
                        Path rootPath = root.toPath();
                        addStarsectorSubfolders(paths, rootPath);
                    } catch (Throwable t) {
                        log.warn("Failed to resolve path for drive root: {}", root, t);
                    }
                }
            }
        }

        if (!isWindows) {
            String[] mountDirs = {"/media", "/mnt", "/Volumes", "/run/media"};
            for (String mountDir : mountDirs) {
                File mDir = new File(mountDir);
                if (mDir.exists() && mDir.isDirectory()) {
                    File[] userMounts = mDir.listFiles();
                    if (userMounts != null) {
                        for (File uMount : userMounts) {
                            if (uMount.isDirectory()) {
                                try {
                                    Path uMountPath = uMount.toPath();
                                    addStarsectorSubfolders(paths, uMountPath);

                                    File[] subMounts = uMount.listFiles();
                                    if (subMounts != null) {
                                        for (File sub : subMounts) {
                                            if (sub.isDirectory()) {
                                                try {
                                                    Path subPath = sub.toPath();
                                                    addStarsectorSubfolders(paths, subPath);
                                                } catch (Throwable t) {
                                                    log.warn("Failed to resolve path for submount: {}", sub, t);
                                                }
                                            }
                                        }
                                    }
                                } catch (Throwable t) {
                                    log.warn("Failed to resolve path for mount: {}", uMount, t);
                                }
                            }
                        }
                    }
                }
            }
        }

        return paths;
    }

    private static void addStarsectorSubfolders(List<Path> paths, Path base) {
        paths.add(base.resolve("Games/Starsector"));
        paths.add(base.resolve("Games/starsector"));
        paths.add(base.resolve("Starsector"));
        paths.add(base.resolve("starsector"));
    }

    public static void selectGameFolder() {
        Settings settings = SettingsManager.getSettings();
        String gameFolderPath = settings.getGameFolderPath();
        if (gameFolderPath != null && !gameFolderPath.isEmpty()) {
            Path gameFolder = Paths.get(gameFolderPath);
            if (Files.exists(gameFolder) && Files.isDirectory(gameFolder) && checkGameFolderEligibility(gameFolder, settings)) {
                return;
            } else {
                log.warn("Configured game folder path is invalid or missing: {}", gameFolderPath);
                settings.setGameFolderPath("");
            }
        }
        // Auto-detect if current working directory or application location is already a Starsector folder
        Path[] localCandidates = getLocalCandidateFolders();
        for (Path localPath : localCandidates) {
            if (localPath != null && Files.isDirectory(localPath) && checkGameFolderEligibility(localPath, settings)) {
                String absPath = localPath.toAbsolutePath().toString();
                log.info("Auto-detected Starsector folder at launch location: {}", absPath);
                settings.setGameFolderPath(absPath);
                return;
            }
        }

        List<Path> potentialPaths = Initializations.getPotentialGameFolders();
        List<String> candidatePaths = new ArrayList<>();
        String detectedPath = null;

        for (Path potentialFolderPath : potentialPaths) {
            if (Files.exists(potentialFolderPath) && Files.isDirectory(potentialFolderPath)) {
                String absPath = potentialFolderPath.toAbsolutePath().toString();
                if (Initializations.checkGameFolderEligibility(potentialFolderPath, settings)) {
                    if (detectedPath == null) {
                        log.info("Auto-detected valid path: {}", potentialFolderPath);
                        detectedPath = absPath;
                    }
                }
                if (!candidatePaths.contains(absPath)) {
                    candidatePaths.add(absPath);
                }
            }
        }

        if (java.awt.GraphicsEnvironment.isHeadless()) {
            if (detectedPath != null) {
                log.info("Saving game folder path from predefined paths in headless mode: {}", detectedPath);
                settings.setGameFolderPath(detectedPath);
                return;
            }
            settings.setGameFolderPath("");
            throw new RuntimeException("Game folder selection failed! No predefined path found and cannot open setup dialog in headless mode.");
        }

        String confirmedPath = shipeditor.components.dialogs.FirstTimeSetupDialog.promptForGameFolder(detectedPath, candidatePaths, settings);
        log.info("Saving game folder path: {}", confirmedPath);
        settings.setGameFolderPath(confirmedPath);
    }

    public static boolean checkGameFolderEligibility(Path filePath, Settings settings) {
        if (!Files.isDirectory(filePath)) {
            return false;
        }

        Path modsPath = filePath.resolve("mods");
        if (!Files.isDirectory(modsPath)) {
            modsPath = filePath.resolve("Contents").resolve("Resources").resolve("mods");
        }
        boolean folderHasMods = Files.isDirectory(modsPath);

        Path corePath = null;
        Path defaultCore = filePath.resolve("starsector-core");
        Path macCore = filePath.resolve("Contents").resolve("Resources").resolve("Java");
        
        if (Files.isDirectory(defaultCore) && isCoreFolder(defaultCore)) {
            corePath = defaultCore;
        } else if (Files.isDirectory(macCore) && isCoreFolder(macCore)) {
            corePath = macCore;
        } else if (isCoreFolder(filePath)) {
            corePath = filePath;
        }

        if (corePath != null && folderHasMods) {
            settings.setCoreFolderPath(corePath.toAbsolutePath().toString());
            SettingsManager.setCoreFolderName(FileUtilities.extractFolderName(corePath.toString()));
            settings.setModFolderPath(modsPath.toAbsolutePath().toString());
            return true;
        }

        return false;
    }

    private static boolean isCoreFolder(Path folderPath) {
        if (!Files.isDirectory(folderPath)) {
            return false;
        }

        Path fileNamePath = folderPath.getFileName();
        if (fileNamePath == null) return false;

        Path shipDataCsv = folderPath.resolve("data").resolve("hulls").resolve("ship_data.csv");
        if (!Files.exists(shipDataCsv)) {
            return false;
        }

        Path starfarerApiJar = folderPath.resolve("starfarer.api.jar");
        if (!Files.exists(starfarerApiJar)) {
            return false;
        }

        Path modInfo = folderPath.resolve("mod_info.json");
        if (Files.exists(modInfo)) {
            return false;
        }

        return true;
    }

    private static Path[] getLocalCandidateFolders() {
        List<Path> list = new ArrayList<>();
        try {
            Path cwd = Paths.get("").toAbsolutePath();
            addFolderAndParents(list, cwd);
        } catch (Exception e) {
            log.debug("Error checking cwd for game folder: {}", e.getMessage());
        }
        try {
            var codeSource = Initializations.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path codePath = Paths.get(codeSource.getLocation().toURI());
                Path appDir = Files.isRegularFile(codePath) ? codePath.getParent() : codePath;
                addFolderAndParents(list, appDir);
            }
        } catch (Exception e) {
            log.debug("Error checking code source for game folder: {}", e.getMessage());
        }
        return list.toArray(new Path[0]);
    }

    private static void addFolderAndParents(List<Path> list, Path folder) {
        if (folder != null) {
            if (!list.contains(folder)) {
                list.add(folder);
            }
            Path p1 = folder.getParent();
            if (p1 != null && !list.contains(p1)) {
                list.add(p1);
            }
            if (p1 != null) {
                Path p2 = p1.getParent();
                if (p2 != null && !list.contains(p2)) {
                    list.add(p2);
                }
            }
        }
    }

}
