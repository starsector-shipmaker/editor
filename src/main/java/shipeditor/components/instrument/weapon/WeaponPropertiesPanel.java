package shipeditor.components.instrument.weapon;

import shipeditor.utility.text.StringManager;

import shipeditor.components.viewer.layers.LayerPainter;
import shipeditor.components.viewer.layers.weapon.WeaponLayer;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.utility.components.CollapsibleSection;
import shipeditor.utility.components.ComponentUtilities;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Unified weapon properties panel. Merges the old WeaponDataPanel, WeaponFirePanel,
 * and WeaponBeamPanel into collapsible sections within a single scrollable tab.
 */
public class WeaponPropertiesPanel extends AbstractWeaponPropertiesPanel {

    

    // --- Identity section ---
    private JTextField idEditor;
    private JComboBox<String> specClassSelector;
    private JComboBox<WeaponType> typeSelector;
    private JComboBox<WeaponSize> sizeSelector;
    private JComboBox<WeaponType> mountTypeOverrideSelector;

    // --- Collision section ---
    private JComboBox<String> collisionClassSelector;
    private JComboBox<String> collisionClassByFighterSelector;

    // --- Handlers (kept as separate objects for clean separation) ---
    private WeaponProjectileHandler projectileHandler;
    private WeaponFiringLogicHandler firingLogicHandler;
    private WeaponAudioHandler audioHandler;

    // --- Firing Logic misc checkboxes (from old WeaponDataPanel) ---
    private JCheckBox showDamageWhenDecorativeCheckbox;
    private JCheckBox passThroughMissilesCheckbox;

    // --- Beam section ---
    private JTextField everyFrameEffectEditor;
    private JTextField beamEffectEditor;
    private JCheckBox beamFireOnlyOnFullChargeCheckbox;
    private JCheckBox useGlowColorForHitGlowCheckbox;
    private JCheckBox darkCoreCheckbox;
    private JCheckBox convergeOnPointCheckbox;
    private JCheckBox skipIdleFrameIfZeroBurstDelayCheckbox;
    private JTextField widthEditor;
    private JTextField coreWidthMultEditor;
    private JTextField textureScrollSpeedEditor;
    private JTextField pixelsPerTexelEditor;
    private JTextField fringeScrollSpeedMultEditor;
    private JTextField hitGlowBrightenDurationEditor;
    private JTextField hitGlowRadiusEditor;
    private JTextField specialWeaponGlowWidthEditor;
    private JTextField specialWeaponGlowHeightEditor;
    private JTextField textureTypeEditor;
    private JTextField pierceSetEditor;

    public WeaponPropertiesPanel() {
        super();
    }

    @Override
    protected void populateContent() {
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        Supplier<Boolean> readinessChecker = () -> isWidgetsReadyForInput();
        Runnable onChange = this::processChange;
        Supplier<WeaponSpecFile> specSupplier = () -> cachedLayer != null ? cachedLayer.getSpecFile() : null;

        this.add(createIdentitySection());
        this.add(createCollisionSection());
        this.add(createProjectileSection(readinessChecker, onChange, specSupplier));
        this.add(createFiringLogicSection(readinessChecker, onChange, specSupplier));
        this.add(createAudioSection(readinessChecker, onChange, specSupplier));
        this.add(createBeamSection());

        clearData();
    }

    private static final String[] SPEC_CLASS_SUGGESTIONS = {"projectile", "beam"};

    private static final String[] COLLISION_CLASS_SUGGESTIONS = {
        "NONE", "RAY", "RAY_FIGHTER", "FIGHTER", "SHIP",
        "PROJECTILE_NO_FF", "PROJECTILE_FF",
        "MISSILE_NO_FF", "MISSILE_FF",
        "HITS_SHIPS_AND_ASTEROIDS",
        "HITS_SHIPS_ONLY_FF", "HITS_SHIPS_ONLY_NO_FF",
        "PROJECTILE_FIGHTER", "ASTEROID"
    };

    // ========== Identity Section ==========

    private CollapsibleSection createIdentitySection() {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");
        int row = 0;

        idEditor = new JTextField();
        idEditor.setColumns(10);
        idEditor.setEditable(false);
        idEditor.setToolTipText(StringManager.getString("ID_IS_READ_ONLY_FROM_THE_SPEC_FILE"));
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("ID")), idEditor, row++);

        specClassSelector = new JComboBox<>(SPEC_CLASS_SUGGESTIONS);
        specClassSelector.setEditable(true);
        specClassSelector.addActionListener(e -> {
            if (isWidgetsReadyForInput() && cachedLayer != null) {
                WeaponSpecFile spec = cachedLayer.getSpecFile();
                if (spec != null) {
                    String selected = (String) specClassSelector.getSelectedItem();
                    if (!Objects.equals(spec.getSpecClass(), selected)) {
                        spec.setSpecClass(selected);
                        processChange();
                    }
                }
            }
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("SPEC_CLASS")), specClassSelector, row++);

        typeSelector = new JComboBox<>(WeaponType.values());
        typeSelector.addActionListener(e -> {
            if (isWidgetsReadyForInput() && cachedLayer != null) {
                WeaponSpecFile spec = cachedLayer.getSpecFile();
                if (spec != null) {
                    WeaponType selected = (WeaponType) typeSelector.getSelectedItem();
                    if (spec.getType() != selected) {
                        spec.setType(selected);
                        processChange();
                    }
                }
            }
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("TYPE")), typeSelector, row++);

        sizeSelector = new JComboBox<>(WeaponSize.values());
        sizeSelector.addActionListener(e -> {
            if (isWidgetsReadyForInput() && cachedLayer != null) {
                WeaponSpecFile spec = cachedLayer.getSpecFile();
                if (spec != null) {
                    WeaponSize selected = (WeaponSize) sizeSelector.getSelectedItem();
                    if (spec.getSize() != selected) {
                        spec.setSize(selected);
                        processChange();
                    }
                }
            }
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("SIZE_1")), sizeSelector, row++);

        mountTypeOverrideSelector = new JComboBox<>(WeaponType.values());
        mountTypeOverrideSelector.addActionListener(e -> {
            if (isWidgetsReadyForInput() && cachedLayer != null) {
                WeaponSpecFile spec = cachedLayer.getSpecFile();
                if (spec != null) {
                    WeaponType selected = (WeaponType) mountTypeOverrideSelector.getSelectedItem();
                    if (spec.getMountTypeOverride() != selected) {
                        spec.setMountTypeOverride(selected);
                        processChange();
                    }
                }
            }
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("MOUNT_TYPE_OVERRIDE")), mountTypeOverrideSelector, row++);

        return new CollapsibleSection("Identity", content);
    }

    // ========== Collision Section ==========

    private CollapsibleSection createCollisionSection() {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");
        int row = 0;

        collisionClassSelector = new JComboBox<>(COLLISION_CLASS_SUGGESTIONS);
        collisionClassSelector.setEditable(true);
        collisionClassSelector.addActionListener(e -> {
            if (isWidgetsReadyForInput() && cachedLayer != null) {
                WeaponSpecFile spec = cachedLayer.getSpecFile();
                if (spec != null) {
                    String text = (String) collisionClassSelector.getSelectedItem();
                    if (!Objects.equals(spec.getCollisionClass(), text)) {
                        spec.setCollisionClass(text);
                        processChange();
                    }
                }
            }
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("COLLISION_CLASS")), collisionClassSelector, row++);

        collisionClassByFighterSelector = new JComboBox<>(COLLISION_CLASS_SUGGESTIONS);
        collisionClassByFighterSelector.setEditable(true);
        collisionClassByFighterSelector.addActionListener(e -> {
            if (isWidgetsReadyForInput() && cachedLayer != null) {
                WeaponSpecFile spec = cachedLayer.getSpecFile();
                if (spec != null) {
                    String text = (String) collisionClassByFighterSelector.getSelectedItem();
                    if (!Objects.equals(spec.getCollisionClassByFighter(), text)) {
                        spec.setCollisionClassByFighter(text);
                        processChange();
                    }
                }
            }
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("BY_FIGHTER")), collisionClassByFighterSelector, row++);

        return new CollapsibleSection("Collision", content);
    }

    // ========== Projectile Section ==========

    private CollapsibleSection createProjectileSection(Supplier<Boolean> readinessChecker, Runnable onChange, Supplier<WeaponSpecFile> specSupplier) {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");

        projectileHandler = new WeaponProjectileHandler(readinessChecker, onChange, specSupplier);
        projectileHandler.populate(content, 0);

        return new CollapsibleSection("Projectile", content);
    }

    // ========== Firing Logic Section ==========

    private CollapsibleSection createFiringLogicSection(Supplier<Boolean> readinessChecker, Runnable onChange, Supplier<WeaponSpecFile> specSupplier) {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");

        firingLogicHandler = new WeaponFiringLogicHandler(readinessChecker, onChange, specSupplier);
        int row = firingLogicHandler.populate(content, 0);

        // Misc checkboxes from old WeaponDataPanel
        showDamageWhenDecorativeCheckbox = WeaponFirePanelUtilities.createCheckBox("Show Damage When Decorative", "Show damage state even when slot is decorative", readinessChecker, value -> {
            WeaponSpecFile spec = specSupplier.get();
            if (spec != null) spec.setShowDamageWhenDecorative(value);
        }, onChange);
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), showDamageWhenDecorativeCheckbox, row++);

        passThroughMissilesCheckbox = WeaponFirePanelUtilities.createCheckBox("Pass Through Missiles", "Projectiles from this weapon pass through missiles", readinessChecker, value -> {
            WeaponSpecFile spec = specSupplier.get();
            if (spec != null) spec.setPassThroughMissiles(value);
        }, onChange);
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), passThroughMissilesCheckbox, row++);

        return new CollapsibleSection("Firing Logic", content);
    }

    // ========== Audio Section ==========

    private CollapsibleSection createAudioSection(Supplier<Boolean> readinessChecker, Runnable onChange, Supplier<WeaponSpecFile> specSupplier) {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");

        audioHandler = new WeaponAudioHandler(readinessChecker, onChange, specSupplier);
        audioHandler.populate(content, 0);

        return new CollapsibleSection("Audio", content, true);
    }

    // ========== Beam Section ==========

    private CollapsibleSection createBeamSection() {
        JPanel content = new JPanel(new GridBagLayout());
        ComponentUtilities.outfitPanelWithTitle(content, new Insets(1, 0, 0, 0), "");
        int row = 0;

        everyFrameEffectEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setEveryFrameEffect(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("EVERY_FRAME_EFFECT")), everyFrameEffectEditor, row++);

        beamEffectEditor = createTextField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setBeamEffect(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("BEAM_EFFECT")), beamEffectEditor, row++);

        beamFireOnlyOnFullChargeCheckbox = createCheckBox("Beam Fire Only On Full Charge", value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setBeamFireOnlyOnFullCharge(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), beamFireOnlyOnFullChargeCheckbox, row++);

        widthEditor = createDoubleField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setWidth(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("WIDTH")), widthEditor, row++);

        coreWidthMultEditor = createDoubleField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setCoreWidthMult(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("CORE_WIDTH_MULT")), coreWidthMultEditor, row++);

        textureScrollSpeedEditor = createDoubleField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setTextureScrollSpeed(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("TEXTURE_SCROLL_SPEED")), textureScrollSpeedEditor, row++);

        fringeScrollSpeedMultEditor = createDoubleField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setFringeScrollSpeedMult(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("FRINGE_SCROLL_SPEED_MULT")), fringeScrollSpeedMultEditor, row++);

        pixelsPerTexelEditor = createDoubleField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setPixelsPerTexel(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("PIXELS_PER_TEXEL")), pixelsPerTexelEditor, row++);

        textureTypeEditor = createListField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setTextureType(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("TEXTURE_TYPE")), textureTypeEditor, row++);

        hitGlowBrightenDurationEditor = createIntField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setHitGlowBrightenDuration(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("HIT_GLOW_BRIGHTEN_DURATION")), hitGlowBrightenDurationEditor, row++);

        hitGlowRadiusEditor = createIntField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setHitGlowRadius(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("HIT_GLOW_RADIUS")), hitGlowRadiusEditor, row++);

        specialWeaponGlowWidthEditor = createIntField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setSpecialWeaponGlowWidth(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("SPECIAL_WEAPON_GLOW_WIDTH")), specialWeaponGlowWidthEditor, row++);

        specialWeaponGlowHeightEditor = createIntField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setSpecialWeaponGlowHeight(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("SPECIAL_WEAPON_GLOW_HEIGHT")), specialWeaponGlowHeightEditor, row++);

        pierceSetEditor = createListField(value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setPierceSet(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(StringManager.getString("PIERCE_SET")), pierceSetEditor, row++);

        useGlowColorForHitGlowCheckbox = createCheckBox("Use Glow Color For Hit Glow", value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setUseGlowColorForHitGlow(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), useGlowColorForHitGlowCheckbox, row++);

        darkCoreCheckbox = createCheckBox("Dark Core", value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setDarkCore(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), darkCoreCheckbox, row++);

        convergeOnPointCheckbox = createCheckBox("Converge On Point", value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setConvergeOnPoint(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), convergeOnPointCheckbox, row++);

        skipIdleFrameIfZeroBurstDelayCheckbox = createCheckBox("Skip Idle Frame If Zero Burst Delay", value -> {
            if (cachedLayer != null && cachedLayer.getSpecFile() != null)
                cachedLayer.getSpecFile().setSkipIdleFrameIfZeroBurstDelay(value);
        });
        ComponentUtilities.addLabelAndComponent(content, new JLabel(), skipIdleFrameIfZeroBurstDelayCheckbox, row++);

        return new CollapsibleSection("Beam", content, true);
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

        

        // Identity
        idEditor.setText(spec.getId() != null ? spec.getId() : "");
        specClassSelector.setSelectedItem(spec.getSpecClass() != null ? spec.getSpecClass() : "");
        typeSelector.setSelectedItem(spec.getType());
        sizeSelector.setSelectedItem(spec.getSize());
        mountTypeOverrideSelector.setSelectedItem(spec.getMountTypeOverride());

        // Collision
        collisionClassSelector.setSelectedItem(spec.getCollisionClass() != null ? spec.getCollisionClass() : "");
        collisionClassByFighterSelector.setSelectedItem(spec.getCollisionClassByFighter() != null ? spec.getCollisionClassByFighter() : "");

        // Projectile
        projectileHandler.refresh(spec);

        // Firing Logic
        firingLogicHandler.refresh(spec);
        showDamageWhenDecorativeCheckbox.setSelected(spec.isShowDamageWhenDecorative());
        passThroughMissilesCheckbox.setSelected(spec.isPassThroughMissiles());

        // Audio
        audioHandler.refresh(spec);

        // Beam
        everyFrameEffectEditor.setText(spec.getEveryFrameEffect() != null ? spec.getEveryFrameEffect() : "");
        beamEffectEditor.setText(spec.getBeamEffect() != null ? spec.getBeamEffect() : "");
        beamFireOnlyOnFullChargeCheckbox.setSelected(spec.isBeamFireOnlyOnFullCharge());
        widthEditor.setText(String.valueOf(spec.getWidth()));
        coreWidthMultEditor.setText(String.valueOf(spec.getCoreWidthMult()));
        textureScrollSpeedEditor.setText(String.valueOf(spec.getTextureScrollSpeed()));
        fringeScrollSpeedMultEditor.setText(String.valueOf(spec.getFringeScrollSpeedMult()));
        pixelsPerTexelEditor.setText(String.valueOf(spec.getPixelsPerTexel()));
        textureTypeEditor.setText(spec.getTextureType() != null ? String.join(", ", spec.getTextureType()) : "");
        hitGlowBrightenDurationEditor.setText(String.valueOf(spec.getHitGlowBrightenDuration()));
        hitGlowRadiusEditor.setText(String.valueOf(spec.getHitGlowRadius()));
        specialWeaponGlowWidthEditor.setText(String.valueOf(spec.getSpecialWeaponGlowWidth()));
        specialWeaponGlowHeightEditor.setText(String.valueOf(spec.getSpecialWeaponGlowHeight()));
        pierceSetEditor.setText(spec.getPierceSet() != null ? String.join(", ", spec.getPierceSet()) : "");
        useGlowColorForHitGlowCheckbox.setSelected(spec.isUseGlowColorForHitGlow());
        darkCoreCheckbox.setSelected(spec.isDarkCore());
        convergeOnPointCheckbox.setSelected(spec.isConvergeOnPoint());
        skipIdleFrameIfZeroBurstDelayCheckbox.setSelected(spec.isSkipIdleFrameIfZeroBurstDelay());

        
    }

    private void clearData() {
        

        // Identity
        idEditor.setText("");
        specClassSelector.setSelectedItem(null);
        typeSelector.setSelectedItem(null);
        sizeSelector.setSelectedItem(null);
        mountTypeOverrideSelector.setSelectedItem(null);

        // Collision
        collisionClassSelector.setSelectedItem(null);
        collisionClassByFighterSelector.setSelectedItem(null);

        // Projectile
        if (projectileHandler != null) projectileHandler.clear();

        // Firing Logic
        if (firingLogicHandler != null) firingLogicHandler.clear();
        if (showDamageWhenDecorativeCheckbox != null) showDamageWhenDecorativeCheckbox.setSelected(false);
        if (passThroughMissilesCheckbox != null) passThroughMissilesCheckbox.setSelected(false);

        // Audio
        if (audioHandler != null) audioHandler.clear();

        // Beam
        if (everyFrameEffectEditor != null) everyFrameEffectEditor.setText("");
        if (beamEffectEditor != null) beamEffectEditor.setText("");
        if (beamFireOnlyOnFullChargeCheckbox != null) beamFireOnlyOnFullChargeCheckbox.setSelected(false);
        if (widthEditor != null) widthEditor.setText("");
        if (coreWidthMultEditor != null) coreWidthMultEditor.setText("");
        if (textureScrollSpeedEditor != null) textureScrollSpeedEditor.setText("");
        if (fringeScrollSpeedMultEditor != null) fringeScrollSpeedMultEditor.setText("");
        if (pixelsPerTexelEditor != null) pixelsPerTexelEditor.setText("");
        if (textureTypeEditor != null) textureTypeEditor.setText("");
        if (hitGlowBrightenDurationEditor != null) hitGlowBrightenDurationEditor.setText("");
        if (hitGlowRadiusEditor != null) hitGlowRadiusEditor.setText("");
        if (specialWeaponGlowWidthEditor != null) specialWeaponGlowWidthEditor.setText("");
        if (specialWeaponGlowHeightEditor != null) specialWeaponGlowHeightEditor.setText("");
        if (pierceSetEditor != null) pierceSetEditor.setText("");
        if (useGlowColorForHitGlowCheckbox != null) useGlowColorForHitGlowCheckbox.setSelected(false);
        if (darkCoreCheckbox != null) darkCoreCheckbox.setSelected(false);
        if (convergeOnPointCheckbox != null) convergeOnPointCheckbox.setSelected(false);
        if (skipIdleFrameIfZeroBurstDelayCheckbox != null) skipIdleFrameIfZeroBurstDelayCheckbox.setSelected(false);

        cachedLayer = null;
    }
}
