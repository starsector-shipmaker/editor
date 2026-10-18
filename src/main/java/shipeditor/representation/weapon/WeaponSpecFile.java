package shipeditor.representation.weapon;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;


import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;
import shipeditor.representation.UnrecognizedPropertiesHolder;
import shipeditor.parsing.deserialize.CustomDeserializers.ColorArrayRGBADeserializer;
import shipeditor.parsing.deserialize.CustomDeserializers.Point2DArrayDeserializer;
import shipeditor.parsing.deserialize.CustomDeserializers.TextureTypeDeserializer;
import shipeditor.parsing.serialize.CustomSerializers.ColorArrayRGBASerializer;
import shipeditor.parsing.serialize.points.Point2DArraySerializer;
import shipeditor.representation.weapon.WeaponEnums.AnimationType;
import shipeditor.representation.weapon.WeaponEnums.BarrelMode;
import shipeditor.representation.weapon.animation.MuzzleFlashSpec;
import shipeditor.representation.weapon.animation.SmokeSpec;
import shipeditor.utility.text.StringConstants;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("ClassWithTooManyFields")
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class WeaponSpecFile extends UnrecognizedPropertiesHolder {

    @Setter
    @JsonIgnore
    private Path tableFilePath;

    @Setter
    @JsonIgnore
    private Path weaponSpecFilePath;

    @Setter
    @JsonIgnore
    private Path containingPackage;

    @JsonProperty(StringConstants.SPEC_CLASS)
    private String specClass;

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private WeaponType type;

    @JsonProperty("mountTypeOverride")
    private WeaponType mountTypeOverride;

    @JsonProperty("size")
    private WeaponSize size;

    @JsonProperty("collisionClass")
    private String collisionClass;

    @JsonProperty("collisionClassByFighter")
    private String collisionClassByFighter;

    /**
     * Path to script, e.g "com.fs.starfarer.api.impl.combat.BlinkerEffect".
     */
    @JsonProperty("everyFrameEffect")
    private String everyFrameEffect;

    /**
     * Path to script as well, say, "com.fs.starfarer.api.impl.combat.GravitonBeamEffect".
     */
    @JsonProperty("beamEffect")
    private String beamEffect;

    @JsonProperty("beamFireOnlyOnFullCharge")
    private boolean beamFireOnlyOnFullCharge;

    @JsonProperty("showDamageWhenDecorative")
    private boolean showDamageWhenDecorative;

    @JsonProperty("renderBelowAllWeapons")
    private boolean renderBelowAllWeapons;

    @JsonProperty("renderAboveAllWeapons")
    private boolean renderAboveAllWeapons;

    @JsonProperty("renderAdditive")
    private boolean renderAdditive;

    @JsonProperty("passThroughMissiles")
    private boolean passThroughMissiles;

    @JsonProperty("displayArcRadius")
    private double displayArcRadius;

    @JsonProperty("fringeScrollSpeedMult")
    private double fringeScrollSpeedMult;

    @JsonProperty("turretSprite")
    private String turretSprite;

    @JsonProperty("turretUnderSprite")
    private String turretUnderSprite;

    @JsonProperty("turretGunSprite")
    private String turretGunSprite;

    @JsonProperty("turretGlowSprite")
    private String turretGlowSprite;

    @JsonProperty("hardpointSprite")
    private String hardpointSprite;

    @JsonProperty("hardpointUnderSprite")
    private String hardpointUnderSprite;

    @JsonProperty("hardpointGunSprite")
    private String hardpointGunSprite;

    @JsonProperty("hardpointGlowSprite")
    private String hardpointGlowSprite;

    @JsonProperty("visualRecoil")
    private double visualRecoil;

    @JsonProperty("separateRecoilForLinkedBarrels")
    private boolean separateRecoilForLinkedBarrels;

    @JsonProperty("turretOffsets")
    @JsonDeserialize(using = Point2DArrayDeserializer.class)
    @JsonSerialize(using = Point2DArraySerializer.class)
    private Point2D.Double[] turretOffsets;

    @JsonProperty("turretAngleOffsets")
    private double[] turretAngleOffsets;

    @JsonProperty("hardpointOffsets")
    @JsonDeserialize(using = Point2DArrayDeserializer.class)
    @JsonSerialize(using = Point2DArraySerializer.class)
    private Point2D.Double[] hardpointOffsets;

    @JsonProperty("hardpointAngleOffsets")
    private double[] hardpointAngleOffsets;

    @JsonProperty("hiddenOffsets")
    @JsonDeserialize(using = Point2DArrayDeserializer.class)
    @JsonSerialize(using = Point2DArraySerializer.class)
    private Point2D.Double[] hiddenOffsets;

    @JsonProperty("hiddenAngleOffsets")
    private double[] hiddenAngleOffsets;

    @JsonProperty("renderHints")
    private List<String> renderHints;

    @JsonProperty("numFrames")
    private int numFrames;

    @JsonAlias("framerate")
    @JsonProperty("frameRate")
    private int frameRate;

    @JsonProperty("alwaysAnimate")
    private boolean alwaysAnimate;

    @JsonDeserialize(using = ColorArrayRGBADeserializer.class)
    @JsonSerialize(using = ColorArrayRGBASerializer.class)
    @JsonProperty("fringeColor")
    private Color fringeColor;

    @JsonDeserialize(using = ColorArrayRGBADeserializer.class)
    @JsonSerialize(using = ColorArrayRGBASerializer.class)
    @JsonProperty("coreColor")
    private Color coreColor;

    @JsonDeserialize(using = ColorArrayRGBADeserializer.class)
    @JsonSerialize(using = ColorArrayRGBASerializer.class)
    @JsonProperty("glowColor")
    private Color glowColor;

    @JsonProperty("useGlowColorForHitGlow")
    private boolean useGlowColorForHitGlow;

    @JsonProperty("darkCore")
    private boolean darkCore;

    @JsonProperty("convergeOnPoint")
    private boolean convergeOnPoint;

    @JsonProperty("skipIdleFrameIfZeroBurstDelay")
    private boolean skipIdleFrameIfZeroBurstDelay;

    @JsonProperty("hitGlowBrightenDuration")
    private int hitGlowBrightenDuration;

    @JsonProperty("hitGlowRadius")
    private int hitGlowRadius;

    @JsonProperty("specialWeaponGlowWidth")
    private int specialWeaponGlowWidth;

    @JsonProperty("specialWeaponGlowHeight")
    private int specialWeaponGlowHeight;

    @JsonProperty(StringConstants.WIDTH)
    private double width;

    @JsonProperty("coreWidthMult")
    private double coreWidthMult;

    @JsonDeserialize(using = TextureTypeDeserializer.class)
    @JsonProperty("textureType")
    private List<String> textureType;

    @JsonProperty("textureScrollSpeed")
    private double textureScrollSpeed;

    @JsonProperty("pixelsPerTexel")
    private double pixelsPerTexel;

    @JsonProperty("pierceSet")
    private List<String> pierceSet;

    @JsonProperty("animationType")
    private AnimationType animationType;

    @JsonProperty("muzzleFlashSpec")
    private MuzzleFlashSpec muzzleFlashSpec;

    @JsonProperty("smokeSpec")
    private SmokeSpec smokeSpec;

    @JsonProperty("barrelMode")
    private BarrelMode barrelMode;

    @JsonProperty("unaffectedByProjectileSpeedBonuses")
    private boolean unaffectedByProjectileSpeedBonuses;

    @JsonProperty("noImpactSounds")
    private boolean noImpactSounds;

    @JsonProperty("noShieldImpactSounds")
    private boolean noShieldImpactSounds;

    @JsonProperty("noNonShieldImpactSounds")
    private boolean noNonShieldImpactSounds;

    @JsonProperty("autocharge")
    private boolean autocharge;

    @JsonProperty("interruptibleBurst")
    private boolean interruptibleBurst;

    @JsonProperty("requiresFullCharge")
    private boolean requiresFullCharge;

    @JsonProperty("projectileSpecId")
    private String projectileSpecId;

    @JsonProperty("playFullFireSoundOne")
    private boolean playFullFireSoundOne;

    @JsonProperty("stopPreviousFireSound")
    private boolean stopPreviousFireSound;

    @JsonProperty("fireSoundOne")
    private String fireSoundOne;

    @JsonProperty("fireSoundTwo")
    private String fireSoundTwo;

}
