package shipeditor.components.instrument.projectile;

import shipeditor.utility.text.StringManager;

import lombok.extern.log4j.Log4j2;
import shipeditor.communication.EventBus;
import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.weapon.ProjectileLayer;
import shipeditor.representation.weapon.ProjectileSpecFile;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.JLabel;
import javax.swing.JTextField;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Log4j2
public class ProjectileDataPanel extends AbstractProjectilePropertiesPanel {

    private ProjectileLayer cachedLayer;
    private JTextField idEditor;
    private JTextField specClassEditor;
    private JTextField missileTypeEditor;

    private boolean readyForInput;

    public ProjectileDataPanel() {
        super();
    }

    @Override
    protected void populateContent() {
        this.setLayout(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(this, new Insets(1, 0, 0, 0), "Projectile data");

        addIDPanel();
        addSpecClassPanel();
        addMissileTypePanel();

        clearData();
    }

    private void addIDPanel() {
        JLabel label = new JLabel(StringManager.getString("ID"));
        idEditor = new JTextField();
        idEditor.setColumns(10);
        idEditor.setEditable(false);
        idEditor.setToolTipText(StringManager.getString("ID_IS_READ_ONLY_FROM_THE_SPEC_FILE"));

        ComponentUtilities.addLabelAndComponent(this, label, idEditor, 0);
    }

    private void addSpecClassPanel() {
        specClassEditor = createSpecTextEditor("SPEC_CLASS", 1,
                ProjectileSpecFile::getSpecClass, ProjectileSpecFile::setSpecClass);
    }

    private void addMissileTypePanel() {
        missileTypeEditor = createSpecTextEditor("MISSILE_TYPE", 2,
                ProjectileSpecFile::getMissileType, ProjectileSpecFile::setMissileType);
    }

    /**
     * Adds a labelled text field editing one string property of the projectile spec file, committing on enter.
     */
    private JTextField createSpecTextEditor(String labelKey, int row,
                                            Function<ProjectileSpecFile, String> getter,
                                            BiConsumer<ProjectileSpecFile, String> setter) {
        JLabel label = new JLabel(StringManager.getString(labelKey));
        JTextField editor = new JTextField();
        editor.setColumns(10);
        editor.addActionListener(e -> {
            if (readyForInput && cachedLayer != null) {
                ProjectileSpecFile spec = cachedLayer.getSpecFile();
                if (spec != null) {
                    String text = editor.getText();
                    if (!java.util.Objects.equals(getter.apply(spec), text)) {
                        log.debug("Committing projectile spec property {} = {}", labelKey, text);
                        setter.accept(spec, text);
                        EventBus.publish(new shipeditor.communication.events.components.ComponentEvents.LayerTabUpdated(cachedLayer));
                        processChange();
                    }
                }
            }
        });

        ComponentUtilities.addLabelAndComponent(this, label, editor, row);
        return editor;
    }

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        if (layerPainter == null || !(layerPainter.getParentLayer() instanceof ProjectileLayer projectileLayer)) {
            clearData();
            return;
        }
        cachedLayer = projectileLayer;
        ProjectileSpecFile spec = cachedLayer.getSpecFile();
        if (spec == null) {
            clearData();
            return;
        }

        readyForInput = false;

        idEditor.setText(spec.getId() != null ? spec.getId() : "");
        specClassEditor.setText(spec.getSpecClass() != null ? spec.getSpecClass() : "");
        missileTypeEditor.setText(spec.getMissileType() != null ? spec.getMissileType() : "");

        readyForInput = true;
    }

    private void clearData() {
        readyForInput = false;

        idEditor.setText("");
        specClassEditor.setText("");
        missileTypeEditor.setText("");

        cachedLayer = null;
    }
}
