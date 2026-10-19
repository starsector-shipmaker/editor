package shipeditor.components.instrument.ship.variant;

import lombok.extern.log4j.Log4j2;
import shipeditor.utility.text.StringManager;

import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectedConfirmed;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.ComponentEnums.EditorInstrument;

import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.ViewerEnums.FireMode;
import shipeditor.components.viewer.painters.points.ship.features.FittedWeaponGroup;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.dialog.DialogUtilities;
import shipeditor.utility.components.rendering.CustomTreeNode;
import shipeditor.utility.overseers.StaticController;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ToolTipManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.communication.events.components.ComponentEvents.WeaponEntryPicked;
import shipeditor.utility.themes.Themes;

@Log4j2
public class VariantWeaponsPanel extends AbstractVariantPanel {

    private final VariantWeaponsTree weaponsTree;

    private final JPanel contentPanel;

    private final JPanel northPanel;

    private final EmptyVariantBanner emptyBanner;

    private JPanel pickedWeaponPanel;

    private JPanel selectedSlotPanel;
    private JLabel slotInfoLabel;
    private JLabel weaponInfoLabel;
    private JLabel weaponIconLabel;
    private JButton installButton;
    private JButton removeButton;
    private WeaponSlotPoint cachedSelectedSlot;

    public VariantWeaponsPanel() {
        this.setLayout(new BorderLayout());

        northPanel = new JPanel();
        northPanel.setLayout(new BorderLayout());

        emptyBanner = new EmptyVariantBanner();
        JPanel topStack = new JPanel(new BorderLayout());
        topStack.add(emptyBanner, BorderLayout.PAGE_START);
        topStack.add(northPanel, BorderLayout.CENTER);
        this.add(topStack, BorderLayout.PAGE_START);

        contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());
        this.add(contentPanel, BorderLayout.CENTER);

        CustomTreeNode weaponGroups = new CustomTreeNode("Weapon Groups");
        weaponsTree = new VariantWeaponsTree(weaponGroups, feature -> {});
        ToolTipManager.sharedInstance().registerComponent(weaponsTree);

        JScrollPane scroller = new JScrollPane(weaponsTree);
        contentPanel.add(scroller, BorderLayout.CENTER);

        selectedSlotPanel = createSelectedSlotPanel();
        contentPanel.add(selectedSlotPanel, BorderLayout.PAGE_END);

        ViewerLayer layer = StaticController.getActiveLayer();
        this.refreshPanel(layer);
    }

    private JPanel createSelectedSlotPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        ComponentUtilities.outfitPanelWithTitle(panel, "Selected Weapon Slot");

        JPanel topRow = new JPanel(new BorderLayout(6, 0));
        slotInfoLabel = new JLabel(StringManager.getString("NO_SLOT_SELECTED"));
        slotInfoLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
        topRow.add(slotInfoLabel, BorderLayout.CENTER);

        JPanel middleRow = new JPanel(new BorderLayout(6, 0));
        weaponIconLabel = new JLabel();
        weaponInfoLabel = new JLabel();
        weaponInfoLabel.setBorder(new EmptyBorder(0, 4, 0, 0));
        middleRow.add(weaponIconLabel, BorderLayout.LINE_START);
        middleRow.add(weaponInfoLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 2, 0));
        installButton = new JButton(StringManager.getString("INSTALL"),
                FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 14, Themes.getIconColor()));
        installButton.setMargin(new Insets(1, 5, 1, 5));
        removeButton = new JButton(
                FontIcon.of(BoxiconsRegular.TRASH, 14, Themes.getIconColor()));
        removeButton.setMargin(new Insets(1, 5, 1, 5));
        removeButton.setToolTipText(StringManager.getString("REMOVE"));
        installButton.setEnabled(false);
        removeButton.setEnabled(false);

        installButton.addActionListener(e -> {
            if (cachedSelectedSlot == null) return;
            var layer = StaticController.getActiveLayer();
            if (layer instanceof ShipLayer shipLayer) {
                WeaponCSVEntry picked = DialogUtilities.showWeaponPickerDialog(cachedSelectedSlot);
                if (picked != null) {
                    shipLayer.getFeaturesOverseer().installWeapon(cachedSelectedSlot, picked);
                    refreshSlotPanel();
                }
            }
        });

        removeButton.addActionListener(e -> {
            if (cachedSelectedSlot == null) return;
            var layer = StaticController.getActiveLayer();
            if (layer instanceof ShipLayer shipLayer) {
                shipLayer.getFeaturesOverseer().uninstallWeapon(cachedSelectedSlot);
                refreshSlotPanel();
            }
        });

        buttonPanel.add(installButton);
        buttonPanel.add(removeButton);
        middleRow.add(buttonPanel, BorderLayout.LINE_END);

        JPanel centerContainer = new JPanel(new BorderLayout(0, 2));
        centerContainer.add(topRow, BorderLayout.PAGE_START);
        centerContainer.add(middleRow, BorderLayout.CENTER);

        panel.add(centerContainer, BorderLayout.CENTER);

        return panel;
    }

    private void refreshSlotPanel() {
        if (slotInfoLabel == null) return;
        if (cachedSelectedSlot != null) {
            String slotDesc = cachedSelectedSlot.getId() + " ["
                    + cachedSelectedSlot.getWeaponSize().getDisplayedName() + " "
                    + cachedSelectedSlot.getWeaponType().getDisplayedName() + " - "
                    + cachedSelectedSlot.getWeaponMount().getDisplayName() + "]";
            slotInfoLabel.setText(StringManager.getString("HTML_B") + slotDesc + "</b></html>");

            ShipVariant activeVariant = null;
            if (StaticController.getActiveLayer() instanceof ShipLayer shipLayer) {
                activeVariant = shipLayer.getPainter().getActiveVariant();
            }

            InstalledFeature installed = (activeVariant != null) ? activeVariant.getFittedWeaponBySlot(cachedSelectedSlot.getId()) : null;
            if (installed != null) {
                weaponInfoLabel.setText(StringManager.getString("HTML_B") + installed.getName() + "</b> <span style='color:gray;'>("
                        + installed.getFeatureID() + ", OP: " + installed.getOPCost() + ")</span></html>");
                installButton.setText(StringManager.getString("CHANGE"));
                installButton.setIcon(FontIcon.of(BoxiconsRegular.EDIT, 16, Themes.getIconColor()));
                boolean isBuiltIn = installed.isContainedInBuiltIns();
                installButton.setEnabled(!isBuiltIn);
                removeButton.setEnabled(!isBuiltIn);

                if (installed.getDataEntry() instanceof WeaponCSVEntry wEntry && wEntry.getWeaponImage() != null) {
                    java.awt.Image scaled = ComponentUtilities.resizeImageToSquareLimit(wEntry.getWeaponImage().getImage(), 22);
                    weaponIconLabel.setIcon(new javax.swing.ImageIcon(scaled));
                } else {
                    weaponIconLabel.setIcon(null);
                }
            } else {
                weaponInfoLabel.setText(StringManager.getString("HTML_SPAN_STYLE_COLOR_GRAY_EMPTY_SLOT_NO_WEAPON_INSTALLED_SPAN_HTML"));
                installButton.setText(StringManager.getString("INSTALL"));
                installButton.setIcon(FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 16, Themes.getIconColor()));
                installButton.setEnabled(cachedSelectedSlot.isFittable());
                removeButton.setEnabled(false);
                weaponIconLabel.setIcon(null);
            }
        } else {
            slotInfoLabel.setText(StringManager.getString("HTML_SPAN_STYLE_COLOR_GRAY_NO_SLOT_SELECTED_CLICK_A_SLOT_TO_QUERY_FIT_WEAPONS_SPAN_HTML"));
            weaponInfoLabel.setText("");
            weaponIconLabel.setIcon(null);
            installButton.setEnabled(false);
            removeButton.setEnabled(false);
        }
        if (selectedSlotPanel != null) {
            selectedSlotPanel.revalidate();
            selectedSlotPanel.repaint();
        }
    }

    @Override
    protected void initLayerListeners() {
        super.initLayerListeners();
        EventBus.subscribe(this, event -> {
            if (event instanceof PointSelectedConfirmed checked) {
                if (checked.point() instanceof WeaponSlotPoint slotPoint) {
                    this.cachedSelectedSlot = slotPoint;
                } else if (checked.point() == null) {
                    this.cachedSelectedSlot = null;
                }
                if (weaponsTree != null) {
                    weaponsTree.selectNode(checked.point());
                }
                this.refreshSlotPanel();
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.VARIANT_WEAPONS) {
                    this.refreshPanel(StaticController.getActiveLayer());
                    this.refreshWeaponPicker();
                    this.refreshSlotPanel();
                }
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof WeaponEntryPicked) {
                this.refreshWeaponPicker();
            }
        });
    }

    private void refreshWeaponPicker() {
        if (pickedWeaponPanel != null) {
            contentPanel.remove(pickedWeaponPanel);
        }

        WeaponCSVEntry pickedForInstall = null;
        if (StaticController.getActiveLayer() instanceof ShipLayer shipLayer) {
            pickedForInstall = shipLayer.getFeaturesOverseer().getWeaponForInstall();
        }
        if (pickedForInstall != null) {
            pickedWeaponPanel = pickedForInstall.createPickedWeaponPanel();
        } else {
            String weaponHint = StringManager.getString("USE_RIGHT_CLICK_CONTEXT_MENU_OF_GAME_DATA_WIDGET_TO_ADD_ENTRIES");
            pickedWeaponPanel = ComponentUtilities.createHintPanel(weaponHint, "Info:");
            Insets insets = new Insets(1, 0, 0, 0);
            ComponentUtilities.outfitPanelWithTitle(pickedWeaponPanel, insets, StringManager.getString("PICKED_WEAPON"));
        }
        contentPanel.add(pickedWeaponPanel, BorderLayout.PAGE_START);

        this.revalidate();
        this.repaint();
    }

    @Override
    public void refreshPanel(ViewerLayer selected) {
        weaponsTree.clearRoot();
        northPanel.removeAll();
        emptyBanner.refresh(selected);

        if (!(selected instanceof ShipLayer checkedLayer)) {
            return;
        }

        ShipHull shipHull = checkedLayer.getHull();
        if (shipHull == null) {
            return;
        }
        ShipPainter painter = checkedLayer.getPainter();
        if (painter.getWeaponSlotPainter() != null) {
            this.cachedSelectedSlot = painter.getWeaponSlotPainter().getSelected();
        }

        ShipVariant activeVariant = painter.getActiveVariant();
        if (activeVariant != null) {
            weaponsTree.setSlotPainter(painter.getWeaponSlotPainter());
            weaponsTree.repopulateTree(activeVariant, checkedLayer);
            this.weaponsTree.selectNode(this.cachedSelectedSlot);

            JPanel buttonContainer = new JPanel(new GridLayout(1, 3, 4, 0));
            buttonContainer.setBorder(new EmptyBorder(4, 4, 2, 4));

            JButton rearrangeGroups = new JButton("Rearrange",
                    FontIcon.of(BoxiconsRegular.GRID_ALT, 15, Themes.getIconColor()));
            rearrangeGroups.setMargin(new Insets(2, 4, 2, 4));
            rearrangeGroups.setToolTipText(StringManager.getString("REARRANGE_WEAPONS"));

            List<InstalledFeature> allFittedWeaponsList = activeVariant.getAllFittedWeaponsList();
            if (allFittedWeaponsList.isEmpty()) {
                rearrangeGroups.setEnabled(false);
            }

            rearrangeGroups.addActionListener(e -> DialogUtilities.showWeaponGroupsDialog(activeVariant));

            JButton addGroupButton = new JButton("Add Group",
                    FontIcon.of(BoxiconsRegular.LIST_PLUS, 15, Themes.getIconColor()));
            addGroupButton.setMargin(new Insets(2, 4, 2, 4));
            addGroupButton.setEnabled(activeVariant.isEmpty() || activeVariant.getWeaponGroups().size() < 7);
            addGroupButton.addActionListener(e -> {
                ShipVariant targetVariant = activeVariant;
                if (targetVariant.isEmpty()) {
                    log.debug("Add Group on empty variant: auto-creating editable variant for '{}'", checkedLayer.getShipID());
                    targetVariant = StaticController.getOrCreateEditableVariant(checkedLayer);
                    if (targetVariant == null) {
                        return;
                    }
                }
                if (targetVariant.getWeaponGroups().size() < 7) {
                    FittedWeaponGroup newGroup = new FittedWeaponGroup(targetVariant, false, FireMode.LINKED);
                    targetVariant.getWeaponGroups().add(newGroup);
                    if (StaticController.getLayerManager() != null) {
                        StaticController.getLayerManager().markUnsaved(checkedLayer, "variant");
                    }
                    this.refreshPanel(checkedLayer);
                }
            });

            JButton clearAllButton = new JButton("Clear All",
                    FontIcon.of(BoxiconsRegular.TRASH, 15, Themes.getIconColor()));
            clearAllButton.setMargin(new Insets(2, 4, 2, 4));
            clearAllButton.setEnabled(!allFittedWeaponsList.isEmpty());
            clearAllButton.addActionListener(e -> {
                for (FittedWeaponGroup group : activeVariant.getWeaponGroups()) {
                    Map<String, InstalledFeature> weaponsCopy = new LinkedHashMap<>(group.getWeapons());
                    for (Map.Entry<String, InstalledFeature> entry : weaponsCopy.entrySet()) {
                        InstalledFeature feature = entry.getValue();
                        if (feature != null && !feature.isContainedInBuiltIns()) {
                            EditDispatch.postFeatureUninstalled(group.getWeapons(), entry.getKey(), feature, null);
                        }
                    }
                }
                this.refreshPanel(checkedLayer);
            });

            buttonContainer.add(addGroupButton);
            buttonContainer.add(rearrangeGroups);
            buttonContainer.add(clearAllButton);

            northPanel.add(buttonContainer, BorderLayout.PAGE_START);

            northPanel.add(VariantWeaponsPanel.createDataSummary(checkedLayer, activeVariant),
                    BorderLayout.CENTER);
        }
        this.refreshSlotPanel();
        this.revalidate();
        this.repaint();
    }

    private static JPanel createDataSummary(ShipLayer shipLayer, ShipVariant activeVariant) {
        JPanel container = new JPanel();
        ComponentUtilities.outfitPanelWithTitle(container, "Fitted weapons");
        container.setLayout(new GridBagLayout());

        JLabel shipOPCapLabel = new JLabel(StringManager.getString("TOTAL_OP_CAPACITY"));
        int shipOPTotalValue = shipLayer.getTotalOP();
        JLabel shipOPCap = new JLabel(String.valueOf(shipOPTotalValue));

        ComponentUtilities.addLabelAndComponent(container, shipOPCapLabel, shipOPCap, 0);

        JLabel usedOPTotalLabel = new JLabel(StringManager.getString("USED_OP_FOR_SHIP"));
        int usedOP = shipLayer.getTotalUsedOP();
        JLabel usedOPTotal = new JLabel(String.valueOf(usedOP));

        ComponentUtilities.addLabelAndComponent(container, usedOPTotalLabel, usedOPTotal, 1);

        JLabel totalOPLabel = new JLabel(StringManager.getString("TOTAL_OP_IN_WEAPONS"));
        int totalOPInWeapons = activeVariant.getTotalOPInWeapons();
        JLabel value = new JLabel(String.valueOf(totalOPInWeapons));

        ComponentUtilities.addLabelAndComponent(container, totalOPLabel, value, 2);

        return container;
    }

}
