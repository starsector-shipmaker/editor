package shipeditor.utility;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatVuesionIJTheme;
import lombok.Getter;

public class UtilityEnums {


    @Getter public static enum RectangleCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}


    @Getter public static enum DrawMode {
    NORMAL,
    QUALITY,
    FAST
}


    @Getter public static enum IncrementType {
    UNARY,
    CHUNK
}


    @Getter public static enum Theme {

    FLAT_INTELLIJ("Flat IntelliJ", FlatIntelliJLaf::setup),

    FLAT_DARK("Flat Dark", FlatDarkLaf::setup),

    ONE_DARK("One Dark", FlatOneDarkIJTheme::setup),

    ARC_DARK("Arc Dark", FlatArcDarkIJTheme::setup),

    VUESION("Vuesion", FlatVuesionIJTheme::setup);

    private final String displayedName;

    private final Runnable setterMethod;

    Theme(String name, Runnable setter) {
        this.displayedName = name;
        this.setterMethod = setter;
    }

}


    @Getter public static enum EditCategory {
    HULL,
    VARIANT,
    SKIN,
    NONE
}

}
