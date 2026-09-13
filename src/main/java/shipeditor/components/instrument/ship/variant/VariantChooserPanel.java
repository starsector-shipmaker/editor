package shipeditor.components.instrument.ship.variant;

import shipeditor.utility.text.StringManager;

import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.layers.ship.data.Variant;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.VariantFile;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.themes.Themes;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class VariantChooserPanel extends JPanel {
    private final JPanel chooserContainer;
    private final JButton createVariantButton;
    private final JButton removeVariantButton;
    private Supplier<ShipVariant> variantToRemoveGetter;
    private ShipLayer selectedLayer;

    public VariantChooserPanel() {
        this.setLayout(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(this, "Variant list");

        chooserContainer = new JPanel();
        chooserContainer.setLayout(new BoxLayout(chooserContainer, BoxLayout.PAGE_AXIS));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.PAGE_START;

        this.add(chooserContainer, constraints);

        createVariantButton = new JButton(StringManager.getString("CREATE"),
                FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 16, Themes.getIconColor()));
        createVariantButton.addActionListener(e -> {
            if (selectedLayer == null) return;
            ShipVariant created = new ShipVariant(false);
            String variantID = ShipVariant.createUniqueVariantID(selectedLayer);
            created.setShipHullId(selectedLayer.getShipID());
            created.setVariantId(variantID);
            Map<String, ShipVariant> loadedVariants = selectedLayer.getLoadedVariants();
            loadedVariants.put(created.getVariantId(), created);

            ShipPainter shipPainter = selectedLayer.getPainter();
            shipPainter.selectVariant(created);
        });

        constraints.gridwidth = 1;
        constraints.weightx = 0.5;
        constraints.gridx = 0;
        constraints.gridy = 1;
        this.add(createVariantButton, constraints);

        removeVariantButton = new JButton(StringManager.getString("REMOVE"),
                FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        String tooltip = Utility.getWithLinebreaks("Remove entry from variants loaded to layer",
                "Newly created variants will be erased entirely",
                "Variants from game data files will be reloaded instead");
        removeVariantButton.setToolTipText(tooltip);
        removeVariantButton.addActionListener(e -> {
            if (selectedLayer == null || variantToRemoveGetter == null) return;
            var variantToRemove = variantToRemoveGetter.get();
            Map<String, ShipVariant> loadedVariants = selectedLayer.getLoadedVariants();
            String variantId = variantToRemove.getVariantId();
            loadedVariants.remove(variantId);

            ShipPainter shipPainter = selectedLayer.getPainter();
            var variantFile = GameDataRepository.getVariantByID(variantId);
            if (variantFile != null) {
                shipPainter.selectVariant(variantFile);
            } else {
                shipPainter.selectVariant(VariantFile.empty());
            }
        });

        constraints.gridwidth = 1;
        constraints.weightx = 0.5;
        constraints.gridx = 1;
        constraints.gridy = 1;
        this.add(removeVariantButton, constraints);
    }

    public void installPlaceholders() {
        chooserContainer.removeAll();
        chooserContainer.add(createDisabledChooser());
        createVariantButton.setEnabled(false);
        removeVariantButton.setText(StringManager.getString("REMOVE"));
        removeVariantButton.setIcon(FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        removeVariantButton.setEnabled(false);
        selectedLayer = null;
        variantToRemoveGetter = null;
        chooserContainer.revalidate();
        chooserContainer.repaint();
    }

    public ShipVariant refresh(ShipLayer layer) {
        chooserContainer.removeAll();
        selectedLayer = layer;
        removeVariantButton.setText(StringManager.getString("REMOVE"));
        removeVariantButton.setIcon(FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        createVariantButton.setEnabled(true);

        ShipVariant variant = recreateVariantChooser(layer);
        variantToRemoveGetter = () -> variant;

        if (variant == null || variant.isEmpty()) {
            removeVariantButton.setEnabled(false);
        } else {
            if (variant.isLoadedFromFile()) {
                removeVariantButton.setText(StringManager.getString("RELOAD"));
                removeVariantButton.setIcon(FontIcon.of(BoxiconsRegular.REFRESH, 16, Themes.getIconColor()));
            }
            removeVariantButton.setEnabled(true);
        }
        chooserContainer.revalidate();
        chooserContainer.repaint();
        return variant;
    }

    private ShipVariant recreateVariantChooser(ShipLayer checkedLayer) {
        Map<String, Variant> variantFiles = new LinkedHashMap<>();
        Variant empty = VariantFile.empty();
        variantFiles.put(StringManager.getString("EMPTY"), empty);

        String shipID = checkedLayer.getShipID();
        variantFiles.putAll(GameDataRepository.getMatchingForHullID(shipID));
        var loaded = checkedLayer.getLoadedVariants();
        loaded.forEach((variantId, shipVariant) -> {
            String variantShipHullId = shipVariant.getShipHullId();
            if (variantShipHullId.equals(shipID)) {
                variantFiles.put(variantId, shipVariant);
            }
        });

        ShipVariant result = null;
        ShipPainter painter = checkedLayer.getPainter();
        Variant[] model = variantFiles.values().toArray(new Variant[0]);
        JComboBox<Variant> variantChooser = new JComboBox<>(model);
        ShipVariant activeVariant = painter.getActiveVariant();
        if (activeVariant != null) {
            variantChooser.setSelectedItem(activeVariant);
            result = activeVariant;
        } else {
            variantChooser.setSelectedItem(empty);
        }
        variantChooser.addActionListener(action -> {
            Variant chosen = (Variant) variantChooser.getSelectedItem();
            if (chosen != null) {
                painter.selectVariant(chosen);
            }
        });
        variantChooser.setAlignmentX(Component.CENTER_ALIGNMENT);

        chooserContainer.add(variantChooser);
        chooserContainer.add(Box.createVerticalGlue());

        return result;
    }

    private static JComboBox<ShipVariant> createDisabledChooser() {
        ShipVariant[] variantsFileArray = {new ShipVariant()};
        JComboBox<ShipVariant> variantChooser = new JComboBox<>(variantsFileArray);
        variantChooser.setSelectedItem(variantsFileArray[0]);
        variantChooser.setEnabled(false);
        return variantChooser;
    }
}
