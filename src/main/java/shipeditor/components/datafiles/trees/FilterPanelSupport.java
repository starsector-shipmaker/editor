package shipeditor.components.datafiles.trees;

import lombok.extern.log4j.Log4j2;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Widgets and tech/manufacturer helpers shared by the sidebar filter panels
 * ({@link ShipFilterPanel} and {@link WeaponFilterPanel}).
 */
@Log4j2
final class FilterPanelSupport {

    static final String ANY = "Any";
    static final String UNKNOWN_TECH = "Unknown";
    private static final String TECH_COLUMN = "tech/manufacturer";

    private FilterPanelSupport() {
    }

    /**
     * Gives the panel the grid layout and padding used by the sidebar filter panels.
     */
    static void setupFilterPanel(JPanel panel) {
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    static GridBagConstraints createFilterConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        return gbc;
    }

    /**
     * Reads the tech/manufacturer of a CSV row, defaulting to {@link #UNKNOWN_TECH} when missing or blank.
     */
    static String resolveTech(Map<String, String> row) {
        String tech = row != null ? row.get(TECH_COLUMN) : null;
        if (tech == null || tech.trim().isEmpty()) {
            return UNKNOWN_TECH;
        }
        return tech;
    }

    /**
     * Creates an (initially empty) tech combo; selecting "Any" reports null to the callback.
     * Selection events are ignored while {@code isUpdating} is true.
     */
    static JComboBox<String> createTechCombo(BooleanSupplier isUpdating, Consumer<String> onSelected) {
        JComboBox<String> techCombo = new JComboBox<>();
        techCombo.addActionListener(e -> {
            if (isUpdating.getAsBoolean()) return;
            String selected = (String) techCombo.getSelectedItem();
            if (ANY.equals(selected)) onSelected.accept(null);
            else onSelected.accept(selected);
        });
        return techCombo;
    }

    /**
     * Rebuilds the tech combo items ("Any" plus the given techs) and restores the previous selection if still present.
     */
    static void repopulateTechCombo(JComboBox<String> techCombo, Set<String> techs, String prevSelected) {
        log.debug("Repopulating tech combo with {} techs (previous selection: {})", techs.size(), prevSelected);
        techCombo.removeAllItems();
        techCombo.addItem(ANY);
        for (String t : techs) {
            techCombo.addItem(t);
        }
        if (prevSelected != null && techs.contains(prevSelected)) {
            techCombo.setSelectedItem(prevSelected);
        } else {
            techCombo.setSelectedIndex(0);
        }
    }

    /**
     * Creates a combo with an "Any" entry followed by one entry per value; selecting "Any" reports null.
     */
    static <E> JComboBox<String> createOptionCombo(E[] values, Function<E, String> displayName,
                                                   Consumer<E> onSelected) {
        JComboBox<String> combo = new JComboBox<>();
        combo.addItem(ANY);
        for (E value : values) {
            combo.addItem(displayName.apply(value));
        }
        combo.addActionListener(e -> {
            int idx = combo.getSelectedIndex();
            if (idx <= 0) onSelected.accept(null);
            else onSelected.accept(values[idx - 1]);
        });
        return combo;
    }
}
