package shipeditor.components.instrument.ship.slots;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import shipeditor.communication.EventBus;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.instrument.ship.AbstractShipPropertiesPanel;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.painters.points.ship.WeaponSlotPainter;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.objects.Pair;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.utility.components.dialog.DialogUtilities;
import shipeditor.utility.themes.Themes;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;
import shipeditor.communication.events.viewer.points.PointEvents.PointAddConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.WeaponSlotInsertedConfirmed;
import shipeditor.communication.events.viewer.points.PointEvents.PointRemovedConfirmed;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class WeaponSlotListPanel extends AbstractShipPropertiesPanel {

    @Getter
    private WeaponSlotList slotPointContainer;

    private SlotDataControlPane slotDataPane;

    private JCheckBox reorderCheckbox;

    private DefaultListModel<WeaponSlotPoint> model;

    WeaponSlotListPanel() {
        this.initPointListener();
    }

    private void refreshPointDataPane(WeaponSlotPoint slotPoint) {
        ShipPainter painter = (ShipPainter) getCachedLayerPainter();
        if (slotPoint != null) {
            painter = slotPoint.getParent();
            this.slotDataPane.refreshWithSelectedPoint(painter, slotPoint);
        } else {
            this.slotDataPane.refresh(painter);
        }
    }

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        DefaultListModel<WeaponSlotPoint> newModel = new DefaultListModel<>();

        if (!(layerPainter instanceof ShipPainter shipPainter) || shipPainter.isUninitialized()) {
            this.model = newModel;
            this.slotPointContainer.setModel(newModel);

            fireClearingListeners(layerPainter);
            refreshPointDataPane(null);

            this.slotPointContainer.setEnabled(false);
            this.reorderCheckbox.setEnabled(false);
            return;
        }

        WeaponSlotPainter weaponSlotPainter = shipPainter.getWeaponSlotPainter();
        newModel.addAll(weaponSlotPainter.getPointsIndex());

        this.model = newModel;
        this.slotPointContainer.setModel(newModel);
        this.slotPointContainer.setEnabled(true);
        this.reorderCheckbox.setEnabled(true);

        fireRefresherListeners(layerPainter);
        refreshPointDataPane(weaponSlotPainter.getSelected());
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BorderLayout());

        this.model = new DefaultListModel<>();
        this.slotPointContainer = new WeaponSlotList(model, this::refreshPointDataPane);
        this.slotDataPane = new SlotDataControlPane(slotPointContainer);

        JPanel northContainer = new JPanel(new BorderLayout());
        JPanel visibilityWidgetContainer = createShipVisibilityPanel(ShipPainter::getWeaponSlotPainter, "SLOTS_VIEW");
        northContainer.add(visibilityWidgetContainer, BorderLayout.PAGE_START);

        ComponentUtilities.outfitPanelWithTitle(slotDataPane, "Slot Data");
        northContainer.add(slotDataPane, BorderLayout.CENTER);

        this.refreshPointDataPane(null);

        JScrollPane scrollableContainer = new JScrollPane(slotPointContainer);

        Pair<JPanel, JCheckBox> reorderWidget = ComponentUtilities.createReorderCheckboxPanel(slotPointContainer);
        reorderCheckbox = reorderWidget.getSecond();

        JButton addSlotDefaultsBtn = new JButton(StringManager.getString("NEW_SLOT_DEFAULTS"));
        addSlotDefaultsBtn.setIcon(FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 14, Themes.getIconColor()));
        addSlotDefaultsBtn.setToolTipText(StringManager.getString("CONFIGURE_DEFAULT_PROPERTIES_AND_MODE_FOR_NEWLY_CREATED_WEAPON_SLOTS"));
        addSlotDefaultsBtn.addActionListener(e -> DialogUtilities.showSlotCreationDialog());
        reorderWidget.getFirst().add(addSlotDefaultsBtn);
        reorderWidget.getFirst().add(Box.createRigidArea(new Dimension(6, 0)));

        northContainer.add(reorderWidget.getFirst(), BorderLayout.PAGE_END);

        this.add(northContainer, BorderLayout.PAGE_START);

        this.add(scrollableContainer, BorderLayout.CENTER);
    }

    @Override
    protected void initLayerListeners() {
        super.initLayerListeners();
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() != EditorInstrument.WEAPON_SLOTS) {
                    return;
                }
                WeaponSlotPainter cachedSlotPainter = getCachedSlotPainter();
                if (cachedSlotPainter != null) {
                    this.model = this.slotPointContainer.synchronizeModel(this.model,
                            cachedSlotPainter.getPointsIndex());
                }

                this.refreshPointDataPane(null);
            }
        });
    }

    private void initPointListener() {
        EventBus.subscribe(this, event -> {
            if (event instanceof PointAddConfirmed checked && checked.point() instanceof WeaponSlotPoint point) {
                model.addElement(point);
                slotPointContainer.setSelectedIndex(model.indexOf(point));
                this.refreshPointDataPane(null);
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof WeaponSlotInsertedConfirmed checked) {
                model.insertElementAt(checked.toInsert(), checked.precedingIndex());
                slotPointContainer.setSelectedIndex(model.indexOf(checked.toInsert()));
                this.refreshPointDataPane(null);
            }
        });
        EventBus.subscribe(this, event -> {
            if (event instanceof PointRemovedConfirmed checked && checked.point() instanceof WeaponSlotPoint point) {
                model.removeElement(point);
                this.refreshPointDataPane(null);
            }
        });
    }

    private WeaponSlotPainter getCachedSlotPainter() {
        LayerPainter cachedLayerPainter = getCachedLayerPainter();
        if (cachedLayerPainter instanceof ShipPainter shipPainter && !shipPainter.isUninitialized()) {
            return shipPainter.getWeaponSlotPainter();
        }
        return null;
    }

}
