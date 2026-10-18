package shipeditor.components.instrument.ship.variant;

import lombok.extern.log4j.Log4j2;
import org.kordamp.ikonli.boxicons.BoxiconsRegular;
import org.kordamp.ikonli.swing.FontIcon;
import shipeditor.PrimaryWindow;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.VariantSaveQueued;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.components.viewer.layers.ship.data.Variant;
import shipeditor.parsing.FileUtilities;
import shipeditor.parsing.saving.SaveCoordinator;
import shipeditor.persistence.SettingsManager;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.ship.VariantFile;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.overseers.StaticController;
import shipeditor.utility.text.StringManager;
import shipeditor.utility.themes.Themes;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Log4j2
public class VariantChooserPanel extends JPanel {
    private final JPanel chooserContainer;
    private final JButton createVariantButton;
    private final JButton cloneVariantButton;
    private final JButton removeVariantButton;
    private final JButton loadVariantButton;
    private final JButton saveVariantButton;
    private final JButton deleteVariantButton;
    private final JButton saveCopyVariantButton;
    private Supplier<ShipVariant> variantToRemoveGetter;
    private ShipLayer selectedLayer;

    public VariantChooserPanel() {
        this.setLayout(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(this, "Variant list");

        chooserContainer = new JPanel();
        chooserContainer.setLayout(new BoxLayout(chooserContainer, BoxLayout.PAGE_AXIS));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 3;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.PAGE_START;

        this.add(chooserContainer, constraints);

        createVariantButton = new JButton(StringManager.getString("CREATE"),
                FontIcon.of(BoxiconsRegular.PLUS_CIRCLE, 16, Themes.getIconColor()));
        createVariantButton.setToolTipText("Create a new blank variant for the current hull");
        createVariantButton.addActionListener(e -> {
            if (selectedLayer == null) return;
            ShipVariant created = new ShipVariant(false);
            String variantID = ShipVariant.createUniqueVariantID(selectedLayer);
            created.setShipHullId(selectedLayer.getShipID());
            created.setVariantId(variantID);
            Map<String, ShipVariant> loadedVariants = selectedLayer.getLoadedVariants();
            loadedVariants.put(created.getVariantId(), created);

            LayerManager layerManager = StaticController.getLayerManager();
            if (layerManager != null) {
                layerManager.markUnsaved(selectedLayer, "variant");
            }

            ShipPainter shipPainter = selectedLayer.getPainter();
            shipPainter.selectVariant(created);
        });

        constraints.gridwidth = 1;
        constraints.weightx = 1.0 / 3.0;
        constraints.gridx = 0;
        constraints.gridy = 1;
        this.add(createVariantButton, constraints);

        cloneVariantButton = new JButton("Clone",
                FontIcon.of(BoxiconsRegular.COPY, 16, Themes.getIconColor()));
        cloneVariantButton.setToolTipText("Clone the currently selected variant into a new editable variant");
        cloneVariantButton.addActionListener(e -> {
            if (selectedLayer == null || variantToRemoveGetter == null) return;
            ShipVariant source = variantToRemoveGetter.get();
            if (source == null || source.isEmpty()) return;
            String newVariantId = ShipVariant.createUniqueVariantID(selectedLayer);
            String baseDisplay = source.getDisplayName() != null && !source.getDisplayName().isBlank()
                    ? source.getDisplayName()
                    : "Custom";
            ShipVariant cloned = source.createDeepCopy(newVariantId, baseDisplay);
            cloned.setShipHullId(selectedLayer.getShipID());
            selectedLayer.getLoadedVariants().put(newVariantId, cloned);

            LayerManager layerManager = StaticController.getLayerManager();
            if (layerManager != null) {
                layerManager.markUnsaved(selectedLayer, "variant");
            }

            ShipPainter shipPainter = selectedLayer.getPainter();
            shipPainter.selectVariant(cloned);
        });

        constraints.gridx = 1;
        constraints.gridy = 1;
        this.add(cloneVariantButton, constraints);

        removeVariantButton = new JButton(StringManager.getString("REMOVE"),
                FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        String tooltip = Utility.getWithLinebreaks("Remove entry from variants loaded to layer",
                "Newly created variants will be erased from memory",
                "Variants from game data files will be reloaded from disk");
        removeVariantButton.setToolTipText(tooltip);
        removeVariantButton.addActionListener(e -> {
            if (selectedLayer == null || variantToRemoveGetter == null) return;
            var variantToRemove = variantToRemoveGetter.get();
            if (variantToRemove == null || variantToRemove.isEmpty()) return;
            Map<String, ShipVariant> loadedVariants = selectedLayer.getLoadedVariants();
            String variantId = variantToRemove.getVariantId();
            loadedVariants.remove(variantId);

            ShipPainter shipPainter = selectedLayer.getPainter();
            var variantFile = GameDataRepository.getVariantByID(variantId);
            if (variantFile == null && variantToRemove.getVariantFilePath() != null) {
                Path targetPath = variantToRemove.getVariantFilePath();
                for (VariantFile candidate : GameDataRepository.getMatchingForHullID(selectedLayer.getShipID()).values()) {
                    if (candidate != null && targetPath.equals(candidate.getVariantFilePath())) {
                        variantFile = candidate;
                        break;
                    }
                }
            }
            if (variantFile != null) {
                shipPainter.selectVariant(variantFile);
            } else {
                shipPainter.selectVariant(VariantFile.empty());
            }
            var layerManager = shipeditor.utility.overseers.StaticController.getLayerManager();
            if (layerManager != null) {
                layerManager.markSaved(selectedLayer, "variant");
            }
        });

        constraints.gridx = 2;
        constraints.gridy = 1;
        this.add(removeVariantButton, constraints);

        loadVariantButton = new JButton("Load...",
                FontIcon.of(BoxiconsRegular.FOLDER_OPEN, 16, Themes.getIconColor()));
        loadVariantButton.setToolTipText("Load a .variant file from disk");
        loadVariantButton.addActionListener(e -> loadVariantFromDisk());

        constraints.gridx = 0;
        constraints.gridy = 2;
        this.add(loadVariantButton, constraints);

        saveVariantButton = new JButton("Save...",
                FontIcon.of(BoxiconsRegular.SAVE, 16, Themes.getIconColor()));
        saveVariantButton.setToolTipText("Save the currently active variant to a .variant file");
        saveVariantButton.addActionListener(e -> {
            if (selectedLayer == null || variantToRemoveGetter == null) return;
            ShipVariant activeVariant = variantToRemoveGetter.get();
            if (activeVariant == null || activeVariant.isEmpty()) return;
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (focusOwner instanceof JTextField textField) {
                for (ActionListener listener : textField.getActionListeners()) {
                    listener.actionPerformed(new ActionEvent(textField, ActionEvent.ACTION_PERFORMED, textField.getText()));
                }
            }
            ShipVariant updatedVariant = selectedLayer.getPainter() != null
                    ? selectedLayer.getPainter().getActiveVariant()
                    : activeVariant;
            if (updatedVariant != null && !updatedVariant.isEmpty()) {
                EventBus.publish(new VariantSaveQueued(updatedVariant));
            }
        });

        constraints.gridx = 1;
        constraints.gridy = 2;
        this.add(saveVariantButton, constraints);

        deleteVariantButton = new JButton("Delete...",
                FontIcon.of(BoxiconsRegular.TRASH_ALT, 16, Themes.getIconColor()));
        deleteVariantButton.setToolTipText("Delete the currently selected variant (and its .variant file on disk if saved)");
        deleteVariantButton.addActionListener(e -> deleteActiveVariant());

        constraints.gridx = 2;
        constraints.gridy = 2;
        this.add(deleteVariantButton, constraints);

        saveCopyVariantButton = new JButton("Save As Copy...",
                FontIcon.of(BoxiconsRegular.SAVE, 16, Themes.getIconColor()));
        saveCopyVariantButton.setToolTipText(
                "Save a copy of the active variant under a new file; the copy's ID becomes the file name");
        saveCopyVariantButton.addActionListener(e -> saveActiveVariantAsCopy());

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 3;
        constraints.weightx = 1.0;
        this.add(saveCopyVariantButton, constraints);
    }

    private void loadVariantFromDisk() {
        if (selectedLayer == null) return;
        FileUtilities.loadVariantFromDisk(selectedLayer);
    }

    private void saveActiveVariantAsCopy() {
        if (selectedLayer == null || variantToRemoveGetter == null) return;
        ShipVariant activeVariant = variantToRemoveGetter.get();
        if (activeVariant == null || activeVariant.isEmpty()) return;
        ShipVariant updatedVariant = selectedLayer.getPainter() != null
                ? selectedLayer.getPainter().getActiveVariant()
                : activeVariant;
        if (updatedVariant != null && !updatedVariant.isEmpty()) {
            SaveCoordinator.saveVariantAsCopy(updatedVariant);
        }
    }

    private void deleteActiveVariant() {
        if (selectedLayer == null || variantToRemoveGetter == null) return;
        ShipVariant variantToDelete = variantToRemoveGetter.get();
        if (variantToDelete == null || variantToDelete.isEmpty()) return;

        String variantId = variantToDelete.getVariantId();
        Path filePath = variantToDelete.getVariantFilePath();
        if (filePath == null && variantId != null) {
            VariantFile repoVariant = GameDataRepository.getVariantByID(variantId);
            if (repoVariant != null) {
                filePath = repoVariant.getVariantFilePath();
            }
        }

        boolean hasDiskFile = filePath != null && Files.exists(filePath);
        String message = hasDiskFile
                ? "Delete variant '" + variantId + "' and remove its file from disk?\n" + filePath
                : "Delete unsaved variant '" + variantId + "'?";
        int confirm = JOptionPane.showConfirmDialog(
                PrimaryWindow.getInstance(),
                message,
                "Confirm Delete Variant",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        if (hasDiskFile) {
            try {
                Files.deleteIfExists(filePath);
                log.info("Deleted variant file from disk: {}", filePath);
            } catch (IOException ex) {
                log.error("Failed to delete variant file: {}", filePath, ex);
                JOptionPane.showMessageDialog(
                        PrimaryWindow.getInstance(),
                        "Failed to delete variant file from disk:\n" + ex.getMessage(),
                        "Delete Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        if (variantId != null) {
            selectedLayer.getLoadedVariants().remove(variantId);
            GameDataRepository gameData = SettingsManager.getGameData();
            if (gameData != null && gameData.getAllVariants() != null) {
                gameData.getAllVariants().remove(variantId);
                gameData.rebuildVariantsByHullIndex();
            }
        }

        if (filePath != null) {
            final Path deletedPath = filePath;
            GameDataRepository gameData = SettingsManager.getGameData();
            if (gameData != null && gameData.getAllVariants() != null) {
                gameData.getAllVariants().entrySet().removeIf(
                        entry -> entry.getValue() != null && deletedPath.equals(entry.getValue().getVariantFilePath()));
                gameData.rebuildVariantsByHullIndex();
            }
        }

        ShipPainter shipPainter = selectedLayer.getPainter();
        if (shipPainter != null) {
            shipPainter.selectVariant(VariantFile.empty());
        }
        var layerManager = shipeditor.utility.overseers.StaticController.getLayerManager();
        if (layerManager != null) {
            layerManager.markSaved(selectedLayer, "variant");
        }
    }

    public void installPlaceholders() {
        chooserContainer.removeAll();
        chooserContainer.add(createDisabledChooser());
        createVariantButton.setEnabled(false);
        cloneVariantButton.setEnabled(false);
        removeVariantButton.setText(StringManager.getString("REMOVE"));
        removeVariantButton.setIcon(FontIcon.of(BoxiconsRegular.TRASH, 16, Themes.getIconColor()));
        removeVariantButton.setEnabled(false);
        loadVariantButton.setEnabled(false);
        saveVariantButton.setEnabled(false);
        deleteVariantButton.setEnabled(false);
        saveCopyVariantButton.setEnabled(false);
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
        loadVariantButton.setEnabled(true);

        ShipVariant variant = recreateVariantChooser(layer);
        variantToRemoveGetter = () -> variant;

        if (variant == null || variant.isEmpty()) {
            cloneVariantButton.setEnabled(false);
            removeVariantButton.setEnabled(false);
            saveVariantButton.setEnabled(false);
            deleteVariantButton.setEnabled(false);
            saveCopyVariantButton.setEnabled(false);
        } else {
            if (variant.isLoadedFromFile()) {
                removeVariantButton.setText(StringManager.getString("RELOAD"));
                removeVariantButton.setIcon(FontIcon.of(BoxiconsRegular.REFRESH, 16, Themes.getIconColor()));
            }
            cloneVariantButton.setEnabled(true);
            removeVariantButton.setEnabled(true);
            saveVariantButton.setEnabled(true);
            deleteVariantButton.setEnabled(true);
            saveCopyVariantButton.setEnabled(true);
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
            if (Objects.equals(variantShipHullId, shipID)) {
                Path loadedPath = shipVariant.getVariantFilePath();
                if (loadedPath != null) {
                    variantFiles.entrySet().removeIf(entry ->
                            !Objects.equals(entry.getKey(), variantId)
                                    && entry.getValue() instanceof VariantFile vf
                                    && loadedPath.equals(vf.getVariantFilePath()));
                }
                variantFiles.put(variantId, shipVariant);
            }
        });

        ShipVariant result = null;
        ShipPainter painter = checkedLayer.getPainter();
        Variant[] model = variantFiles.values().toArray(new Variant[0]);
        JComboBox<Variant> variantChooser = new JComboBox<>(model);
        ShipVariant activeVariant = painter.getActiveVariant();
        if (activeVariant != null && !activeVariant.isEmpty()) {
            Variant toSelect = variantFiles.getOrDefault(activeVariant.getVariantId(), activeVariant);
            variantChooser.setSelectedItem(toSelect);
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
