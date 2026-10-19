package shipeditor.components;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.layering.ViewerLayersPanel;
import shipeditor.components.viewer.LayerViewer;
import shipeditor.components.viewer.PrimaryViewer;

import java.awt.BorderLayout;
import java.awt.Container;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "MS_EXPOSE_REP"})
public final class WindowContentPanes {

    /**
     * Complex component responsible for ship layers display.
     */
    @Getter
    private LayerViewer shipView;

    /**
     * Status line panel for ship sprite viewer.
     */
    @Getter
    private ViewerStatusPanel statusPanel;

    private final Container primaryContentPane;

    /**
     * Holds datafile panel on the left and secondary split pane on the right.
     */
    @Getter
    private TripleSplitContainer tripleSplitter;

    public WindowContentPanes(Container pane) {
        this.primaryContentPane = pane;
    }

    public void loadLayerHandling() {
        if (shipView == null) {
            // We want to fail fast here, just to be safe and find out quick.
            throw new IllegalStateException("Ship view was null at the time of layer panel initialization!");
        }
        ViewerLayersPanel layersPanel = new ViewerLayersPanel(shipView.getLayerManager());

        javax.swing.JPanel topContainer = new javax.swing.JPanel();
        topContainer.setLayout(new BorderLayout());
        
        shipeditor.menubar.MainToolBar mainToolBar = new shipeditor.menubar.MainToolBar();
        topContainer.add(mainToolBar, BorderLayout.PAGE_START);
        topContainer.add(layersPanel, BorderLayout.CENTER);

        primaryContentPane.add(topContainer, BorderLayout.PAGE_START);
    }

    public void loadShipView() {
        this.shipView = new PrimaryViewer().commenceInitialization();
        this.statusPanel = new ViewerStatusPanel(this.shipView);
        primaryContentPane.add(this.statusPanel, BorderLayout.PAGE_END);
        this.refreshContent();
    }

    public void refreshContent() {
        primaryContentPane.revalidate();
        primaryContentPane.repaint();
    }

    public void loadEditingPanes() {
        tripleSplitter = new TripleSplitContainer();
        tripleSplitter.loadContentPanes(shipView);
        primaryContentPane.add(tripleSplitter, BorderLayout.CENTER);
        this.refreshContent();
    }

}
