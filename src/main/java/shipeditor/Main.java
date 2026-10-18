package shipeditor;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import shipeditor.components.logging.StandardOutputRedirector;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.persistence.Initializations;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.persistence.database.DatabaseManager;
import shipeditor.utility.Errors;
import shipeditor.utility.ToastingExceptionHandler;
import shipeditor.utility.Utility;
import shipeditor.utility.text.StringConstants;
import shipeditor.utility.UtilityEnums.Theme;
import shipeditor.utility.themes.Themes;

import javax.swing.JPopupMenu;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.function.Function;

@Log4j2
public final class Main {

    public static final String VERSION = "0.0.1f";

    private static FileLock applicationLock;
    private static FileChannel lockChannel;

    private Main() {}

    /**
     * Checks if the currently running JVM heap size meets the minimum 4GB requirement.
     * If the allocated heap is below 4GB, this method forks a new Java process with {@code -Xmx4g}
     * and essential JVM flags (such as disabling D3D/OpenGL hardware acceleration for Swing-LWJGL compatibility),
     * then terminates the current process.
     * 
     * @param args Command line arguments to forward to the child JVM.
     */
    private static void checkAndRelaunch(String[] args) {
        if (Boolean.getBoolean("shipeditor.relaunched")) {
            return;
        }

        boolean needsRelaunch = false;
        long maxMemory = Runtime.getRuntime().maxMemory();
        long threshold = 3900L * 1024L * 1024L;

        if (maxMemory < threshold) {
            needsRelaunch = true;
            log.info("Max memory available is {} MB, which is less than the 4 GB required.", maxMemory / (1024 * 1024));
        }

        boolean isWindows = Utility.isWindows();
        boolean isLinux = Utility.isLinux();

        if (isWindows) {
            if (!Boolean.getBoolean("sun.awt.noerasebackground") || !Boolean.getBoolean("sun.java2d.noddraw")) {
                needsRelaunch = true;
                log.info("Missing essential Windows UI properties for AWT/Swing compatibility.");
            }
        } else if (isLinux) {
            if (!Boolean.getBoolean("sun.awt.noerasebackground") || !Boolean.getBoolean("sun.java2d.noddraw")) {
                needsRelaunch = true;
                log.info("Missing essential Linux UI properties for AWT/Swing compatibility.");
            }
            if (System.getenv("DISPLAY") == null) {
                needsRelaunch = true;
                log.info("DISPLAY environment variable is missing, forcing relaunch to set default display.");
            }
        }

        if (needsRelaunch) {
            log.info("Relaunching JVM with updated arguments...");
            try {
                String javaHome = System.getProperty("java.home");
                String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
                if (isWindows) {
                    javaBin += ".exe";
                }

                List<String> command = new ArrayList<>();
                command.add(javaBin);
                command.add("-Xmx4g");
                command.add("-XX:+UseG1GC");
                command.add("-XX:+UseStringDeduplication");
                command.add("-XX:MinHeapFreeRatio=10");
                command.add("-XX:MaxHeapFreeRatio=20");
                command.add("-Dsun.java2d.opengl=false");
                command.add("-Dsun.java2d.d3d=false");
                command.add("-Dsun.java2d.noddraw=true");
                command.add("-Dsun.awt.noerasebackground=true");
                command.add("-Dorg.lwjgl.opengl.contextAPI=native");
                command.add("-Dshipeditor.relaunched=true");

                var codeSource = Main.class.getProtectionDomain().getCodeSource();
                if (codeSource != null) {
                    File codeLocation = new File(codeSource.getLocation().toURI());
                    if (codeLocation.isFile() && codeLocation.getName().endsWith(".jar")) {
                        command.add("-jar");
                        command.add(codeLocation.getAbsolutePath());
                    } else {
                        command.add("-cp");
                        command.add(System.getProperty("java.class.path"));
                        command.add("shipeditor.Main");
                    }
                } else {
                    command.add("-cp");
                    command.add(System.getProperty("java.class.path"));
                    command.add("shipeditor.Main");
                }

                command.addAll(Arrays.asList(args));
                log.info("Starting child JVM with command: {}", String.join(" ", command));

                ProcessBuilder builder = new ProcessBuilder(command);
                if (isLinux && System.getenv("DISPLAY") == null) {
                    builder.environment().put("DISPLAY", ":0");
                    log.info("Injected DISPLAY=:0 into child JVM environment.");
                }
                builder.inheritIO();
                Process process = builder.start();

                // Clean up child process if parent process is killed/terminated unexpectedly
                Thread parentShutdownHook = new Thread(() -> {
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                });
                Runtime.getRuntime().addShutdownHook(parentShutdownHook);

                try {
                    process.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                } finally {
                    try {
                        Runtime.getRuntime().removeShutdownHook(parentShutdownHook);
                    } catch (IllegalStateException ignored) {
                        // VM already shutting down
                    }
                }

                System.exit(process.exitValue());
            } catch (java.io.IOException | java.net.URISyntaxException | SecurityException e) {
                log.error("Failed to relaunch JVM with required arguments", e);
            }
        }
    }

    /**
     * Tries to acquire an exclusive OS file lock on {@code .ship_editor.lock} to enforce single-instance execution
     * and prevent database corruption or dual 4GB heap allocation.
     *
     * @return {@code true} if lock acquired or bypassed via {@code -Dshipeditor.force=true}, {@code false} if another instance is running.
     */
    private static boolean acquireApplicationLock() {
        if (Boolean.getBoolean("shipeditor.force")) {
            log.info("Bypassing single-instance lock due to -Dshipeditor.force=true");
            return true;
        }

        try {
            Path workingDirectory = Paths.get("").toAbsolutePath();
            Path lockPath = workingDirectory.resolve(".ship_editor.lock");

            lockChannel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.READ);

            applicationLock = lockChannel.tryLock();
            if (applicationLock == null) {
                log.error("Another instance of Starsector Ship Editor is already running (file lock held at: {})", lockPath);
                return false;
            }

            long pid = ProcessHandle.current().pid();
            lockChannel.truncate(0);
            lockChannel.write(ByteBuffer.wrap(String.valueOf(pid).getBytes(StandardCharsets.UTF_8)));
            lockChannel.force(true);

            return true;
        } catch (Exception e) {
            log.warn("Could not acquire application file lock, proceeding without lock: {}", e.getMessage());
            return true;
        }
    }

    private static void releaseApplicationLock() {
        try {
            if (applicationLock != null && applicationLock.isValid()) {
                applicationLock.release();
                applicationLock = null;
            }
            if (lockChannel != null && lockChannel.isOpen()) {
                lockChannel.close();
                lockChannel = null;
            }
            Path lockPath = Paths.get("").toAbsolutePath().resolve(".ship_editor.lock");
            Files.deleteIfExists(lockPath);
        } catch (Exception e) {
            log.warn("Error releasing application lock: {}", e.getMessage());
        }
    }

    public static void configurePlatformProperties() {
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.d3d", "false");
        System.setProperty("sun.java2d.noddraw", "true");
        System.setProperty("sun.awt.noerasebackground", "true");
        System.setProperty("org.lwjgl.opengl.contextAPI", "native");

        try {
            org.lwjgl.system.Configuration.OPENGL_CONTEXT_API.set("native");
        } catch (Throwable ignored) {
            // In case LWJGL Configuration class is not yet on classloader
        }
    }

    public static void main(String[] args) {
        configurePlatformProperties();

        checkAndRelaunch(args);

        // Enforce single-instance lock in Java layer
        if (!acquireApplicationLock()) {
            System.err.println("[ERROR] Starsector Ship Editor is already running!");
            System.err.println("Only one instance may run simultaneously to avoid SQLite database corruption and excessive memory consumption.");
            System.err.println("To force launch a new instance, pass -Dshipeditor.force=true or use './ship_editor.sh -f'");

            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                try {
                    javax.swing.JOptionPane.showMessageDialog(
                            null,
                            StringManager.getString("STARSECTOR_SHIP_EDITOR_IS_ALREADY_RUNNIN_MSG")
                                    + "Only one instance may run at a time to prevent SQLite database corruption and high memory usage.\n\n"
                                    + "Please close the existing instance or pass '-f' to force start.",
                            "Already Running",
                            javax.swing.JOptionPane.WARNING_MESSAGE
                    );
                } catch (Exception e) {
                    log.warn("Failed to display 'Already Running' dialog: {}", e.getMessage());
                }
            }
            System.exit(1);
        }

        // Register global shutdown hook for resources (HikariCP pool, database flush, lock release)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM shutdown initiated. Releasing database and file locks...");
            DatabaseManager.closeDataSource();
            releaseApplicationLock();
        }, "ShipEditor-Shutdown-Hook"));
        
        configurePlatformProperties();
        
        Locale.setDefault(Locale.US);
        SwingUtilities.invokeLater(() -> {
            // These method calls are initialization block; the order of calls is important.
            Initializations.initializeSettingsFile();
            configureLaf();

            Initializations.selectGameFolder();
            Settings settings = SettingsManager.getSettings();

            boolean shouldLoadData = settings.isLoadDataAtStart();
            if (settings.isPromptForModsAtStart()) {
                shipeditor.components.dialogs.ModSelectionDialog modDialog = new shipeditor.components.dialogs.ModSelectionDialog(null);
                if (modDialog.showDialog()) {
                    shouldLoadData = true;
                }
            }

            PrimaryWindow window = PrimaryWindow.create();
            Initializations.updateStateFromSettings(window);

            window.showGUI();

            if (shouldLoadData) {
                SwingUtilities.invokeLater(FileLoading::forceReindexAndLoadGameData);
            }

            // Bind the error streams AFTER the UI is fully initialized and visible
            // to prevent silent layout crashes on startup!
            StandardOutputRedirector.redirectStandardStreams();
            Errors.initGlobalHandler();
            ToastingExceptionHandler.install();
        });
    }

    private static void configureLaf() {
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);

        if (Utility.isLinux()) {
            UIManager.put("Popup.dropShadowPainted", false);
        }
        
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabSeparatorsFullHeight", true);
        UIManager.put("SplitPane.dividerSize", 8);
        UIManager.put("SplitPane.oneTouchButtonSize", 10);
        if (!Utility.isLinux()) {
            UIManager.put("TitlePane.useWindowDecorations", true);
        }

        UIManager.put(StringConstants.TREE_PAINT_LINES, true);
        UIManager.put("Tree.showDefaultIcons", true);
        UIManager.put("TitlePane.showIcon", true);
        UIManager.put("TitlePane.showIconInDialogs", true);
        UIManager.put("FileChooser.readOnly", true);

        UIManager.put(Initializations.FILE_CHOOSER_SHORTCUTS_FILES_FUNCTION, (Function<File[], File[]>) files -> {
            ArrayList<File> list = new ArrayList<>( Arrays.asList( files ) );
            list.removeIf(next -> Initializations.SHELL_FOLDER_0_X_12.equals(next.getPath()));
            return list.toArray(new File[0]);
        } );

        Settings settings = SettingsManager.getSettings();
        Theme settingsTheme = settings.getTheme();
        Runnable setterMethod = settingsTheme.getSetterMethod();
        setterMethod.run();

        Themes.setupColors();

        // Force early initialization of ToolTipUI to avoid lazy-loading classloader issues later on EDT
        try {
            new JToolTip().updateUI();
        } catch (RuntimeException ignored) {
        }
    }

}
