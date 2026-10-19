package shipeditor.representation.weapon;

import java.awt.Color;
import java.util.Locale;
import java.util.Optional;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.components.datafiles.entities.CSVEntry;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.viewer.entities.weapon.SlotData;
import shipeditor.representation.RepresentationEnums.SizeEnum;
import shipeditor.utility.text.StringConstants;
import shipeditor.utility.themes.Themes;

public class WeaponEnums {


    @Getter public static enum AnimationType {
    NONE, MUZZLE_FLASH, GLOW, SMOKE, GLOW_AND_FLASH
}


    @Getter public static enum BarrelMode {
    LINKED, ALTERNATING, ALTERNATING_BURST, DUAL
}


    @Getter public static enum WeaponRenderHints {
    RENDER_ADDITIVE,
    RENDER_BARREL_BELOW,
    RENDER_LOADED_MISSILES,
    RENDER_LOADED_MISSILES_UNLESS_HIDDEN,
    SUSPEND_RECOIL,
    NEVER_RENDER_IN_CAMPAIGN;

    public static boolean shouldRenderMissiles(java.util.List<WeaponRenderHints> hints, WeaponMount mount) {
        if (hints == null || hints.isEmpty()) return false;
        return hints.contains(RENDER_LOADED_MISSILES) || 
               (mount != WeaponMount.HIDDEN && hints.contains(RENDER_LOADED_MISSILES_UNLESS_HIDDEN));
    }
}


    @Getter public static enum WeaponMount {

    TURRET("TURRET", "Turret"),
    HARDPOINT("HARDPOINT", "Hardpoint"),
    HIDDEN("HIDDEN", "Hidden");

    @Getter
    private final String id;

    @Getter
    private final String displayName;

    WeaponMount(String serialized, String name) {
        this.id = serialized;
        this.displayName = name;
    }

    public static WeaponMount value(String textValue) {
        if (textValue == null || textValue.isEmpty()) {
            return null;
        }
        try {
            return WeaponMount.valueOf(textValue);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}

    @Getter public static enum WeaponSize implements SizeEnum {

    SMALL("SMALL", "Small", BoxiconsRegular.DICE_1, 1),
    MEDIUM("MEDIUM", "Medium", BoxiconsRegular.DICE_2, 2),
    LARGE("LARGE", "Large", BoxiconsRegular.DICE_3, 3);

    private final String id;
    private final String displayedName;

    private final int numericSize;

    private final Ikon ikon;

    WeaponSize(String serialized, String name, Ikon ikonTemplate, int numeric) {
        this.id = serialized;
        this.displayedName = name;
        this.numericSize = numeric;
        this.ikon = ikonTemplate;
    }

    @Override
    public FontIcon getIcon() {
        return FontIcon.of(this.ikon, 19, Themes.getIconColor());
    }

    static int getSizeDifference(WeaponSize firstSize, WeaponSize secondSize) {
        if (firstSize == null || secondSize == null) {
            throw new IllegalArgumentException("Both sizes must be non-null");
        }

        return firstSize.numericSize - secondSize.numericSize;
    }

    public static WeaponSize value(String textValue) {
        if (textValue == null || textValue.isEmpty()) {
            return null;
        }
        try {
            return WeaponSize.valueOf(textValue);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}


    @Getter public static enum WeaponType {

    BALLISTIC("BALLISTIC", "Ballistic", new Color(255,215,0,255)),
    ENERGY("ENERGY", "Energy", new Color(70,200,255,255)),
    MISSILE("MISSILE", "Missile", new Color(155,255,0,255)),
    LAUNCH_BAY(StringConstants.LAUNCH_BAY, "Launch Bay", new Color(255, 255, 255, 255)),
    UNIVERSAL("UNIVERSAL", "Universal", new Color(235, 235, 235,255)),
    HYBRID("HYBRID", "Hybrid", new Color(255,165,0,255)),
    SYNERGY("SYNERGY", "Synergy", new Color(0,255,200,255)),
    COMPOSITE("COMPOSITE", "Composite", new Color(215,255,0,255)),
    BUILT_IN("BUILT_IN", "Built in", new Color(195, 195, 195, 255)),
    DECORATIVE("DECORATIVE", "Decorative", new Color(255, 0, 0,255)),
    SYSTEM("SYSTEM", "System", new Color(145, 145, 145, 255)),
    STATION_MODULE("STATION_MODULE", "Station Module", new Color(170, 0, 255,255));

    private final String id;

    private final Color color;

    private final String displayedName;

    WeaponType(String serialized, String name, Color tone) {
        this.id = serialized;
        this.displayedName = name;
        this.color = tone;
    }

    public static WeaponType value(String textValue) {
        if (textValue == null || textValue.isEmpty()) {
            return null;
        }
        try {
            return WeaponType.valueOf(textValue);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @param entry only relevant for ship and weapon entries.
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass")
    public static boolean isValidForSlot(SlotData slotPoint, CSVEntry entry) {
        if (slotPoint == null || entry == null) {
            return false;
        }
        if (entry instanceof WeaponCSVEntry weaponEntry) {
            return WeaponType.isWeaponFitting(slotPoint, weaponEntry);
        } else {
            WeaponType slotPointType = slotPoint.getWeaponType();
            return slotPointType == WeaponType.STATION_MODULE;
        }
    }

    /**
     * Yeah, this sucks, but... weapon type rules suck in the first place, sorry Alex!
     */
    public static boolean isWeaponFitting(SlotData slotPoint, WeaponCSVEntry weaponEntry) {
        return getIncompatibilityReason(slotPoint, weaponEntry).isEmpty();
    }

    /**
     * Explains why the weapon cannot be installed into the slot.
     *
     * @return empty if the weapon fits, otherwise a human-readable reason
     * (e.g. "Slot is SMALL, weapon is MEDIUM" or a type mismatch description).
     */
    public static Optional<String> getIncompatibilityReason(SlotData slotPoint, WeaponCSVEntry weaponEntry) {
        if (slotPoint == null || weaponEntry == null) {
            return Optional.of("No slot or weapon specified");
        }
        WeaponType slotType = slotPoint.getWeaponType();
        WeaponSize slotSize = slotPoint.getWeaponSize();
        WeaponType weaponType = weaponEntry.getType();
        WeaponSize weaponSize = weaponEntry.getSize();
        if (slotType == null || slotSize == null || weaponType == null || weaponSize == null) {
            return Optional.of("Slot or weapon type/size data is missing");
        }
        if (slotType != WeaponType.BUILT_IN
                && slotType != WeaponType.SYSTEM
                && slotType != WeaponType.DECORATIVE
                && weaponEntry.isBuiltIn()) {
            return Optional.of("Built-in / system weapon cannot be fitted into a normal slot");
        }
        if (!isTypeAllowed(slotType, weaponType)) {
            return Optional.of("Slot is " + slotType.getDisplayedName().toUpperCase(Locale.ROOT)
                    + ", weapon is " + weaponType.getDisplayedName().toUpperCase(Locale.ROOT)
                    + " (type mismatch)");
        }
        boolean sizeMatters = slotType != WeaponType.DECORATIVE;
        if (sizeMatters && WeaponSize.getSizeDifference(slotSize, weaponSize) < 0) {
            return Optional.of("Slot is " + slotSize.getId() + ", weapon is " + weaponSize.getId());
        }
        return Optional.empty();
    }

    @SuppressWarnings({"OverlyComplexBooleanExpression", "RedundantLabeledSwitchRuleCodeBlock", "OverlyComplexMethod"})
    private static boolean isTypeAllowed(WeaponType slotType, WeaponType weaponType) {
        boolean result;
        switch (slotType) {
            case BALLISTIC -> result = weaponType == WeaponType.BALLISTIC
                    || weaponType == WeaponType.HYBRID
                    || weaponType == WeaponType.COMPOSITE;
            case ENERGY -> result = weaponType == WeaponType.ENERGY
                    || weaponType == WeaponType.HYBRID
                    || weaponType == WeaponType.SYNERGY;
            case MISSILE -> result = weaponType == WeaponType.MISSILE
                    || weaponType == WeaponType.COMPOSITE
                    || weaponType == WeaponType.SYNERGY;
            case HYBRID -> result = weaponType == WeaponType.HYBRID
                    || weaponType == WeaponType.BALLISTIC
                    || weaponType == WeaponType.ENERGY;
            case COMPOSITE -> result = weaponType == WeaponType.COMPOSITE
                    || weaponType == WeaponType.BALLISTIC
                    || weaponType == WeaponType.MISSILE;
            case SYNERGY -> result = weaponType == WeaponType.SYNERGY
                    || weaponType == WeaponType.ENERGY
                    || weaponType == WeaponType.MISSILE;
            case UNIVERSAL -> result = weaponType == WeaponType.UNIVERSAL
                    || (weaponType != WeaponType.LAUNCH_BAY
                    && weaponType != WeaponType.BUILT_IN
                    && weaponType != WeaponType.DECORATIVE
                    && weaponType != WeaponType.SYSTEM
                    && weaponType != WeaponType.STATION_MODULE);
            case BUILT_IN -> result = weaponType != WeaponType.DECORATIVE;
            case DECORATIVE -> result = weaponType == WeaponType.DECORATIVE;
            case SYSTEM -> result = weaponType == WeaponType.SYSTEM;
            default -> result = false;
        }
        return result;
    }

}

}
