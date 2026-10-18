package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.HullSpecFile;
import shipeditor.representation.ship.SkinSpecFile;
import shipeditor.representation.ship.VariantFile;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.MouseoverLabelListener;
import shipeditor.utility.graphics.Sprite;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

class ShipFilesSubpanel extends JPanel {

    ShipFilesSubpanel(JPanel parentPanel) {
    }

    JPanel createShipFilesPanel(ShipCSVEntry selected, HullsTreePanel parent) {
        JPanel shipFilesPanel = this;
        shipFilesPanel.setLayout(new BoxLayout(shipFilesPanel, BoxLayout.PAGE_AXIS));

        JPanel infoPanel = ShipFilesSubpanel.createInfoPanel(selected);
        shipFilesPanel.add(infoPanel);
        shipFilesPanel.add(Box.createVerticalStrut(4));

        JPanel filesPanel = ShipFilesSubpanel.createFilesPanel(selected, parent);
        shipFilesPanel.add(filesPanel);
        shipFilesPanel.add(Box.createVerticalStrut(4));

        ShipFilesSubpanel.addHullmodPanel(shipFilesPanel, selected);

        JPanel variantsPanel = ShipFilesSubpanel.createVariantPanel(selected.getShipID());
        if (variantsPanel != null) {
            shipFilesPanel.add(Box.createVerticalStrut(4));
            shipFilesPanel.add(variantsPanel);
        }

        return shipFilesPanel;
    }

    private static JPanel createInfoPanel(ShipCSVEntry selected) {
        JPanel infoPanel = new JPanel(new java.awt.GridBagLayout());
        infoPanel.setAlignmentX(LEFT_ALIGNMENT);
        infoPanel.setBorder(new EmptyBorder(0, 4, 0, 0));

        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 4, 0);

        Insets labelInsets = new Insets(2, 4, 2, 4);

        javax.swing.JTextArea shipNameLabel = ComponentUtilities.createWrappingLabel("Ship name: " + selected.getShipName());
        shipNameLabel.setBorder(new EmptyBorder(labelInsets));
        infoPanel.add(shipNameLabel, gbc);
        gbc.gridy++;

        javax.swing.JTextArea shipIDLabel = ComponentUtilities.createWrappingLabel("Ship ID: " + selected.getShipID());
        shipIDLabel.setBorder(new EmptyBorder(labelInsets));
        infoPanel.add(shipIDLabel, gbc);
        gbc.gridy++;

        String designation = selected.getShipDesignation();
        if (designation != null && !designation.isBlank()) {
            javax.swing.JTextArea designationLabel = ComponentUtilities.createWrappingLabel("Designation: " + designation);
            designationLabel.setBorder(new EmptyBorder(labelInsets));
            infoPanel.add(designationLabel, gbc);
            gbc.gridy++;
        }

        shipeditor.representation.RepresentationEnums.HullSize hullSize = selected.getSize();
        if (hullSize != null) {
            javax.swing.JTextArea hullSizeLabel = ComponentUtilities.createWrappingLabel("Hull size: " + hullSize.getDisplayedName());
            hullSizeLabel.setBorder(new EmptyBorder(labelInsets));
            infoPanel.add(hullSizeLabel, gbc);
            gbc.gridy++;
        }

        return infoPanel;
    }

    private static JPanel createFilesPanel(ShipCSVEntry selected, HullsTreePanel parent) {
        JPanel filesPanel = new JPanel(new java.awt.GridBagLayout());
        filesPanel.setAlignmentX(LEFT_ALIGNMENT);
        ComponentUtilities.outfitPanelWithTitle(filesPanel, new Insets(1, 0, 0, 0), StringManager.getString("FILES"));

        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0; gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(2, 4, 4, 4);

        Sprite sprite = selected.getEntrySprite();
        if (sprite != null) {
            String tooltip = Utility.getTooltipForSprite(sprite);
            JLabel spriteIcon = ComponentUtilities.createIconFromImage(sprite.getImage(), tooltip, 128);
            JPanel spriteWrapper = ComponentUtilities.createCenteredPanel(spriteIcon);
            filesPanel.add(spriteWrapper, gbc);
            gbc.gridy++;
        }

        Insets labelInsets = new Insets(2, 4, 2, 4);
        Border labelSimpleBorder = ComponentUtilities.createLabelSimpleBorder(labelInsets);

        String hullFileName = selected.getHullFileName();
        HullSpecFile selectedHullFileSpecFile = selected.getHullSpecFile();
        Path shipFilePath = selectedHullFileSpecFile != null ? selectedHullFileSpecFile.getFilePath() : null;
        String shipFilePathName = shipFilePath != null ? shipFilePath.toString() : "Not found";

        javax.swing.JTextArea hullFileNameLabel = ComponentUtilities.createWrappingLabel("Hull file: " + hullFileName);
        hullFileNameLabel.setToolTipText(shipFilePathName);
        hullFileNameLabel.setBorder(labelSimpleBorder);
        if (shipFilePath != null) {
            JPopupMenu hullContextMenu = ComponentUtilities.createPathContextMenu(shipFilePath);
            hullFileNameLabel.addMouseListener(new MouseoverLabelListener(hullContextMenu, hullFileNameLabel));
        }
        filesPanel.add(hullFileNameLabel, gbc);
        gbc.gridy++;

        String spriteFileName = selected.getShipSpriteName();
        File spriteFile = FileLoading.fetchDataFile(Path.of(spriteFileName), selected.getPackageFolderPath());
        javax.swing.JTextArea spriteFileNameLabel;
        if (spriteFile != null) {
            spriteFileNameLabel = ComponentUtilities.createWrappingLabel("Sprite file: " + spriteFile.getName());
        } else {
            spriteFileNameLabel = ComponentUtilities.createWrappingLabel("Sprite file: failed to fetch!");
        }
        spriteFileNameLabel.setBorder(labelSimpleBorder);
        if (spriteFile != null) {
            JPopupMenu spriteContextMenu = ComponentUtilities.createPathContextMenu(spriteFile.toPath());
            spriteFileNameLabel.addMouseListener(new MouseoverLabelListener(spriteContextMenu, spriteFileNameLabel));
            spriteFileNameLabel.setToolTipText(spriteFile.toString());
        }
        filesPanel.add(spriteFileNameLabel, gbc);
        gbc.gridy++;

        Map<String, SkinSpecFile> skins = selected.getSkins();
        if (skins != null && !skins.isEmpty()) {
            Collection<SkinSpecFile> values = skins.values();
            SkinSpecFile[] skinSpecFileArray = values.toArray(new SkinSpecFile[0]);
            JComboBox<SkinSpecFile> skinChooser = new JComboBox<>(skinSpecFileArray);
            skinChooser.setSelectedItem(selected.getActiveSkinSpecFile());
            skinChooser.addActionListener(e -> {
                SkinSpecFile chosen = (SkinSpecFile) skinChooser.getSelectedItem();
                selected.setActiveSkinSpecFile(chosen);
                if (parent != null) {
                    parent.updateEntryPanel(selected);
                }
            });
            
            JPanel chooserPanel = new JPanel();
            chooserPanel.setLayout(new BoxLayout(chooserPanel, BoxLayout.X_AXIS));
            chooserPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel skinLabel = new JLabel(StringManager.getString("SKIN"));
            chooserPanel.add(skinLabel);
            chooserPanel.add(skinChooser);
            chooserPanel.setBorder(new EmptyBorder(2, 0, 2, 0));
            
            filesPanel.add(chooserPanel, gbc);
            gbc.gridy++;
        }

        SkinSpecFile activeSkinSpecFile = selected.getActiveSkinSpecFile();
        if (activeSkinSpecFile != null && !activeSkinSpecFile.isBase()) {
            Path skinFilePath = activeSkinSpecFile.getFilePath();
            if (skinFilePath != null) {
                Path fileName = skinFilePath.getFileName();
                if (fileName != null) {
                    String skinFileName = fileName.toString();
                    javax.swing.JTextArea skinFileNameLabel = ComponentUtilities.createWrappingLabel("Skin file: " + skinFileName);
                    skinFileNameLabel.setBorder(labelSimpleBorder);
                    JPopupMenu skinContextMenu = ComponentUtilities.createPathContextMenu(skinFilePath);
                    skinFileNameLabel.addMouseListener(new MouseoverLabelListener(skinContextMenu, skinFileNameLabel));
                    skinFileNameLabel.setToolTipText(skinFilePath.toString());
                    filesPanel.add(skinFileNameLabel, gbc);
                    gbc.gridy++;
                }
            }
        }

        return filesPanel;
    }

    private static void addHullmodPanel(JPanel panel, ShipCSVEntry selected) {
        GameDataRepository gameData = SettingsManager.getGameData();
        if (!gameData.isHullmodDataLoaded()) return;

        JPanel hullmodsPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        ComponentUtilities.outfitPanelWithTitle(hullmodsPanel,
                new Insets(1, 0, 0, 0), "Built-in hullmods");
        hullmodsPanel.setAlignmentX(LEFT_ALIGNMENT);

        Collection<String> hullmodIDs = selected.getBuiltInHullmods();

        if (hullmodIDs.isEmpty()) return;

        for (String id : hullmodIDs) {
            HullmodCSVEntry entry = GameDataRepository.retrieveHullmodCSVEntryByID(id);
            if (entry != null) {
                JLabel imageLabel = entry.getIconLabel();
                hullmodsPanel.add(imageLabel);
            }
        }

        panel.add(hullmodsPanel);
        panel.revalidate();
        panel.repaint();
    }

    private static JPanel createVariantPanel(String shipId) {
        GameDataRepository gameData = SettingsManager.getGameData();

        Collection<VariantFile> variantsForHull = new ArrayList<>();
        Map<String, VariantFile> allVariants = gameData.getAllVariants();
        for (VariantFile variantFile : allVariants.values()) {
            if (variantFile == null) {
                continue;
            }
            String hullID = variantFile.getHullId();
            if (shipId != null && shipId.equals(hullID)) {
                variantsForHull.add(variantFile);
            }
        }

        if (variantsForHull.isEmpty()) return null;
        return DataTreeVariantPanelBuilder.createVariantsPanel(variantsForHull, true);
    }

}
