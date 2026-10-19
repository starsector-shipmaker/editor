package shipeditor.utility.components.dialog;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.datafiles.entities.HullmodCSVEntry;
import shipeditor.persistence.SettingsManager;
import shipeditor.persistence.database.CoreIndexManager;
import shipeditor.persistence.database.DatabaseQueryService;
import shipeditor.persistence.database.IndexedFile;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.RepresentationEnums.HullSize;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.components.widgets.SimpleDocumentListener;
import shipeditor.utility.text.StringConstants;
import shipeditor.utility.themes.Themes;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public class PickHullmodDialog extends JPanel {

    private final HullSize currentShipSize;
    private final Path shipPackage;
    private final shipeditor.components.viewer.layers.ship.ShipLayer shipLayer;
    private final boolean excludeBuiltIn;
    private final Runnable onDoubleClick;

    private List<HullmodCSVEntry> allIndexedHullmods;
    private final Map<HullmodCSVEntry, String> hullmodPackageNameMap = new HashMap<>();

    private DefaultListModel<HullmodCSVEntry> listModel;
    private JList<HullmodCSVEntry> hullmodList;

    private JTextField searchField;
    private JComboBox<String> packageComboBox;
    private JComboBox<String> categoryComboBox;
    private JLabel countLabel;

    private JPanel previewPanel;
    private JLabel previewImageLabel;
    private JLabel previewNameLabel;
    private JLabel previewIdLabel;
    private JLabel previewPackageLabel;
    private JPanel previewCostTable;
    private JLabel[] costLabels;
    private JTextArea previewDescArea;
    private JLabel previewTagsLabel;

    private final Map<HullmodCSVEntry, ImageIcon> listIconCache = new HashMap<>();

    private HullmodCSVEntry selectedHullmod;

    public PickHullmodDialog(HullSize currentShipSize, Path shipPackage, Runnable onDoubleClick) {
        this(currentShipSize, shipPackage, null, false, onDoubleClick);
    }

    public PickHullmodDialog(HullSize currentShipSize, Path shipPackage,
                             shipeditor.components.viewer.layers.ship.ShipLayer shipLayer,
                             boolean excludeBuiltIn, Runnable onDoubleClick) {
        this.currentShipSize = currentShipSize != null ? currentShipSize : HullSize.FRIGATE;
        this.shipPackage = shipPackage;
        this.shipLayer = shipLayer;
        this.excludeBuiltIn = excludeBuiltIn;
        this.onDoubleClick = onDoubleClick;

        this.setLayout(new BorderLayout(6, 6));
        this.setBorder(new EmptyBorder(8, 8, 8, 8));
        this.setPreferredSize(new Dimension(840, 580));

        this.initData();
        this.add(createFilterPanel(), BorderLayout.PAGE_START);
        this.add(createCenterSplitPane(), BorderLayout.CENTER);

        this.applyFilters();

        SwingUtilities.invokeLater(() -> {
            if (searchField != null) {
                searchField.requestFocusInWindow();
            }
        });
    }

    public HullmodCSVEntry getSelectedHullmod() {
        return selectedHullmod != null ? selectedHullmod : hullmodList.getSelectedValue();
    }

    private boolean shouldIncludeHullmod(HullmodCSVEntry mod) {
        if (mod == null || mod.getID() == null) {
            return false;
        }
        if (excludeBuiltIn) {
            if (!mod.isFittableOnVariant(shipLayer)) {
                return false;
            }
            if (shipLayer != null && mod.isFittedOnVariant(shipLayer.getActiveVariant())) {
                return false;
            }
        }
        return true;
    }

    private void initData() {
        allIndexedHullmods = new ArrayList<>();
        hullmodPackageNameMap.clear();

        Set<String> seenIds = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        GameDataRepository gameData = SettingsManager.getGameData();

        // 1. Load aggregated hullmods from GameDataRepository
        if (gameData != null) {
            Map<String, HullmodCSVEntry> allMap = gameData.getAllHullmodEntries();
            if (allMap != null) {
                for (HullmodCSVEntry mod : allMap.values()) {
                    if (shouldIncludeHullmod(mod) && seenIds.add(mod.getID())) {
                        allIndexedHullmods.add(mod);
                        String pkgName = resolvePackageName(mod.getPackageFolderPath());
                        hullmodPackageNameMap.put(mod, pkgName);
                    }
                }
            }
        }

        // 2. Query all indexed hullmod files from DatabaseQueryService
        // dbFiles already includes CoreIndexManager files. We process in reverse to let mods take precedence.
        List<IndexedFile> dbFiles = DatabaseQueryService.getFilesByType(StringConstants.HULLMOD_CSV_TYPE);
        if (dbFiles != null && gameData != null) {
            for (int i = dbFiles.size() - 1; i >= 0; i--) {
                IndexedFile file = dbFiles.get(i);
                if (file.getEntityId() != null && !seenIds.contains(file.getEntityId())) {
                    HullmodCSVEntry entry = gameData.getOrCreateHullmodEntry(file.getEntityId());
                    if (shouldIncludeHullmod(entry) && seenIds.add(entry.getID())) {
                        allIndexedHullmods.add(entry);
                        String modId = file.getModId();
                        String pkgName = resolveModIdName(modId);
                        hullmodPackageNameMap.put(entry, pkgName);
                    }
                }
            }
        }

        allIndexedHullmods.sort(Comparator.comparing(HullmodCSVEntry::getID, String.CASE_INSENSITIVE_ORDER));
    }

    private String resolvePackageName(Path packagePath) {
        return DialogHelper.resolvePackageName(packagePath);
    }

    private String resolveModIdName(String modId) {
        return DialogHelper.resolveModIdName(modId);
    }

    private JPanel createFilterPanel() {
        JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setBorder(new CompoundBorder(
                new LineBorder(Themes.getBorderColor(), 1, true),
                new EmptyBorder(6, 8, 6, 8)
        ));

        // Row 1: Search & Mod Package
        JPanel row1 = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        row1.add(new JLabel(StringManager.getString("SEARCH_1")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.55;
        searchField = new JTextField();
        searchField.setToolTipText(StringManager.getString("FILTER_HULLMODS_BY_ID_NAME_DESCRIPTION_OR_TAGS"));
        searchField.getDocument().addDocumentListener(new SimpleDocumentListener(this::applyFilters));
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && hullmodList != null && listModel.getSize() > 0) {
                    hullmodList.requestFocusInWindow();
                    if (hullmodList.getSelectedIndex() == -1) {
                        hullmodList.setSelectedIndex(0);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && onDoubleClick != null) {
                    selectedHullmod = hullmodList.getSelectedValue();
                    if (selectedHullmod != null) {
                        onDoubleClick.run();
                    }
                }
            }
        });
        row1.add(searchField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        row1.add(new JLabel(StringManager.getString("MOD_PACKAGE")), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.45;
        packageComboBox = new JComboBox<>(buildPackageList());
        packageComboBox.addActionListener(e -> applyFilters());
        row1.add(packageComboBox, gbc);

        filterPanel.add(row1);

        // Row 2: Category and Result Count
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));

        row2.add(new JLabel(StringManager.getString("CATEGORY_TAG")));
        categoryComboBox = new JComboBox<>(new String[]{
                "All Hullmods", "Standard / Buffs", "D-Mods / Negative", "Logistics", "Shields", "Weapons / Ordnance", "Defense / Armor"
        });
        categoryComboBox.addActionListener(e -> applyFilters());
        row2.add(categoryComboBox);

        countLabel = new JLabel(StringManager.getString("HULLMODS_1") + allIndexedHullmods.size());
        countLabel.setForeground(Themes.getDisabledTextColor());
        row2.add(countLabel);

        filterPanel.add(row2);

        return filterPanel;
    }

    private String[] buildPackageList() {
        Set<String> uniquePackages = new HashSet<>(hullmodPackageNameMap.values());
        List<String> sortedPackages = new ArrayList<>(uniquePackages);
        sortedPackages.remove("Starsector Core");
        Collections.sort(sortedPackages, String.CASE_INSENSITIVE_ORDER);

        List<String> list = new ArrayList<>();
        list.add("All Indexed Mods (" + allIndexedHullmods.size() + " hullmods)");
        list.add("Starsector Core");
        if (shipPackage != null) {
            String shipModName = resolvePackageName(shipPackage);
            if (!"Starsector Core".equals(shipModName) && !list.contains(shipModName)) {
                list.add("Ship's Mod (" + shipModName + ")");
            }
        }
        list.addAll(sortedPackages);

        return list.toArray(new String[0]);
    }

    private JSplitPane createCenterSplitPane() {
        listModel = new DefaultListModel<>();
        hullmodList = new JList<>(listModel);
        hullmodList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hullmodList.setCellRenderer(new HullmodRichCellRenderer());

        hullmodList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                HullmodCSVEntry selected = hullmodList.getSelectedValue();
                this.selectedHullmod = selected;
                updatePreview(selected);
            }
        });

        hullmodList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectedHullmod = hullmodList.getSelectedValue();
                    if (selectedHullmod != null && onDoubleClick != null) {
                        onDoubleClick.run();
                    }
                }
            }
        });

        hullmodList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectedHullmod = hullmodList.getSelectedValue();
                    if (selectedHullmod != null && onDoubleClick != null) {
                        onDoubleClick.run();
                    }
                }
            }
        });

        JScrollPane listScroller = new JScrollPane(hullmodList);
        listScroller.getVerticalScrollBar().setUnitIncrement(16);
        listScroller.setBorder(new LineBorder(Themes.getBorderColor()));
        listScroller.setMinimumSize(new Dimension(360, 200));

        JPanel previewCard = createPreviewPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroller, previewCard);
        splitPane.setDividerLocation(470);
        splitPane.setResizeWeight(0.55);
        splitPane.setBorder(null);

        return splitPane;
    }

    private JPanel createPreviewPanel() {
        previewPanel = new JPanel();
        previewPanel.setLayout(new BoxLayout(previewPanel, BoxLayout.Y_AXIS));
        previewPanel.setBorder(new CompoundBorder(
                new LineBorder(Themes.getBorderColor(), 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));
        previewPanel.setMinimumSize(new Dimension(300, 200));

        previewImageLabel = new JLabel();
        previewImageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewImageLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));

        previewNameLabel = new JLabel(StringManager.getString("SELECT_A_HULLMOD"));
        previewNameLabel.setFont(previewNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        previewNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewIdLabel = new JLabel(" ");
        previewIdLabel.setForeground(Themes.getDisabledTextColor());
        previewIdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewPackageLabel = new JLabel(" ");
        previewPackageLabel.setForeground(new Color(90, 180, 255));
        previewPackageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // OP Cost breakdown table
        previewCostTable = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
        previewCostTable.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewCostTable.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(Themes.getBorderColor(), 1, true), "OP Cost by Hull Size"));

        costLabels = new JLabel[4];
        String[] sizeNames = {"Frigate", "Destroyer", "Cruiser", "Capital"};
        for (int i = 0; i < 4; i++) {
            costLabels[i] = new JLabel(sizeNames[i] + ": 0");
            costLabels[i].setBorder(new EmptyBorder(2, 4, 2, 4));
            previewCostTable.add(costLabels[i]);
        }

        previewTagsLabel = new JLabel(" ");
        previewTagsLabel.setForeground(new Color(255, 180, 90));
        previewTagsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewDescArea = new JTextArea(" ");
        previewDescArea.setEditable(false);
        previewDescArea.setLineWrap(true);
        previewDescArea.setWrapStyleWord(true);
        previewDescArea.setOpaque(false);
        previewDescArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        previewDescArea.setForeground(Themes.getTextColor());
        previewDescArea.setBorder(new EmptyBorder(4, 2, 4, 2));

        JScrollPane descScroller = new JScrollPane(previewDescArea);
        descScroller.setBorder(null);
        descScroller.setOpaque(false);
        descScroller.getViewport().setOpaque(false);
        descScroller.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewPanel.add(previewImageLabel);
        previewPanel.add(previewNameLabel);
        previewPanel.add(previewIdLabel);
        previewPanel.add(previewPackageLabel);
        previewPanel.add(javax.swing.Box.createVerticalStrut(6));
        previewPanel.add(previewCostTable);
        previewPanel.add(javax.swing.Box.createVerticalStrut(4));
        previewPanel.add(previewTagsLabel);
        previewPanel.add(javax.swing.Box.createVerticalStrut(6));
        previewPanel.add(descScroller);

        return previewPanel;
    }

    private void updatePreview(HullmodCSVEntry hullmod) {
        if (hullmod == null) {
            previewImageLabel.setIcon(null);
            previewNameLabel.setText(StringManager.getString("SELECT_A_HULLMOD"));
            previewIdLabel.setText(" ");
            previewPackageLabel.setText(" ");
            previewTagsLabel.setText(" ");
            previewDescArea.setText(" ");
            for (JLabel cl : costLabels) {
                cl.setText(StringManager.getString("EMPTY_STRING_3"));
                cl.setOpaque(false);
            }
            return;
        }

        // Sprite
        BufferedImage img = hullmod.getSpriteImage();
        if (img != null) {
            Image scaled = ComponentUtilities.resizeImageToSquareLimit(img, 64);
            previewImageLabel.setIcon(new ImageIcon(scaled));
        } else {
            previewImageLabel.setIcon(null);
        }

        previewNameLabel.setText(hullmod.getName());
        previewIdLabel.setText(StringManager.getString("ID_1") + hullmod.getID());

        String pkg = hullmodPackageNameMap.getOrDefault(hullmod, "Starsector Core");
        previewPackageLabel.setText(StringManager.getString("PACKAGE") + pkg);

        // OP costs
        HullSize[] sizes = {HullSize.FRIGATE, HullSize.DESTROYER, HullSize.CRUISER, HullSize.CAPITAL_SHIP};
        String[] sizeNames = {"Frigate", "Destroyer", "Cruiser", "Capital"};
        for (int i = 0; i < 4; i++) {
            int cost = hullmod.getOrdnanceCost(sizes[i]);
            costLabels[i].setText(sizeNames[i] + ": " + cost + " OP");
            if (sizes[i] == currentShipSize) {
                costLabels[i].setOpaque(true);
                costLabels[i].setBackground(new Color(40, 90, 150));
                costLabels[i].setForeground(Color.WHITE);
                costLabels[i].setFont(costLabels[i].getFont().deriveFont(Font.BOLD));
            } else {
                costLabels[i].setOpaque(false);
                costLabels[i].setForeground(Themes.getTextColor());
                costLabels[i].setFont(costLabels[i].getFont().deriveFont(Font.PLAIN));
            }
        }

        String tags = hullmod.getTags();
        if (!tags.isBlank()) {
            previewTagsLabel.setText(StringManager.getString("TAGS") + tags);
        } else {
            previewTagsLabel.setText(" ");
        }

        String desc = hullmod.getDescription();
        previewDescArea.setText(desc.isBlank() ? "(No description available)" : desc);
        previewDescArea.setCaretPosition(0);
    }

    private void applyFilters() {
        String filterText = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String selectedPackage = packageComboBox != null ? (String) packageComboBox.getSelectedItem() : null;
        String selectedCategory = categoryComboBox != null ? (String) categoryComboBox.getSelectedItem() : null;

        listModel.clear();
        int matched = 0;

        for (HullmodCSVEntry mod : allIndexedHullmods) {
            String modId = mod.getID() != null ? mod.getID().toLowerCase(Locale.ROOT) : "";
            String modName = mod.getName().toLowerCase(Locale.ROOT);
            String modDesc = mod.getDescription().toLowerCase(Locale.ROOT);
            String modTags = mod.getTags().toLowerCase(Locale.ROOT);

            // 1. Text Search Filter
            if (!filterText.isEmpty()) {
                boolean matchesText = modId.contains(filterText)
                        || modName.contains(filterText)
                        || modDesc.contains(filterText)
                        || modTags.contains(filterText);
                if (!matchesText) continue;
            }

            // 2. Package Filter
            if (selectedPackage != null && !selectedPackage.startsWith("All Indexed Mods")) {
                String pkgName = hullmodPackageNameMap.get(mod);
                if (selectedPackage.startsWith("Ship's Mod")) {
                    String shipModName = resolvePackageName(shipPackage);
                    if (!shipModName.equals(pkgName)) continue;
                } else if (!selectedPackage.equals(pkgName)) {
                    continue;
                }
            }

            // 3. Category / Tag Filter
            if (selectedCategory != null && !selectedCategory.equals("All Hullmods")) {
                boolean isDMod = modTags.contains("dmod") || modTags.contains("negative") || modId.startsWith("dmod_");
                switch (selectedCategory) {
                    case "Standard / Buffs" -> {
                        if (isDMod) continue;
                    }
                    case "D-Mods / Negative" -> {
                        if (!isDMod) continue;
                    }
                    case "Logistics" -> {
                        if (!modTags.contains("logistics") && !modId.contains("survey") && !modId.contains("cargo") && !modId.contains("fuel") && !modId.contains("efficiency")) continue;
                    }
                    case "Shields" -> {
                        if (!modTags.contains("shield") && !modId.contains("shield") && !modDesc.contains("shield")) continue;
                    }
                    case "Weapons / Ordnance" -> {
                        if (!modTags.contains("weapon") && !modTags.contains("missile") && !modTags.contains("ballistic") && !modTags.contains("energy") && !modId.contains("expanded") && !modId.contains("targeting")) continue;
                    }
                    case "Defense / Armor" -> {
                        if (!modTags.contains("armor") && !modTags.contains("defense") && !modId.contains("armor") && !modId.contains("hull") && !modDesc.contains("armor")) continue;
                    }
                    default -> {}
                }
            }

            listModel.addElement(mod);
            matched++;
        }

        if (countLabel != null) {
            countLabel.setText(StringManager.getString("HULLMODS_1") + matched + " of " + allIndexedHullmods.size());
        }

        if (!listModel.isEmpty()) {
            hullmodList.setSelectedIndex(0);
        } else {
            selectedHullmod = null;
            updatePreview(null);
        }
    }

    private class HullmodRichCellRenderer extends DefaultListCellRenderer {
        private final JPanel cellPanel = new JPanel(new BorderLayout(8, 0));
        private final JLabel iconLabel = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel idLabel = new JLabel();
        private final JLabel costBadge = new JLabel();
        private final JLabel tagBadge = new JLabel();
        private final JPanel textContainer = new JPanel();
        private final JPanel rightBadgePanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));

        HullmodRichCellRenderer() {
            cellPanel.setBorder(new EmptyBorder(3, 6, 3, 6));
            cellPanel.setOpaque(true);

            textContainer.setLayout(new BoxLayout(textContainer, BoxLayout.LINE_AXIS));
            textContainer.setOpaque(false);

            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
            idLabel.setForeground(Themes.getDisabledTextColor());
            idLabel.setBorder(new EmptyBorder(0, 4, 0, 0));

            textContainer.add(nameLabel);
            textContainer.add(idLabel);

            rightBadgePanel.setOpaque(false);
            costBadge.setFont(costBadge.getFont().deriveFont(Font.BOLD, 11f));
            rightBadgePanel.add(tagBadge);
            rightBadgePanel.add(costBadge);

            cellPanel.add(iconLabel, BorderLayout.LINE_START);
            cellPanel.add(textContainer, BorderLayout.CENTER);
            cellPanel.add(rightBadgePanel, BorderLayout.LINE_END);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (!(value instanceof HullmodCSVEntry mod)) {
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }

            nameLabel.setText(mod.getName());
            idLabel.setText(StringManager.getString("EMPTY_STRING_2") + mod.getID() + "]");

            int opCost = mod.getOrdnanceCost(currentShipSize);
            costBadge.setText(opCost + " OP");
            costBadge.setForeground(opCost == 0 ? Themes.getDisabledTextColor() : new Color(130, 215, 255));

            String tags = mod.getTags();
            boolean isDMod = tags.contains("dmod") || tags.contains("negative") || mod.getID().startsWith("dmod_");
            if (isDMod) {
                tagBadge.setText(StringManager.getString("D_MOD"));
                tagBadge.setForeground(new Color(255, 120, 80));
                tagBadge.setVisible(true);
            } else {
                tagBadge.setText("");
                tagBadge.setVisible(false);
            }

            ImageIcon cachedIcon = listIconCache.get(mod);
            if (cachedIcon == null && !listIconCache.containsKey(mod)) {
                BufferedImage img = mod.getSpriteImage();
                if (img != null) {
                    Image scaled = ComponentUtilities.resizeImageToSquareLimit(img, 24);
                    cachedIcon = new ImageIcon(scaled);
                }
                listIconCache.put(mod, cachedIcon);
            }
            iconLabel.setIcon(cachedIcon);

            if (isSelected) {
                cellPanel.setBackground(list.getSelectionBackground());
                nameLabel.setForeground(list.getSelectionForeground());
            } else {
                cellPanel.setBackground(index % 2 == 0 ? Themes.getListBackgroundColor() : Themes.getPanelDarkColor());
                nameLabel.setForeground(Themes.getTextColor());
            }

            return cellPanel;
        }
    }
}
