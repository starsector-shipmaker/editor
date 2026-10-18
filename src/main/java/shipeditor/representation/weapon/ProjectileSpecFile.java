package shipeditor.representation.weapon;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import shipeditor.representation.UnrecognizedPropertiesHolder;
import shipeditor.parsing.deserialize.CustomDeserializers.ColorArrayRGBADeserializer;
import shipeditor.parsing.deserialize.CustomDeserializers.Point2DDeserializer;
import shipeditor.parsing.deserialize.CustomDeserializers.TextureTypeDeserializer;
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
public class ProjectileSpecFile extends UnrecognizedPropertiesHolder {

    @Setter
    @JsonIgnore
    private Path projectileSpecFilePath;

    @Setter
    @JsonIgnore
    private Path containingPackage;

    // Core identity

    @JsonProperty("id")
    private String id;

    @JsonProperty(StringConstants.SPEC_CLASS)
    private String specClass;

    @JsonProperty("missileType")
    private String missileType;

    @JsonProperty("spawnType")
    private String spawnType;

    // Sprite / visuals

    @JsonProperty(StringConstants.SPRITE)
    private String sprite;

    @JsonProperty("bulletSprite")
    private String bulletSprite;

    @JsonProperty("size")
    private int[] size;

    @JsonDeserialize(using = Point2DDeserializer.class)
    @JsonProperty(StringConstants.CENTER)
    private Point2D.Double center;

    // Geometry (for non-missile projectiles like ballistic shots)

    @JsonProperty("length")
    private Double length;

    @JsonProperty("width")
    private Double width;

    // Collision

    @JsonProperty("collisionRadius")
    private Double collisionRadius;

    @JsonProperty("collisionClass")
    private String collisionClass;

    @JsonProperty("collisionClassByFighter")
    private String collisionClassByFighter;

    // Visual effects

    @JsonDeserialize(using = ColorArrayRGBADeserializer.class)
    @JsonProperty("fringeColor")
    private Color fringeColor;

    @JsonDeserialize(using = ColorArrayRGBADeserializer.class)
    @JsonProperty("coreColor")
    private Color coreColor;

    @JsonDeserialize(using = ColorArrayRGBADeserializer.class)
    @JsonProperty("explosionColor")
    private Color explosionColor;

    @JsonDeserialize(using = ColorArrayRGBADeserializer.class)
    @JsonProperty("glowColor")
    private Color glowColor;

    @JsonProperty("explosionRadius")
    private Double explosionRadius;

    @JsonProperty("glowRadius")
    private Double glowRadius;

    @JsonProperty("hitGlowRadius")
    private Double hitGlowRadius;

    @JsonProperty("fadeTime")
    private Double fadeTime;

    @JsonProperty("flameoutTime")
    private Double flameoutTime;

    @JsonProperty("noEngineGlowTime")
    private Double noEngineGlowTime;

    @JsonProperty("armingTime")
    private Double armingTime;

    // Texture

    @JsonProperty("textureScrollSpeed")
    private Double textureScrollSpeed;

    @JsonProperty("pixelsPerTexel")
    private Double pixelsPerTexel;

    @JsonDeserialize(using = TextureTypeDeserializer.class)
    @JsonProperty("textureType")
    private List<String> textureType;

    // Behaviors / effects

    @JsonProperty("onFireEffect")
    private String onFireEffect;

    @JsonProperty("onHitEffect")
    private String onHitEffect;

    // Passthrough flags

    @JsonProperty("passThroughMissiles")
    private Boolean passThroughMissiles;

    @JsonProperty("passThroughFighters")
    private Boolean passThroughFighters;

    @JsonProperty("passThroughFightersOnlyWhenDestroyed")
    private Boolean passThroughFightersOnlyWhenDestroyed;

    // Complex nested objects stored as raw JSON for display/editing

    @JsonProperty("engineSpec")
    private JsonNode engineSpec;

    @JsonProperty("engineSlots")
    private JsonNode engineSlots;

    @JsonProperty("behaviorSpec")
    private JsonNode behaviorSpec;

    @JsonProperty("explosionSpec")
    private JsonNode explosionSpec;

}
