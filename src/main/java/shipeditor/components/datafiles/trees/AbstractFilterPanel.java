package shipeditor.components.datafiles.trees;

import shipeditor.utility.text.StringManager;

import shipeditor.utility.components.UIConstants;
import shipeditor.utility.components.widgets.SimpleDocumentListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public abstract class AbstractFilterPanel extends JPanel {

    private final Set<JCheckBox> allFilterBoxes = new HashSet<>();

    protected AbstractFilterPanel() {
        this.setLayout(new BorderLayout());
    }

    protected void initUI() {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.PAGE_AXIS));

        JComponent extraHeader = createHeaderComponent();
        if (extraHeader != null) {
            headerPanel.add(extraHeader);
        }

        headerPanel.add(this.createLogicToggle(isMatchAny(), matchAny -> {
            setMatchAny(matchAny);
            applyFilters();
        }));

        JPanel selectionButtons = this.getSelectionButtonsPanel();
        headerPanel.add(selectionButtons);

        this.add(headerPanel, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        initTabs(tabbedPane);
        this.add(tabbedPane, BorderLayout.CENTER);
    }

    protected JTabbedPane tabbedPane;

    protected void rebuildTabs() {
        tabbedPane.removeAll();
        allFilterBoxes.clear();
        initTabs(tabbedPane);
        tabbedPane.revalidate();
        tabbedPane.repaint();
    }

    protected abstract void applyFilters();

    protected abstract boolean isMatchAny();

    protected abstract void setMatchAny(boolean matchAny);

    protected abstract void initTabs(JTabbedPane tabbedPane);

    protected JComponent createHeaderComponent() {
        return null;
    }

    protected void addTab(JTabbedPane tabbedPane, String title, JPanel contentPanel) {
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        scrollPane.setBorder(null);
        tabbedPane.addTab(title, scrollPane);
    }

    protected JPanel getSelectionButtonsPanel() {
        JButton selectAll = new JButton();
        selectAll.setText(StringManager.getString("SELECT_ALL"));
        selectAll.addActionListener(e -> {
            this.toggleAll(true);
            applyFilters();
        });

        JButton deselectAll = new JButton();
        deselectAll.setText(StringManager.getString("DESELECT_ALL"));
        deselectAll.addActionListener(e -> {
            this.toggleAll(false);
            applyFilters();
        });

        JButton invert = new JButton();
        invert.setText(StringManager.getString("INVERT"));
        invert.addActionListener(e -> {
            this.invertAll();
            applyFilters();
        });

        JPanel buttonContainer = new JPanel();
        buttonContainer.setLayout(new BoxLayout(buttonContainer, BoxLayout.LINE_AXIS));
        buttonContainer.setBorder(new EmptyBorder(6, 6, 2, 0));
        buttonContainer.add(selectAll);
        buttonContainer.add(deselectAll);
        buttonContainer.add(invert);
        return buttonContainer;
    }

    protected abstract void toggleAll(boolean enable);

    protected abstract void invertAll();

    protected void updateAllFilterBoxes(boolean enable) {
        allFilterBoxes.forEach(checkBox -> checkBox.setSelected(enable));
    }

    protected void updateAllFilterBoxesInverted() {
        allFilterBoxes.forEach(checkBox -> checkBox.setSelected(!checkBox.isSelected()));
    }

    private ButtonGroup logicGroup;

    protected JPanel createLogicToggle(boolean isMatchAny, Consumer<Boolean> onToggle) {
        JPanel togglePanel = new JPanel();
        togglePanel.setLayout(new BoxLayout(togglePanel, BoxLayout.LINE_AXIS));
        togglePanel.setBorder(new EmptyBorder(4, 6, 4, 0));

        JRadioButton matchAll = new JRadioButton(StringManager.getString("MATCH_ALL_AND"));
        JRadioButton matchAny = new JRadioButton(StringManager.getString("MATCH_ANY_OR"));

        this.logicGroup = new ButtonGroup();
        this.logicGroup.add(matchAll);
        this.logicGroup.add(matchAny);

        matchAll.setSelected(!isMatchAny);
        matchAny.setSelected(isMatchAny);

        matchAll.addActionListener(e -> onToggle.accept(false));
        matchAny.addActionListener(e -> onToggle.accept(true));

        togglePanel.add(matchAll);
        togglePanel.add(matchAny);
        return togglePanel;
    }

    protected <T> JPanel createFilterSection(String title, Iterable<T> items, Map<T, Boolean> filtersMap,
                                             java.util.function.Function<T, String> nameFunction,
                                             java.util.function.Function<T, JLabel> iconFunction) {
        return createFilterSection(title, items, filtersMap, nameFunction, iconFunction, false);
    }

    protected <T> JPanel createFilterSection(String title, Iterable<T> items, Map<T, Boolean> filtersMap,
                                             java.util.function.Function<T, String> nameFunction,
                                             java.util.function.Function<T, JLabel> iconFunction,
                                             boolean includeSearch) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.PAGE_AXIS));
        container.setAlignmentX(0.5f);
        container.setAlignmentY(0);

        JPanel itemsContainer = new JPanel();
        itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.PAGE_AXIS));
        itemsContainer.setAlignmentX(0.0f);

        if (includeSearch) {
            JTextField searchField = new JTextField();
            searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            searchField.setAlignmentX(0.0f);
            container.add(searchField);
            container.add(Box.createRigidArea(new Dimension(0, 4)));

            searchField.getDocument().addDocumentListener(
                    new SimpleDocumentListener(() -> updateItemVisibility(searchField, itemsContainer)));
        }

        container.add(itemsContainer);

        for (T item : items) {
            JPanel buttonContainer = new JPanel();
            buttonContainer.setLayout(new BoxLayout(buttonContainer, BoxLayout.LINE_AXIS));
            buttonContainer.setBorder(new EmptyBorder(4, 0, 0, 0));

            JCheckBox checkBox = new JCheckBox();
            checkBox.setText(nameFunction.apply(item));
            Boolean selected = filtersMap.get(item);
            checkBox.setSelected(selected != null ? selected : true);
            checkBox.addActionListener(e -> {
                filtersMap.put(item, checkBox.isSelected());
                applyFilters();
            });

            if (iconFunction != null) {
                JLabel iconLabel = iconFunction.apply(item);
                if (iconLabel != null) {
                    buttonContainer.add(iconLabel);
                }
            }

            buttonContainer.add(checkBox);
            buttonContainer.add(Box.createHorizontalGlue());

            allFilterBoxes.add(checkBox);
            itemsContainer.add(buttonContainer);
        }
        container.add(Box.createRigidArea(UIConstants.PADDING_10_4));
        return container;
    }

    private static void updateItemVisibility(JTextField searchField, JPanel itemsContainer) {
        String text = searchField.getText().toLowerCase(java.util.Locale.ROOT);
        for (Component c : itemsContainer.getComponents()) {
            if (c instanceof JPanel row) {
                for (Component child : row.getComponents()) {
                    if (child instanceof JCheckBox cb) {
                        row.setVisible(cb.getText().toLowerCase(java.util.Locale.ROOT).contains(text));
                        break;
                    }
                }
            }
        }
    }
}
