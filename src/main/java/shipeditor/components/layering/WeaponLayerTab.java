package shipeditor.components.layering;

import shipeditor.utility.text.StringManager;

import lombok.Getter;
import lombok.Setter;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.utility.Utility;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter @Setter
public class WeaponLayerTab extends LayerTab {

    private String spriteName;

    private String underSpriteName;

    private String gunSpriteName;

    private String glowSpriteName;

    private String specFileName;

    WeaponLayerTab(WeaponLayer layer) {
        super(layer);
    }

    @Override
    public String getTabTooltip() {
        List<String> result = new ArrayList<>();
        result.add("Main sprite file: " + orNotLoaded(spriteName));
        result.add("Gun sprite file: " + orNotLoaded(gunSpriteName));
        result.add("Under sprite file: " + orNotLoaded(underSpriteName));
        result.add("Glow sprite file: " + orNotLoaded(glowSpriteName));
        result.add("Weapon spec file: " + orNotLoaded(specFileName));

        return Utility.getWithLinebreaks(result.toArray(new String[0]));
    }

    private static String orNotLoaded(String fileName) {
        if (Objects.equals(fileName, "") || fileName == null) {
            return StringManager.getString("NOT_LOADED");
        }
        return fileName;
    }

}
