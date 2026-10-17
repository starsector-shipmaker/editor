package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import shipeditor.representation.ship.VariantFile;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.MouseoverLabelListener;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Insets;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;

public class DataTreeVariantPanelBuilder {

    public static JPanel createVariantsPanel(Collection<VariantFile> variantFiles, boolean withSelector) {
        JPanel variantsPanel = new JPanel();
        variantsPanel.setLayout(new java.awt.BorderLayout());
        ComponentUtilities.outfitPanelWithTitle(variantsPanel, new Insets(1, 0, 0, 0), "Variants");
        variantsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel labelContainer = new JPanel(new java.awt.GridBagLayout());
        labelContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        labelContainer.setBorder(new EmptyBorder(2, 0, 0, 0));

        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 2, 0);

        if (variantFiles.isEmpty()) throw new IllegalArgumentException("Empty variants list!");

        ButtonGroup group = new ButtonGroup();
        for (VariantFile variantFile : variantFiles) {
            if (variantFile == null) continue;
            JPanel variantLine = new JPanel(new java.awt.BorderLayout());
            variantLine.setAlignmentX(Component.LEFT_ALIGNMENT);

            javax.swing.JTextArea variantFileLabel = createVariantFileLabel(variantFile);
            if (withSelector) {
                JPanel selectorPanel = new JPanel(new java.awt.BorderLayout());
                outfitVariantLabelWithSelector(variantFile, selectorPanel, group, variantFileLabel);
                variantLine.add(selectorPanel, java.awt.BorderLayout.WEST);
            }
            variantLine.add(variantFileLabel, java.awt.BorderLayout.CENTER);
            labelContainer.add(variantLine, gbc);
            gbc.gridy++;
        }

        if (withSelector) {
            Enumeration<AbstractButton> elements = group.getElements();
            if (elements.hasMoreElements()) {
                AbstractButton abstractButton = elements.nextElement();
                abstractButton.doClick();
            }
        }

        variantsPanel.add(labelContainer, java.awt.BorderLayout.CENTER);
        return variantsPanel;
    }

    private static javax.swing.JTextArea createVariantFileLabel(VariantFile variantFile) {
        Path variantFilePath = variantFile.getVariantFilePath();
        Path fileNamePath = variantFilePath != null ? variantFilePath.getFileName() : null;
        String fileName = fileNamePath != null ? fileNamePath.toString() : "Unknown";
        javax.swing.JTextArea variantLabel = ComponentUtilities.createWrappingLabel("Variant file: " + fileName);
        variantLabel.setToolTipText(variantFilePath != null ? variantFilePath.toString() : "Unknown");
        variantLabel.setBorder(ComponentUtilities.createLabelSimpleBorder(new Insets(2, 4, 2, 4)));
        if (variantFilePath != null) {
            JPopupMenu pathContextMenu = ComponentUtilities.createPathContextMenu(variantFilePath);
            variantLabel.addMouseListener(new MouseoverLabelListener(pathContextMenu, variantLabel));
        }
        return variantLabel;
    }

    private static void outfitVariantLabelWithSelector(VariantFile variant, JPanel variantLine,
                                                       ButtonGroup group, javax.swing.JTextArea variantFileLabel) {
        JRadioButton selector = new JRadioButton();
        selector.setBorder(new EmptyBorder(0, 0, 2, 4));
        selector.addActionListener(e -> shipeditor.communication.EventBus.publish(new shipeditor.communication.events.components.ComponentEvents.ShipEntryPicked(variant)));
        selector.setToolTipText(StringManager.getString("SELECT_VARIANT_OR_DRAG_LABEL_TO_BE_INSTALLED_AS_MODULE"));
        group.add(selector);
        variantLine.add(selector);

        DragSource dragSource = DragSource.getDefaultDragSource();
        DragGestureListener gestureListener = new LabelDragListener(variant, variantFileLabel);
        dragSource.createDefaultDragGestureRecognizer(variantFileLabel,
                DnDConstants.ACTION_COPY, gestureListener);
    }
}
