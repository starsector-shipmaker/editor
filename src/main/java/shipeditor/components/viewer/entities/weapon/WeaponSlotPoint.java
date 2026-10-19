package shipeditor.components.viewer.entities.weapon;

import shipeditor.utility.graphics.opengl.SpriteRenderer;
import shipeditor.utility.graphics.opengl.ShapeRenderer;
import org.joml.Matrix4f;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import shipeditor.components.ComponentEnums.CoordsDisplayMode;
import shipeditor.components.datafiles.entities.CSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.entities.AngledPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.representation.RepresentationEnums.ShipTypeHints;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.graphics.ColorUtilities;
import shipeditor.utility.overseers.EventScheduler;
import shipeditor.utility.overseers.StaticController;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.List;

@SuppressWarnings("ClassWithTooManyFields")
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class WeaponSlotPoint extends AngledPoint implements SlotPoint {

    @Getter
    @Setter
    private String id;

    @Setter
    private WeaponSize weaponSize;

    @Setter
    private WeaponType weaponType;

    @Setter
    private WeaponMount weaponMount;

    @Getter
    @Setter
    private int renderOrderMod;

    @Setter
    private double arc;

    @Setter
    private double angle;

    @Getter
    @Setter
    private WeaponSlotOverride skinOverride;

    @Getter
    @Setter
    private double transparency = 1.0d;

    private Double cachedRelativeOffset;

    private SlotDrawer slotDrawer;

    public WeaponSlotPoint(Point2D pointPosition, ShipPainter layer) {
        this(pointPosition, layer, null);
    }

    public WeaponSlotPoint(Point2D pointPosition, ShipPainter layer, WeaponSlotPoint valuesSource) {
        super(pointPosition, layer);
        this.initHelper();
        if (valuesSource != null) {
            this.setWeaponSize(valuesSource.weaponSize);
            this.setWeaponType(valuesSource.weaponType);
            this.setWeaponMount(valuesSource.weaponMount);
            this.setAngle(valuesSource.angle);
            this.setArc(valuesSource.arc);
            this.setRenderOrderMod(valuesSource.renderOrderMod);
        }
    }

    @Override
    public ShipPainter getParent() {
        return (ShipPainter) super.getParent();
    }

    private void initHelper() {
        this.slotDrawer = new SlotDrawer(this);
    }

    public WeaponMount getWeaponMount() {
        if (skinOverride != null && skinOverride.getWeaponMount() != null) {
            return skinOverride.getWeaponMount();
        } else
            return weaponMount;
    }

    public WeaponMount getBaseMount() {
        return weaponMount;
    }

    public WeaponSize getWeaponSize() {
        if (skinOverride != null && skinOverride.getWeaponSize() != null) {
            return skinOverride.getWeaponSize();
        } else {
            return weaponSize;
        }
    }

    public WeaponSize getBaseSize() {
        return weaponSize;
    }

    public WeaponType getWeaponType() {
        if (skinOverride != null && skinOverride.getWeaponType() != null) {
            return skinOverride.getWeaponType();
        } else {
            return weaponType;
        }
    }

    public WeaponType getBaseType() {
        return weaponType;
    }

    public double getArc() {
        if (skinOverride != null && skinOverride.getBoxedArc() != null) {
            return skinOverride.getBoxedArc();
        } else {
            return arc;
        }
    }

    @Override
    public double getAngle() {
        if (skinOverride != null && skinOverride.getBoxedAngle() != null) {
            return skinOverride.getAngle();
        } else {
            return angle;
        }
    }

    public void changeSlotID(String newId) {
        ShipPainter parent = this.getParent();
        if (!parent.isGeneratedIDUnassigned(newId)) {
            shipeditor.utility.components.dialog.DialogHelper.showDuplicateIDError();
            EventScheduler repainter = StaticController.getScheduler();
            repainter.queueViewerRepaint();
            repainter.queueSlotsPanelRepaint();
            repainter.queueBuiltInsRepaint();
            return;
        }

        this.setId(newId);
        WeaponSlotPainter.setSlotOverrideFromSkin(this, parent.getActiveSkin());
        EventScheduler repainter = StaticController.getScheduler();
        repainter.queueViewerRepaint();
        repainter.queueSlotsPanelRepaint();
        repainter.queueBuiltInsRepaint();
    }

    public void changeSlotType(WeaponType newType) {
        if (skinOverride != null && skinOverride.getWeaponType() != null)
            return;
        EditDispatch.postSlotTypeChanged(this, newType);
    }

    public void changeSlotMount(WeaponMount newMount) {
        if (skinOverride != null && skinOverride.getWeaponMount() != null)
            return;
        EditDispatch.postSlotMountChanged(this, newMount);
    }

    public void changeSlotSize(WeaponSize newSize) {
        if (skinOverride != null && skinOverride.getWeaponSize() != null)
            return;
        EditDispatch.postSlotSizeChanged(this, newSize);
    }

    public void changeSlotAngle(double degrees) {
        EditDispatch.postSlotAngleSet(this, this.angle, degrees);
    }

    public void changeSlotArc(double degrees) {
        EditDispatch.postSlotArcSet(this, this.arc, degrees);
    }

    public void changeRenderOrder(int renderOrder) {
        EditDispatch.postRenderOrderChanged(this, this.renderOrderMod, renderOrder);
    }

    @Override
    public EditorInstrument getAssociatedMode() {
        return EditorInstrument.WEAPON_SLOTS;
    }

    @Override
    protected Color createBaseColor() {
        WeaponType type = this.getWeaponType();
        return type.getColor();
    }

    @Override
    protected Color createSelectColor() {
        Color base = this.createBaseColor();
        return ColorUtilities.getBlendedColor(base, Color.WHITE, 0.5);
    }

    @Override
    protected boolean isInteractable() {
        ShipPainter layer = getParent();
        if (layer == null) {
            return true;
        }
        EditorInstrument mode = StaticController.getEditorMode();
        boolean validMode = mode == EditorInstrument.WEAPON_SLOTS ||
                            mode == EditorInstrument.VARIANT_WEAPONS ||
                            mode == EditorInstrument.VARIANT_MODULES ||
                            mode == EditorInstrument.SKIN_SLOTS;
        return validMode && layer.isLayerActive();
    }

    public InstalledFeature getInstalledBuiltIn() {
        ShipPainter shipPainter = this.getParent();
        if (shipPainter == null || this.id == null) return null;
        var activeSkin = shipPainter.getActiveSkin();
        if (activeSkin != null && !activeSkin.isBase()) {
            var skinBuiltIns = activeSkin.getInitializedBuiltIns();
            if (skinBuiltIns != null && skinBuiltIns.containsKey(this.id)) {
                return skinBuiltIns.get(this.id);
            }
        }
        var baseBuiltIns = shipPainter.getBuiltInWeapons();
        if (baseBuiltIns != null) {
            return baseBuiltIns.get(this.id);
        }
        return null;
    }

    public boolean hasBuiltInWeapon() {
        return getInstalledBuiltIn() != null;
    }

    public String getBuiltInWeaponName() {
        InstalledFeature feature = getInstalledBuiltIn();
        if (feature != null) {
            if (feature.getName() != null && !feature.getName().isBlank()) {
                return feature.getName();
            }
            return feature.getID();
        }
        return null;
    }

    public String getNameForLabel() {
        WeaponType type = getWeaponType();
        return type.getDisplayedName();
    }

    @Override
    protected String[] getHoverLines() {
        Point2D toDisplay = this.getCoordinatesForDisplay();
        String idLine = this.id != null ? this.id : "No ID";
        String typeMountSize = getWeaponType().getDisplayedName() + " / "
                + getWeaponMount().getDisplayName() + " / "
                + getWeaponSize().getDisplayedName();
        String angleArc = "Angle: " + Utility.round(getAngle(), 1) + "\u00B0"
                + "  Arc: " + Utility.round(getArc(), 1) + "\u00B0";
        String coords = "(" + toDisplay.getX() + ", " + toDisplay.getY() + ")";
        String builtIn = getBuiltInWeaponName();
        if (builtIn != null) {
            return new String[] { idLine + " [Built-in: " + builtIn + "]", typeMountSize, angleArc, coords };
        }
        return new String[] { idLine, typeMountSize, angleArc, coords };
    }

    @Override
    public void paint(SpriteRenderer spriteRenderer, ShapeRenderer shapeRenderer, Matrix4f projection, Matrix4f view) {
        slotDrawer.setPointPosition(this.getPosition());
        slotDrawer.setType(this.getWeaponType());
        slotDrawer.setMount(this.getWeaponMount());
        slotDrawer.setSize(this.getWeaponSize());
        slotDrawer.setAngle(this.getAngle());
        slotDrawer.setArc(this.getArc());
        slotDrawer.setPaintSizeMultiplier(this.getPaintSizeMultiplier());

        slotDrawer.paintSlotVisuals(spriteRenderer, shapeRenderer, projection, view);
    }

    public boolean isDecorative() {
        return this.getWeaponType() == WeaponType.DECORATIVE;
    }

    public boolean isBuiltIn() {
        return this.getWeaponType() == WeaponType.BUILT_IN;
    }

    public boolean isModule() {
        return this.getWeaponType() == WeaponType.STATION_MODULE;
    }

    public boolean isFittable() {
        if (hasBuiltInWeapon()) {
            return false;
        }
        WeaponType type = this.getWeaponType();
        switch (type) {
            case BALLISTIC, ENERGY, MISSILE, COMPOSITE, HYBRID, SYNERGY, UNIVERSAL -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean canFit(CSVEntry dataEntry) {
        return WeaponType.isValidForSlot(this, dataEntry);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean canFit(InstalledFeature feature) {
        CSVEntry dataEntry = feature.getDataEntry();
        if (dataEntry == null)
            return false;
        return canFit(dataEntry);
    }

    @Override
    public void setPosition(double x, double y) {
        super.setPosition(x, y);
        cachedRelativeOffset = null;
    }

    public double getOffsetRelativeToAxis() {
        if (cachedRelativeOffset != null) {
            return cachedRelativeOffset;
        }
        double result;

        ShipPainter shipPainter = this.getParent();
        List<ShipTypeHints> hints = shipPainter.getHintsModified();

        Point2D locationRelativeToCenter = Utility.getPointCoordinatesForDisplay(this.getPosition(),
                shipPainter, CoordsDisplayMode.SHIP_CENTER);

        if (hints == null) {
            return Math.abs(locationRelativeToCenter.getY() / 10000)
                    + Math.abs(locationRelativeToCenter.getX() / 10000000);
        }

        if (hints.contains(ShipTypeHints.WEAPONS_FRONT_TO_BACK)) {
            result = Math.abs(locationRelativeToCenter.getY() / 50000)
                    + (locationRelativeToCenter.getX() / 10000);
        } else if (hints.contains(ShipTypeHints.WEAPONS_BACK_TO_FRONT)) {
            result = Math.abs(locationRelativeToCenter.getY() / 50000)
                    - (locationRelativeToCenter.getX() / 10000);
        } else {
            result = Math.abs(locationRelativeToCenter.getY() / 10000)
                    + Math.abs(locationRelativeToCenter.getX() / 10000000);
        }

        cachedRelativeOffset = result;
        return result;
    }

    @Override
    public String toString() {
        return "WeaponSlotPoint #" + this.hashCode() + " (ID: " + this.getId() + ")";
    }

}
