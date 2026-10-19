package shipeditor.components.datafiles.entities;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.layers.LayerEvents.ActiveLayerUpdated;
import shipeditor.components.viewer.entities.weapon.OffsetPoint;
import shipeditor.components.viewer.layers.ship.ShipPainterInitialization;
import shipeditor.components.viewer.layers.weapon.*;
import shipeditor.components.viewer.ViewerEnums.WeaponRenderOrdering;
import shipeditor.components.viewer.painters.points.weapon.ProjectilePainter;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.weapon.*;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponRenderHints;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.graphics.DrawUtilities;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.objects.Size2D;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.StringConstants;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;

@SuppressWarnings("OverlyCoupledClass")
@Getter
@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class WeaponCSVEntry implements LayerableEntry, InstallableEntry {

    private final Map<String, String> rowData;

    private final Path packageFolderPath;

    private final Path tableFilePath;

    private String weaponID;

    private WeaponSpecFile specFile;

    private boolean specLoadAttempted;

    private WeaponSprites sprites;

    private Sprite weaponImage;

    public WeaponCSVEntry(Map<String, String> row, Path folder, Path tablePath) {
        this.rowData = row;
        packageFolderPath = folder;
        this.tableFilePath = tablePath;
        weaponID = this.rowData.get("id");
    }

    @Override
    public String getID() {
        return weaponID;
    }

    public void setWeaponID(String newID) {
        this.weaponID = newID;
        this.rowData.put("id", newID);
        this.specLoadAttempted = false;
    }

    public void setSpecFile(WeaponSpecFile weaponSpecFile) {
        this.specFile = weaponSpecFile;
        this.specLoadAttempted = weaponSpecFile != null;
    }

    public WeaponSpecFile getSpecFile() {
        if (specFile == null && !specLoadAttempted) {
            specLoadAttempted = true;
            if (weaponID == null || weaponID.isBlank()) {
                return null;
            }
            Path filePath = shipeditor.persistence.database.DatabaseQueryService.getFilePathForEntity(weaponID, StringConstants.WEAPON_TYPE);
            if (filePath != null) {
                specFile = FileLoading.loadWeaponFile(filePath.toFile());
            } else {
                log.debug("No weapon spec file path found for ID: {}", weaponID);
                shipeditor.utility.DataProblemsRegistry.recordInPackage(
                        shipeditor.utility.DataProblemsRegistry.Category.MISSING_WEAPON_SPEC, weaponID,
                        packageFolderPath, "No .wpn spec file found for weapon ID");
            }
        }
        return specFile;
    }

    @Override
    public String getMultilineTooltip() {
        return getMultilineTooltip(null);
    }

    public String getMultilineTooltip(String appendHint) {
        String entryID = StringManager.getString("WEAPON_ID") + this.getWeaponID();
        WeaponType weaponType = this.getType();
        String type =  "Weapon type: " + weaponType.getDisplayedName();
        WeaponSize weaponSize = this.getSize();
        String size =  "Weapon size: " + weaponSize.getDisplayedName();
        if (appendHint != null) {
            return Utility.getWithLinebreaks(entryID, type, size, appendHint);
        }
        return Utility.getWithLinebreaks(entryID, type, size);
    }

    public WeaponType getLazyType() {
        String typeString = this.rowData.get("type");
        if (typeString != null) {
            WeaponType val = WeaponType.value(typeString);
            if (val != null) {
                return val;
            }
        }
        return WeaponType.BALLISTIC; // Fallback
    }

    public WeaponType getType() {
        WeaponSpecFile loadedSpec = getSpecFile();
        if (loadedSpec != null) {
            WeaponType mountTypeOverride = loadedSpec.getMountTypeOverride();
            if (mountTypeOverride != null) {
                return mountTypeOverride;
            } else {
                WeaponType val = loadedSpec.getType();
                if (val != null) return val;
            }
        }
        return getLazyType();
    }

    public boolean hasSystemHint() {
        if (this.rowData == null) {
            return false;
        }
        String hints = this.rowData.get("hints");
        if (hints == null || hints.isBlank()) {
            return false;
        }
        for (String token : hints.split(",")) {
            String trimmed = token.trim();
            if ("SYSTEM".equalsIgnoreCase(trimmed)
                    || "BUILT_IN".equalsIgnoreCase(trimmed)
                    || "DECORATIVE".equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    public boolean isBuiltIn() {
        WeaponType type = getType();
        if (type == WeaponType.BUILT_IN || type == WeaponType.DECORATIVE || type == WeaponType.SYSTEM) {
            return true;
        }
        return hasSystemHint();
    }

    public int getOPCost() {
        var data = this.getRowData();
        String costText = data.get("OPs");
        return Utility.parseIntegerOrDefault(costText, 0);
    }

    public int getDrawOrder() {
        WeaponSize specFileSize = getSize();
        return specFileSize.getNumericSize();
    }

    public WeaponSize getSize() {
        WeaponSpecFile loadedSpec = getSpecFile();
        if (loadedSpec != null) {
            WeaponSize val = loadedSpec.getSize();
            if (val != null) return val;
        }
        String sizeString = this.rowData.get("size");
        if (sizeString != null) {
            WeaponSize val = WeaponSize.value(sizeString);
            if (val != null) return val;
        }
        return WeaponSize.SMALL; // Fallback
    }

    public WeaponSprites getSprites() {
        if (sprites == null) {
            WeaponSprites spriteHolder = new WeaponSprites();
            WeaponSpecFile weaponSpecFile = this.getSpecFile();

            if (weaponSpecFile != null) {
                int numFrames = weaponSpecFile.getNumFrames();
                String turretSprite = weaponSpecFile.getTurretSprite();
                setSpecSpriteWithFrames(turretSprite, numFrames,
                        spriteHolder::setTurretSprite, spriteHolder::setTurretSpriteFrames);
                String turretGunSprite = weaponSpecFile.getTurretGunSprite();
                setSpecSpriteWithFrames(turretGunSprite, numFrames,
                        spriteHolder::setTurretGunSprite, spriteHolder::setTurretGunSpriteFrames);
                String turretGlowSprite = weaponSpecFile.getTurretGlowSprite();
                setSpecSpriteWithFrames(turretGlowSprite, numFrames,
                        spriteHolder::setTurretGlowSprite, spriteHolder::setTurretGlowSpriteFrames);
                String turretUnderSprite = weaponSpecFile.getTurretUnderSprite();
                setSpecSpriteWithFrames(turretUnderSprite, numFrames,
                        spriteHolder::setTurretUnderSprite, spriteHolder::setTurretUnderSpriteFrames);

                String hardpointSprite = weaponSpecFile.getHardpointSprite();
                setSpecSpriteWithFrames(hardpointSprite, numFrames,
                        spriteHolder::setHardpointSprite, spriteHolder::setHardpointSpriteFrames);
                String hardpointGunSprite = weaponSpecFile.getHardpointGunSprite();
                setSpecSpriteWithFrames(hardpointGunSprite, numFrames,
                        spriteHolder::setHardpointGunSprite, spriteHolder::setHardpointGunSpriteFrames);
                String hardpointGlowSprite = weaponSpecFile.getHardpointGlowSprite();
                setSpecSpriteWithFrames(hardpointGlowSprite, numFrames,
                        spriteHolder::setHardpointGlowSprite, spriteHolder::setHardpointGlowSpriteFrames);
                String hardpointUnderSprite = weaponSpecFile.getHardpointUnderSprite();
                setSpecSpriteWithFrames(hardpointUnderSprite, numFrames,
                        spriteHolder::setHardpointUnderSprite, spriteHolder::setHardpointUnderSpriteFrames);
            }

            sprites = spriteHolder;
        }
        return sprites;
    }

    public Sprite getWeaponImage() {
        if (weaponImage == null) {
            WeaponSprites spriteHolder = this.getSprites();

            var turretSprite = spriteHolder.getTurretSprite();
            if (turretSprite == null) return null;
            BufferedImage turretMain = turretSprite.getImage();

            var turretGunSprite = spriteHolder.getTurretGunSprite();
            BufferedImage turretGun = null;
            if (turretGunSprite != null) {
                turretGun = turretGunSprite.getImage();
            }

            BufferedImage combinedImage = new BufferedImage(turretMain.getWidth(), turretMain.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = combinedImage.createGraphics();

            boolean barrelsBelow = false;
            WeaponSpecFile loadedSpec = getSpecFile();
            var renderHints = loadedSpec != null ? loadedSpec.getRenderHints() : null;
            if (renderHints != null && renderHints.contains(StringConstants.RENDER_BARREL_BELOW)) {
                barrelsBelow = true;
            }

            if (barrelsBelow) {
                if (turretGun != null) {
                    g2d.drawImage(turretGun, 0, 0, null);
                }
                g2d.drawImage(turretMain, 0, 0, null);
            } else {
                g2d.drawImage(turretMain, 0, 0, null);
                if (turretGun != null) {
                    g2d.drawImage(turretGun, 0, 0, null);
                }
            }

            g2d.dispose();
            weaponImage = new Sprite(combinedImage, turretSprite.getPath(), turretSprite.getFilename());
        }
        return weaponImage;
    }

    @Override
    public Sprite getEntrySprite() {
        return getWeaponImage();
    }

    /**
     * @param rotation - in degrees.
     */
    public void paintEntry(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer,
                           Matrix4f projection, Matrix4f view,
                           double rotation, Point2D targetLocation, WeaponMount mount) {
        WeaponSprites weaponSprites = getSprites();
        Sprite neededSprite = weaponSprites.getMainSprite(mount);
        DrawUtilities.paintInstallableGhostGL(spriteRenderer, projection, view,
                rotation, targetLocation, neededSprite);
    }


    @Override
    public WeaponLayer loadLayerFromEntry() {
        WeaponSpecFile loadedSpec = getSpecFile();
        String turretSprite = loadedSpec != null ? loadedSpec.getTurretSprite() : null;

        Sprite sprite = null;

        if (turretSprite != null && !turretSprite.isEmpty()) {
            Path spriteFilePath = Path.of(turretSprite);
            File spriteFile = FileLoading.fetchDataFile(spriteFilePath, this.packageFolderPath);
            if (spriteFile != null) {
                sprite = FileLoading.loadSprite(spriteFile);
            }
        }

        var manager = StaticController.getLayerManager();
        if (manager == null) {
            throw new IllegalStateException("Layer manager is not found during runtime!");
        }
        WeaponLayer newLayer = manager.createWeaponLayer();
        newLayer.setSpecFile(loadedSpec);

        WeaponPainter weaponPainter = createPainterFromEntry(newLayer, loadedSpec);
        newLayer.setPainter(weaponPainter);

        manager.setActiveLayer(newLayer);
        var viewer = StaticController.getViewer();
        viewer.loadLayer(newLayer, sprite);

        EventBus.publish(new ActiveLayerUpdated(newLayer));

        return newLayer;
    }

    /**
     * @param layer can be null.
     */
    @SuppressWarnings("WeakerAccess")
    public WeaponPainter createPainterFromEntry(WeaponLayer layer, WeaponSpecFile weaponSpecFile) {
        WeaponPainter weaponPainter = new WeaponPainter(layer);
        var spriteHolder = this.getSprites();
        weaponPainter.setWeaponSprites(spriteHolder);
        weaponPainter.setWeaponID(weaponSpecFile.getId());

        if (weaponSpecFile.isRenderBelowAllWeapons()) {
            weaponPainter.setRenderOrderType(WeaponRenderOrdering.BELOW_ALL);
        } else if (weaponSpecFile.isRenderAboveAllWeapons()) {
            weaponPainter.setRenderOrderType(WeaponRenderOrdering.ABOVE_ALL);
        } else {
            weaponPainter.setRenderOrderType(WeaponRenderOrdering.NORMAL);
        }

        var hardpointOffsets = weaponSpecFile.getHardpointOffsets();
        var hardpointAngles = weaponSpecFile.getHardpointAngleOffsets();
        WeaponCSVEntry.initializeOffsets(weaponPainter, WeaponMount.HARDPOINT,
                hardpointOffsets, hardpointAngles);

        var turretOffsets = weaponSpecFile.getTurretOffsets();
        var turretAngles = weaponSpecFile.getTurretAngleOffsets();
        WeaponCSVEntry.initializeOffsets(weaponPainter, WeaponMount.TURRET,
                turretOffsets, turretAngles);

        var hiddenOffsets = weaponSpecFile.getHiddenOffsets();
        var hiddenAngles = weaponSpecFile.getHiddenAngleOffsets();
        WeaponCSVEntry.initializeOffsets(weaponPainter, WeaponMount.HIDDEN,
                hiddenOffsets, hiddenAngles);

        WeaponSpecFile loadedSpec = getSpecFile();
        var renderHints = loadedSpec != null ? loadedSpec.getRenderHints() : null;
        if (renderHints != null && !renderHints.isEmpty()) {
            List<WeaponRenderHints> hintEnums = new ArrayList<>();
            renderHints.forEach(hintText -> {
                try {
                    hintEnums.add(WeaponRenderHints.valueOf(hintText));
                } catch (IllegalArgumentException ignored) {
                    // Unknown render hint from mod data; skip silently.
                }
            });
            weaponPainter.setRenderHints(hintEnums);
        }

        ProjectileSpecFile projectileSpec = null;
        if (loadedSpec != null && loadedSpec.getProjectileSpecId() != null) {
            projectileSpec = GameDataRepository.getProjectileByID(loadedSpec.getProjectileSpecId());
        }
        if (projectileSpec != null) {
            String sprite = projectileSpec.getSprite();
            if (sprite != null && !sprite.isEmpty()) {
                Path projectileSpecSpritePath = Path.of(sprite);
                Path containingPackage = projectileSpec.getContainingPackage();
                File file = FileLoading.fetchDataFile(projectileSpecSpritePath, containingPackage);
                if (file != null) {
                    Sprite projectileSprite = FileLoading.loadSprite(file);

                    double spriteWidth = projectileSpec.getSize()[0];
                    double spriteHeight = projectileSpec.getSize()[1];
                    ProjectilePainter projectilePainter = new ProjectilePainter(projectileSprite,
                            projectileSpec.getCenter(), new Size2D(spriteWidth, spriteHeight));
                    weaponPainter.setProjectilePainter(projectilePainter);
                }
            }
        }

        Sprite loadedTurretSprite = spriteHolder.getTurretSprite();
        if (loadedTurretSprite != null) {
            weaponPainter.setSprite(loadedTurretSprite);
        }
        weaponPainter.setMount(WeaponMount.TURRET);
        return weaponPainter;
    }

    private static void initializeOffsets(WeaponPainter painter, WeaponMount mount,
                                          Point2D[] offsetPoints, double[] offsetAngles) {
        if (offsetPoints == null || offsetPoints.length == 0) return;
        int length = offsetPoints.length;
        painter.setMount(mount);
        var offsetPainter = painter.getOffsetPainter();
        for (int i = 0; i < length; i++) {
            Point2D offset = offsetPoints[i];
            Point2D rotated = ShipPainterInitialization.rotatePointByCenter(offset,
                    painter.getEntityCenter());
            OffsetPoint initialized = new OffsetPoint(rotated, painter);
            if (offsetAngles != null && offsetAngles.length > i) {
                initialized.setAngle(offsetAngles[i]);
            }

            offsetPainter.addPoint(initialized);
        }
    }

    private void setSpecSpriteFromPath(String pathInPackage, Consumer<Sprite> setter) {
        if (pathInPackage == null || pathInPackage.isEmpty()) {
            return;
        }
        Sprite sprite = Utility.loadSpriteFromPath(pathInPackage, this.packageFolderPath);
        setter.accept(sprite);
    }

    private void setSpecSpriteWithFrames(String pathInPackage, int numFrames,
                                         Consumer<Sprite> singleSetter,
                                         Consumer<List<Sprite>> framesSetter) {
        if (pathInPackage == null || pathInPackage.isEmpty()) {
            return;
        }
        Sprite baseSprite = Utility.loadSpriteFromPath(pathInPackage, this.packageFolderPath);
        if (baseSprite != null) {
            singleSetter.accept(baseSprite);
        }
        if (numFrames <= 1) {
            if (baseSprite != null) {
                List<Sprite> singleList = new ArrayList<>();
                singleList.add(baseSprite);
                framesSetter.accept(singleList);
            }
            return;
        }
        List<Sprite> frames = new ArrayList<>();
        if (baseSprite != null) {
            frames.add(baseSprite);
        }
        for (int i = 1; i < numFrames; i++) {
            String framePath = Utility.getFrameSpritePath(pathInPackage, i);
            if (framePath != null && !framePath.equals(pathInPackage)) {
                Path filePath = Path.of(framePath);
                File spriteFile = shipeditor.parsing.loading.FileSystemUtils.fetchDataFileQuiet(filePath, this.packageFolderPath);
                if (spriteFile != null && spriteFile.exists()) {
                    Sprite frameSprite = FileLoading.loadSprite(spriteFile);
                    if (frameSprite != null) {
                        frames.add(frameSprite);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        framesSetter.accept(frames);
    }

    public JPanel createPickedWeaponPanel() {
        JPanel weaponPickPanel = new JPanel();
        weaponPickPanel.setLayout(new BoxLayout(weaponPickPanel, BoxLayout.LINE_AXIS));
        weaponPickPanel.setBorder(new EmptyBorder(4, 4, 4, 4));

        WeaponSize weaponSize = this.getSize();
        JLabel sizeLabel = new JLabel(StringManager.getString("EMPTY_STRING_2") + weaponSize.getDisplayedName() + "]");
        sizeLabel.setToolTipText(weaponSize.getDisplayedName());
        weaponPickPanel.add(sizeLabel);

        WeaponType weaponType = this.getType();
        JLabel typeLabel = new JLabel(StringManager.getString("EMPTY_STRING_2") + weaponType.getDisplayedName() + "]");
        typeLabel.setForeground(weaponType.getColor());
        typeLabel.setToolTipText(weaponType.getDisplayedName());
        weaponPickPanel.add(typeLabel);

        JLabel text = new JLabel(this.toString());
        text.setBorder(new EmptyBorder(0, 4, 0, 0));
        weaponPickPanel.add(text);

        Insets insets = new Insets(1, 0, 0, 0);
        ComponentUtilities.outfitPanelWithTitle(weaponPickPanel, insets, StringManager.getString("PICKED_FOR_INSTALL"));

        return weaponPickPanel;
    }

    @Override
    public String toString() {
        String displayedName = rowData != null ? rowData.get(StringConstants.NAME) : null;
        if (displayedName == null || displayedName.isBlank()) {
            displayedName = this.getWeaponID();
        }
        return displayedName != null ? displayedName : "Unknown Weapon";
    }

}
