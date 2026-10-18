package shipeditor.components.instrument.weapon;

import shipeditor.utility.text.StringManager;

import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.representation.weapon.animation.MuzzleFlashSpec;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.function.Supplier;

public class WeaponMuzzleFlashHandler {
    private final JTextField mfLengthEditor;
    private final JTextField mfSpreadEditor;
    private final JTextField mfParticleSizeMinEditor;
    private final JTextField mfParticleSizeRangeEditor;
    private final JTextField mfParticleDurationEditor;
    private final JTextField mfParticleCountEditor;
    private final JLabel mfParticleColorValue;
    private final JLabel colorLabel;

    private MuzzleFlashSpec getOrCreate(WeaponSpecFile spec) {
        if (spec.getMuzzleFlashSpec() == null) {
            spec.setMuzzleFlashSpec(new MuzzleFlashSpec());
        }
        return spec.getMuzzleFlashSpec();
    }

    public WeaponMuzzleFlashHandler(Supplier<Boolean> readinessChecker, Runnable onChange, Supplier<WeaponSpecFile> specSupplier) {
        mfLengthEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange, specSupplier,
                this::getOrCreate, MuzzleFlashSpec::setLength);

        mfSpreadEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange, specSupplier,
                this::getOrCreate, MuzzleFlashSpec::setSpread);

        mfParticleSizeMinEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, MuzzleFlashSpec::setParticleSizeMin);

        mfParticleSizeRangeEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, MuzzleFlashSpec::setParticleSizeRange);

        mfParticleDurationEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, MuzzleFlashSpec::setParticleDuration);

        mfParticleCountEditor = WeaponFirePanelUtilities.createSpecIntField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, MuzzleFlashSpec::setParticleCount);

        mfParticleColorValue = new JLabel();
        colorLabel = WeaponFirePanelUtilities.createColorLabel("Particle Color:", mfParticleColorValue,
                () -> {
                    WeaponSpecFile spec = specSupplier.get();
                    return (spec != null && spec.getMuzzleFlashSpec() != null) ? spec.getMuzzleFlashSpec().getParticleColor() : null;
                },
                color -> {
                    WeaponSpecFile spec = specSupplier.get();
                    if (spec != null) {
                        getOrCreate(spec).setParticleColor(color);
                        onChange.run();
                    }
                });
    }

    public int populate(JPanel panel, int startRow) {
        int row = startRow;
        mfLengthEditor.setToolTipText(StringManager.getString("LENGTH_OF_THE_MUZZLE_FLASH"));
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("LENGTH")), mfLengthEditor, row++);
        mfSpreadEditor.setToolTipText(StringManager.getString("SPREAD_OF_THE_MUZZLE_FLASH_IN_PIXELS_DEGREES"));
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("SPREAD")), mfSpreadEditor, row++);
        mfParticleSizeMinEditor.setToolTipText(StringManager.getString("MINIMUM_SIZE_OF_MUZZLE_FLASH_PARTICLES"));
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("PARTICLE_SIZE_MIN")), mfParticleSizeMinEditor, row++);
        mfParticleSizeRangeEditor.setToolTipText(StringManager.getString("RANDOM_SIZE_ADDED_TO_MIN_SIZE_FOR_PARTICLES"));
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("PARTICLE_SIZE_RANGE")), mfParticleSizeRangeEditor, row++);
        mfParticleDurationEditor.setToolTipText(StringManager.getString("HOW_LONG_THE_PARTICLES_LAST"));
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("PARTICLE_DURATION")), mfParticleDurationEditor, row++);
        mfParticleCountEditor.setToolTipText(StringManager.getString("NUMBER_OF_PARTICLES_SPAWNED_PER_SHOT"));
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("PARTICLE_COUNT")), mfParticleCountEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, colorLabel, mfParticleColorValue, row++);
        return row;
    }

    public void refresh(WeaponSpecFile spec) {
        MuzzleFlashSpec mfSpec = spec.getMuzzleFlashSpec();
        if (mfSpec != null) {
            mfLengthEditor.setText(String.valueOf(mfSpec.getLength()));
            mfSpreadEditor.setText(String.valueOf(mfSpec.getSpread()));
            mfParticleSizeMinEditor.setText(String.valueOf(mfSpec.getParticleSizeMin()));
            mfParticleSizeRangeEditor.setText(String.valueOf(mfSpec.getParticleSizeRange()));
            mfParticleDurationEditor.setText(String.valueOf(mfSpec.getParticleDuration()));
            mfParticleCountEditor.setText(String.valueOf(mfSpec.getParticleCount()));
            WeaponFirePanelUtilities.updateColorLabel(mfParticleColorValue, mfSpec.getParticleColor());
        } else {
            clear();
        }
    }

    public void clear() {
        mfLengthEditor.setText("");
        mfSpreadEditor.setText("");
        mfParticleSizeMinEditor.setText("");
        mfParticleSizeRangeEditor.setText("");
        mfParticleDurationEditor.setText("");
        mfParticleCountEditor.setText("");
        WeaponFirePanelUtilities.updateColorLabel(mfParticleColorValue, null);
    }
}
