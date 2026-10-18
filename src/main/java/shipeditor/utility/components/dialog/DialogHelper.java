package shipeditor.utility.components.dialog;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.communication.events.files.FileEvents.HullSaveQueued;
import shipeditor.communication.events.files.FileEvents.VariantSaveQueued;
import shipeditor.components.viewer.layers.LayerManager;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.data.ShipVariant;
import shipeditor.persistence.SettingsManager;
import shipeditor.utility.text.StringManager;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Component;
import java.nio.file.Path;

@Log4j2
public final class DialogHelper {

    /**
     * Session-only flag: when true, the pre-save summary dialog is skipped.
     * Not persisted, because the persisted Settings class is outside the save/load stream.
     */
    private static boolean saveSummarySuppressed;

    private DialogHelper() {}

    public static void showDuplicateIDError() {
        JOptionPane.showMessageDialog(shipeditor.PrimaryWindow.getInstance(),
                StringManager.getString("INPUT_ID_ALREADY_ASSIGNED_TO_SLOT_MSG"),
                "Duplicate ID",
                JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Shows a summary of what is about to be written to disk and asks for confirmation.
     * The "Don't ask again" choice lasts until the application is closed.
     *
     * @param summary human-readable description of the files that will be saved
     * @return true if saving should proceed (including when the dialog is suppressed), false if cancelled
     */
    public static boolean confirmSaveSummary(String summary) {
        if (saveSummarySuppressed) {
            log.debug("Save summary suppressed for this session; proceeding: {}", summary);
            return true;
        }
        log.debug("Showing save summary dialog: {}", summary);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.PAGE_AXIS));
        JLabel messageLabel = new JLabel("<html>" + summary.replace("\n", "<br>") + "</html>");
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JCheckBox dontAsk = new JCheckBox("Don't ask again (this session)");
        dontAsk.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(messageLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(dontAsk);

        int choice = JOptionPane.showConfirmDialog(
                shipeditor.PrimaryWindow.getInstance(),
                content,
                "Confirm Save",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return false;
        }
        if (dontAsk.isSelected()) {
            saveSummarySuppressed = true;
        }
        return true;
    }

    public static boolean confirmLayerRemoval(LayerManager manager, ViewerLayer layer) {
        if (!(layer instanceof ShipLayer shipLayer)) {
            return true;
        }

        boolean hullDirty = manager.isHullDirty(shipLayer);
        boolean variantDirty = manager.isVariantDirty(shipLayer);

        if (!hullDirty && !variantDirty) {
            return true;
        }

        manager.setActiveLayer(shipLayer);

        String title = "Unsaved Changes";
        String message = "Layer has unsaved changes. Do you want to save them before closing?";

        if (hullDirty && variantDirty) {
            Object[] options = {"Save Both", "Save Hull", "Save Variant", "Don't Save", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    shipeditor.PrimaryWindow.getInstance(),
                    message,
                    title,
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            switch (choice) {
                case 0 -> { // Save Both
                    EventBus.publish(new HullSaveQueued(shipLayer));
                    ShipVariant variant = shipLayer.getActiveVariant();
                    if (variant != null && !variant.isEmpty()) {
                        EventBus.publish(new VariantSaveQueued(variant));
                    }
                    return !manager.isHullDirty(shipLayer) && !manager.isVariantDirty(shipLayer);
                }
                case 1 -> { // Save Hull
                    EventBus.publish(new HullSaveQueued(shipLayer));
                    return !manager.isHullDirty(shipLayer);
                }
                case 2 -> { // Save Variant
                    ShipVariant variant = shipLayer.getActiveVariant();
                    if (variant != null && !variant.isEmpty()) {
                        EventBus.publish(new VariantSaveQueued(variant));
                    }
                    return !manager.isVariantDirty(shipLayer);
                }
                case 3 -> { // Don't Save
                    manager.getUnsavedChangesRegistry().remove(shipLayer);
                    return true;
                }
                default -> { // Cancel
                    return false;
                }
            }
        } else if (hullDirty) {
            Object[] options = {"Save Hull", "Don't Save", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("HULL_HAS_UNSAVED_CHANGES_DO_YOU_WANT_TO_MSG"),
                    title,
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            switch (choice) {
                case 0 -> { // Save Hull
                    EventBus.publish(new HullSaveQueued(shipLayer));
                    return !manager.isHullDirty(shipLayer);
                }
                case 1 -> { // Don't Save
                    manager.getUnsavedChangesRegistry().remove(shipLayer);
                    return true;
                }
                default -> { // Cancel
                    return false;
                }
            }
        } else { // variantDirty
            Object[] options = {"Save Variant", "Don't Save", "Cancel"};
            int choice = JOptionPane.showOptionDialog(
                    shipeditor.PrimaryWindow.getInstance(),
                    StringManager.getString("VARIANT_HAS_UNSAVED_CHANGES_DO_YOU_WANT_MSG"),
                    title,
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            switch (choice) {
                case 0 -> { // Save Variant
                    ShipVariant variant = shipLayer.getActiveVariant();
                    if (variant != null && !variant.isEmpty()) {
                        EventBus.publish(new VariantSaveQueued(variant));
                    }
                    return !manager.isVariantDirty(shipLayer);
                }
                case 1 -> { // Don't Save
                    manager.getUnsavedChangesRegistry().remove(shipLayer);
                    return true;
                }
                default -> { // Cancel
                    return false;
                }
            }
        }
    }

    /**
     * Resolves the human-readable display name for a package folder path.
     *
     * @param packagePath the directory path of the package/mod
     * @return "Starsector Core", the folder name, or "Unknown" if null
     */
    public static String resolvePackageName(Path packagePath) {
        if (packagePath == null) {
            return "Unknown";
        }
        if (SettingsManager.isCoreFolder(packagePath)) {
            return "Starsector Core";
        }
        Path fileName = packagePath.getFileName();
        return fileName != null ? fileName.toString() : packagePath.toString();
    }

    /**
     * Resolves the display name for a mod ID string.
     *
     * @param modId the mod ID string
     * @return "Starsector Core" if core/blank, otherwise the mod ID
     */
    public static String resolveModIdName(String modId) {
        if (modId == null || modId.isBlank() || "starsector-core".equals(modId)) {
            return "Starsector Core";
        }
        return modId;
    }

}
