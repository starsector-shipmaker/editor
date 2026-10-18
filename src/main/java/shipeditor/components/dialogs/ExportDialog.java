package shipeditor.components.dialogs;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.PrimaryWindow;
import shipeditor.components.viewer.ImageExporter;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.parsing.FileUtilities;
import shipeditor.persistence.database.DatabaseQueryService;
import shipeditor.persistence.database.IndexedFile;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.graphics.ShowcaseGenerator;
import shipeditor.utility.graphics.opengl.FramebufferUtilities;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.StringConstants;
import shipeditor.utility.themes.Themes;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Log4j2
public class ExportDialog extends JDialog {

    private final JTabbedPane tabbedPane;

    // Viewport Snapshot Tab Components
    private JComboBox<String> vsFormatCombo;
    private JCheckBox vsTransparentCheck;
    private JButton vsColorButton;
    private Color vsBgColor = new Color(0, 0, 0, 0); // Transparent by default

    // Sprite Print Tab Components
    private JComboBox<String> spScaleCombo;
    private JSpinner spPaddingSpinner;
    private JComboBox<String> spBgCombo;
    private JCheckBox spRenderMountsCheck;
    private JLabel spDimensionLabel;
    private JLabel spPreviewLabel;
    private JPanel spPreviewWrapper;
    private JScrollPane spScrollPane;

    // Entity Showcase Tab Components
    private JComboBox<String> scTypeComboBox;
    private JComboBox<String> scModComboBox;
    private JSpinner scCellSizeSpinner;
    private JSpinner scLimitSpinner;
    private JCheckBox scRenderMissilesBox;
    private JButton scColorButton;
    private Color scBgColor = new Color(40, 40, 50, 255);

    public ExportDialog(int initialTabIndex) {
        super(PrimaryWindow.getInstance(), StringManager.getString("EXPORT_MANAGER_TITLE"), true);
        this.setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();
        
        // Tab 1: Viewport Snapshot
        tabbedPane.addTab(StringManager.getString("TAB_VIEWPORT_SNAPSHOT"), FontIcon.of(BoxiconsRegular.IMAGE, 16, Themes.getIconColor()), createViewportSnapshotPanel());
        
        // Tab 2: Sprite Print
        JPanel spritePrintPanel = createSpritePrintPanel();
        tabbedPane.addTab(StringManager.getString("TAB_SPRITE_PRINT"), FontIcon.of(BoxiconsRegular.PRINTER, 16, Themes.getIconColor()), spritePrintPanel);
        
        // Tab 3: Entity Showcase
        tabbedPane.addTab(StringManager.getString("TAB_ENTITY_SHOWCASE"), FontIcon.of(BoxiconsRegular.GRID, 16, Themes.getIconColor()), createEntityShowcasePanel());

        if (initialTabIndex >= 0 && initialTabIndex < tabbedPane.getTabCount()) {
            tabbedPane.setSelectedIndex(initialTabIndex);
        }

        this.add(tabbedPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportButton = new JButton(StringManager.getString("EXPORT"), FontIcon.of(BoxiconsRegular.EXPORT, 16, Themes.getIconColor()));
        exportButton.addActionListener(e -> executeExport());
        buttonPanel.add(exportButton);

        JButton cancelButton = new JButton(StringManager.getString("CANCEL"));
        cancelButton.addActionListener(e -> this.dispose());
        buttonPanel.add(cancelButton);

        this.add(buttonPanel, BorderLayout.SOUTH);

        // Calculate size based on Sprite Print panel which is usually largest, or give a good default
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setMinimumSize(new Dimension(600, 400));
        this.setSize(new Dimension(Math.min(900, (int)(screenSize.width * 0.8)), Math.min(600, (int)(screenSize.height * 0.8))));
        this.setLocationRelativeTo(PrimaryWindow.getInstance());

        // Initial preview update for Sprite Print
        updateSpritePrintPreview();
    }

    private JPanel createViewportSnapshotPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        int row = 0;
        
        vsFormatCombo = new JComboBox<>(new String[]{"PNG", "JPG"});
        vsFormatCombo.addActionListener(e -> updateVsControls());
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("IMAGE_FORMAT")), vsFormatCombo, row++);

        vsTransparentCheck = new JCheckBox(StringManager.getString("TRANSPARENT_BACKGROUND"), true);
        vsTransparentCheck.addActionListener(e -> updateVsControls());
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("BACKGROUND")), vsTransparentCheck, row++);

        vsColorButton = new JButton(StringManager.getString("SELECT_COLOR"));
        vsColorButton.setBackground(Color.BLACK);
        vsColorButton.setForeground(Color.WHITE);
        vsColorButton.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, StringManager.getString("SELECT_BACKGROUND_COLOR_TITLE"), vsBgColor);
            if (c != null) {
                vsBgColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
                styleColorButton(vsColorButton, vsBgColor);
            }
        });
        vsColorButton.setEnabled(false); // Disabled initially since transparent is checked
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("SOLID_COLOR")), vsColorButton, row++);
        
        // Add a push-to-top glue
        addVerticalGlue(panel, row);

        return panel;
    }
    
    private void updateVsControls() {
        boolean isPng = "PNG".equals(vsFormatCombo.getSelectedItem());
        if (!isPng) {
            vsTransparentCheck.setSelected(false);
            vsTransparentCheck.setEnabled(false);
        } else {
            vsTransparentCheck.setEnabled(true);
        }
        vsColorButton.setEnabled(!vsTransparentCheck.isSelected());
    }

    private JPanel createSpritePrintPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        spPreviewWrapper = new JPanel(new GridBagLayout());
        spPreviewLabel = new JLabel(StringManager.getString("NO_SPRITE_IMAGE"));
        spPreviewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        spPreviewWrapper.add(spPreviewLabel);
        
        spScrollPane = new JScrollPane(spPreviewWrapper);
        spScrollPane.setBorder(BorderFactory.createTitledBorder(StringManager.getString("PREVIEW")));
        spScrollPane.setWheelScrollingEnabled(true);
        
        MouseAdapter panAdapter = new MouseAdapter() {
            private Point origin;
            @Override
            public void mousePressed(MouseEvent e) {
                origin = e.getPoint();
                if (e.getSource() == spPreviewLabel) {
                    origin = SwingUtilities.convertPoint(spPreviewLabel, origin, spPreviewWrapper);
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (origin != null) {
                    Point current = e.getPoint();
                    if (e.getSource() == spPreviewLabel) {
                        current = SwingUtilities.convertPoint(spPreviewLabel, current, spPreviewWrapper);
                    }
                    int deltaX = origin.x - current.x;
                    int deltaY = origin.y - current.y;
                    
                    JViewport viewPort = spScrollPane.getViewport();
                    if (viewPort != null) {
                        Rectangle view = viewPort.getViewRect();
                        view.x += deltaX;
                        view.y += deltaY;
                        spPreviewWrapper.scrollRectToVisible(view);
                    }
                }
            }
        };
        spPreviewWrapper.addMouseListener(panAdapter);
        spPreviewWrapper.addMouseMotionListener(panAdapter);
        spPreviewLabel.addMouseListener(panAdapter);
        spPreviewLabel.addMouseMotionListener(panAdapter);
        
        spPreviewLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        spPreviewWrapper.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        
        panel.add(spScrollPane, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setPreferredSize(new Dimension(250, 0));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        spScaleCombo = new JComboBox<>(new String[]{"1x", "2x", "3x", "4x", "5x", "6x", "8x", "10x", "16x"});
        spPaddingSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 2000, 10));
        spBgCombo = new JComboBox<>(new String[]{"Transparent", "Solid Black", "Solid White", "Solid Gray"});
        spRenderMountsCheck = new JCheckBox(StringManager.getString("RENDER_MOUNTS_BOUNDS"), false);
        spDimensionLabel = new JLabel(StringManager.getString("OUTPUT_SIZE"));
        
        Component[] componentsToAdd = {
            new JLabel(StringManager.getString("OUTPUT_SCALE")), spScaleCombo, Box.createVerticalStrut(15),
            new JLabel(StringManager.getString("PADDING_PX")), spPaddingSpinner, Box.createVerticalStrut(15),
            new JLabel(StringManager.getString("BACKGROUND")), spBgCombo, Box.createVerticalStrut(15),
            spRenderMountsCheck, Box.createVerticalStrut(25),
            spDimensionLabel, Box.createVerticalGlue()
        };
        
        for (Component c : componentsToAdd) {
            if (c instanceof JComponent) ((JComponent) c).setAlignmentX(Component.CENTER_ALIGNMENT);
            rightPanel.add(c);
        }

        spScaleCombo.addActionListener(e -> updateSpritePrintPreview());
        spPaddingSpinner.addChangeListener(e -> updateSpritePrintPreview());
        spBgCombo.addActionListener(e -> updateSpritePrintPreview());
        spRenderMountsCheck.addActionListener(e -> updateSpritePrintPreview());

        panel.add(rightPanel, BorderLayout.EAST);
        return panel;
    }

    private void updateSpritePrintPreview() {
        ViewerLayer activeLayer = getActiveLayer();
        if (activeLayer == null) {
            spPreviewLabel.setText(StringManager.getString("NO_ACTIVE_LAYER"));
            return;
        }

        LayerPainter painter = activeLayer.getPainter();
        if (painter == null || painter.isUninitialized()) {
            spPreviewLabel.setText(StringManager.getString("LAYER_UNINITIALIZED"));
            return;
        }

        String scaleStr = (String) spScaleCombo.getSelectedItem();
        if (scaleStr == null) return;
        double tempScale = Double.parseDouble(scaleStr.replace("x", ""));
        int pad = (Integer) spPaddingSpinner.getValue();
        boolean drawMounts = spRenderMountsCheck.isSelected();
        
        final Color finalBgCol = getSpritePrintBackground();

        Rectangle2D bounds = painter.getVisualBounds();
        int baseW = (int) Math.ceil(bounds.getWidth());
        int baseH = (int) Math.ceil(bounds.getHeight());
        if (baseW <= 0 || baseH <= 0) return;
        
        int outW = (int) Math.round((baseW + pad * 2) * tempScale);
        int outH = (int) Math.round((baseH + pad * 2) * tempScale);
        spDimensionLabel.setText(StringManager.getString("OUTPUT_SIZE") + outW + " x " + outH + " px");
        
        StaticController.getViewer().queueGLTask(() -> {
            BufferedImage fboImg = FramebufferUtilities.renderLayerToImage(
                activeLayer, baseW, baseH, tempScale, pad, finalBgCol, drawMounts
            );
            if (fboImg != null) {
                SwingUtilities.invokeLater(() -> {
                    spPreviewLabel.setIcon(new ImageIcon(fboImg));
                    spPreviewLabel.setText("");
                    spPreviewWrapper.revalidate();
                    spPreviewWrapper.repaint();
                    
                    Dimension viewSize = spScrollPane.getViewport().getExtentSize();
                    Dimension viewSizeTotal = spPreviewWrapper.getSize();
                    int x = (viewSizeTotal.width - viewSize.width) / 2;
                    int y = (viewSizeTotal.height - viewSize.height) / 2;
                    spScrollPane.getViewport().setViewPosition(new Point(Math.max(0, x), Math.max(0, y)));
                });
            }
        });
    }

    private JPanel createEntityShowcasePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        int row = 0;
        
        scRenderMissilesBox = new JCheckBox(StringManager.getString("RENDER_LOADED_MISSILES"));
        scRenderMissilesBox.setSelected(true);

        scModComboBox = new JComboBox<>();

        scTypeComboBox = new JComboBox<>(new String[]{"Weapons", "Ships", "Fighters"});
        scTypeComboBox.addActionListener(e -> {
            boolean isWeapons = "Weapons".equals(scTypeComboBox.getSelectedItem());
            scRenderMissilesBox.setEnabled(isWeapons);
            updateShowcaseModList((String) scTypeComboBox.getSelectedItem());
        });
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("SHOWCASE_TYPE")), scTypeComboBox, row++);
        
        updateShowcaseModList((String) scTypeComboBox.getSelectedItem());
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("FILTER_MOD")), scModComboBox, row++);

        scCellSizeSpinner = new JSpinner(new SpinnerNumberModel(120, 32, 2048, 10));
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("CELL_SIZE_PX")), scCellSizeSpinner, row++);

        scLimitSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 5000, 1));
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("MAX_ENTITIES")), scLimitSpinner, row++);

        ComponentUtilities.addLabelAndComponent(panel, new JLabel(), scRenderMissilesBox, row++);

        scColorButton = new JButton(StringManager.getString("SELECT_COLOR"));
        scColorButton.setBackground(scBgColor);
        scColorButton.setForeground(Color.WHITE);
        scColorButton.addActionListener(e -> {
            Color c = JColorChooser.showDialog(this, StringManager.getString("SELECT_BACKGROUND_COLOR_TITLE"), scBgColor);
            if (c != null) {
                scBgColor = c;
                styleColorButton(scColorButton, scBgColor);
            }
        });
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("BACKGROUND_COLOR")), scColorButton, row++);
        
        // Glue
        addVerticalGlue(panel, row);

        return panel;
    }

    private void updateShowcaseModList(String type) {
        String entityType = "Weapons".equals(type) ? StringConstants.WEAPON_TYPE : StringConstants.SHIP_TYPE;
        List<IndexedFile> files = DatabaseQueryService.getFilesByType(entityType);
        Set<String> mods = new LinkedHashSet<>();
        mods.add("All");
        for (IndexedFile f : files) {
            if (f.getModId() != null) {
                mods.add(f.getModId());
            }
        }
        scModComboBox.setModel(new DefaultComboBoxModel<>(mods.toArray(new String[0])));
    }

    private void executeExport() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        switch (selectedIndex) {
            case 0 -> executeViewportSnapshot();
            case 1 -> executeSpritePrint();
            case 2 -> executeEntityShowcase();
            default -> {}
        }
    }

    private void executeViewportSnapshot() {
        String format = (String) vsFormatCombo.getSelectedItem();
        boolean isPng = "PNG".equals(format);
        
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(StringManager.getString("EXPORT_VIEWPORT_TITLE"));
        String ext = isPng ? "png" : "jpg";
        chooser.setFileFilter(new FileNameExtensionFilter(format + " Images", ext));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            if (dest == null) return;
            if (!dest.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + ext)) {
                dest = new File(dest.getParentFile(), dest.getName() + "." + ext);
            }
            
            Color finalBg = vsTransparentCheck.isSelected() ? null : vsBgColor;
            ImageExporter.exportViewport(StaticController.getViewer(), dest, ext.toLowerCase(java.util.Locale.ROOT), finalBg);
            this.dispose();
        }
    }

    private void executeSpritePrint() {
        ViewerLayer activeLayer = getActiveLayer();
        if (activeLayer == null || activeLayer.getPainter() == null || activeLayer.getPainter().isUninitialized()) {
            JOptionPane.showMessageDialog(this, StringManager.getString("NO_VALID_LAYER_TO_PRINT_MSG"), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        var chooser = FileUtilities.getImageChooser();
        File directory = FileUtilities.getLastGeneralDirectory();
        if (directory != null) {
            chooser.setCurrentDirectory(directory);
        }
        chooser.setDialogTitle(StringManager.getString("SAVE_SPRITE_IMAGE_TITLE"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            FileUtilities.setLastGeneralDirectory(chooser.getCurrentDirectory());
            javax.swing.filechooser.FileFilter filter = chooser.getFileFilter();
            String extension = (filter instanceof FileNameExtensionFilter fneFilter && fneFilter.getExtensions().length > 0)
                    ? fneFilter.getExtensions()[0]
                    : "png";
            File result = FileUtilities.ensureFileExtension(chooser, extension);
            
            Rectangle2D bounds = activeLayer.getPainter().getVisualBounds();
            int width = (int) Math.ceil(bounds.getWidth());
            int height = (int) Math.ceil(bounds.getHeight());
            
            if (width <= 0 || height <= 0) {
                JOptionPane.showMessageDialog(this, StringManager.getString("LAYER_IS_EMPTY_OR_INVALID_SIZE_MSG"), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String scaleStr = (String) spScaleCombo.getSelectedItem();
            double tempScale = scaleStr != null ? Double.parseDouble(scaleStr.replace("x", "")) : 1.0;
            int pad = (Integer) spPaddingSpinner.getValue();
            boolean renderMounts = spRenderMountsCheck.isSelected();
            
            final Color finalBgCol = getSpritePrintBackground();

            log.info("Commencing sprite printing: {}", result);
            StaticController.getViewer().queueGLTask(() -> FramebufferUtilities.printLayerToImage(activeLayer, width, height, tempScale, pad, result, finalBgCol, renderMounts));
            this.dispose();
        }
    }

    private void executeEntityShowcase() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(StringManager.getString("SAVE_SHOWCASE_IMAGE_TITLE"));
        String type = (String) scTypeComboBox.getSelectedItem();
        String defaultName = type != null ? type.toLowerCase(java.util.Locale.ROOT) + "_catalog.png" : "catalog.png";
        fileChooser.setSelectedFile(new File(defaultName));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = fileChooser.getSelectedFile();
            if (dest == null) return;
            
            String modId = (String) scModComboBox.getSelectedItem();
            int cellSize = (int) scCellSizeSpinner.getValue();
            int limit = (int) scLimitSpinner.getValue();
            boolean renderMissiles = scRenderMissilesBox.isSelected();
            
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    ShowcaseGenerator.generate(dest, type, modId, cellSize, scBgColor, limit, renderMissiles);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        JOptionPane.showMessageDialog(ExportDialog.this, StringManager.getString("SHOWCASE_GENERATED_SUCCESSFULLY_N_MSG") + dest.getAbsolutePath(), "Success", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(ExportDialog.this, StringManager.getString("FAILED_TO_GENERATE_SHOWCASE_N_MSG") + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        log.error("Failed to generate showcase", ex);
                    }
                }
            };
            worker.execute();
            this.dispose();
        }
    }

    private ViewerLayer getActiveLayer() {
        LayerManager layerManager = StaticController.getLayerManager();
        if (layerManager != null) {
            return layerManager.getActiveLayer();
        }
        return null;
    }

    private Color getSpritePrintBackground() {
        int bgIdx = spBgCombo.getSelectedIndex();
        Color bgCol = new Color(0,0,0,0);
        if (bgIdx == 1) bgCol = Color.BLACK;
        if (bgIdx == 2) bgCol = Color.WHITE;
        if (bgIdx == 3) bgCol = Color.GRAY;
        return bgCol;
    }

    private void styleColorButton(JButton button, Color background) {
        button.setBackground(background);
        button.setForeground(isDark(background) ? Color.WHITE : Color.BLACK);
    }

    private static void addVerticalGlue(JPanel panel, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);
    }

    private boolean isDark(Color color) {
        // HSP Color Model (standard perceived brightness)
        double perceived = Math.sqrt(
            0.299 * color.getRed() * color.getRed() +
            0.587 * color.getGreen() * color.getGreen() +
            0.114 * color.getBlue() * color.getBlue()
        );
        return perceived < 127.5;
    }
}
