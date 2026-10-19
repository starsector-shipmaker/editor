package shipeditor.components.instrument.ship.variant;

import lombok.extern.log4j.Log4j2;
import shipeditor.utility.text.StringManager;

import shipeditor.components.datafiles.entities.ShipCSVEntry;
import shipeditor.components.viewer.layers.ViewerLayer;
import shipeditor.components.viewer.layers.ship.ShipLayer;
import shipeditor.components.viewer.layers.ship.data.ShipHull;
import shipeditor.utility.Utility;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.themes.Themes;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

@Log4j2
public class VariantOrdnancePanel extends JPanel {
    private static final double OP_WARNING_RATIO = 0.85d;

    private final JLabel shipOPCap;
    private final JLabel usedOPTotal;
    private final JLabel usedOPInHullmods;
    private final JLabel usedOPInWings;
    private final JLabel usedOPInWeapons;
    private final JLabel usedVentsAndMax;
    private final JLabel usedCapsAndMax;
    private final JLabel totalFluxCapacity;
    private final JLabel totalFluxDissipation;
    private final JLabel opStatusLabel;
    private final JProgressBar opBar;

    public VariantOrdnancePanel() {
        ComponentUtilities.outfitPanelWithTitle(this, "Ordnance & Flux Stats");
        this.setLayout(new GridBagLayout());

        Border emptyBorder = new EmptyBorder(1, 0, 2, 0);

        JLabel shipOPCapLabel = new JLabel(StringManager.getString("TOTAL_OP_CAPACITY"));
        shipOPCapLabel.setBorder(emptyBorder);
        shipOPCap = new JLabel();

        JLabel usedOPTotalLabel = new JLabel(StringManager.getString("USED_OP_FOR_SHIP"));
        usedOPTotalLabel.setBorder(emptyBorder);
        usedOPTotal = new JLabel();

        JLabel usedOPInWeaponsLabel = new JLabel(StringManager.getString("USED_OP_IN_WEAPONS"));
        usedOPInWeaponsLabel.setBorder(emptyBorder);
        usedOPInWeapons = new JLabel();

        JLabel usedOPInModsLabel = new JLabel(StringManager.getString("USED_OP_IN_HULLMODS"));
        usedOPInModsLabel.setBorder(emptyBorder);
        usedOPInHullmods = new JLabel();

        JLabel usedOPInWingsLabel = new JLabel(StringManager.getString("USED_OP_IN_WINGS"));
        usedOPInWingsLabel.setBorder(emptyBorder);
        usedOPInWings = new JLabel();

        JLabel ventsMaxLabel = new JLabel(StringManager.getString("FLUX_VENTS_ALLOCATED_MAX"));
        ventsMaxLabel.setBorder(emptyBorder);
        usedVentsAndMax = new JLabel();

        JLabel capsMaxLabel = new JLabel(StringManager.getString("FLUX_CAPACITORS_ALLOCATED_MAX"));
        capsMaxLabel.setBorder(emptyBorder);
        usedCapsAndMax = new JLabel();

        JLabel fluxCapLabel = new JLabel(StringManager.getString("TOTAL_FLUX_CAPACITY"));
        fluxCapLabel.setBorder(emptyBorder);
        totalFluxCapacity = new JLabel();

        JLabel fluxDissLabel = new JLabel(StringManager.getString("TOTAL_FLUX_DISSIPATION"));
        fluxDissLabel.setBorder(emptyBorder);
        totalFluxDissipation = new JLabel();

        JLabel opStatusTitle = new JLabel(StringManager.getString("OP_STATUS"));
        opStatusTitle.setBorder(emptyBorder);
        opStatusLabel = new JLabel();

        ComponentUtilities.addLabelAndComponent(this, shipOPCapLabel, shipOPCap, 0);
        ComponentUtilities.addLabelAndComponent(this, usedOPTotalLabel, usedOPTotal, 1);
        ComponentUtilities.addLabelAndComponent(this, usedOPInWeaponsLabel, usedOPInWeapons, 2);
        ComponentUtilities.addLabelAndComponent(this, usedOPInModsLabel, usedOPInHullmods, 3);
        ComponentUtilities.addLabelAndComponent(this, usedOPInWingsLabel, usedOPInWings, 4);

        ComponentUtilities.addLabelAndComponent(this, ventsMaxLabel, usedVentsAndMax, 5);
        ComponentUtilities.addLabelAndComponent(this, capsMaxLabel, usedCapsAndMax, 6);
        ComponentUtilities.addLabelAndComponent(this, fluxCapLabel, totalFluxCapacity, 7);
        ComponentUtilities.addLabelAndComponent(this, fluxDissLabel, totalFluxDissipation, 8);
        ComponentUtilities.addLabelAndComponent(this, opStatusTitle, opStatusLabel, 9);

        JLabel opBarLabel = new JLabel("OP usage:");
        opBarLabel.setBorder(emptyBorder);
        opBar = new JProgressBar(0, 1);
        opBar.setStringPainted(true);
        opBar.setString(StringManager.getString("NOT_INITIALIZED"));
        ComponentUtilities.addLabelAndComponent(this, opBarLabel, opBar, 10);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 6, 0, 3);
        constraints.gridx = 0;
        constraints.gridy = 11;
        constraints.weightx = 0.0;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.LINE_START;
        this.add(new JLabel(), constraints);
    }

    public void refreshOrdnanceInfo(ViewerLayer selected) {
        String notInitialized = StringManager.getString("NOT_INITIALIZED");

        if (selected instanceof ShipLayer shipLayer) {
            String totalOPStr = Utility.translateIntegerValue(shipLayer::getTotalOP);
            shipOPCap.setText(totalOPStr);

            var activeVariant = shipLayer.getActiveVariant();
            if (activeVariant == null) {
                resetFields(notInitialized);
                return;
            }

            int totalUsedOP = shipLayer.getTotalUsedOP();
            usedOPTotal.setText(String.valueOf(totalUsedOP));
            usedOPInWeapons.setText(String.valueOf(activeVariant.getTotalOPInWeapons()));
            usedOPInHullmods.setText(String.valueOf(activeVariant.getTotalOPInHullmods(shipLayer)));
            usedOPInWings.setText(String.valueOf(shipLayer.getTotalOPInWings()));

            int maxVentsCaps = 0;
            ShipHull shipHull = shipLayer.getHull();
            if (shipHull != null && shipHull.getHullSize() != null) {
                maxVentsCaps = shipHull.getHullSize().getMaxFluxRegulators();
            }

            int usedVents = activeVariant.getFluxVents();
            int usedCaps = activeVariant.getFluxCapacitors();

            log.debug("Ordnance refresh: OP {}/{}, vents {}/{}, capacitors {}/{}",
                    totalUsedOP, shipLayer.getTotalOP(), usedVents, maxVentsCaps, usedCaps, maxVentsCaps);
            applyFluxLimit(usedVentsAndMax, "vents", usedVents, maxVentsCaps);
            applyFluxLimit(usedCapsAndMax, "capacitors", usedCaps, maxVentsCaps);

            ShipCSVEntry shipEntry = activeVariant.getEntryFromShipID();
            if (shipEntry != null) {
                int baseCapacity = shipEntry.getBaseFluxCapacity();
                int baseDissipation = shipEntry.getBaseFluxDissipation();

                int totalCapVal = baseCapacity + (usedCaps * 200);
                int totalDissVal = baseDissipation + (usedVents * 10);

                totalFluxCapacity.setText(totalCapVal + " (Base: " + baseCapacity + " + " + (usedCaps * 200) + ")");
                totalFluxDissipation.setText(totalDissVal + " (Base: " + baseDissipation + " + " + (usedVents * 10) + ")");
            } else {
                totalFluxCapacity.setText(String.valueOf(usedCaps * 200));
                totalFluxDissipation.setText(String.valueOf(usedVents * 10));
            }

            int opCap = shipLayer.getTotalOP();
            updateOPBar(totalUsedOP, opCap);
            if (opCap > 0) {
                if (totalUsedOP > opCap) {
                    opStatusLabel.setText(StringManager.getString("OP_EXCEEDED_EXCEEDS_BY") + (totalUsedOP - opCap) + " OP)");
                    opStatusLabel.setForeground(Themes.getErrorColor());
                    opStatusLabel.setFont(opStatusLabel.getFont().deriveFont(java.awt.Font.BOLD));
                } else if (totalUsedOP < opCap) {
                    opStatusLabel.setText(StringManager.getString("WARNING") + (opCap - totalUsedOP) + " unallocated OP remaining!");
                    opStatusLabel.setForeground(Themes.getWarningColor());
                    opStatusLabel.setFont(opStatusLabel.getFont().deriveFont(java.awt.Font.BOLD));
                } else {
                    opStatusLabel.setText(StringManager.getString("ALL_OP_ALLOCATED"));
                    opStatusLabel.setForeground(Themes.getSuccessColor());
                    opStatusLabel.setFont(opStatusLabel.getFont().deriveFont(java.awt.Font.BOLD));
                }
            } else {
                opStatusLabel.setText(StringManager.getString("N_A"));
                opStatusLabel.setForeground(null);
            }
        } else {
            shipOPCap.setText(notInitialized);
            resetFields(notInitialized);
        }
    }

    /**
     * Colours the OP bar: green below 85% usage, amber from 85% to 100%, red when over budget.
     */
    private void updateOPBar(int usedOP, int opCap) {
        if (opCap <= 0) {
            opBar.setMaximum(1);
            opBar.setValue(0);
            opBar.setString(StringManager.getString("N_A"));
            opBar.setForeground(null);
            return;
        }
        double ratio = (double) usedOP / opCap;
        Color barColor;
        if (ratio > 1.0d) {
            barColor = Themes.getErrorColor();
        } else if (ratio >= OP_WARNING_RATIO) {
            barColor = Themes.getWarningColor();
        } else {
            barColor = Themes.getSuccessColor();
        }
        opBar.setMaximum(opCap);
        opBar.setValue(Math.min(usedOP, opCap));
        opBar.setString(usedOP + " / " + opCap + " OP (" + Math.round(ratio * 100) + "%)");
        opBar.setForeground(barColor);
    }

    /**
     * Shows 'used / max' for flux vents or capacitors, in the error colour when the hull size limit is exceeded.
     */
    private static void applyFluxLimit(JLabel label, String what, int used, int max) {
        label.setText(used + " / " + max);
        label.setToolTipText("Maximum flux " + what + " for this hull size: " + max);
        label.setForeground(used > max ? Themes.getErrorColor() : null);
    }

    private void resetFields(String notInitialized) {
        usedOPTotal.setText(notInitialized);
        usedOPInWeapons.setText(notInitialized);
        usedOPInHullmods.setText(notInitialized);
        usedOPInWings.setText(notInitialized);
        usedVentsAndMax.setText(notInitialized);
        usedCapsAndMax.setText(notInitialized);
        totalFluxCapacity.setText(notInitialized);
        totalFluxDissipation.setText(notInitialized);
        opStatusLabel.setText(notInitialized);
        opStatusLabel.setForeground(null);
        usedVentsAndMax.setForeground(null);
        usedCapsAndMax.setForeground(null);
        opBar.setMaximum(1);
        opBar.setValue(0);
        opBar.setString(notInitialized);
        opBar.setForeground(null);
    }
}
