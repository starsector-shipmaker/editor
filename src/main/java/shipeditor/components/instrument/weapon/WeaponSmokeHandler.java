package shipeditor.components.instrument.weapon;

import shipeditor.utility.text.StringManager;

import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.representation.weapon.animation.SmokeSpec;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.function.Supplier;

public class WeaponSmokeHandler {
    private final JTextField smokeParticleSizeMinEditor;
    private final JTextField smokeParticleSizeRangeEditor;
    private final JTextField smokeCloudParticleCountEditor;
    private final JTextField smokeCloudDurationEditor;
    private final JTextField smokeCloudRadiusEditor;
    private final JTextField smokeBlowbackParticleCountEditor;
    private final JTextField smokeBlowbackDurationEditor;
    private final JTextField smokeBlowbackLengthEditor;
    private final JTextField smokeBlowbackSpreadEditor;
    private final JLabel smokeParticleColorValue;
    private final JLabel colorLabel;

    private SmokeSpec getOrCreate(WeaponSpecFile spec) {
        if (spec.getSmokeSpec() == null) {
            spec.setSmokeSpec(new SmokeSpec());
        }
        return spec.getSmokeSpec();
    }

    public WeaponSmokeHandler(Supplier<Boolean> readinessChecker, Runnable onChange, Supplier<WeaponSpecFile> specSupplier) {
        smokeParticleSizeMinEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, SmokeSpec::setParticleSizeMin);

        smokeParticleSizeRangeEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, SmokeSpec::setParticleSizeRange);

        smokeCloudParticleCountEditor = WeaponFirePanelUtilities.createSpecIntField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, SmokeSpec::setCloudParticleCount);

        smokeCloudDurationEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, SmokeSpec::setCloudDuration);

        smokeCloudRadiusEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, SmokeSpec::setCloudRadius);

        smokeBlowbackParticleCountEditor = WeaponFirePanelUtilities.createSpecIntField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, SmokeSpec::setBlowbackParticleCount);

        smokeBlowbackDurationEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, SmokeSpec::setBlowbackDuration);

        smokeBlowbackLengthEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, SmokeSpec::setBlowbackLength);

        smokeBlowbackSpreadEditor = WeaponFirePanelUtilities.createSpecDoubleField(readinessChecker, onChange,
                specSupplier, this::getOrCreate, SmokeSpec::setBlowbackSpread);

        smokeParticleColorValue = new JLabel();
        colorLabel = WeaponFirePanelUtilities.createColorLabel("Particle Color:", smokeParticleColorValue,
                () -> {
                    WeaponSpecFile spec = specSupplier.get();
                    return (spec != null && spec.getSmokeSpec() != null) ? spec.getSmokeSpec().getParticleColor() : null;
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
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("HTML_B_SMOKE_B_HTML")), new JLabel(""), row++);
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("PARTICLE_SIZE_MIN_PX")), smokeParticleSizeMinEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("PARTICLE_SIZE_RANGE_PX")), smokeParticleSizeRangeEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("CLOUD_PARTICLE_COUNT")), smokeCloudParticleCountEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("CLOUD_DURATION_S")), smokeCloudDurationEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("CLOUD_RADIUS_PX")), smokeCloudRadiusEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("BLOWBACK_PARTICLE_COUNT")), smokeBlowbackParticleCountEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("BLOWBACK_DURATION_S")), smokeBlowbackDurationEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("BLOWBACK_LENGTH_PX")), smokeBlowbackLengthEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, new JLabel(StringManager.getString("BLOWBACK_SPREAD_PX")), smokeBlowbackSpreadEditor, row++);
        ComponentUtilities.addLabelAndComponent(panel, colorLabel, smokeParticleColorValue, row++);
        return row;
    }

    public void refresh(WeaponSpecFile spec) {
        SmokeSpec smokeSpec = spec.getSmokeSpec();
        if (smokeSpec != null) {
            smokeParticleSizeMinEditor.setText(String.valueOf(smokeSpec.getParticleSizeMin()));
            smokeParticleSizeRangeEditor.setText(String.valueOf(smokeSpec.getParticleSizeRange()));
            smokeCloudParticleCountEditor.setText(String.valueOf(smokeSpec.getCloudParticleCount()));
            smokeCloudDurationEditor.setText(String.valueOf(smokeSpec.getCloudDuration()));
            smokeCloudRadiusEditor.setText(String.valueOf(smokeSpec.getCloudRadius()));
            smokeBlowbackParticleCountEditor.setText(String.valueOf(smokeSpec.getBlowbackParticleCount()));
            smokeBlowbackDurationEditor.setText(String.valueOf(smokeSpec.getBlowbackDuration()));
            smokeBlowbackLengthEditor.setText(String.valueOf(smokeSpec.getBlowbackLength()));
            smokeBlowbackSpreadEditor.setText(String.valueOf(smokeSpec.getBlowbackSpread()));
            WeaponFirePanelUtilities.updateColorLabel(smokeParticleColorValue, smokeSpec.getParticleColor());
        } else {
            clear();
        }
    }

    public void clear() {
        smokeParticleSizeMinEditor.setText("");
        smokeParticleSizeRangeEditor.setText("");
        smokeCloudParticleCountEditor.setText("");
        smokeCloudDurationEditor.setText("");
        smokeCloudRadiusEditor.setText("");
        smokeBlowbackParticleCountEditor.setText("");
        smokeBlowbackDurationEditor.setText("");
        smokeBlowbackLengthEditor.setText("");
        smokeBlowbackSpreadEditor.setText("");
        WeaponFirePanelUtilities.updateColorLabel(smokeParticleColorValue, null);
    }
}
