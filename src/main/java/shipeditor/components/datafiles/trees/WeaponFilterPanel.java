package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Getter;
import lombok.Setter;
import shipeditor.communication.EventBus;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.communication.events.files.FileEvents.WeaponTreeReloadQueued;
import shipeditor.communication.events.components.ComponentEvents.DataTreesReloadQueued;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class WeaponFilterPanel extends JPanel {

    public enum OPCostBracket {
        LOW("0-4"), MEDIUM("5-9"), HIGH("10-14"), VERY_HIGH("15+");
        private final String display;
        OPCostBracket(String d) { this.display = d; }
        public String getDisplay() { return display; }
        public boolean matches(int cost) {
            if (this == LOW) return cost <= 4;
            if (this == MEDIUM) return cost >= 5 && cost <= 9;
            if (this == HIGH) return cost >= 10 && cost <= 14;
            return cost >= 15;
        }
    }

    @Getter @Setter
    private static String currentTextFilter;

    @Getter @Setter
    private static WeaponType selectedType = null;

    @Getter @Setter
    private static WeaponSize selectedSize = null;

    @Getter @Setter
    private static OPCostBracket selectedOPCost = null;

    @Getter @Setter
    private static String selectedTech = null;

    @Getter
    private static WeaponSlotPoint lastSelectedSlot;

    private static boolean filterBySelectedSlot = false;

    private JComboBox<String> techCombo;
    private JComboBox<String> typeCombo;
    private JComboBox<String> sizeCombo;
    private JComboBox<String> opCostCombo;
    private JCheckBox filterBySlotBox;
    private boolean isUpdatingDropdown = false;

    public WeaponFilterPanel() {
        FilterPanelSupport.setupFilterPanel(this);
        this.initUI();
        
        EventBus.subscribe(this, event -> {
            if (event instanceof DataTreesReloadQueued) {
                this.updateTechDropdown();
            }
        });
    }

    private void initUI() {
        GridBagConstraints gbc = FilterPanelSupport.createFilterConstraints();
        
        gbc.gridy = 0;
        filterBySlotBox = new JCheckBox(StringManager.getString("FILTER_BY_SELECTED_SLOT"));
        filterBySlotBox.setSelected(filterBySelectedSlot);
        filterBySlotBox.addActionListener(e -> {
            filterBySelectedSlot = filterBySlotBox.isSelected();
            applyFilters();
        });
        this.add(filterBySlotBox, gbc);

        gbc.gridy = 1;
        this.add(new JLabel(StringManager.getString("TECH_MANUFACTURER")), gbc);
        
        gbc.gridy = 2;
        techCombo = FilterPanelSupport.createTechCombo(() -> isUpdatingDropdown, selected -> {
            selectedTech = selected;
            applyFilters();
        });
        this.add(techCombo, gbc);

        gbc.gridy = 3;
        this.add(new JLabel(StringManager.getString("WEAPON_TYPE")), gbc);
        
        gbc.gridy = 4;
        typeCombo = FilterPanelSupport.createOptionCombo(WeaponType.values(), WeaponType::getDisplayedName, type -> {
            selectedType = type;
            applyFilters();
        });
        this.add(typeCombo, gbc);

        gbc.gridy = 5;
        this.add(new JLabel(StringManager.getString("WEAPON_SIZE")), gbc);
        
        gbc.gridy = 6;
        sizeCombo = FilterPanelSupport.createOptionCombo(WeaponSize.values(), WeaponSize::getDisplayedName, size -> {
            selectedSize = size;
            applyFilters();
        });
        this.add(sizeCombo, gbc);

        gbc.gridy = 7;
        this.add(new JLabel(StringManager.getString("OP_COST")), gbc);
        
        gbc.gridy = 8;
        opCostCombo = FilterPanelSupport.createOptionCombo(OPCostBracket.values(), OPCostBracket::getDisplay, bracket -> {
            selectedOPCost = bracket;
            applyFilters();
        });
        this.add(opCostCombo, gbc);

        gbc.gridy = 9;
        JButton clearButton = new JButton(StringManager.getString("CLEAR_FILTERS"));
        clearButton.addActionListener(e -> {
            filterBySlotBox.setSelected(false);
            filterBySelectedSlot = false;
            techCombo.setSelectedIndex(0);
            typeCombo.setSelectedIndex(0);
            sizeCombo.setSelectedIndex(0);
            opCostCombo.setSelectedIndex(0);
            selectedTech = null;
            selectedType = null;
            selectedSize = null;
            selectedOPCost = null;
            applyFilters();
        });
        this.add(clearButton, gbc);
        
        gbc.gridy = 10;
        gbc.weighty = 1.0;
        this.add(new JPanel(), gbc);
        
        updateTechDropdown();
    }

    public static void invalidateMetadataCache() {
        // No longer needed, as in-memory GameDataRepository handles caching.
    }

    private void updateTechDropdown() {
        isUpdatingDropdown = true;
        try {
            String prevSelected = selectedTech;
            
            Set<String> techs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            
            Map<String, WeaponCSVEntry> weaponEntries = SettingsManager.getGameData().getAllWeaponEntries();
            if (weaponEntries != null) {
                for (WeaponCSVEntry entry : weaponEntries.values()) {
                    techs.add(FilterPanelSupport.resolveTech(entry.getRowData()));
                }
            }
            
            FilterPanelSupport.repopulateTechCombo(techCombo, techs, prevSelected);
        } finally {
            isUpdatingDropdown = false;
        }
    }

    private void applyFilters() {
        EventBus.publish(new WeaponTreeReloadQueued());
    }

    public static void updateSelectedSlot(WeaponSlotPoint slot) {
        lastSelectedSlot = slot;
        if (filterBySelectedSlot) {
            EventBus.publish(new WeaponTreeReloadQueued());
        }
    }

    private static boolean shouldDisplayByHandle(shipeditor.persistence.database.IndexedFile entry) {
        if (currentTextFilter == null || currentTextFilter.isEmpty()) return true;
        String currentInput = currentTextFilter.toLowerCase(Locale.ROOT);
        if (entry.getEntityName().toLowerCase(Locale.ROOT).contains(currentInput)) return true;
        if (entry.getEntityId().toLowerCase(Locale.ROOT).contains(currentInput)) return true;

        // Also search by the weapon's display name from CSV
        Map<String, WeaponCSVEntry> weaponEntries = SettingsManager.getGameData().getAllWeaponEntries();
        if (weaponEntries != null) {
            WeaponCSVEntry csvEntry = weaponEntries.get(entry.getEntityId());
            if (csvEntry != null) {
                String weaponName = csvEntry.toString();
                if (weaponName.toLowerCase(Locale.ROOT).contains(currentInput)) return true;
            }
        }
        return false;
    }

    private static boolean shouldDisplayBySlot(WeaponCSVEntry csvEntry) {
        if (!filterBySelectedSlot || lastSelectedSlot == null || csvEntry == null) return true;
        return WeaponType.isWeaponFitting(lastSelectedSlot, csvEntry);
    }

    private static boolean shouldDisplayBySize(WeaponCSVEntry csvEntry) {
        if (selectedSize == null) return true;
        if (csvEntry == null) return true;
        Map<String, String> row = csvEntry.getRowData();
        String sizeStr = row.get("size");
        if (sizeStr == null) return true;
        return WeaponSize.value(sizeStr) == selectedSize;
    }

    private static boolean shouldDisplayByType(WeaponCSVEntry csvEntry) {
        if (selectedType == null) return true;
        if (csvEntry == null) return true;
        Map<String, String> row = csvEntry.getRowData();
        String typeStr = row.get("type");
        if (typeStr == null) return true;
        return WeaponType.value(typeStr) == selectedType;
    }

    private static boolean shouldDisplayByTech(WeaponCSVEntry csvEntry) {
        if (selectedTech == null) return true;
        if (csvEntry == null) return true;
        return FilterPanelSupport.resolveTech(csvEntry.getRowData()).equalsIgnoreCase(selectedTech);
    }

    private static boolean shouldDisplayByOPCost(WeaponCSVEntry csvEntry) {
        if (selectedOPCost == null) return true;
        if (csvEntry == null) return true;
        return selectedOPCost.matches(csvEntry.getOPCost());
    }

    static Map<String, List<shipeditor.persistence.database.IndexedFile>> getFilteredEntries() {
        List<shipeditor.persistence.database.IndexedFile> allWeapons = shipeditor.persistence.database.DatabaseQueryService.getFilesByType(shipeditor.utility.text.StringConstants.WEAPON_TYPE);
        Map<String, WeaponCSVEntry> weaponEntries = SettingsManager.getGameData().getAllWeaponEntries();

        Map<String, List<shipeditor.persistence.database.IndexedFile>> filteredResult = new LinkedHashMap<>();
        for (shipeditor.persistence.database.IndexedFile entry : allWeapons) {
            WeaponCSVEntry csvEntry = weaponEntries != null ? weaponEntries.get(entry.getEntityId()) : null;
            if (shouldDisplayByHandle(entry) && 
                shouldDisplayByType(csvEntry) && 
                shouldDisplayBySize(csvEntry) && 
                shouldDisplayByTech(csvEntry) && 
                shouldDisplayByOPCost(csvEntry) && 
                shouldDisplayBySlot(csvEntry)) {
                
                filteredResult.computeIfAbsent(entry.getModId(), k -> new ArrayList<>()).add(entry);
            }
        }
        return filteredResult;
    }
}
