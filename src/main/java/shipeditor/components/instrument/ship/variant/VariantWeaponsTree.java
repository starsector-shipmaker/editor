package shipeditor.components.instrument.ship.variant;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.map.ListOrderedMap;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.viewer.ViewerRepaintQueued;
import shipeditor.components.datafiles.entities.CSVEntry;
import shipeditor.components.viewer.entities.WorldPoint;
import shipeditor.components.viewer.entities.weapon.SlotData;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.components.viewer.ViewerEnums.FireMode;
import shipeditor.components.viewer.painters.points.ship.features.FittedWeaponGroup;
import shipeditor.components.viewer.painters.points.ship.features.InstalledFeature;
import shipeditor.undo.EditDispatch;
import shipeditor.utility.Utility;
import shipeditor.utility.components.containers.trees.DynamicWidthTree;
import shipeditor.utility.components.dialog.DialogUtilities;
import shipeditor.utility.components.rendering.CustomTreeNode;
import shipeditor.utility.overseers.StaticController;

import javax.swing.tree.*;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import shipeditor.communication.events.viewer.points.PointEvents.PointSelectQueued;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class VariantWeaponsTree extends DynamicWidthTree {

    @Getter
    private final CustomTreeNode rootNode;

    @Setter
    @Getter
    private CustomTreeNode cachedSelectedNode;

    @Getter @Setter
    private WeaponSlotPainter slotPainter;

    private DefaultTreeModel model;

    private final Consumer<InstalledFeature> selectionAction;

    VariantWeaponsTree(CustomTreeNode root, Consumer<InstalledFeature> selector) {
        super(root);
        this.selectionAction = selector;
        this.rootNode = root;
        this.model = new DefaultTreeModel(rootNode);
        this.setModel(model);
        this.setCellRenderer(new WeaponsTreeCellRenderer(this, this));
        this.initListeners();
    }

    private WeaponSlotPoint getSlotPoint(InstalledFeature installed) {
        return slotPainter.getSlotByID(installed.getSlotID());
    }

    private void actOnNodeByPoint(SlotData point, Consumer<CustomTreeNode> action) {
        Enumeration<TreeNode> allNodes = this.rootNode.depthFirstEnumeration();

        while (allNodes.hasMoreElements()) {
            CustomTreeNode node = (CustomTreeNode) allNodes.nextElement();
            Object nodeObject = node.getUserObject();
            if (nodeObject instanceof InstalledFeature feature) {
                if (Objects.equals(feature.getSlotID(), point.getId())) {
                    action.accept(node);
                    break;
                }
            } else if (nodeObject instanceof WeaponSlotPoint slotPoint) {
                if (Objects.equals(slotPoint.getId(), point.getId())) {
                    action.accept(node);
                    break;
                }
            }
        }
    }

    private void initListeners() {
        this.addTreeSelectionListener(e -> {
            CustomTreeNode node = (CustomTreeNode) this.getLastSelectedPathComponent();
            if (node == null) return;
            Object nodeObject = node.getUserObject();
            if (nodeObject instanceof InstalledFeature checked) {
                var slot = this.getSlotPoint(checked);
                if (slot != null && !slot.isPointSelected()) {
                    EventBus.publish(new PointSelectQueued(slot));
                    EventBus.publish(new ViewerRepaintQueued());
                }
                selectionAction.accept(checked);
            } else if (nodeObject instanceof WeaponSlotPoint emptySlot) {
                if (!emptySlot.isPointSelected()) {
                    EventBus.publish(new PointSelectQueued(emptySlot));
                    EventBus.publish(new ViewerRepaintQueued());
                }
            }
        });
        this.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    TreePath path = getPathForLocation(e.getX(), e.getY());
                    if (path != null && path.getLastPathComponent() instanceof CustomTreeNode node) {
                        Object obj = node.getUserObject();
                        WeaponSlotPoint slot = null;
                        if (obj instanceof InstalledFeature feature && !feature.isContainedInBuiltIns()) {
                            slot = getSlotPoint(feature);
                        } else if (obj instanceof WeaponSlotPoint slotPoint && slotPoint.isFittable()) {
                            slot = slotPoint;
                        }
                        if (slot != null && slot.isFittable()) {
                            EventBus.publish(new PointSelectQueued(slot));
                            var layer = StaticController.getActiveLayer();
                            if (layer instanceof ShipLayer shipLayer) {
                                shipeditor.components.datafiles.entities.WeaponCSVEntry picked =
                                        DialogUtilities.showWeaponPickerDialog(slot);
                                if (picked != null) {
                                    shipLayer.getFeaturesOverseer().installWeapon(slot, picked);
                                }
                            }
                        }
                    }
                }
            }
        });
        this.addMouseListener(new WeaponTreeContextMenuController(this));
    }

    void selectNode(WorldPoint point) {
        this.clearSelection();
        if (point instanceof WeaponSlotPoint slotPoint) {
            actOnNodeByPoint(slotPoint, node -> {
                this.expandTree();
                TreePath path = new TreePath(node.getPath());
                this.setSelectionPath(path);
                this.scrollPathToVisible(path);
            });
        }
    }

    private void addWeaponGroup(FittedWeaponGroup group) {
        MutableTreeNode newChild = new CustomTreeNode(group);
        model.insertNodeInto(newChild, rootNode, rootNode.getChildCount());
    }

    void insertWeaponGroup(FittedWeaponGroup group, int index) {
        MutableTreeNode newChild = new CustomTreeNode(group);
        model.insertNodeInto(newChild, rootNode, index);
        model.reload();
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    public void removeWeaponGroup(FittedWeaponGroup group) {
        Enumeration<TreeNode> groups = rootNode.children();
        while (groups.hasMoreElements()) {
            CustomTreeNode groupNode = (CustomTreeNode) groups.nextElement();
            FittedWeaponGroup groupObject = (FittedWeaponGroup) groupNode.getUserObject();
            if (groupObject == group) {
                // Bulk uninstalls are all undoable actions.
                group.uninstallAll();
                ShipVariant groupParent = group.getParent();
                groupParent.removeWeaponGroup(group);
                model.reload();
            }
        }
    }

    private void addWeaponInstall(InstalledFeature feature, FittedWeaponGroup target) {
        Enumeration<TreeNode> groups = rootNode.children();
        while (groups.hasMoreElements()) {
            CustomTreeNode groupNode = (CustomTreeNode) groups.nextElement();
            FittedWeaponGroup groupObject = (FittedWeaponGroup) groupNode.getUserObject();
            if (groupObject == target) {
                MutableTreeNode portNode = new CustomTreeNode(feature);

                model.insertNodeInto(portNode, groupNode, groupNode.getChildCount());
                model.reload();
            }
        }
    }

    void removeWeaponInstall(SlotData point) {
        actOnNodeByPoint(point, node -> {
            model.removeNodeFromParent(node);
            model.reload();
        });
        this.expandTree();
    }

    void clearRoot() {
        rootNode.removeAllChildren();
        this.model = new DefaultTreeModel(rootNode);
        this.setModel(model);
        this.reloadModel();
    }

    private void reloadModel() {
        this.model.reload();
        this.expandTree();
        this.repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (getRowForLocation(event.getX(), event.getY()) == -1)
            return null;
        TreePath currentPath = getPathForLocation(event.getX(), event.getY());
        if (currentPath != null) {
            Object node = currentPath.getLastPathComponent();
            if (node instanceof CustomTreeNode customTreeNode) {
                Object data = customTreeNode.getUserObject();
                if (data instanceof InstalledFeature feature) {
                    CSVEntry dataEntry = feature.getDataEntry();
                    return dataEntry.getMultilineTooltip();
                } else {
                    String id = customTreeNode.getFirstLineTip();
                    String tip = customTreeNode.getSecondLineTip();
                    return Utility.getWithLinebreaks(id, tip);
                }

            }
        }
        return null;
    }

    void repopulateTree(ShipVariant variant, ShipLayer layer) {
        ShipPainter shipPainter = layer.getPainter();
        variant.ensureBuiltInsSync(shipPainter);

        final var weaponGroups = variant.getWeaponGroups();
        java.util.Set<String> fittedSlotIDs = new java.util.HashSet<>();
        for (FittedWeaponGroup group : weaponGroups) {
            this.addWeaponGroup(group);
            final ListOrderedMap<String, InstalledFeature> weapons = group.getWeapons();
            for (InstalledFeature feature : weapons.valueList()) {
                this.addWeaponInstall(feature, group);
                fittedSlotIDs.add(feature.getSlotID());
            }
        }

        // Query all weapon slots from slotPainter to include unassigned / empty slots
        if (slotPainter != null) {
            List<WeaponSlotPoint> allSlots = slotPainter.getSlotPoints();
            List<WeaponSlotPoint> unassignedSlots = new java.util.ArrayList<>();
            for (WeaponSlotPoint slot : allSlots) {
                if (slot.isFittable() && !fittedSlotIDs.contains(slot.getId())) {
                    unassignedSlots.add(slot);
                }
            }

            if (!unassignedSlots.isEmpty()) {
                CustomTreeNode unassignedRoot = new CustomTreeNode("Unassigned Slots (" + unassignedSlots.size() + ")");
                model.insertNodeInto(unassignedRoot, rootNode, rootNode.getChildCount());
                for (WeaponSlotPoint emptySlot : unassignedSlots) {
                    CustomTreeNode slotNode = new CustomTreeNode(emptySlot);
                    model.insertNodeInto(slotNode, unassignedRoot, unassignedRoot.getChildCount());
                }
            }
        }

        this.reloadModel();
    }

    @Override
    public MutableTreeNode handleAdditionToRoot(MutableTreeNode dragged) {
        if (dragged instanceof DefaultMutableTreeNode treeNode &&
                treeNode.getUserObject() instanceof InstalledFeature) {
            ShipLayer layer = (ShipLayer) StaticController.getActiveLayer();
            var shipPainter = layer.getPainter();
            var variant = shipPainter.getActiveVariant();
            List<FittedWeaponGroup> weaponGroups = variant.getWeaponGroups();
            if (weaponGroups.size() >= 7) {
                return null;
            }

            FittedWeaponGroup weaponGroup = new FittedWeaponGroup(variant, false, FireMode.LINKED);

            weaponGroups.add(weaponGroup);

            MutableTreeNode groupNode = new CustomTreeNode(weaponGroup);
            model.insertNodeInto(groupNode, rootNode, rootNode.getChildCount());
            return groupNode;
        }
        return null;
    }

    @Override
    public boolean isNodeDragValid(DefaultMutableTreeNode dragged) {
        return dragged.getUserObject() instanceof InstalledFeature;
    }

    @SuppressWarnings("ChainOfInstanceofChecks")
    @Override
    public void sortTreeModel(DefaultMutableTreeNode dragged, DefaultMutableTreeNode target, int targetIndex) {
        FittedWeaponGroup targetGroup = null;
        if (target.getUserObject() instanceof FittedWeaponGroup checkedGroup) {
            targetGroup = checkedGroup;
        } else if (target.getUserObject() instanceof InstalledFeature checkedFeature) {
            targetGroup = checkedFeature.getParentGroup();
        }
        if (targetGroup == null) {
            throw new IllegalStateException("Confirmed feature drop with illegal destination!");
        }
        InstalledFeature feature = (InstalledFeature) dragged.getUserObject();

        EditDispatch.postWeaponGroupsRearranged(feature, targetGroup, targetIndex);

        this.reloadModel();
    }

    public void moveWeaponToGroup(InstalledFeature feature, FittedWeaponGroup targetGroup) {
        if (feature == null || targetGroup == null || feature.getParentGroup() == targetGroup) {
            return;
        }
        EditDispatch.postWeaponGroupsRearranged(feature, targetGroup, targetGroup.getWeapons().size());
        this.reloadModel();
    }

    public void moveWeaponToNewGroup(InstalledFeature feature) {
        if (feature == null) {
            return;
        }
        if (!(StaticController.getActiveLayer() instanceof ShipLayer layer)) {
            return;
        }
        ShipPainter shipPainter = layer.getPainter();
        if (shipPainter == null) {
            return;
        }
        ShipVariant variant = shipPainter.getActiveVariant();
        if (variant == null || variant.isEmpty()) {
            return;
        }
        List<FittedWeaponGroup> weaponGroups = variant.getWeaponGroups();
        if (weaponGroups.size() >= 7) {
            return;
        }
        FittedWeaponGroup newGroup = new FittedWeaponGroup(variant, false, FireMode.LINKED);
        weaponGroups.add(newGroup);
        moveWeaponToGroup(feature, newGroup);
    }

}
