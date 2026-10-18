package shipeditor.utility;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import shipeditor.components.ComponentEnums.CoordsDisplayMode;
import shipeditor.components.viewer.entities.AngledPoint;
import shipeditor.components.viewer.entities.BaseWorldPoint;
import shipeditor.components.viewer.entities.WorldPoint;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.overseers.MiscCaching;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.CoordinatesFormatter;
import javax.swing.Timer;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@SuppressWarnings("ClassWithTooManyMethods")
@Log4j2
public final class Utility {

    @SuppressWarnings("RegExpSimplifiable")
    public static final Pattern SPLIT_BY_COMMA = Pattern.compile(",[ ]*");
    private static final Pattern FILE_EXTENSION = Pattern.compile("[.][^.]+$");

    /**
     * Private constructor prevents instantiation of utility class.
     */
    private Utility() {}

    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");
    private static final boolean IS_LINUX = OS_NAME.contains("linux") || OS_NAME.contains("nix");
    private static final boolean IS_MAC = OS_NAME.contains("mac");

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    public static boolean isLinux() {
        return IS_LINUX;
    }

    public static boolean isMac() {
        return IS_MAC;
    }

    public static int parseIntegerOrDefault(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double parseDoubleOrDefault(String value, double defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }


    private static final java.util.Map<Integer, Font> ORBITRON_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static Font getOrbitron(int size) {
        return ORBITRON_CACHE.computeIfAbsent(size, s -> new Font("Orbitron", Font.BOLD, s));
    }

    public static Font getDefaultFont() {
        return javax.swing.UIManager.getFont("Label.font");
    }

    public static Point2D getSpriteCenterDifferenceToAnchor(RenderedImage image) {
        Point2D point = MiscCaching.getNewPoint();
        float x = image.getWidth() / 2.0f;
        float y = image.getHeight() / 2.0f;
        point.setLocation(x, y);
        return point;
    }

    public static Point2D correctAdjustedCursor(Point2D adjustedCursor, AffineTransform screenToWorld) {
        Point2D wP = screenToWorld.transform(adjustedCursor, null);
        double roundedX = Math.round(wP.getX() * 2) / 2.0;
        double roundedY = Math.round(wP.getY() * 2) / 2.0;
        return new Point2D.Double(roundedX, roundedY);
    }

    public static ActionListener scheduleTask(int waitTime, ActionListener taskBeforeStart, ActionListener taskWhenDone) {
        return e -> {
            taskBeforeStart.actionPerformed(e);
            Timer timer = new Timer(waitTime, evt -> taskWhenDone.actionPerformed(e));
            timer.setRepeats(false);
            timer.start();
        };
    }

    @SuppressWarnings("WeakerAccess")
    public static Point2D roundPointCoordinates(Point2D point, int decimalPlaces) {
        double roundedX = Utility.round(point.getX(), decimalPlaces);
        double roundedY = Utility.round(point.getY(), decimalPlaces);
        return new Point2D.Double(roundedX, roundedY);
    }

    public static double round(double value, int decimalPlaces) {
        if (decimalPlaces < 0) throw new IllegalArgumentException("Decimal places cannot be negative.");
        if (Double.isNaN(value) || Double.isInfinite(value)) return value;
        BigDecimal bigDecimal = BigDecimal.valueOf(value);
        bigDecimal = bigDecimal.setScale(decimalPlaces, RoundingMode.HALF_UP);
        return bigDecimal.doubleValue();
    }

    public static String wrapTextWithHtml(String text, int maxWords) {
        if (text == null || text.isEmpty()) return text;
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) return text;
        
        StringBuilder builder = new StringBuilder("<html>");
        for (int i = 0; i < words.length; i++) {
            builder.append(words[i]);
            if ((i + 1) % maxWords == 0 && i != words.length - 1) {
                builder.append("<br>");
            } else if (i != words.length - 1) {
                builder.append(" ");
            }
        }
        builder.append("</html>");
        return builder.toString();
    }

    public static String getWithLinebreaks(String ... lines) {
        StringBuilder builder = new StringBuilder("<html>" );
        Stream<String> stringStream = Arrays.stream(lines);
        stringStream.forEachOrdered(line -> {
            if (line == null || line.isEmpty()) return;
            
            String[] words = line.split("\\s+");
            if (words.length > 7) {
                for (int i = 0; i < words.length; i++) {
                    builder.append(words[i]);
                    if ((i + 1) % 7 == 0 && i != words.length - 1) {
                        builder.append("<br>");
                    } else if (i != words.length - 1) {
                        builder.append(" ");
                    }
                }
            } else {
                builder.append(line);
            }
            builder.append("<br>");
        });
        String builderUnfinished = builder.toString();
        if ("<html>".equals(builderUnfinished)) return "";
        builder.append("</html>");
        return builder.toString();
    }

    public static String getTooltipForSprite(Sprite sprite) {
        String spriteName = "Filename: " + sprite.getFilename();
        BufferedImage image = sprite.getImage();
        String width = "Width: " + image.getWidth();
        String height = "Height: " + image.getHeight();
        return Utility.getWithLinebreaks(spriteName, width, height);
    }

    public static String getPointPositionText(Point2D location) {
            return location.getX() + ", " + location.getY();
    }

    public static Point2D getPointCoordinatesForDisplay(Point2D pointPosition) {
        CoordsDisplayMode coordsMode = StaticController.getCoordsMode();
        ViewerLayer activeLayer = StaticController.getActiveLayer();
        if (activeLayer == null) {
            return pointPosition;
        }
        LayerPainter layerPainter = activeLayer.getPainter();
        if (layerPainter == null || layerPainter.isUninitialized()) {
            return pointPosition;
        }
        return Utility.getPointCoordinatesForDisplay(pointPosition, layerPainter, coordsMode);
    }

    public static Point2D getPointCoordinatesForDisplay(Point2D pointPosition, LayerPainter layerPainter,
                                                        CoordsDisplayMode mode) {
        Point2D result = pointPosition;

        double positionX = pointPosition.getX();
        double positionY = pointPosition.getY();
        switch (mode) {
            case WORLD -> {
                AffineTransform transform = layerPainter.getRotationTransform();
                result = transform.transform(result, null);
            }
            case SPRITE_CENTER -> {
                Point2D center = layerPainter.getSpriteCenter();
                double centerX = center.getX();
                double centerY = center.getY();
                result = new Point2D.Double(positionX - centerX, positionY - centerY);

            }
            case SHIPCENTER_ANCHOR -> {
                if (!(layerPainter instanceof ShipPainter checkedPainter)) break;
                Point2D center = checkedPainter.getCenterAnchor();
                double centerX = center.getX();
                double centerY = center.getY();
                result = new Point2D.Double(positionX - centerX, (-positionY + centerY));
            }
            // This case uses different coordinate system alignment to be consistent with game files.
            // Otherwise, user might be confused as shown point coordinates won't match with those in file.
            case SHIP_CENTER -> {
                if (!(layerPainter instanceof ShipPainter checkedPainter)) break;
                BaseWorldPoint shipCenter = checkedPainter.getShipCenter();
                Point2D center = shipCenter.getPosition();
                double centerX = center.getX();
                double centerY = center.getY();
                result = new Point2D.Double(-(positionY - centerY), -(positionX - centerX));
            }
        }
        result = CoordinatesFormatter.roundPoint(result);
        return result;
    }

    public static double clampAngleWithRounding(double radians) {
        double rotationDegrees = Math.toDegrees(radians);
        double clampedDegrees = (((360 - rotationDegrees) % 360) + 360) % 360;
        double rounded = Utility.round(clampedDegrees, 5);
        return rounded >= 360.0 ? 0.0 : rounded;
    }

    public static double flipAngle(double degrees) {
        double flipped = -degrees;
        return ((flipped % 360) + 360) % 360;
    }

    public static String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase(Locale.ROOT) + input.substring(1);
    }

    public static double transformAngle(double raw) {
        double transformed = raw % 360;
        if (transformed < 0) {
            transformed += 360;
        }

        transformed = (360 - transformed) % 360;
        return transformed - 90;
    }

    public static Sprite loadSpriteFromPath(String pathInPackage, Path packageFolderPath) {
        if (pathInPackage != null && !pathInPackage.isEmpty()) {
            Path filePath = Path.of(pathInPackage);
            File spriteFile = FileLoading.fetchDataFile(filePath, packageFolderPath);

            if (spriteFile == null) {
                Errors.showSpriteNotFound(pathInPackage);
                return null;
            }
            return FileLoading.loadSprite(spriteFile);
        }
        Errors.showSpriteNotFound(pathInPackage);
        return null;
    }

    public static String getFrameSpritePath(String basePath, int frameIndex) {
        if (basePath == null || basePath.isEmpty()) {
            return null;
        }
        int dotIndex = basePath.lastIndexOf('.');
        if (dotIndex <= 0) {
            return null;
        }
        String baseWithoutExt = basePath.substring(0, dotIndex);
        String ext = basePath.substring(dotIndex);

        if (baseWithoutExt.endsWith("00")) {
            return baseWithoutExt.substring(0, baseWithoutExt.length() - 2)
                    + String.format(java.util.Locale.ROOT, "%02d", frameIndex) + ext;
        }
        if (baseWithoutExt.endsWith("0")) {
            return baseWithoutExt.substring(0, baseWithoutExt.length() - 1)
                    + String.format(java.util.Locale.ROOT, "%d", frameIndex) + ext;
        }
        return null;
    }

    public static String translateIntegerValue(Supplier<Integer> getter) {
        String notInitialized = StringManager.getString("NOT_INITIALIZED");
        int value = getter.get();
        String textResult;
        if (value == -1) {
            textResult = notInitialized;
        } else {
            textResult = String.valueOf(value);
        }
        return textResult;
    }

    public static String computeRelativePathFromPackage(Path fullPath) {
        Path coreDataFolder = SettingsManager.getCoreFolderPath();
        List<Path> otherModFolders = SettingsManager.getAllModFolders();
        String relativePathFromCore = Utility.findRelativePath(coreDataFolder, fullPath);

        if (relativePathFromCore != null) {
            return relativePathFromCore;
        }

        for (Path modFolder : otherModFolders) {
            String relativePathFromMod = Utility.findRelativePath(modFolder, fullPath);
            if (relativePathFromMod != null) {
                return relativePathFromMod;
            }
        }

        return fullPath.toString();
    }

    private static String findRelativePath(Path baseFolder, Path fullPath) {
        if (fullPath.startsWith(baseFolder)) {
            Path relativePath = baseFolder.relativize(fullPath).normalize();
            if (!relativePath.isAbsolute()) {
                return relativePath.toString().replace("\\", "/");
            }
        }
        return null;
    }

    private static final ThreadLocal<DecimalFormat> DOUBLE_FORMATTER = ThreadLocal.withInitial(() -> 
            new DecimalFormat("0.###", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US)));

    public static String formatDouble(double value) {
        if (value % 1 == 0) {
            return String.format(java.util.Locale.US, "%8d", (int) value);
        } else {
            String formattedValue = DOUBLE_FORMATTER.get().format(value);
            return String.format(java.util.Locale.US, "%8s", formattedValue);
        }
    }

    public static boolean areDoublesEqual(double first, double second) {
        return Math.abs(first - second) < 0.005;
    }

    public static String getFilenameWithoutExtension(String filename) {
        Path path = Paths.get(filename);
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) return "";
        Matcher matcher = FILE_EXTENSION.matcher(fileNamePath.toString());
        return matcher.replaceFirst("");
    }

    public static void flipPointHorizontally(WorldPoint toFlip, WorldPoint anchor) {
        Utility.flipPointHorizontally(toFlip.getPosition(), anchor.getPosition());
        if (toFlip instanceof AngledPoint angledPoint) {
            double flipped = Utility.flipAngle(angledPoint.getAngle());
            angledPoint.setAngle(flipped);
        }
    }

    private static void flipPointHorizontally(Point2D toFlip, Point2D anchor) {
        double anchorX = anchor.getX();
        double deltaX = toFlip.getX() - anchorX;
        double newX = anchorX - deltaX;
        toFlip.setLocation(newX, toFlip.getY());
    }

    public static WeaponSlotPoint getSelectedFromLayer(LayerPainter layerPainter) {
        if (layerPainter instanceof ShipPainter shipPainter) {
            if (shipPainter.isUninitialized()) return null;
            var slotPainter = shipPainter.getWeaponSlotPainter();

            WeaponSlotPoint selected = slotPainter.getSelected();
            var eligibleSlots = slotPainter.getEligibleForSelection();

            if (selected != null && eligibleSlots.contains(selected)) {
                return selected;
            }
        }
        return null;
    }

}
