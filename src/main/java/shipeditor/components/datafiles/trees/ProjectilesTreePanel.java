package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.components.ComponentEnums.OpenDataTarget;
import shipeditor.components.viewer.layers.weapon.ProjectileLayer;
import shipeditor.components.viewer.layers.weapon.ProjectileLayerPainter;
import shipeditor.parsing.FileUtilities;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.weapon.ProjectileSpecFile;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.graphics.SmartColorPaste;
import shipeditor.utility.graphics.Sprite;

import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import shipeditor.communication.events.files.FileEvents.WeaponTreeReloadQueued;

@Log4j2
public class ProjectilesTreePanel extends DataTreePanel {

    public ProjectilesTreePanel() {
        super("Projectile file packages");
    }

    @Override
    protected boolean isDataLoaded() {
        return SettingsManager.getGameData().isWeaponsDataLoaded();
    }


    @Override
    protected void initTreePanelListeners(JPanel passedTreePanel) {
        EventBus.subscribe(this, event -> {
            if (event instanceof WeaponTreeReloadQueued) {
                this.queueReload();
            }
        });

        JTree tree = getTree();
        tree.addMouseListener(createContextMenuListener());
        getTree().addMouseListener(new DoubleClickLayerLoader());
        tree.addTreeSelectionListener(new ProjectileSelectionListener());
    }

    private Map<String, List<shipeditor.persistence.database.IndexedFile>> fetchProjectilesByMod() {
        java.util.List<shipeditor.persistence.database.IndexedFile> projFiles = shipeditor.persistence.database.DatabaseQueryService.getFilesByType(shipeditor.utility.text.StringConstants.PROJECTILE_TYPE);
        Map<String, List<shipeditor.persistence.database.IndexedFile>> byMod = new java.util.LinkedHashMap<>();
        if (projFiles != null) {
            for (shipeditor.persistence.database.IndexedFile file : projFiles) {
                byMod.computeIfAbsent(file.getModId(), k -> new java.util.ArrayList<>()).add(file);
            }
        }
        return byMod;
    }

    @Override
    protected java.util.List<DefaultMutableTreeNode> buildTreeNodesBackground() {
        Map<String, List<shipeditor.persistence.database.IndexedFile>> byMod = fetchProjectilesByMod();
        if (byMod == null || byMod.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<DefaultMutableTreeNode> packageRoots = new java.util.ArrayList<>();
        for (Map.Entry<String, List<shipeditor.persistence.database.IndexedFile>> folder : byMod.entrySet()) {
            String modId = folder.getKey();
            if (SettingsManager.isModActive(modId)) {
                packageRoots.add(createPackageNode(folder));
            }
        }
        return packageRoots;
    }

    @Override
    protected void onTreePopulated() {
        resetInfoPanel();
    }

    private static DefaultMutableTreeNode createPackageNode(Map.Entry<String, List<shipeditor.persistence.database.IndexedFile>> folder) {
        String modId = folder.getKey();

        DefaultMutableTreeNode result = DataTreeSupport.createPackageRootNode(modId,
                SettingsManager.isCoreFolder(modId));

        for (shipeditor.persistence.database.IndexedFile entry : folder.getValue()) {
            MutableTreeNode node = new DefaultMutableTreeNode(entry);
            result.add(node);
        }

        return result;
    }

    @Override
    protected Class<?> getEntryClass() {
        return shipeditor.persistence.database.IndexedFile.class;
    }

    @Override
    protected JPanel createTopPanel() {
        return null;
    }

    @Override
    protected String getTooltipForEntry(Object entry) {
        if (entry instanceof shipeditor.persistence.database.IndexedFile proj) {
            return "<html><b>" + proj.getEntityId() + "</b><br>(Double-click to load sprite)</html>";
        } else if (entry instanceof GameDataPackage dataPackage) {
            return DataTreePanel.getTooltipForPackage(dataPackage);
        }
        return null;
    }

    @Override
    JPopupMenu getContextMenu() {
        JPopupMenu menu = super.getContextMenu();
        DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
        if (cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof shipeditor.persistence.database.IndexedFile) {
            menu.addSeparator();
            JMenuItem loadAsLayer = new JMenuItem(StringManager.getString("LOAD_AS_PROJECTILE_LAYER"));
            loadAsLayer.addActionListener(new LoadLayerFromTree());
            menu.add(loadAsLayer);
        }
        return menu;
    }

    @Override
    protected void openEntryPath(OpenDataTarget target) {
        DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
        if (cachedSelectForMenu == null || !(cachedSelectForMenu.getUserObject() instanceof shipeditor.persistence.database.IndexedFile checked))
            return;
        Path toOpen = checked.getFilePath();
        if (target == OpenDataTarget.CONTAINER && toOpen != null) {
            toOpen = toOpen.getParent();
        }
        if (toOpen != null) {
            FileUtilities.openPathInDesktop(toOpen);
        }
    }

    // Right panel population

    private class ProjectileSelectionListener implements TreeSelectionListener {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
            TreePath path = e.getNewLeadSelectionPath();
            if (path == null) {
                resetInfoPanel();
                return;
            }
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof shipeditor.persistence.database.IndexedFile spec) {
                ProjectileSpecFile parsed = GameDataRepository.getProjectileByID(spec.getEntityId());
                if (parsed != null) {
                    updateEntryPanel(parsed);
                } else {
                    resetInfoPanel();
                }
            } else {
                resetInfoPanel();
            }
        }
    }

    private void updateEntryPanel(ProjectileSpecFile spec) {
        JPanel consolePanel = getConsolePanel();
        consolePanel.removeAll();
        consolePanel.add(new javax.swing.JLabel("No CSV data for projectiles."));
        consolePanel.revalidate();
        consolePanel.repaint();

        JPanel infoPanel = getLeftInfoPanel();
        infoPanel.removeAll();
        infoPanel.setLayout(new javax.swing.BoxLayout(infoPanel, javax.swing.BoxLayout.Y_AXIS));

        ComponentUtilities.InfoPanelBuilder builder = new ComponentUtilities.InfoPanelBuilder("Projectile Info");

        // Sprite preview
        Sprite sprite = loadProjectileSprite(spec);
        builder.addSpritePreview(sprite);

        // File path
        Path specFilePath = spec.getProjectileSpecFilePath();
        if (specFilePath != null) {
            builder.addWrappingPathLabel("Proj file: ", specFilePath);
        }

        JPanel specFilePanel = builder.getPanel();
        int row = builder.getRow();

        // Core identity fields
        row = addStringEditor(specFilePanel, "ID", spec.getId(), spec::setId, row);
        row = addStringEditor(specFilePanel, "Spec class", spec.getSpecClass(), spec::setSpecClass, row);
        row = addStringEditor(specFilePanel, "Missile type", spec.getMissileType(), spec::setMissileType, row);
        row = addStringEditor(specFilePanel, "Spawn type", spec.getSpawnType(), spec::setSpawnType, row);

        // Sprite paths
        row = addStringEditor(specFilePanel, "Sprite", spec.getSprite(), spec::setSprite, row);
        row = addStringEditor(specFilePanel, "Bullet sprite", spec.getBulletSprite(), spec::setBulletSprite, row);

        // Geometry
        if (spec.getSize() != null) {
            row = addStringEditor(specFilePanel, "Size (int[])", Arrays.toString(spec.getSize()), val -> {
            }, row); // Just display for now
        }
        if (spec.getCenter() != null) {
            Point2D.Double center = spec.getCenter();
            row = addStringEditor(specFilePanel, "Center", String.format("[%.1f, %.1f]", center.x, center.y), val -> {
            }, row);
        }
        row = addDoubleEditor(specFilePanel, "Length", spec.getLength(), spec::setLength, row);
        row = addDoubleEditor(specFilePanel, "Width", spec.getWidth(), spec::setWidth, row);

        // Collision
        row = addDoubleEditor(specFilePanel, "Collision radius", spec.getCollisionRadius(), spec::setCollisionRadius,
                row);
        row = addStringEditor(specFilePanel, "Collision class", spec.getCollisionClass(), spec::setCollisionClass, row);
        row = addStringEditor(specFilePanel, "Collision (fighter)", spec.getCollisionClassByFighter(),
                spec::setCollisionClassByFighter, row);

        // Colors (Editable via text for simplicity in tree panel)
        row = addColorEditor(specFilePanel, "Fringe color", spec.getFringeColor(), spec::setFringeColor, row);
        row = addColorEditor(specFilePanel, "Core color", spec.getCoreColor(), spec::setCoreColor, row);
        row = addColorEditor(specFilePanel, "Explosion color", spec.getExplosionColor(), spec::setExplosionColor, row);
        row = addColorEditor(specFilePanel, "Glow color", spec.getGlowColor(), spec::setGlowColor, row);

        // Visual params
        row = addDoubleEditor(specFilePanel, "Explosion radius", spec.getExplosionRadius(), spec::setExplosionRadius,
                row);
        row = addDoubleEditor(specFilePanel, "Glow radius", spec.getGlowRadius(), spec::setGlowRadius, row);
        row = addDoubleEditor(specFilePanel, "Hit glow radius", spec.getHitGlowRadius(), spec::setHitGlowRadius, row);
        row = addDoubleEditor(specFilePanel, "Fade time", spec.getFadeTime(), spec::setFadeTime, row);
        row = addDoubleEditor(specFilePanel, "Flameout time", spec.getFlameoutTime(), spec::setFlameoutTime, row);
        row = addDoubleEditor(specFilePanel, "No engine glow time", spec.getNoEngineGlowTime(),
                spec::setNoEngineGlowTime, row);
        row = addDoubleEditor(specFilePanel, "Arming time", spec.getArmingTime(), spec::setArmingTime, row);

        // Texture
        row = addDoubleEditor(specFilePanel, "Texture scroll speed", spec.getTextureScrollSpeed(),
                spec::setTextureScrollSpeed, row);
        row = addDoubleEditor(specFilePanel, "Pixels per texel", spec.getPixelsPerTexel(), spec::setPixelsPerTexel,
                row);
        row = addStringListEditor(specFilePanel, "Texture type", spec.getTextureType(), spec::setTextureType, row);

        // Effects / scripts
        row = addStringEditor(specFilePanel, "On-fire effect", spec.getOnFireEffect(), spec::setOnFireEffect, row);
        row = addStringEditor(specFilePanel, "On-hit effect", spec.getOnHitEffect(), spec::setOnHitEffect, row);

        // Passthrough flags
        row = addBooleanEditor(specFilePanel, "Pass-through missiles", spec.getPassThroughMissiles(),
                spec::setPassThroughMissiles, row);
        row = addBooleanEditor(specFilePanel, "Pass-through fighters", spec.getPassThroughFighters(),
                spec::setPassThroughFighters, row);
        row = addBooleanEditor(specFilePanel, "Pass-through fighters (destroyed)",
                spec.getPassThroughFightersOnlyWhenDestroyed(), spec::setPassThroughFightersOnlyWhenDestroyed, row);

        // Complex nested specs (just show as read-only text area for now)
        row = addJsonSummary(specFilePanel, "Engine spec", spec.getEngineSpec(), row);
        row = addJsonSummary(specFilePanel, "Engine slots", spec.getEngineSlots(), row);
        row = addJsonSummary(specFilePanel, "Behavior spec", spec.getBehaviorSpec(), row);
        row = addJsonSummary(specFilePanel, "Explosion spec", spec.getExplosionSpec(), row);

        // Add glue at the bottom
        GridBagConstraints glueConstraints = new GridBagConstraints();
        glueConstraints.gridx = 0;
        glueConstraints.gridy = row;
        glueConstraints.weighty = 1.0;
        specFilePanel.add(javax.swing.Box.createVerticalGlue(), glueConstraints);

        infoPanel.add(builder.getPanel());
        infoPanel.add(javax.swing.Box.createVerticalStrut(20));

        infoPanel.revalidate();
        infoPanel.repaint();
    }

    private Sprite loadProjectileSprite(ProjectileSpecFile spec) {
        String spritePath = spec.getSprite();
        if (spritePath == null || spritePath.isBlank()) {
            spritePath = spec.getBulletSprite();
        }
        if (spritePath == null || spritePath.isBlank()) {
            return null;
        }
        Path containingPackage = spec.getContainingPackage();
        File file = FileLoading.fetchDataFile(Path.of(spritePath), containingPackage);
        if (file != null && file.isFile()) {
            return FileLoading.loadSprite(file);
        }
        return null;
    }

    private static int addStringEditor(JPanel container, String key, String value,
            java.util.function.Consumer<String> setter, int row) {
        javax.swing.JTextField field = new javax.swing.JTextField(value != null ? value : "", 15);
        field.addActionListener(e -> setter.accept(field.getText().isEmpty() ? null : field.getText()));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                setter.accept(field.getText().isEmpty() ? null : field.getText());
            }
        });
        ComponentUtilities.addLabelAndComponent(container, new JLabel(key + ":"), field, row++);
        return row;
    }

    private static int addStringListEditor(JPanel container, String key, java.util.List<String> value,
            java.util.function.Consumer<java.util.List<String>> setter, int row) {
        String text = value == null ? "" : String.join(", ", value);
        javax.swing.JTextField field = new javax.swing.JTextField(text, 15);
        java.awt.event.ActionListener updater = e -> {
            if (field.getText().isEmpty()) {
                setter.accept(null);
            } else {
                java.util.List<String> list = java.util.Arrays.stream(field.getText().split(","))
                        .map(s -> s.trim())
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
                setter.accept(list.isEmpty() ? null : list);
            }
        };
        field.addActionListener(updater);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                updater.actionPerformed(null);
            }
        });
        ComponentUtilities.addLabelAndComponent(container, new JLabel(key + ":"), field, row++);
        return row;
    }

    private static int addDoubleEditor(JPanel container, String key, Double value,
            java.util.function.Consumer<Double> setter, int row) {
        String formatted = value != null
                ? ((Double.compare(value, Math.floor(value)) == 0) ? String.valueOf(value.intValue()) : String.valueOf(value))
                : "";
        javax.swing.JTextField field = new javax.swing.JTextField(formatted, 15);
        java.awt.event.ActionListener updater = e -> {
            try {
                if (field.getText().isEmpty())
                    setter.accept(null);
                else
                    setter.accept(Double.parseDouble(field.getText()));
            } catch (NumberFormatException ex) {
            }
        };
        field.addActionListener(updater);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                updater.actionPerformed(null);
            }
        });
        ComponentUtilities.addLabelAndComponent(container, new JLabel(key + ":"), field, row++);
        return row;
    }

    private static int addBooleanEditor(JPanel container, String key, Boolean value,
            java.util.function.Consumer<Boolean> setter, int row) {
        javax.swing.JCheckBox check = new javax.swing.JCheckBox();
        check.setSelected(Boolean.TRUE.equals(value));
        check.addActionListener(e -> setter.accept(check.isSelected()));
        ComponentUtilities.addLabelAndComponent(container, new JLabel(key + ":"), check, row++);
        return row;
    }

    private static int addColorEditor(JPanel container, String key, Color color,
            java.util.function.Consumer<Color> setter, int row) {
        String text = color == null ? ""
                : String.format("%d, %d, %d, %d", color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        javax.swing.JTextField field = new javax.swing.JTextField(text, 15);
        SmartColorPaste.install(field, pastedColor -> {
            String starsectorFormat = pastedColor.getRed() + ", " + pastedColor.getGreen() + ", "
                    + pastedColor.getBlue() + ", " + pastedColor.getAlpha();
            field.setText(starsectorFormat);
            setter.accept(pastedColor);
        });
        java.awt.event.ActionListener updater = e -> {
            try {
                if (field.getText().isEmpty()) {
                    setter.accept(null);
                } else {
                    String[] parts = field.getText().split(",");
                    if (parts.length >= 3) {
                        int r = Integer.parseInt(parts[0].trim());
                        int g = Integer.parseInt(parts[1].trim());
                        int b = Integer.parseInt(parts[2].trim());
                        int a = parts.length >= 4 ? Integer.parseInt(parts[3].trim()) : 255;
                        setter.accept(new Color(r, g, b, a));
                    }
                }
            } catch (NumberFormatException ex) {
            }
        };
        field.addActionListener(updater);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                updater.actionPerformed(null);
            }
        });
        shipeditor.utility.graphics.SmartColorPaste.install(field, pastedColor -> {
            String starsectorFormat = pastedColor.getRed() + ", " + pastedColor.getGreen() + ", "
                    + pastedColor.getBlue() + ", " + pastedColor.getAlpha();
            field.setText(starsectorFormat);
            setter.accept(pastedColor);
        });
        ComponentUtilities.addLabelAndComponent(container, new JLabel(key + ":"), field, row++);
        return row;
    }

    private static int addJsonSummary(JPanel container, String key, JsonNode node, int row) {
        if (node == null || node.isMissingNode() || node.isNull())
            return row;

        StringBuilder sb = new StringBuilder();
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                JsonNode val = field.getValue();
                sb.append(field.getKey()).append(": ").append(val.isValueNode() ? val.asText() : val.toString())
                        .append("\n");
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode element = node.get(i);
                if (element.isObject() && element.has("id")) {
                    sb.append("[").append(i).append("] id: ").append(element.get("id").asText()).append("\n");
                } else {
                    sb.append("[").append(i).append("] ").append(element.toString()).append("\n");
                }
            }
        }

        javax.swing.JTextArea area = new javax.swing.JTextArea(sb.toString().trim());
        area.setEditable(false);
        area.setOpaque(false);
        area.setFont(new javax.swing.JTextField().getFont());
        ComponentUtilities.addLabelAndComponent(container, new JLabel(key + ":"), area, row++);
        return row;
    }

    // Layer loading

    private class LoadLayerFromTree extends AbstractAction {
        @Override
        public boolean isEnabled() {
            DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
            return super.isEnabled() && cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof shipeditor.persistence.database.IndexedFile;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            DefaultMutableTreeNode cachedSelectForMenu = getCachedSelectForMenu();
            if (cachedSelectForMenu != null && cachedSelectForMenu.getUserObject() instanceof shipeditor.persistence.database.IndexedFile checked) {
                ProjectileSpecFile parsed = GameDataRepository.getProjectileByID(checked.getEntityId());
                if (parsed != null) {
                    loadProjectileAsLayer(parsed);
                }
            }
        }
    }

    private class DoubleClickLayerLoader extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            DefaultMutableTreeNode node = DataTreeSupport.getDoubleClickedNode(getTree(), e);
            if (node != null && node.getUserObject() instanceof shipeditor.persistence.database.IndexedFile checked) {
                ProjectileSpecFile parsed = GameDataRepository.getProjectileByID(checked.getEntityId());
                if (parsed != null) {
                    loadProjectileAsLayer(parsed);
                }
            }
        }
    }

    private void loadProjectileAsLayer(ProjectileSpecFile specFile) {
        ProjectileLayer layer = new ProjectileLayer();
        layer.setSpecFile(specFile);

        String spritePathStr = specFile.getSprite();
        if (spritePathStr != null && !spritePathStr.trim().isEmpty()) {
            Path spritePath = Path.of(spritePathStr);
            Path containingPackage = specFile.getContainingPackage();
            java.io.File file = FileLoading.fetchDataFile(spritePath, containingPackage);

            if (file != null && file.isFile()) {
                shipeditor.utility.graphics.Sprite sprite = FileLoading.loadSprite(file);
                ProjectileLayerPainter painter = new ProjectileLayerPainter(layer, sprite, specFile);
                layer.setPainter(painter);
            } else {
                log.error("Invalid sprite file resolved for projectile {}: {}", specFile.getId(), file);
            }

            shipeditor.communication.events.viewer.layers.LayerEvents.ProjectileLayerCreated event = new shipeditor.communication.events.viewer.layers.LayerEvents.ProjectileLayerCreated(
                    layer);
            shipeditor.utility.overseers.StaticController.getViewer().getLayerManager().getLayers().add(layer);
            shipeditor.communication.EventBus.publish(event);
        }
    }

    @Override
    protected JTree createCustomTree() {
        JTree custom = super.createCustomTree();
        custom.setCellRenderer(new ProjectilesTreeCellRenderer());
        return custom;
    }

    private static class ProjectilesTreeCellRenderer extends TreeEntryRenderer {
        @Override
        protected void customizeEntry(Object object, boolean leaf) {
            if (object instanceof shipeditor.persistence.database.IndexedFile checked && leaf) {
                setText(checked.getEntityId());
            }
        }
    }

}
