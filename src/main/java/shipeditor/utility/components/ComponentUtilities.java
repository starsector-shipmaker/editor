package shipeditor.utility.components;

import shipeditor.utility.text.StringManager;

import com.formdev.flatlaf.ui.FlatLineBorder;
import com.formdev.flatlaf.ui.FlatRoundBorder;

import shipeditor.communication.BusEventListener;
import shipeditor.communication.EventBus;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.parsing.FileUtilities;
import shipeditor.parsing.loading.FileLoading;
import shipeditor.utility.Utility;
import shipeditor.utility.components.containers.SortableList;
import shipeditor.utility.components.containers.TextScrollPanel;
import shipeditor.utility.graphics.ColorUtilities;
import shipeditor.utility.objects.Pair;
import shipeditor.utility.themes.Themes;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.*;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import shipeditor.communication.events.components.ComponentEvents.LoadingActionFired;

@SuppressWarnings("ClassWithTooManyMethods")
public final class ComponentUtilities {

    private ComponentUtilities() {
    }

    /**
     * Arranges two components as opposites within a container with optional side padding.
     * It is assumed that container's layout is set to BoxLayout.
     * @param sidePadding The amount of horizontal padding (in pixels) to be added on both sides. Use 0 for no padding.
     */
    public static void layoutAsOpposites(JPanel container, Component left, Component right, int sidePadding) {
        if (!(container.getLayout() instanceof BoxLayout)) {
            throw new IllegalArgumentException("Passed container is not set to BoxLayout!");
        }
        container.add(Box.createRigidArea(new Dimension(sidePadding,0)));
        container.add(left);
        container.add(Box.createHorizontalGlue());
        container.add(right);
        container.add(Box.createRigidArea(new Dimension(sidePadding,0)));
    }

    public static JPanel createBoxLabelPanel(String leftName, JLabel rightValue) {
        int sidePadding = 6;
        return ComponentUtilities.createBoxLabelPanel(leftName, rightValue, sidePadding);
    }

    @SuppressWarnings("WeakerAccess")
    public static JPanel createBoxLabelPanel(String leftName, JLabel rightValue, int sidePadding) {
        JPanel infoContainer = UIFactory.createPanel();
        infoContainer.setLayout(new BoxLayout(infoContainer, BoxLayout.LINE_AXIS));
        JLabel label = UIFactory.createLabel(leftName);
        ComponentUtilities.layoutAsOpposites(infoContainer, label, rightValue, sidePadding);
        return infoContainer;
    }

    public static void addSeparatorToBoxPanel(JPanel panel) {
        ComponentUtilities.addSeparatorToBoxPanel(panel, 5);
    }

    @SuppressWarnings("WeakerAccess")
    public static void addSeparatorToBoxPanel(JPanel panel, int height) {
        panel.add(Box.createVerticalStrut(height));
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        panel.add(separator);
    }

    @SuppressWarnings("WeakerAccess")
    public static void setPanelTopLineBorder(JPanel panel, int height) {
        Border emptyBorder = new EmptyBorder(height, 0, 2, 0);
        Border lineBorder = new MatteBorder(
                UIConstants.INSETS_TOP_1, Themes.getBorderColor()
        );
        panel.setBorder(new CompoundBorder(emptyBorder, lineBorder));
    }

    private static JLabel createIconLabelWithBorder(Icon icon) {
        JLabel imageLabel = new JLabel(icon);
        imageLabel.setOpaque(true);
        imageLabel.setBorder(new FlatLineBorder(UIConstants.INSETS_SMALL, Themes.getBorderColor()));
        imageLabel.setBackground(Color.LIGHT_GRAY);
        return imageLabel;
    }

    @SuppressWarnings("WeakerAccess")
    public static JLabel createIconFromImage(File imageFile, String tooltip) {
        return ComponentUtilities.createIconFromImage(imageFile, tooltip, 32);
    }

    @SuppressWarnings("WeakerAccess")
    public static JLabel createIconFromImage(File imageFile, String tooltip, int maxSize) {
        BufferedImage iconImage = FileLoading.loadSpriteAsImage(imageFile);
        return ComponentUtilities.createIconFromImage(iconImage, tooltip, maxSize);
    }

    @SuppressWarnings("WeakerAccess")
    public static JLabel createIconFromImage(BufferedImage image, String tooltip, int maxSize) {
        if (image == null) {
            JLabel fallbackLabel = UIFactory.createLabel("?");
            if (tooltip != null && !tooltip.isEmpty()) {
                fallbackLabel.setToolTipText(tooltip);
            }
            return fallbackLabel;
        }
        Image clamped = ComponentUtilities.resizeImageToSquareLimit(image, maxSize);
        JLabel imageLabel = ComponentUtilities.createIconLabelWithBorder(new ImageIcon(clamped));
        if (tooltip != null && !tooltip.isEmpty()) {
            imageLabel.setToolTipText(tooltip);
        }
        return imageLabel;
    }

    /**
     * Creates a non-opaque panel that lays out its children left-to-right.
     */
    public static JPanel createTransparentBoxPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
        return panel;
    }

    /**
     * Wraps a component in a centered flow-layout panel.
     */
    public static JPanel createCenteredPanel(JComponent content) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.add(content);
        return panel;
    }

    /**
     * Copies the icon, text, tooltip, border and background settings of a freshly built label onto an
     * existing label, so already-displayed placeholder labels can be updated in place.
     */
    public static void copyLabelAppearance(JLabel target, JLabel source) {
        target.setIcon(source.getIcon());
        target.setText(source.getText());
        target.setToolTipText(source.getToolTipText());
        target.setBorder(source.getBorder());
        target.setOpaque(source.isOpaque());
        target.setBackground(source.getBackground());
    }

    @SuppressWarnings("WeakerAccess")
    public static Image resizeImageToSquareLimit(Image iconImage, int limit) {
        int originalWidth = iconImage.getWidth(null);
        int originalHeight = iconImage.getHeight(null);

        BufferedImage resizedImage = new BufferedImage(limit, limit, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();

        if (originalWidth > limit || originalHeight > limit) {
            double widthRatio = (double) limit / originalWidth;
            double heightRatio = (double) limit / originalHeight;
            double ratio = Math.min(widthRatio, heightRatio);

            int newWidth = (int) (originalWidth * ratio);
            int newHeight = (int) (originalHeight * ratio);

            int xOffset = (limit - newWidth) / 2;
            int yOffset = (limit - newHeight) / 2;

            g2d.drawImage(iconImage, xOffset, yOffset, newWidth, newHeight, null);
        } else {
            int xOffset = (limit - originalWidth) / 2;
            int yOffset = (limit - originalHeight) / 2;

            g2d.drawImage(iconImage, xOffset, yOffset, originalWidth, originalHeight, null);
        }

        g2d.dispose();
        return resizedImage;
    }

    public static Pair<JLabel, JSlider> createOpacityWidget(ChangeListener change,
                                                            BusEventListener eventListener) {
        Pair<JLabel, JSlider> widgets = ComponentUtilities.createOpacityWidget();
        JSlider slider = widgets.getSecond();
        slider.addChangeListener(change);
        EventBus.subscribe(slider, eventListener);

        return widgets;
    }

    public static Pair<JLabel, JSlider> createOpacityWidget(BooleanSupplier widgetChecker,
                                                            Function<LayerPainter, Float> opacityGetter,
                                                            Consumer<Float> setter,
                                                            BiConsumer<JComponent,
                                                                    Consumer<LayerPainter>> clearerListener,
                                                            BiConsumer<JComponent,
                                                                    Consumer<LayerPainter>> refresherListener) {
        Pair<JLabel, JSlider> widgetComponents = ComponentUtilities.createOpacityWidget();

        JLabel opacityLabel = widgetComponents.getFirst();
        JSlider opacitySlider = widgetComponents.getSecond();

        ChangeListener changeListener = e -> {
            if (widgetChecker.getAsBoolean()) {
                int sliderValue = opacitySlider.getValue();
                float resultValue = sliderValue / 100.0f;
                setter.accept(resultValue);
            }
        };

        opacitySlider.addChangeListener(changeListener);

        clearerListener.accept(opacitySlider, layer -> {
            opacitySlider.setValue(100);
            opacitySlider.setEnabled(false);
            opacityLabel.setToolTipText(StringManager.getString("NOT_INITIALIZED"));
        });

        refresherListener.accept(opacitySlider, layerPainter -> {
            // Refresh code is expected to make sure this block never gets called if layer does not have a painter.
            int value = (int) (opacityGetter.apply(layerPainter) * 100.0f);
            opacityLabel.setToolTipText(StringManager.getString("CURRENT_VALUE") + value + "%");
            opacitySlider.setValue(value);
            opacitySlider.setEnabled(true);
        });

        return widgetComponents;
    }

    private static Pair<JLabel, JSlider> createOpacityWidget() {
        JSlider opacitySlider = new JSlider(SwingConstants.HORIZONTAL,
                0, 100, 100);
        opacitySlider.setAlignmentX(0.0f);
        opacitySlider.setEnabled(false);
        opacitySlider.setSnapToTicks(true);
        Dictionary<Integer, JLabel> labelTable = new Hashtable<>();
        labelTable.put(0, UIFactory.createLabel("0%"));
        labelTable.put(50, UIFactory.createLabel("50%"));
        labelTable.put(100, UIFactory.createLabel("100%"));
        opacitySlider.setLabelTable(labelTable);
        opacitySlider.setMajorTickSpacing(50);
        opacitySlider.setMinorTickSpacing(10);
        opacitySlider.setPaintTicks(true);
        opacitySlider.setPaintLabels(true);
        JLabel opacityLabel = UIFactory.createLabel("");
        opacityLabel.setAlignmentX(0.0f);

        return new Pair<>(opacityLabel, opacitySlider);
    }

    public static JPopupMenu createPathContextMenu(Path filePath) {
        JPopupMenu openFileMenu = new JPopupMenu();
        JMenuItem openSourceFile = new JMenuItem(StringManager.getString("OPEN_SOURCE_FILE"));
        openSourceFile.addActionListener(e -> FileUtilities.openPathInDesktop(filePath));
        openFileMenu.add(openSourceFile);
        JMenuItem openContainingFolder = new JMenuItem(StringManager.getString("OPEN_CONTAINING_FOLDER"));
        openContainingFolder.addActionListener(e -> FileUtilities.openPathInDesktop(filePath.getParent()));
        openFileMenu.add(openContainingFolder);

        return openFileMenu;
    }

    public static Border createLabelSimpleBorder(Insets insets) {
        return new FlatLineBorder(insets, Themes.getBorderColor());
    }

    public static ImageIcon createIconFromColor(Color color, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect (0, 0, width, height);
        return new ImageIcon(image);
    }

    @SuppressWarnings("WeakerAccess")
    public static JLabel createColorIconLabel(Color color) {
        ImageIcon colorIcon = ComponentUtilities.createIconFromColor(color, 10, 10);
        return ComponentUtilities.createIconLabelWithBorder(colorIcon);
    }

    public static JPanel createFileTitlePanel(Path filePath, Path packagePath, String titleText) {
        JLabel label = UIFactory.createLabel(titleText);
        Insets empty = new Insets(0, 3, 2, 4);
        label.setBorder(ComponentUtilities.createLabelSimpleBorder(empty));

        if (filePath != null) {
            JPopupMenu contextMenu = ComponentUtilities.createPathContextMenu(filePath);

            if (packagePath != null) {
                JMenuItem openContainingPackage = new JMenuItem(StringManager.getString("OPEN_DATA_PACKAGE"));
                openContainingPackage.addActionListener(e -> FileUtilities.openPathInDesktop(packagePath));
                contextMenu.add(openContainingPackage);
            }

            label.addMouseListener(new MouseoverLabelListener(contextMenu, label, Themes.getPanelHighlightColor()));
            label.setToolTipText(filePath.toString());
        } else {
            label.setToolTipText(StringManager.getString("NO_FILE_PATH_ASSOCIATED"));
        }

        JPanel titleContainer = UIFactory.createPanel();
        titleContainer.setLayout(new BoxLayout(titleContainer, BoxLayout.LINE_AXIS));
        titleContainer.add(label);

        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 0));

        titleContainer.add(separator);

        titleContainer.setBorder(new FlatRoundBorder());
        titleContainer.setBackground(Themes.getDarkerBackgroundColor());
        return titleContainer;
    }

    public static JPanel createColorPropertyPanel(Component left, Color color, int sidePadding, java.util.function.Consumer<Color> setter) {
        JPanel container = new JPanel();

        container.setLayout(new BoxLayout(container, BoxLayout.LINE_AXIS));
        container.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel colorIcon = ComponentUtilities.createColorIconLabel(color);
        colorIcon.setToolTipText(ColorUtilities.getColorBreakdown(color));
        
        if (setter != null) {
            shipeditor.utility.graphics.SmartColorPaste.install(colorIcon, setter);
        }

        ComponentUtilities.layoutAsOpposites(container, left, colorIcon, sidePadding);
        return container;
    }

    public static Insets createLabelInsets() {
        return new Insets(0, 3, 2, 4);
    }

    public static<T> Pair<JPanel, JCheckBox> createReorderCheckboxPanel(SortableList<T> pointList) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.LINE_AXIS));

        ComponentUtilities.setPanelTopLineBorder(container, 6);

        JCheckBox reorderCheckbox = new JCheckBox(StringManager.getString("REORDER_BY_DRAG"));
        reorderCheckbox.setBorder(new EmptyBorder(4, 0, 0, 0));
        reorderCheckbox.addItemListener(e -> {
            boolean reorderOn = reorderCheckbox.isSelected();
            pointList.setDragEnabled(reorderOn);
        });
        container.add(Box.createRigidArea(new Dimension(6,0)));
        container.add(reorderCheckbox);
        container.add(Box.createHorizontalGlue());

        return new Pair<>(container, reorderCheckbox);
    }

    public static JPanel createTitledSeparatorPanel(String text) {
        var insets = UIConstants.INSETS_TOP_1;
        return ComponentUtilities.createTitledSeparatorPanel(text, insets);
    }

    public static TextScrollPanel createTextPanel(String text, int pad) {
        TextScrollPanel container = new TextScrollPanel(new FlowLayout());
        container.setBorder(new EmptyBorder(0, 0, pad, 0));
        container.setLayout(new BoxLayout(container, BoxLayout.LINE_AXIS));
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setMinimumSize(new Dimension(100,100));
        container.setMinimumSize(new Dimension(100,100));

        container.add(textArea);
        container.setAlignmentY(0);
        return container;
    }

    public static JTextArea createWrappingLabel(String text) {
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setOpaque(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(false);
        textArea.setFont(UIManager.getFont("Label.font"));
        return textArea;
    }

    private static JPanel createTitledSeparatorPanel(String text, Insets insets) {
        JPanel container = new JPanel();
        container.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        ComponentUtilities.outfitPanelWithTitle(container, insets, text);
        container.setAlignmentY(0);
        return container;
    }

    public static void outfitPanelWithTitle(JPanel panel, String text) {
        var insets = UIConstants.INSETS_TOP_1;
        MatteBorder matteLine = new MatteBorder(insets, Themes.getBorderColor());
        Border titledBorder = new TitledBorder(matteLine, text,
                TitledBorder.CENTER, TitledBorder.DEFAULT_POSITION);
        panel.setBorder(titledBorder);
    }

    public static void outfitPanelWithTitle(JPanel panel, Insets insets, String text) {
        MatteBorder matteLine = new MatteBorder(insets, Themes.getBorderColor());
        Border titledBorder = new TitledBorder(matteLine, text,
                TitledBorder.CENTER, TitledBorder.DEFAULT_POSITION);
        panel.setBorder(titledBorder);
    }

    public static Pair<JPanel, JButton> createLoaderButtonPanel(String labelText, Action buttonAction) {
        JPanel topContainer = new JPanel();
        topContainer.add(UIFactory.createLabel(labelText));
        JButton loadButton = new JButton(buttonAction);
        java.lang.ref.WeakReference<JButton> buttonRef = new java.lang.ref.WeakReference<>(loadButton);
        EventBus.subscribe(loadButton, event -> {
            if (event instanceof LoadingActionFired checked) {
                JButton button = buttonRef.get();
                if (button != null) {
                    button.setEnabled(!checked.started());
                    button.repaint();
                }
            }
        });
        topContainer.add(loadButton);
        return new Pair<>(topContainer, loadButton);
    }

    public static JComponent addLabelAndComponent(JPanel parent, JLabel label, JComponent component, int y) {
        int leftPad = 6;
        int rightPad = 6;
        int labelPad = 3;
        return ComponentUtilities.addLabelAndComponent(parent, label, component, leftPad, rightPad, labelPad, y);
    }

    /**
     * @param parent assumes that JPanel instance has GridBagLayout set as component layout.
     */
    @SuppressWarnings("MethodWithTooManyParameters")
    public static JComponent addLabelAndComponent(JPanel parent, JLabel label, JComponent component,
                                                  int leftPad, int rightPad, int labelPad, int y) {
        GridBagConstraints constraints = new GridBagConstraints();

        constraints.insets = new Insets(3, leftPad, 0, 3);
        constraints.gridx = 0;
        constraints.gridy = y;
        constraints.weightx = 0.0;
        constraints.anchor = GridBagConstraints.LINE_START;
        parent.add(label, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.gridy = y;
        if (component instanceof JLabel) {
            constraints.fill = GridBagConstraints.NONE;
            constraints.insets = new Insets(3, 3, 0, rightPad + labelPad);
        } else {
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.insets = new Insets(3, 3, 0, rightPad);
        }
        constraints.anchor = GridBagConstraints.LINE_END;
        parent.add(component, constraints);
        return component;
    }

    public static JLabel getNoSelected() {
        JLabel label = UIFactory.createLabel(StringManager.getString("NO_SELECTED"));
        label.setBorder(new EmptyBorder(5, 0, 5, 0));
        return label;
    }

    public static JPanel createCSVEntryPanel(JLabel iconLabel, JComponent middleUpper,
                                             JComponent middleLower, ActionListener removeAction) {
        JPanel container = new JPanel(new GridBagLayout());
        Border flatRoundBorder = new FlatRoundBorder();
        container.setBorder(flatRoundBorder);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridheight = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 4, 4, 0);
        constraints.anchor = GridBagConstraints.LINE_START;

        container.add(iconLabel, constraints);

        constraints.gridx = 1;
        if (middleLower == null) {
            constraints.gridheight = 2;
            constraints.insets = new Insets(0, 4, 0, 0);
        } else {
            constraints.gridheight = 1;
            constraints.insets = new Insets(2, 4, 0, 0);
        }
        constraints.weightx = 1;
        if (middleUpper != null) {
            container.add(middleUpper, constraints);
        }

        constraints.gridy = 1;
        constraints.insets = new Insets(0, 4, 0, 0);
        if (middleLower != null) {
            container.add(middleLower, constraints);
        }

        constraints.gridx = 2;
        constraints.gridy = 0;
        constraints.weightx = 0;
        constraints.gridheight = 2;
        constraints.insets = new Insets(0, 0, 0, 4);
        constraints.anchor = GridBagConstraints.LINE_END;

        JButton removeButton = UIFactory.createButton("Remove");

        removeButton.addActionListener(removeAction);
        removeButton.setToolTipText(StringManager.getString("REMOVE_FROM_LIST"));

        container.add(removeButton, constraints);

        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        container.setAlignmentY(0);
        return container;
    }

    public static JPanel createHintPanel(String text, String prefix) {
        JPanel hintPanel = new JPanel();
        hintPanel.setLayout(new BoxLayout(hintPanel, BoxLayout.LINE_AXIS));

        JLabel hintLabel = UIFactory.createLabel(prefix);
        hintLabel.setBorder(new EmptyBorder(4, 4, 4, 4));
        hintLabel.setAlignmentY(0.5f);
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.BOLD));
        hintPanel.add(hintLabel);

        JPanel hintInfo = ComponentUtilities.createTextPanel(text, 2);
        hintInfo.setBorder(new EmptyBorder(4, 0, 4, 4));
        hintInfo.setAlignmentY(0.5f);
        hintPanel.add(hintInfo);

        hintPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

        return hintPanel;
    }

    public static JPanel createDragInfoPanel() {
        String hintText = "Drag and drop from game data widget to add entries.";
        return ComponentUtilities.createHintPanel(hintText, "Info:");
    }

    public static JLabel createFileLabel(Path path, String description) {
        Path fileNamePath = path != null ? path.getFileName() : null;
        String fileName = fileNamePath != null ? fileNamePath.toString() : "Unknown";
        JLabel label = UIFactory.createLabel(description + fileName);
        label.setToolTipText(path != null ? path.toString() : "Unknown");
        label.setBorder(ComponentUtilities.createLabelSimpleBorder(ComponentUtilities.createLabelInsets()));
        if (path != null) {
            JPopupMenu pathContextMenu = ComponentUtilities.createPathContextMenu(path);
            label.addMouseListener(new MouseoverLabelListener(pathContextMenu, label));
        }
        return label;
    }

    /**
     * Titles the base, added-by-skin, and removed-by-skin list panes and stacks them inside a scroll pane.
     */
    public static javax.swing.JScrollPane createBuiltInListsScroller(JPanel baseList, JPanel addedBySkinList,
                                                                     JPanel removedBySkinList) {
        outfitPanelWithTitle(baseList, StringManager.getString("BASE_BUILT_INS"));
        outfitPanelWithTitle(addedBySkinList, StringManager.getString("ADDED_BY_SKIN"));
        outfitPanelWithTitle(removedBySkinList, StringManager.getString("REMOVED_BY_SKIN"));

        JPanel container = new JPanel(new java.awt.GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 0.33;
        constraints.weightx = 1;
        constraints.ipady = 40;
        constraints.gridy = 0;

        container.add(baseList, constraints);
        constraints.gridy = 1;
        container.add(addedBySkinList, constraints);
        constraints.gridy = 2;
        container.add(removedBySkinList, constraints);

        javax.swing.JScrollPane scroller = new javax.swing.JScrollPane(container);
        scroller.getVerticalScrollBar().setUnitIncrement(16);
        return scroller;
    }

    public static class InfoPanelBuilder {
        private final JPanel panel;
        private final GridBagConstraints gbc;
        private int row = 0;

        public InfoPanelBuilder(String title) {
            panel = new JPanel(new java.awt.GridBagLayout());
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
            ComponentUtilities.outfitPanelWithTitle(panel, new Insets(1, 0, 0, 0), title);

            gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.insets = new Insets(2, 4, 4, 4);
        }

        public void addSpritePreview(shipeditor.utility.graphics.Sprite sprite) {
            if (sprite != null) {
                String tooltip = Utility.getTooltipForSprite(sprite);
                JLabel spriteIcon = ComponentUtilities.createIconFromImage(sprite.getImage(), tooltip, 128);
                JPanel spriteWrapper = ComponentUtilities.createCenteredPanel(spriteIcon);
                panel.add(spriteWrapper, gbc);
                gbc.gridy++;
                row++;
            }
        }

        public void addWrappingPathLabel(String prefix, java.nio.file.Path path) {
            Path fileNamePath = path != null ? path.getFileName() : null;
            String fileName = fileNamePath != null ? fileNamePath.toString() : "Not found";
            String fullPath = path != null ? path.toString() : "Not found";
            JLabel label = new JLabel(String.format("<html><b>%s%s</b></html>", prefix, fileName));
            label.setToolTipText(fullPath);
            label.setBorder(ComponentUtilities.createLabelSimpleBorder(new Insets(2, 4, 2, 4)));
            panel.add(label, gbc);
            gbc.gridy++;
            row++;
        }
        
        public void addCustomComponent(JComponent component) {
            panel.add(component, gbc);
            gbc.gridy++;
            row++;
        }

        public JPanel getPanel() {
            return panel;
        }

        public int getRow() {
            return row;
        }
        
        public void setRow(int row) {
            this.row = row;
            this.gbc.gridy = row;
        }
    }
}
