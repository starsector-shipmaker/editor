package shipeditor.components.instrument.ship.hull;

import shipeditor.utility.text.StringManager;

import com.formdev.flatlaf.ui.FlatLineBorder;
import shipeditor.communication.EventBus;
import shipeditor.components.ComponentEnums.OpenDataTarget;
import shipeditor.components.viewer.PrimaryViewer;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.parsing.FileUtilities;
import shipeditor.parsing.loading.OpenSpriteAction;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.RepresentationEnums.SizeEnum;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.representation.ship.HullStyle;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.MouseoverLabelListener;
import shipeditor.utility.graphics.ColorUtilities;
import shipeditor.utility.graphics.SmartColorPaste;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.overseers.EventScheduler;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.themes.Themes;

import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import shipeditor.communication.events.components.ComponentEvents.LayerTabUpdated;
import shipeditor.communication.events.components.ComponentEvents.CSVEntryIDChanged;

@SuppressWarnings("ClassWithTooManyFields")
public class HullDataControlPanel extends JPanel {

    private ShipLayer cachedLayer;

    private JLabel coversColorValue;

    private javax.swing.JTextField spritePathValue;

    private javax.swing.JTextField spriteNameValue;

    private JLabel spritePathLabel;

    private JLabel coversColorLabel;

    private JComboBox<HullStyle> styleSelector;

    private JComboBox<HullSize> sizeSelector;

    private JTextField hullIDEditor;

    /**
     * Is needed to prevent recursive refresh calls from action listeners.
     */
    private boolean readyForInput;
    private JTextField hullNameEditor;

    private static final int RIGHT_PAD = 2;

    @SuppressWarnings("ThisEscapedInObjectConstruction")
    HullDataControlPanel() {
        this.setLayout(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(this,
                new Insets(1, 0, 0, 0), "Hull data");

        addHullNamePanel();
        addHullIDPanel();

        addSizeSelector();
        addStyleSelector();
        addCoversColorChooser();
        addSpriteNameLabel();

        clearData();
    }

    private void addHullNamePanel() {
        JLabel label = new JLabel(StringManager.getString("HULL_NAME"));

        hullNameEditor = new JTextField();
        hullNameEditor.setToolTipText(StringManager.getString("ENTER_TO_SAVE_CHANGES"));
        hullNameEditor.putClientProperty("JTextField.placeholderText", "Hull name...");
        hullNameEditor.setColumns(10);
        hullNameEditor.addActionListener(e -> {
            if (readyForInput) {
                String currentText = hullNameEditor.getText();

                ShipHull shipHull = cachedLayer.getHull();
                shipHull.setHullName(currentText);

                EventBus.publish(new LayerTabUpdated(cachedLayer));
                processChange();
            }
        });

        ComponentUtilities.addLabelAndComponent(this, label, hullNameEditor,
                3, RIGHT_PAD, 0, 1);
    }

    private void addHullIDPanel() {
        JLabel label = new JLabel(StringManager.getString("HULL_ID"));

        hullIDEditor = new JTextField();
        hullIDEditor.setToolTipText(StringManager.getString("ENTER_TO_SAVE_CHANGES"));
        hullIDEditor.putClientProperty("JTextField.placeholderText", "Hull ID...");
        hullIDEditor.setColumns(10);
        hullIDEditor.addActionListener(e -> {
            if (readyForInput) {
                String currentText = hullIDEditor.getText();

                ShipHull shipHull = cachedLayer.getHull();
                String oldID = shipHull.getHullID();
                shipHull.setHullID(currentText);

                if (!currentText.equals(oldID)) {
                    var shipEntry = GameDataRepository.retrieveShipCSVEntryByID(oldID);
                    if (shipEntry != null) {
                        EventBus.publish(new CSVEntryIDChanged(oldID, currentText, shipEntry));
                    }
                }

                processChange();
            }
        });

        ComponentUtilities.addLabelAndComponent(this, label, hullIDEditor,
                3, RIGHT_PAD, 0, 2);
    }

    private void addSizeSelector() {
        JLabel selectorLabel = new JLabel(StringManager.getString("HULL_SIZE_1"));
        ComboBoxModel<HullSize> sizeModel = new DefaultComboBoxModel<>(HullSize.values());
        sizeSelector  = new JComboBox<>(sizeModel);
        sizeSelector.setRenderer(new HullSizeListCellRenderer());

        sizeSelector.addActionListener(e -> {
            if (readyForInput) {
                HullSize selectedValue = (HullSize) sizeSelector.getSelectedItem();

                if (cachedLayer != null) {
                    ShipHull shipHull = cachedLayer.getHull();
                    shipHull.setHullSize(selectedValue);

                    processChange();
                }
            }
        });

        ComponentUtilities.addLabelAndComponent(this, selectorLabel, sizeSelector,
                3, RIGHT_PAD, 0, 3);
    }

    private void addStyleSelector() {
        JLabel selectorLabel = new JLabel(StringManager.getString("HULL_STYLE"));
        styleSelector  = new JComboBox<>();
        styleSelector.setRenderer(new HullStyleListCellRenderer());

        styleSelector.addActionListener(e -> {
            if (readyForInput) {
                HullStyle selectedValue = (HullStyle) styleSelector.getSelectedItem();

                if (cachedLayer != null) {
                    cachedLayer.setHullStyle(selectedValue);
                    processChange();
                }
            }
        });

        ComponentUtilities.addLabelAndComponent(this, selectorLabel, styleSelector,
                3, RIGHT_PAD, 0, 4);
    }

    private void addSpriteNameLabel() {
        spritePathValue = new JTextField("");
        spritePathValue.setColumns(10);
        spritePathValue.setEditable(false);
        spritePathValue.setBorder(new EmptyBorder(0, 0, 0, 0));
        spritePathValue.setOpaque(false);
        spritePathValue.setBackground(new java.awt.Color(0,0,0,0));
        spritePathLabel = new JLabel(StringManager.getString("SPRITE_PATH"));
        spritePathLabel.setBorder(new EmptyBorder(5, 0, 2, 0));

        spritePathLabel.setToolTipText(StringManager.getString("RIGHT_CLICK_TO_CHANGE_SPRITE"));

        JPopupMenu spriteChooserMenu = HullDataControlPanel.getSpriteChooserMenu();

        spritePathLabel.addMouseListener(new MouseoverLabelListener(spriteChooserMenu, spritePathLabel));

        Insets insets = ComponentUtilities.createLabelInsets();
        insets.top = 1;
        spritePathLabel.setBorder(ComponentUtilities.createLabelSimpleBorder(insets));

        ComponentUtilities.addLabelAndComponent(this, spritePathLabel,
                spritePathValue, 0, 4, 0, 5);

        spriteNameValue = new JTextField("");
        spriteNameValue.setColumns(10);
        spriteNameValue.setEditable(false);
        spriteNameValue.setBorder(new EmptyBorder(0, 0, 0, 0));
        spriteNameValue.setOpaque(false);
        spriteNameValue.setBackground(new java.awt.Color(0,0,0,0));
        JLabel spriteNameLabel = new JLabel(StringManager.getString("SPRITE_NAME"));
        spriteNameLabel.setBorder(new EmptyBorder(5, 0, 6, 0));

        ComponentUtilities.addLabelAndComponent(this, spriteNameLabel,
                spriteNameValue, 3, 4, 0, 6);
    }

    private static JPopupMenu getSpriteChooserMenu() {
        JPopupMenu spriteChooserMenu = new JPopupMenu();

        JMenuItem changeSprite = new JMenuItem(StringManager.getString("CHANGE_SPRITE"));
        changeSprite.addActionListener(event -> {
            var activeLayer = StaticController.getActiveLayer();
            if (activeLayer instanceof ShipLayer shipLayer) {
                OpenSpriteAction.openSpriteAndDo(sprite -> {
                    PrimaryViewer viewer = StaticController.getViewer();
                    viewer.loadSpriteToLayer(shipLayer, sprite);
                });
            }
        });
        spriteChooserMenu.add(changeSprite);

        spriteChooserMenu.addSeparator();

        JMenuItem openSourceFile = new JMenuItem(StringManager.getString("OPEN_SOURCE_FILE"));
        openSourceFile.addActionListener(e -> HullDataControlPanel.openSpritePath(OpenDataTarget.FILE));
        spriteChooserMenu.add(openSourceFile);
        JMenuItem openInExplorer = new JMenuItem(StringManager.getString("OPEN_CONTAINING_FOLDER"));
        openInExplorer.addActionListener(e -> HullDataControlPanel.openSpritePath(OpenDataTarget.CONTAINER));
        spriteChooserMenu.add(openInExplorer);

        return spriteChooserMenu;
    }

    private static void openSpritePath(OpenDataTarget target) {
        var activeLayer = StaticController.getActiveLayer();
        LayerPainter layerPainter = activeLayer.getPainter();
        Sprite sprite = layerPainter.getSprite();
        if (sprite == null) return;
        Path toOpen = null;
        switch (target) {
            case FILE -> toOpen = sprite.getPath();
            case CONTAINER -> toOpen = sprite.getPath().getParent();
            default -> {}
        }
        if (toOpen != null) {
            FileUtilities.openPathInDesktop(toOpen);
        }
    }

    private void addCoversColorChooser() {
        coversColorValue = new JLabel();
        coversColorLabel = new JLabel(StringManager.getString("COVERS_COLOR"));

        coversColorLabel.setToolTipText(StringManager.getString("RIGHT_CLICK_TO_CHANGE_COLOR"));
        JPopupMenu colorChooserMenu = HullDataControlPanel.getColorChooserMenu();
        coversColorLabel.addMouseListener(new MouseoverLabelListener(colorChooserMenu, coversColorLabel));

        Insets insets = ComponentUtilities.createLabelInsets();
        insets.top = 1;
        coversColorLabel.setBorder(ComponentUtilities.createLabelSimpleBorder(insets));

        ComponentUtilities.addLabelAndComponent(this, coversColorLabel,
                coversColorValue, 0, 2, 0, 7);
    }

    private void processChange() {
        this.refreshData(cachedLayer);
        EventScheduler repainter = StaticController.getScheduler();
        repainter.queueViewerRepaint();
        repainter.queueActiveLayerUpdate();
    }

    void clearData() {
        cachedLayer = null;

        readyForInput = false;

        spritePathValue.setText(StringManager.getString("NOT_INITIALIZED"));
        spritePathValue.setForeground(Themes.getDisabledTextColor());
        spritePathValue.setToolTipText(StringManager.getString("NOT_INITIALIZED"));

        spriteNameValue.setText(StringManager.getString("NOT_INITIALIZED"));
        spriteNameValue.setForeground(Themes.getDisabledTextColor());
        spriteNameValue.setToolTipText(StringManager.getString("NOT_INITIALIZED"));

        spritePathLabel.setEnabled(false);
        spritePathLabel.setToolTipText(null);
        spritePathLabel.setBackground(Themes.getDarkerBackgroundColor());

        coversColorLabel.setEnabled(false);
        coversColorLabel.setToolTipText(null);
        coversColorLabel.setBackground(Themes.getDarkerBackgroundColor());

        coversColorValue.setIcon(null);
        coversColorValue.setOpaque(false);
        coversColorValue.setBorder(new EmptyBorder(0, 2, 0, 2));
        coversColorValue.setBackground(null);
        coversColorValue.setToolTipText(null);
        coversColorValue.setForeground(Themes.getDisabledTextColor());
        coversColorValue.setText(StringManager.getString("NOT_INITIALIZED"));

        styleSelector.setSelectedItem(null);
        styleSelector.setEnabled(false);

        sizeSelector.setSelectedItem(null);
        sizeSelector.setEnabled(false);

        hullIDEditor.setText(StringManager.getString("NOT_INITIALIZED"));
        hullIDEditor.setEnabled(false);

        hullNameEditor.setText(StringManager.getString("NOT_INITIALIZED"));
        hullNameEditor.setEnabled(false);
    }

    void refreshData(ShipLayer layer) {
        cachedLayer = layer;

        readyForInput = false;

        ShipHull shipHull = layer.getHull();
        var coversColor = shipHull.getCoversColor();
        String notDefined = "Not defined";
        if (coversColor != null) {
            ImageIcon colorIcon = ComponentUtilities.createIconFromColor(coversColor, 10, 10);
            coversColorValue.setIcon(colorIcon);
            coversColorValue.setOpaque(true);
            coversColorValue.setBorder(new FlatLineBorder(new Insets(2, 2, 2, 2), Themes.getBorderColor()));
            coversColorValue.setBackground(Themes.getPanelHighlightColor());
            coversColorValue.setToolTipText(ColorUtilities.getColorBreakdown(coversColor));
            coversColorValue.setText(null);
        } else {
            coversColorValue.setText(notDefined);
        }
        coversColorValue.setForeground(Themes.getTextColor());
        SmartColorPaste.install(coversColorValue, color -> {
            var activeLayer = StaticController.getActiveLayer();
            if (activeLayer instanceof ShipLayer shipLayer) {
                ShipHull hull = shipLayer.getHull();
                if (hull != null) {
                    shipeditor.undo.EditDispatch.postHullCoversColorSet(activeLayer, hull.getCoversColor(), color);
                    hull.setCoversColor(color);
                    StaticController.reselectCurrentLayer();
                }
            }
        });

        spritePathLabel.setEnabled(true);
        spritePathLabel.setToolTipText(StringManager.getString("RIGHT_CLICK_TO_CHANGE_SPRITE"));
        spritePathLabel.setBackground(Themes.getPanelBackgroundColor());

        coversColorLabel.setEnabled(true);
        coversColorLabel.setToolTipText(StringManager.getString("RIGHT_CLICK_TO_CHANGE_COLOR"));
        coversColorLabel.setBackground(Themes.getPanelBackgroundColor());

        String relativeSpritePath = layer.getRelativeSpritePath();
        spritePathValue.setText(relativeSpritePath);
        spritePathValue.setForeground(Themes.getTextColor());
        spritePathValue.setToolTipText(relativeSpritePath);

        Sprite sprite = layer.getBaseHullSprite();
        String spriteName = sprite != null ? sprite.getFilename() : notDefined;
        spriteNameValue.setText(spriteName);
        spriteNameValue.setForeground(Themes.getTextColor());
        spriteNameValue.setToolTipText(spriteName);

        GameDataRepository gameData = SettingsManager.getGameData();
        Map<String, HullStyle> allHullStyles = gameData.getAllHullStyles();
        if (allHullStyles != null) {
            Collection<HullStyle> styleCollection = allHullStyles.values();
            HullStyle[] hullStyles = styleCollection.toArray(new HullStyle[0]);

            ComboBoxModel<HullStyle> styleModel = new DefaultComboBoxModel<>(hullStyles);

            styleSelector.setEnabled(true);
            styleSelector.setModel(styleModel);
            styleSelector.setSelectedItem(shipHull.getHullStyle());
        }

        sizeSelector.setEnabled(true);
        sizeSelector.setSelectedItem(shipHull.getHullSize());

        hullIDEditor.setEnabled(true);
        hullIDEditor.setText(shipHull.getHullID());

        hullNameEditor.setEnabled(true);
        hullNameEditor.setText(shipHull.getHullName());

        readyForInput = true;
    }

    @SuppressWarnings("ExtractMethodRecommender")
    private static JPopupMenu getColorChooserMenu() {
        JPopupMenu colorChooserMenu = new JPopupMenu();
        JMenuItem adjustColor = new JMenuItem(StringManager.getString("ADJUST_VALUE"));
        adjustColor.addActionListener(event -> {
            var activeLayer = StaticController.getActiveLayer();
            if (activeLayer instanceof ShipLayer shipLayer) {
                ShipHull shipHull = shipLayer.getHull();
                if (shipHull != null) {
                    Color chosen;
                    var current = shipHull.getCoversColor();
                    if (current != null) {
                        chosen = ColorUtilities.showColorChooser(current);
                    } else {
                        chosen = ColorUtilities.showColorChooser();
                    }
                    if (chosen != null) {
                        shipeditor.undo.EditDispatch.postHullCoversColorSet(activeLayer, current, chosen);
                        shipHull.setCoversColor(chosen);
                        StaticController.reselectCurrentLayer();
                    }
                } else {
                    HullDataControlPanel.abortColorInteraction();
                }
            } else {
                HullDataControlPanel.abortColorInteraction();
            }
        });
        colorChooserMenu.add(adjustColor);

        JMenuItem removeColor = new JMenuItem(StringManager.getString("CLEAR_VALUE"));
        removeColor.addActionListener(event -> {
            var activeLayer = StaticController.getActiveLayer();
            if (activeLayer instanceof ShipLayer shipLayer) {
                ShipHull shipHull = shipLayer.getHull();
                if (shipHull != null) {
                    var current = shipHull.getCoversColor();
                    shipeditor.undo.EditDispatch.postHullCoversColorSet(activeLayer, current, null);
                    shipHull.setCoversColor(null);
                    StaticController.reselectCurrentLayer();
                } else {
                    HullDataControlPanel.abortColorInteraction();
                }
            } else {
                HullDataControlPanel.abortColorInteraction();
            }
        });
        colorChooserMenu.add(removeColor);

        return colorChooserMenu;
    }

    private static void abortColorInteraction() {
        JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                StringManager.getString("CURRENT_LAYER_INVALID_COLOR_INTERACTION_MSG"),
                "Color interaction",
                JOptionPane.ERROR_MESSAGE);
    }

    private static class HullSizeListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            SizeEnum size = (SizeEnum) value;
            if (size != null) {
                setText(size.getDisplayedName());
            } else {
                setText(StringManager.getString("NOT_INITIALIZED"));
            }
            return this;
        }
    }

    private static class HullStyleListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            HullStyle style = (HullStyle) value;
            if (style != null) {
                setText(style.getHullStyleID());
            } else {
                setText(StringManager.getString("NOT_INITIALIZED"));
            }
            return this;
        }
    }

}
