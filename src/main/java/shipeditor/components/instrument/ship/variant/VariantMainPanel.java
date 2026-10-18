package shipeditor.components.instrument.ship.variant;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.components.ComponentEnums.EditorInstrument;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.utility.overseers.StaticController;
import shipeditor.communication.events.components.ComponentEvents.InstrumentRepaintQueued;

import javax.swing.JPanel;
import java.awt.BorderLayout;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public class VariantMainPanel extends AbstractVariantPanel {

    private final VariantChooserPanel chooserPanel;
    private final VariantDataPanel dataPanel;
    private final VariantOrdnancePanel ordnancePanel;
    private final EmptyVariantBanner emptyBanner;
    private ShipLayer selectedLayer;

    public VariantMainPanel() {
        this.setLayout(new BorderLayout());

        chooserPanel = new VariantChooserPanel();
        this.add(chooserPanel, BorderLayout.PAGE_START);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());
        this.add(contentPanel, BorderLayout.CENTER);

        ordnancePanel = new VariantOrdnancePanel();
        dataPanel = new VariantDataPanel(() -> ordnancePanel.refreshOrdnanceInfo(selectedLayer));
        emptyBanner = new EmptyVariantBanner();

        JPanel northContent = new JPanel(new BorderLayout());
        northContent.add(emptyBanner, BorderLayout.PAGE_START);
        northContent.add(dataPanel, BorderLayout.CENTER);

        contentPanel.add(northContent, BorderLayout.PAGE_START);
        contentPanel.add(ordnancePanel, BorderLayout.CENTER);

        ViewerLayer layer = StaticController.getActiveLayer();
        this.refreshPanel(layer);
    }

    @Override
    protected void initLayerListeners() {
        super.initLayerListeners();
        EventBus.subscribe(this, event -> {
            if (event instanceof InstrumentRepaintQueued checked) {
                if (checked.editorMode() == EditorInstrument.VARIANT_DATA) {
                    emptyBanner.refresh(selectedLayer);
                    ordnancePanel.refreshOrdnanceInfo(selectedLayer);
                }
            }
        });
    }

    @Override
    public void refreshPanel(ViewerLayer selected) {
        selectedLayer = null;
        emptyBanner.refresh(selected);

        if (!(selected instanceof ShipLayer checkedLayer) || checkedLayer.getHull() == null) {
            chooserPanel.installPlaceholders();
            dataPanel.installPlaceholders();
            ordnancePanel.refreshOrdnanceInfo(selected);
            return;
        }

        selectedLayer = checkedLayer;
        ShipVariant variant = chooserPanel.refresh(checkedLayer);
        dataPanel.refresh(checkedLayer, variant);
        ordnancePanel.refreshOrdnanceInfo(checkedLayer);
    }
}
