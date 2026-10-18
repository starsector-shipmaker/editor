package shipeditor.components.instrument.ship.slots;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import shipeditor.components.instrument.ship.shared.AbstractSlotValuesPanel;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.utility.Utility; // force rebuild
import shipeditor.utility.overseers.StaticController;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class SlotDataControlPane extends AbstractSlotValuesPanel {

    private final WeaponSlotList slotList;

    private WeaponSlotPoint cachedSelected;

    SlotDataControlPane(WeaponSlotList weaponSlotList) {
        super(true);
        this.slotList = weaponSlotList;
    }

    public void refreshWithSelectedPoint(LayerPainter painter, WeaponSlotPoint selected) {
        this.cachedSelected = selected;
        this.refresh(painter);
    }

    @Override
    protected String getEntityName() {
        return "Slot";
    }

    @Override
    protected WeaponSlotPoint getSelectedFromLayer(LayerPainter layerPainter) {
        // Such a check is probably unnecessary, but we better err on the side of
        // safety...
        boolean layerContainsPoint = false;
        if (layerPainter instanceof ShipPainter shipPainter) {
            WeaponSlotPainter weaponSlotPainter = shipPainter.getWeaponSlotPainter();
            if (weaponSlotPainter != null) {
                List<WeaponSlotPoint> pointsIndex = weaponSlotPainter.getPointsIndex();
                layerContainsPoint = pointsIndex.contains(cachedSelected);
            }
        }
        if (cachedSelected != null && layerContainsPoint) {
            return cachedSelected;
        } else
            return Utility.getSelectedFromLayer(layerPainter);
    }

    @Override
    protected String getNextUniqueID() {
        var layer = StaticController.getActiveLayer();
        if (!(layer instanceof ShipLayer shipLayer))
            return null;
        var shipPainter = shipLayer.getPainter();
        if (shipPainter == null || shipPainter.isUninitialized())
            return null;

        var slotPainter = shipPainter.getWeaponSlotPainter();
        return slotPainter.generateUniqueSlotID();
    }

    @Override
    protected Consumer<String> getIDSetter() {
        return slotID -> actOnSelectedValues(
                (weaponSlotPainter, slotPoints) -> weaponSlotPainter.changeSlotsIDWithMirrorCheck(slotID, slotPoints));
    }

    @Override
    protected Consumer<WeaponType> getTypeSetter() {
        return type -> actOnSelectedValues(
                (weaponSlotPainter, slotPoints) -> weaponSlotPainter.changeSlotsTypeWithMirrorCheck(type, slotPoints));
    }

    @Override
    protected Consumer<WeaponMount> getMountSetter() {
        return mount -> this.actOnSelectedValues((weaponSlotPainter, weaponSlotPoints) -> weaponSlotPainter
                .changeSlotsMountWithMirrorCheck(mount, weaponSlotPoints));
    }

    @Override
    protected Consumer<WeaponSize> getSizeSetter() {
        return size -> this.actOnSelectedValues((weaponSlotPainter, weaponSlotPoints) -> weaponSlotPainter
                .changeSlotsSizeWithMirrorCheck(size, weaponSlotPoints));
    }

    @Override
    protected Consumer<Double> getAngleSetter() {
        return angle -> actOnSelectedFromLayer((weaponSlotPainter, selectedFromLayer) ->
                weaponSlotPainter.changePointAngleWithMirrorCheck(selectedFromLayer, angle));
    }

    @Override
    protected Consumer<Double> getArcSetter() {
        return arc -> actOnSelectedFromLayer((weaponSlotPainter, selectedFromLayer) ->
                weaponSlotPainter.changeArcWithMirrorCheck(selectedFromLayer, arc));
    }

    @Override
    protected Consumer<Double> getRenderOrderSetter() {
        return renderOrder -> actOnSelectedFromLayer((weaponSlotPainter, selectedFromLayer) -> {
            int renderOrderValue = renderOrder.intValue();
            weaponSlotPainter.changeRenderOrderWithMirrorCheck(selectedFromLayer, renderOrderValue);
        });
    }

    @Override
    protected void populateContent() {
        super.populateContent();

        JPanel builtInPanel = createBuiltInFeaturePanel();
        this.add(builtInPanel, BorderLayout.PAGE_END);
    }

    private JPanel createBuiltInFeaturePanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 4));
        shipeditor.utility.components.ComponentUtilities.outfitPanelWithTitle(panel, "Built-In Feature");

        JPanel content = new JPanel(new BorderLayout(6, 0));

        JLabel iconLabel = new JLabel();
        JLabel infoLabel = new JLabel(StringManager.getString("NONE"));
        infoLabel.setBorder(new javax.swing.border.EmptyBorder(0, 4, 0, 0));

        JPanel buttonPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 4, 0));
        JButton installButton = new JButton(StringManager.getString("INSTALL"));
        JButton removeButton = new JButton(StringManager.getString("REMOVE"));
        removeButton.setEnabled(false);

        installButton.addActionListener(e -> {
            if (cachedSelected == null)
                return;
            var layer = StaticController.getActiveLayer();
            if (layer instanceof ShipLayer shipLayer) {
                shipeditor.components.datafiles.entities.WeaponCSVEntry picked = shipeditor.utility.components.dialog.DialogUtilities
                        .showWeaponPickerDialog(cachedSelected);
                if (picked != null) {
                    shipLayer.getFeaturesOverseer().installBuiltIn(cachedSelected, picked);
                }
            }
        });

        removeButton.addActionListener(e -> {
            if (cachedSelected == null)
                return;
            var layer = StaticController.getActiveLayer();
            if (layer instanceof ShipLayer shipLayer) {
                shipLayer.getFeaturesOverseer().uninstallBuiltIn(cachedSelected);
            }
        });

        buttonPanel.add(installButton);
        buttonPanel.add(removeButton);

        registerWidgetListeners(installButton, layerPainter -> {
            installButton.setEnabled(false);
            removeButton.setEnabled(false);
            infoLabel.setText(StringManager.getString("NONE"));
            iconLabel.setIcon(null);
        }, layerPainter -> {
            if (cachedSelected != null) {
                installButton.setEnabled(true);
                var shipPainter = cachedSelected.getParent();
                String featureName = null;
                String featureId = null;
                shipeditor.utility.graphics.Sprite featureSprite = null;

                var activeSkin = shipPainter.getActiveSkin();
                if (activeSkin != null && !activeSkin.isBase()) {
                    var w = activeSkin.getBuiltInWeapons().get(cachedSelected.getId());
                    if (w != null) {
                        featureName = w.toString();
                        featureId = w.getWeaponID();
                        featureSprite = w.getWeaponImage();
                    }
                } else {
                    var f = shipPainter.getBuiltInWeapons().get(cachedSelected.getId());
                    if (f != null) {
                        featureName = f.getName();
                        featureId = f.getID();
                        if (f.getDataEntry() instanceof shipeditor.components.datafiles.entities.WeaponCSVEntry wEntry) {
                            featureSprite = wEntry.getWeaponImage();
                        }
                    }
                }

                if (featureName != null || featureId != null) {
                    String displayName = featureName != null ? featureName : featureId;
                    infoLabel.setText(StringManager.getString("HTML_B") + displayName + "</b> <span style='color:gray;'>(" + featureId + ")</span></html>");
                    installButton.setText(StringManager.getString("CHANGE"));
                    removeButton.setEnabled(true);

                    if (featureSprite != null && featureSprite.getImage() != null) {
                        java.awt.Image scaled = shipeditor.utility.components.ComponentUtilities.resizeImageToSquareLimit(featureSprite.getImage(), 22);
                        iconLabel.setIcon(new javax.swing.ImageIcon(scaled));
                    } else {
                        iconLabel.setIcon(null);
                    }
                } else {
                    infoLabel.setText(StringManager.getString("NONE_EMPTY_SLOT"));
                    installButton.setText(StringManager.getString("INSTALL"));
                    removeButton.setEnabled(false);
                    iconLabel.setIcon(null);
                }
            } else {
                installButton.setEnabled(false);
                removeButton.setEnabled(false);
                infoLabel.setText(StringManager.getString("NONE"));
                iconLabel.setIcon(null);
            }
        });

        JPanel infoContainer = new JPanel(new BorderLayout(4, 0));
        infoContainer.add(iconLabel, BorderLayout.LINE_START);
        infoContainer.add(infoLabel, BorderLayout.CENTER);

        content.add(infoContainer, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.LINE_END);
        panel.add(content, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Runs the action for the slot selected on the cached ship painter, if both painter and selection exist.
     */
    private void actOnSelectedFromLayer(BiConsumer<WeaponSlotPainter, WeaponSlotPoint> action) {
        ShipPainter slotParent = getCachedLayerPainter();
        if (slotParent == null) return;
        WeaponSlotPainter weaponSlotPainter = slotParent.getWeaponSlotPainter();
        if (weaponSlotPainter == null) return;
        WeaponSlotPoint selectedFromLayer = getSelectedFromLayer(slotParent);
        if (selectedFromLayer != null) {
            action.accept(weaponSlotPainter, selectedFromLayer);
        }
    }

    private void actOnSelectedValues(BiConsumer<WeaponSlotPainter, List<WeaponSlotPoint>> action) {
        WeaponSlotPoint selectedValue = slotList.getSelectedValue();
        if (selectedValue == null)
            return;
        ShipPainter parentLayer = selectedValue.getParent();
        WeaponSlotPainter slotPainter = parentLayer.getWeaponSlotPainter();
        List<WeaponSlotPoint> selectedValuesList = slotList.getSelectedValuesList();
        if (!selectedValuesList.isEmpty()) {
            action.accept(slotPainter, selectedValuesList);
        }
    }

}
