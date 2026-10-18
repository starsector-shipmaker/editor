package shipeditor.components.instrument.weapon;

import shipeditor.utility.text.StringManager;

import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.utility.components.CollapsibleSection;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Supplier;

/**
 * Sprites and rendering panel with collapsible sections.
 * Includes turret/hardpoint sprites, render flags, colors, animation,
 * muzzle flash, smoke, and misc rendering fields.
 */
public class WeaponVisualsPanel extends AbstractWeaponPropertiesPanel {

    

    // Turret sprites
    private JTextField turretSpriteEditor;
    private JTextField turretUnderSpriteEditor;
    private JTextField turretGunSpriteEditor;
    private JTextField turretGlowSpriteEditor;

    // Hardpoint sprites
    private JTextField hardpointSpriteEditor;
    private JTextField hardpointUnderSpriteEditor;
    private JTextField hardpointGunSpriteEditor;
    private JTextField hardpointGlowSpriteEditor;

    // Render flags
    private JCheckBox renderBelowWeaponsCheckbox;
    private JCheckBox renderAboveWeaponsCheckbox;
    private JCheckBox renderAdditiveCheckbox;

    // Colors
    private JLabel fringeColorValue;
    private JLabel coreColorValue;
    private JLabel glowColorValue;

    // Animation
    private JTextField numFramesEditor;
    private JTextField frameRateEditor;
    private JCheckBox alwaysAnimateCheckbox;

    // Misc
    private JTextField renderHintsEditor;
    private JTextField displayArcRadiusEditor;

    // Handlers (kept as handler objects for clean separation)
    private WeaponMuzzleFlashHandler muzzleFlashHandler;
    private WeaponSmokeHandler smokeHandler;

    public WeaponVisualsPanel() {
        super();
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        Supplier<Boolean> readinessChecker = () -> isWidgetsReadyForInput();
        Runnable onChange = this::processChange;
        Supplier<WeaponSpecFile> specSupplier = () -> cachedLayer != null ? cachedLayer.getSpecFile() : null;

        this.add(createTurretSpritesSection());
        this.add(createHardpointSpritesSection());
        this.add(createRenderFlagsSection());
        this.add(createColorsSection());
        this.add(createAnimationSection());
        this.add(createMuzzleFlashSection(readinessChecker, onChange, specSupplier));
        this.add(createSmokeSection(readinessChecker, onChange, specSupplier));
        this.add(createMiscSection());

        clearData();
    }

    // ========== Turret Sprites ==========

    private CollapsibleSection createTurretSpritesSection() {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");
        int row = 0;

        turretSpriteEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setTurretSprite(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("SPRITE")), turretSpriteEditor, row++);

        turretUnderSpriteEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setTurretUnderSprite(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("UNDER_SPRITE")), turretUnderSpriteEditor, row++);

        turretGunSpriteEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setTurretGunSprite(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("GUN_SPRITE")), turretGunSpriteEditor, row++);

        turretGlowSpriteEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setTurretGlowSprite(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("GLOW_SPRITE")), turretGlowSpriteEditor, row++);

        return new CollapsibleSection("Turret Sprites", content);
    }

    // ========== Hardpoint Sprites ==========

    private CollapsibleSection createHardpointSpritesSection() {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");
        int row = 0;

        hardpointSpriteEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setHardpointSprite(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("SPRITE")), hardpointSpriteEditor, row++);

        hardpointUnderSpriteEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setHardpointUnderSprite(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("UNDER_SPRITE")), hardpointUnderSpriteEditor, row++);

        hardpointGunSpriteEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setHardpointGunSprite(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("GUN_SPRITE")), hardpointGunSpriteEditor, row++);

        hardpointGlowSpriteEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setHardpointGlowSprite(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("GLOW_SPRITE")), hardpointGlowSpriteEditor, row++);

        return new CollapsibleSection("Hardpoint Sprites", content);
    }

    // ========== Render Flags ==========

    private CollapsibleSection createRenderFlagsSection() {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");
        int row = 0;

        renderBelowWeaponsCheckbox = createCheckBox("Render Below All Weapons", value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setRenderBelowAllWeapons(value);
        });
        renderBelowWeaponsCheckbox.setToolTipText(StringManager.getString("RENDER_THIS_WEAPON_BELOW_ALL_OTHER_WEAPONS_USEFUL_FOR_DECORATIVE_PARTS"));
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), renderBelowWeaponsCheckbox, row++);

        renderAboveWeaponsCheckbox = createCheckBox("Render Above All Weapons", value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setRenderAboveAllWeapons(value);
        });
        renderAboveWeaponsCheckbox.setToolTipText(StringManager.getString("RENDER_THIS_WEAPON_ABOVE_ALL_OTHER_WEAPONS"));
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), renderAboveWeaponsCheckbox, row++);

        renderAdditiveCheckbox = createCheckBox("Render Additive", value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setRenderAdditive(value);
        });
        renderAdditiveCheckbox.setToolTipText(StringManager.getString("RENDER_THIS_WEAPON_WITH_ADDITIVE_BLENDING_USEFUL_FOR_ENERGY_WEAPONS_AND_GLOWS"));
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), renderAdditiveCheckbox, row++);

        return new CollapsibleSection("Render Flags", content);
    }

    // ========== Colors ==========

    private CollapsibleSection createColorsSection() {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");
        int row = 0;

        fringeColorValue = new JLabel();
        JLabel fringeColorLabel = WeaponFirePanelUtilities.createColorLabel("Fringe Color:", fringeColorValue,
                () -> cachedLayer != null ? cachedLayer.getSpecFile().getFringeColor() : null,
                color -> {
                    if (cachedLayer != null && cachedLayer.getSpecFile() != null) {
                        cachedLayer.getSpecFile().setFringeColor(color);
                        processChange();
                    }
                });
        ComponentUtilities.addLabelAndComponent(content, fringeColorLabel, fringeColorValue, row++);

        coreColorValue = new JLabel();
        JLabel coreColorLabel = WeaponFirePanelUtilities.createColorLabel("Core Color:", coreColorValue,
                () -> cachedLayer != null ? cachedLayer.getSpecFile().getCoreColor() : null,
                color -> {
                    if (cachedLayer != null && cachedLayer.getSpecFile() != null) {
                        cachedLayer.getSpecFile().setCoreColor(color);
                        processChange();
                    }
                });
        ComponentUtilities.addLabelAndComponent(content, coreColorLabel, coreColorValue, row++);

        glowColorValue = new JLabel();
        JLabel glowColorLabel = WeaponFirePanelUtilities.createColorLabel("Glow Color:", glowColorValue,
                () -> cachedLayer != null ? cachedLayer.getSpecFile().getGlowColor() : null,
                color -> {
                    if (cachedLayer != null && cachedLayer.getSpecFile() != null) {
                        cachedLayer.getSpecFile().setGlowColor(color);
                        processChange();
                    }
                });
        ComponentUtilities.addLabelAndComponent(content, glowColorLabel, glowColorValue, row++);

        return new CollapsibleSection("Colors", content);
    }

    // ========== Animation ==========

    private CollapsibleSection createAnimationSection() {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");
        int row = 0;

        numFramesEditor = createIntField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setNumFrames(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("NUM_FRAMES")), numFramesEditor, row++);

        frameRateEditor = createIntField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setFrameRate(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("FRAME_RATE")), frameRateEditor, row++);

        alwaysAnimateCheckbox = createCheckBox("Always Animate", value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setAlwaysAnimate(value);
        });
        alwaysAnimateCheckbox.setToolTipText(StringManager.getString("ALWAYS_PLAY_THE_ANIMATION_EVEN_WHEN_NOT_FIRING"));
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), alwaysAnimateCheckbox, row++);

        return new CollapsibleSection("Animation", content);
    }

    // ========== Muzzle Flash ==========

    private CollapsibleSection createMuzzleFlashSection(Supplier<Boolean> readinessChecker, Runnable onChange, Supplier<WeaponSpecFile> specSupplier) {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");

        muzzleFlashHandler = new WeaponMuzzleFlashHandler(readinessChecker, onChange, specSupplier);
        // Populate without the bold header label (the collapsible section header replaces it).
        // The handler's populate() adds its own "<b>Muzzle Flash</b>" label at row 0, so we skip it.
        // We re-populate manually to avoid the header label.
        muzzleFlashHandler.populate(content, 0);

        return new CollapsibleSection("Muzzle Flash", content, true);
    }

    // ========== Smoke ==========

    private CollapsibleSection createSmokeSection(Supplier<Boolean> readinessChecker, Runnable onChange, Supplier<WeaponSpecFile> specSupplier) {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");

        smokeHandler = new WeaponSmokeHandler(readinessChecker, onChange, specSupplier);
        smokeHandler.populate(content, 0);

        return new CollapsibleSection("Smoke", content, true);
    }

    // ========== Misc ==========

    private CollapsibleSection createMiscSection() {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");
        int row = 0;

        renderHintsEditor = createListField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setRenderHints(value);
        });
        renderHintsEditor.setToolTipText(StringManager.getString("COMMA_SEPARATED_RENDER_HINTS_E_G_RENDER_BARREL_BELOW_SUSPEND_RECOIL"));
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("RENDER_HINTS")), renderHintsEditor, row++);

        displayArcRadiusEditor = createDoubleField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setDisplayArcRadius(value);
        });
        displayArcRadiusEditor.setToolTipText(StringManager.getString("RADIUS_OF_THE_FIRING_ARC_DISPLAY_0_DEFAULT"));
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("DISPLAY_ARC_RADIUS")), displayArcRadiusEditor, row++);

        return new CollapsibleSection("Misc", content, true);
    }

    // ========== Refresh / Clear ==========

    @Override
    public void refreshContent(LayerPainter layerPainter) {
        if (layerPainter == null || !(layerPainter.getParentLayer() instanceof WeaponLayer weaponLayer)) {
            clearData();
            return;
        }
        cachedLayer = weaponLayer;
        WeaponSpecFile spec = cachedLayer.getSpecFile();
        if (spec == null) {
            clearData();
            return;
        }

        

        // Turret sprites
        turretSpriteEditor.setText(spec.getTurretSprite() != null ? spec.getTurretSprite() : "");
        turretUnderSpriteEditor.setText(spec.getTurretUnderSprite() != null ? spec.getTurretUnderSprite() : "");
        turretGunSpriteEditor.setText(spec.getTurretGunSprite() != null ? spec.getTurretGunSprite() : "");
        turretGlowSpriteEditor.setText(spec.getTurretGlowSprite() != null ? spec.getTurretGlowSprite() : "");

        // Hardpoint sprites
        hardpointSpriteEditor.setText(spec.getHardpointSprite() != null ? spec.getHardpointSprite() : "");
        hardpointUnderSpriteEditor.setText(spec.getHardpointUnderSprite() != null ? spec.getHardpointUnderSprite() : "");
        hardpointGunSpriteEditor.setText(spec.getHardpointGunSprite() != null ? spec.getHardpointGunSprite() : "");
        hardpointGlowSpriteEditor.setText(spec.getHardpointGlowSprite() != null ? spec.getHardpointGlowSprite() : "");

        // Render flags
        renderBelowWeaponsCheckbox.setSelected(spec.isRenderBelowAllWeapons());
        renderAboveWeaponsCheckbox.setSelected(spec.isRenderAboveAllWeapons());
        renderAdditiveCheckbox.setSelected(spec.isRenderAdditive());

        // Colors
        WeaponFirePanelUtilities.updateColorLabel(fringeColorValue, spec.getFringeColor());
        WeaponFirePanelUtilities.updateColorLabel(coreColorValue, spec.getCoreColor());
        WeaponFirePanelUtilities.updateColorLabel(glowColorValue, spec.getGlowColor());

        // Animation
        numFramesEditor.setText(String.valueOf(spec.getNumFrames()));
        frameRateEditor.setText(String.valueOf(spec.getFrameRate()));
        alwaysAnimateCheckbox.setSelected(spec.isAlwaysAnimate());

        // Muzzle flash & smoke
        muzzleFlashHandler.refresh(spec);
        smokeHandler.refresh(spec);

        // Misc
        renderHintsEditor.setText(spec.getRenderHints() != null ? String.join(", ", spec.getRenderHints()) : "");
        displayArcRadiusEditor.setText(String.valueOf(spec.getDisplayArcRadius()));

        
    }

    private void clearData() {
        

        if (turretSpriteEditor != null) turretSpriteEditor.setText("");
        if (turretUnderSpriteEditor != null) turretUnderSpriteEditor.setText("");
        if (turretGunSpriteEditor != null) turretGunSpriteEditor.setText("");
        if (turretGlowSpriteEditor != null) turretGlowSpriteEditor.setText("");

        if (hardpointSpriteEditor != null) hardpointSpriteEditor.setText("");
        if (hardpointUnderSpriteEditor != null) hardpointUnderSpriteEditor.setText("");
        if (hardpointGunSpriteEditor != null) hardpointGunSpriteEditor.setText("");
        if (hardpointGlowSpriteEditor != null) hardpointGlowSpriteEditor.setText("");

        if (renderBelowWeaponsCheckbox != null) renderBelowWeaponsCheckbox.setSelected(false);
        if (renderAboveWeaponsCheckbox != null) renderAboveWeaponsCheckbox.setSelected(false);
        if (renderAdditiveCheckbox != null) renderAdditiveCheckbox.setSelected(false);

        if (fringeColorValue != null) WeaponFirePanelUtilities.updateColorLabel(fringeColorValue, null);
        if (coreColorValue != null) WeaponFirePanelUtilities.updateColorLabel(coreColorValue, null);
        if (glowColorValue != null) WeaponFirePanelUtilities.updateColorLabel(glowColorValue, null);

        if (numFramesEditor != null) numFramesEditor.setText("");
        if (frameRateEditor != null) frameRateEditor.setText("");
        if (alwaysAnimateCheckbox != null) alwaysAnimateCheckbox.setSelected(false);

        if (muzzleFlashHandler != null) muzzleFlashHandler.clear();
        if (smokeHandler != null) smokeHandler.clear();

        if (renderHintsEditor != null) renderHintsEditor.setText("");
        if (displayArcRadiusEditor != null) displayArcRadiusEditor.setText("");

        cachedLayer = null;
    }
}
