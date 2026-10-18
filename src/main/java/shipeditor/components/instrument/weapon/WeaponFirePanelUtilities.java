package shipeditor.components.instrument.weapon;

import shipeditor.utility.text.StringManager;

import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.MouseoverLabelListener;
import shipeditor.utility.graphics.ColorUtilities;
import shipeditor.utility.themes.Themes;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Insets;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class WeaponFirePanelUtilities {

    public static JTextField createTextField(String tooltip, Supplier<Boolean> readinessChecker, Consumer<String> setter, Runnable onChange) {
        JTextField textField = new JTextField();
        textField.setColumns(10);
        if (tooltip != null) textField.setToolTipText(tooltip);
        textField.addActionListener(e -> {
            if (readinessChecker.get()) {
                setter.accept(textField.getText());
                onChange.run();
            }
        });
        return textField;
    }

    public static JTextField createDoubleField(String tooltip, Supplier<Boolean> readinessChecker, Consumer<Double> setter, Runnable onChange) {
        JTextField textField = new JTextField();
        textField.setColumns(10);
        if (tooltip != null) textField.setToolTipText(tooltip);
        textField.addActionListener(e -> {
            if (readinessChecker.get()) {
                try {
                    setter.accept(Double.parseDouble(textField.getText()));
                    onChange.run();
                } catch (NumberFormatException ex) {
                    // Ignore invalid input
                }
            }
        });
        return textField;
    }

    public static JTextField createIntField(String tooltip, Supplier<Boolean> readinessChecker, Consumer<Integer> setter, Runnable onChange) {
        JTextField textField = new JTextField();
        textField.setColumns(10);
        if (tooltip != null) textField.setToolTipText(tooltip);
        textField.addActionListener(e -> {
            if (readinessChecker.get()) {
                try {
                    setter.accept(Integer.parseInt(textField.getText()));
                    onChange.run();
                } catch (NumberFormatException ex) {
                    // Ignore invalid input
                }
            }
        });
        return textField;
    }

    /**
     * Creates a double field bound to a sub-spec of the current weapon spec (muzzle flash, smoke, ...).
     *
     * @param specResolver returns the sub-spec for the weapon spec, creating it when absent.
     * @param setter       applies the parsed value to the resolved sub-spec.
     */
    public static <S> JTextField createSpecDoubleField(Supplier<Boolean> readinessChecker, Runnable onChange,
                                                       Supplier<WeaponSpecFile> specSupplier,
                                                       Function<WeaponSpecFile, S> specResolver,
                                                       BiConsumer<S, Double> setter) {
        return createDoubleField(null, readinessChecker, value -> {
            WeaponSpecFile spec = specSupplier.get();
            if (spec != null) setter.accept(specResolver.apply(spec), value);
        }, onChange);
    }

    /**
     * Integer counterpart of {@link #createSpecDoubleField}.
     */
    public static <S> JTextField createSpecIntField(Supplier<Boolean> readinessChecker, Runnable onChange,
                                                    Supplier<WeaponSpecFile> specSupplier,
                                                    Function<WeaponSpecFile, S> specResolver,
                                                    BiConsumer<S, Integer> setter) {
        return createIntField(null, readinessChecker, value -> {
            WeaponSpecFile spec = specSupplier.get();
            if (spec != null) setter.accept(specResolver.apply(spec), value);
        }, onChange);
    }

    public static JCheckBox createCheckBox(String text, String tooltip, Supplier<Boolean> readinessChecker, Consumer<Boolean> setter, Runnable onChange) {
        JCheckBox checkBox = new JCheckBox(text);
        if (tooltip != null) checkBox.setToolTipText(tooltip);
        checkBox.addActionListener(e -> {
            if (readinessChecker.get()) {
                setter.accept(checkBox.isSelected());
                onChange.run();
            }
        });
        return checkBox;
    }

    public static JLabel createColorLabel(String labelText, JLabel valueLabel, Supplier<Color> getter, Consumer<Color> setter) {
        JLabel label = new JLabel(labelText);
        label.setToolTipText(StringManager.getString("RIGHT_CLICK_TO_CHANGE_COLOR"));

        JPopupMenu colorChooserMenu = new JPopupMenu();
        JMenuItem adjustColor = new JMenuItem(StringManager.getString("ADJUST_VALUE"));
        adjustColor.addActionListener(event -> {
            Color current = getter.get();
            Color chosen = current != null ? ColorUtilities.showColorChooser(current) : ColorUtilities.showColorChooser();
            if (chosen != null) {
                setter.accept(chosen);
            }
        });
        colorChooserMenu.add(adjustColor);

        JMenuItem removeColor = new JMenuItem(StringManager.getString("CLEAR_VALUE"));
        removeColor.addActionListener(event -> setter.accept(null));
        colorChooserMenu.add(removeColor);

        label.addMouseListener(new MouseoverLabelListener(colorChooserMenu, label));
        valueLabel.addMouseListener(new MouseoverLabelListener(colorChooserMenu, valueLabel));
        Insets insets = ComponentUtilities.createLabelInsets();
        insets.top = 1;
        label.setBorder(ComponentUtilities.createLabelSimpleBorder(insets));

        return label;
    }

    public static void updateColorLabel(JLabel valueLabel, Color color) {
        if (color != null) {
            valueLabel.setIcon(ComponentUtilities.createIconFromColor(color, 10, 10));
            valueLabel.setOpaque(true);
            valueLabel.setBorder(new FlatLineBorder(new Insets(2, 2, 2, 2), Themes.getBorderColor()));
            valueLabel.setBackground(Themes.getPanelHighlightColor());
            valueLabel.setToolTipText(ColorUtilities.getColorBreakdown(color));
            valueLabel.setText(null);
        } else {
            valueLabel.setIcon(null);
            valueLabel.setOpaque(false);
            valueLabel.setBorder(new EmptyBorder(0, 2, 0, 2));
            valueLabel.setBackground(null);
            valueLabel.setToolTipText(null);
            valueLabel.setText(StringManager.getString("NOT_DEFINED"));
        }
        valueLabel.setForeground(Themes.getTextColor());
    }
}
