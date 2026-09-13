package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import shipeditor.communication.EventBus;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.communication.events.files.FileEvents.HullTreeReloadQueued;
import shipeditor.communication.events.components.ComponentEvents.DataTreesReloadQueued;
import shipeditor.persistence.database.IndexedFile;
import shipeditor.persistence.database.DatabaseQueryService;

public class ShipFilterPanel extends JPanel {
    @Getter @Setter
    private static String currentTextFilter;
    
    @Getter @Setter
    private static HullSize selectedSize = null;
    
    @Getter @Setter
    private static String selectedTech = null;

    private JComboBox<String> techCombo;
    private JComboBox<String> sizeCombo;

    public ShipFilterPanel() {
        this.setLayout(new GridBagLayout());
        this.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.initUI();
        
        EventBus.subscribe(this, event -> {
            if (event instanceof DataTreesReloadQueued) {
                this.updateTechDropdown();
            }
        });
    }

    private void initUI() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        
        gbc.gridy = 0;
        this.add(new JLabel(StringManager.getString("TECH_MANUFACTURER")), gbc);
        
        gbc.gridy = 1;
        techCombo = new JComboBox<>();
        techCombo.addActionListener(e -> {
            if (isUpdatingDropdown) return;
            String selected = (String) techCombo.getSelectedItem();
            if ("Any".equals(selected)) selectedTech = null;
            else selectedTech = selected;
            applyFilters();
        });
        this.add(techCombo, gbc);
        
        gbc.gridy = 2;
        this.add(new JLabel(StringManager.getString("HULL_SIZE")), gbc);
        
        gbc.gridy = 3;
        sizeCombo = new JComboBox<>();
        sizeCombo.addItem("Any");
        for (HullSize size : HullSize.values()) {
            sizeCombo.addItem(size.getDisplayedName());
        }
        sizeCombo.addActionListener(e -> {
            int idx = sizeCombo.getSelectedIndex();
            if (idx <= 0) selectedSize = null;
            else selectedSize = HullSize.values()[idx - 1];
            applyFilters();
        });
        this.add(sizeCombo, gbc);

        gbc.gridy = 4;
        gbc.weighty = 1.0;
        this.add(new JPanel(), gbc); // spacer
        
        updateTechDropdown();
    }
    
    private boolean isUpdatingDropdown = false;

    private void updateTechDropdown() {
        isUpdatingDropdown = true;
        try {
            String prevSelected = selectedTech;
            techCombo.removeAllItems();
            techCombo.addItem("Any");
            
            Set<String> techs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            
            Map<String, ShipCSVEntry> shipEntries = SettingsManager.getGameData().getAllShipEntries();
            if (shipEntries != null) {
                for (ShipCSVEntry entry : shipEntries.values()) {
                    Map<String, String> row = entry.getRowData();
                    String tech = row.get("tech/manufacturer");
                    if (tech == null || tech.trim().isEmpty()) tech = "Unknown";
                    techs.add(tech);
                }
            }
            
            for (String t : techs) {
                techCombo.addItem(t);
            }
            
            if (prevSelected != null && techs.contains(prevSelected)) {
                techCombo.setSelectedItem(prevSelected);
            } else {
                techCombo.setSelectedIndex(0);
            }
        } finally {
            isUpdatingDropdown = false;
        }
    }
    
    private void applyFilters() {
        EventBus.publish(new HullTreeReloadQueued());
    }

    static Map<String, List<IndexedFile>> getFilteredEntries() {
        List<IndexedFile> allShips = DatabaseQueryService.getFilesByType(shipeditor.utility.text.StringConstants.SHIP_TYPE);
        Map<String, ShipCSVEntry> shipEntries = SettingsManager.getGameData().getAllShipEntries();
        
        Map<String, List<IndexedFile>> filteredResult = new LinkedHashMap<>();
        for (IndexedFile entry : allShips) {
            if (shouldDisplayByHandle(entry, shipEntries) && shouldDisplayByType(entry, shipEntries) && shouldDisplayBySize(entry, shipEntries)) {
                filteredResult.computeIfAbsent(entry.getModId(), k -> new ArrayList<>()).add(entry);
            }
        }
        return filteredResult;
    }

    private static boolean shouldDisplayByHandle(IndexedFile entry, Map<String, ShipCSVEntry> shipEntries) {
        if (currentTextFilter == null || currentTextFilter.isEmpty()) return true;
        String currentInput = currentTextFilter.toLowerCase(Locale.ROOT);
        
        if (entry.getEntityName().toLowerCase(Locale.ROOT).contains(currentInput)) return true;
        if (entry.getEntityId().toLowerCase(Locale.ROOT).contains(currentInput)) return true;
        if (entry.getDesignation() != null && entry.getDesignation().toLowerCase(Locale.ROOT).contains(currentInput)) return true;

        // Also search by the ship's display name from CSV
        if (shipEntries != null) {
            ShipCSVEntry csvEntry = shipEntries.get(entry.getEntityId());
            if (csvEntry != null) {
                String shipName = csvEntry.getShipName();
                if (shipName != null && shipName.toLowerCase(Locale.ROOT).contains(currentInput)) return true;
            }
        }
        
        return false;
    }

    private static boolean shouldDisplayBySize(IndexedFile entry, Map<String, ShipCSVEntry> shipEntries) {
        if (selectedSize == null) return true;
        if (shipEntries == null) return true;

        ShipCSVEntry csvEntry = shipEntries.get(entry.getEntityId());
        if (csvEntry == null) {
            var gameData = SettingsManager.getGameData();
            if (gameData != null) {
                csvEntry = gameData.getOrCreateShipEntry(entry);
            }
        }
        if (csvEntry != null) {
            HullSize size = csvEntry.getSize();
            if (size != null && size != HullSize.DEFAULT) {
                return size == selectedSize;
            }
            Map<String, String> row = csvEntry.getRowData();
            if (row != null) {
                String sizeStr = row.get("hull size");
                if (sizeStr != null) {
                    for (HullSize hs : HullSize.values()) {
                        if (hs.name().equalsIgnoreCase(sizeStr) || hs.getDisplayedName().equalsIgnoreCase(sizeStr)) {
                            return hs == selectedSize;
                        }
                    }
                }
            }
        }
        // No metadata found — exclude if a specific size is selected
        return selectedSize == HullSize.DEFAULT;
    }

    private static boolean shouldDisplayByType(IndexedFile entry, Map<String, ShipCSVEntry> shipEntries) {
        if (selectedTech == null || selectedTech.isEmpty() || "Any".equals(selectedTech)) return true;
        if (shipEntries == null) return "Unknown".equalsIgnoreCase(selectedTech);

        ShipCSVEntry csvEntry = shipEntries.get(entry.getEntityId());
        if (csvEntry != null) {
            Map<String, String> row = csvEntry.getRowData();
            String tech = row.get("tech/manufacturer");
            if (tech == null || tech.trim().isEmpty()) tech = "Unknown";
            return selectedTech.equalsIgnoreCase(tech);
        }
        return "Unknown".equalsIgnoreCase(selectedTech);
    }
}
