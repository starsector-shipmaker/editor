package shipeditor.utility.components.dialog;

import shipeditor.utility.text.StringManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.log4j.Log4j2;
import shipeditor.components.datafiles.entities.WeaponCSVEntry;
import shipeditor.components.viewer.entities.weapon.WeaponSlotPoint;
import shipeditor.components.viewer.layers.ship.ShipPainter;
import shipeditor.components.viewer.layers.ship.data.ShipSkin;
import shipeditor.persistence.GameDataPackage;
import shipeditor.persistence.Settings;
import shipeditor.persistence.SettingsManager;
import shipeditor.persistence.database.CoreIndexManager;
import shipeditor.persistence.database.DatabaseQueryService;
import shipeditor.persistence.database.IndexedFile;
import shipeditor.representation.GameDataRepository;
import shipeditor.representation.weapon.WeaponEnums.WeaponMount;
import shipeditor.representation.weapon.WeaponEnums.WeaponSize;
import shipeditor.representation.weapon.WeaponEnums.WeaponType;
import shipeditor.representation.weapon.WeaponSpecFile;
import shipeditor.utility.components.ComponentUtilities;
import shipeditor.utility.graphics.Sprite;
import shipeditor.utility.text.StringConstants;
import shipeditor.utility.themes.Themes;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Log4j2
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public class PickWeaponDialog extends JPanel {

    private final WeaponSlotPoint slotPoint;
    private final Path shipPackage;

    private List<WeaponCSVEntry> allIndexedWeapons;
    private final Map<WeaponCSVEntry, String> weaponPackageNameMap = new HashMap<>();

    private DefaultListModel<WeaponCSVEntry> listModel;
    private JList<WeaponCSVEntry> weaponList;

    private JTextField searchField;
    private JComboBox<String> packageComboBox;
    private JComboBox<String> typeComboBox;
    private JComboBox<String> sizeComboBox;
    private JCheckBox slotCompatibilityCheckBox;
    private JLabel countLabel;

    private JPanel previewPanel;
    private JLabel previewImageLabel;
    private JLabel previewNameLabel;
    private JLabel previewIdLabel;
    private JLabel previewTypeSizeLabel;
    private JLabel previewPackageLabel;
    private JLabel previewStatsLabel;
    private JLabel previewCompatibilityLabel;

    private final Map<WeaponCSVEntry, ImageIcon> listIconCache = new HashMap<>();
    private WeaponCSVEntry selectedWeapon;
    private final Runnable onDoubleClick;
    private boolean okGuardInstalled;

    public PickWeaponDialog(WeaponSlotPoint slotPoint, Runnable onDoubleClick) {
        this.slotPoint = slotPoint;
        this.onDoubleClick = onDoubleClick;
        this.shipPackage = resolveShipPackage(slotPoint);

        this.setLayout(new BorderLayout(6, 6));
        this.setBorder(new EmptyBorder(8, 8, 8, 8));
        this.setPreferredSize(new Dimension(820, 560));

        this.initData();
        this.add(createFilterPanel(), BorderLayout.PAGE_START);
        this.add(createCenterSplitPane(), BorderLayout.CENTER);

        this.applyFilters();

        this.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && this.isShowing()) {
                installOkGuard();
            }
        });
    }

    /**
     * @return the reason the currently selected weapon cannot be installed into the slot,
     * or empty if it fits (or there is no slot/selection to check against).
     */
    public Optional<String> getSelectedIncompatibilityReason() {
        return getIncompatibilityReason(getSelectedWeapon());
    }

    private Optional<String> getIncompatibilityReason(WeaponCSVEntry weapon) {
        if (slotPoint == null || weapon == null) {
            return Optional.empty();
        }
        return WeaponType.getIncompatibilityReason(slotPoint, weapon);
    }

    /**
     * Confirms the current list selection, unless the weapon is incompatible with the slot,
     * in which case the reason is shown and the dialog stays open.
     */
    private void confirmSelection() {
        selectedWeapon = weaponList.getSelectedValue();
        if (selectedWeapon == null || onDoubleClick == null) {
            return;
        }
        Optional<String> reason = getIncompatibilityReason(selectedWeapon);
        if (reason.isPresent()) {
            log.debug("Blocked confirmation of weapon {} for slot {}: {}", selectedWeapon.getWeaponID(),
                    slotPoint.getId(), reason.get());
            showIncompatibleMessage(reason.get());
            return;
        }
        onDoubleClick.run();
    }

    private void showIncompatibleMessage(String reason) {
        previewCompatibilityLabel.setForeground(new Color(255, 100, 100));
        previewCompatibilityLabel.setText("<html>" + StringManager.getString("INCOMPATIBLE_WITH_SLOT")
                + slotPoint.getId() + "<br>" + reason + "</html>");
        JOptionPane.showMessageDialog(this, reason, "Weapon cannot be installed", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * The OK button belongs to the enclosing {@link JOptionPane}, so its listeners are wrapped here
     * to veto confirmation of an incompatible weapon.
     */
    private void installOkGuard() {
        if (okGuardInstalled || slotPoint == null) {
            return;
        }
        try {
            JOptionPane pane = (JOptionPane) SwingUtilities.getAncestorOfClass(JOptionPane.class, this);
            if (pane == null) {
                return;
            }
            JButton okButton = findFirstButton(pane);
            if (okButton == null) {
                log.debug("OK button not found in option pane, incompatible-weapon guard not installed");
                return;
            }
            log.debug("Installing incompatible weapon guard on OK button '{}'", okButton.getText());
            ActionListener[] originals = okButton.getActionListeners();
            for (ActionListener original : originals) {
                okButton.removeActionListener(original);
            }
            okButton.addActionListener(event -> {
                Optional<String> reason = getSelectedIncompatibilityReason();
                if (reason.isPresent()) {
                    showIncompatibleMessage(reason.get());
                    return;
                }
                for (ActionListener original : originals) {
                    original.actionPerformed(event);
                }
            });
            okGuardInstalled = true;
        } catch (RuntimeException e) {
            log.error("Failed to install OK button guard on weapon picker", e);
        }
    }

    private JButton findFirstButton(Container container) {
        for (Component child : container.getComponents()) {
            if (child == this) {
                continue;
            }
            if (child instanceof JButton button) {
                return button;
            }
            if (child instanceof Container nested) {
                JButton found = findFirstButton(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public WeaponCSVEntry getSelectedWeapon() {
        return selectedWeapon != null ? selectedWeapon : weaponList.getSelectedValue();
    }

    private Path resolveShipPackage(WeaponSlotPoint point) {
        if (point == null) return null;
        ShipPainter shipPainter = point.getParent();
        if (shipPainter != null) {
            ShipSkin activeSkin = shipPainter.getActiveSkin();
            if (activeSkin != null && !activeSkin.isBase()) {
                return activeSkin.getContainingPackage();
            } else {
                var shipLayer = shipPainter.getParentLayer();
                var shipHull = shipLayer.getHull();
                if (shipHull != null) {
                    var shipEntry = GameDataRepository.retrieveShipCSVEntryByID(shipHull.getHullID());
                    if (shipEntry != null) {
                        return shipEntry.getPackageFolderPath();
                    }
                }
            }
        }
        return null;
    }

    private void initData() {
        allIndexedWeapons = new ArrayList<>();
        weaponPackageNameMap.clear();

        Set<String> seenWeaponIds = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        GameDataRepository gameData = SettingsManager.getGameData();

        List<IndexedFile> dbWeaponFiles = DatabaseQueryService.getFilesByType(StringConstants.WEAPON_TYPE);
        Set<String> indexedSpecIds = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (dbWeaponFiles != null) {
            for (IndexedFile file : dbWeaponFiles) {
                if (file.getEntityId() != null && !file.getEntityId().isBlank()) {
                    indexedSpecIds.add(file.getEntityId());
                }
            }
        }

        // 1. Load aggregated weapons from GameDataRepository (filtering out dummy/projectile rows with no .wpn spec)
        if (gameData != null) {
            Map<String, WeaponCSVEntry> allMap = gameData.getAllWeaponEntries();
            if (allMap != null) {
                for (WeaponCSVEntry weapon : allMap.values()) {
                    String wid = weapon.getWeaponID();
                    if (wid != null && !wid.isBlank()
                            && (indexedSpecIds.isEmpty() || indexedSpecIds.contains(wid))
                            && seenWeaponIds.add(wid)) {
                        allIndexedWeapons.add(weapon);
                        String pkgName = resolvePackageName(weapon.getPackageFolderPath());
                        weaponPackageNameMap.put(weapon, pkgName);
                    }
                }
            }
        }

        // 2. Query all indexed weapon files from DatabaseQueryService
        // dbWeaponFiles already includes CoreIndexManager files.
        // We process in reverse order so mod files (appended last) take precedence over core files (added first).
        if (dbWeaponFiles != null && gameData != null) {
            for (int i = dbWeaponFiles.size() - 1; i >= 0; i--) {
                IndexedFile file = dbWeaponFiles.get(i);
                if (file.getEntityId() != null && !file.getEntityId().isBlank() && !seenWeaponIds.contains(file.getEntityId())) {
                    WeaponCSVEntry entry = gameData.getOrCreateWeaponEntry(file);
                    if (entry != null && entry.getWeaponID() != null && !entry.getWeaponID().isBlank() && seenWeaponIds.add(entry.getWeaponID())) {
                        allIndexedWeapons.add(entry);
                        String modId = file.getModId();
                        String pkgName = resolveModIdName(modId);
                        weaponPackageNameMap.put(entry, pkgName);
                    }
                }
            }
        }

        allIndexedWeapons.sort(Comparator.comparing(WeaponCSVEntry::getWeaponID, String.CASE_INSENSITIVE_ORDER));
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

        // Row 1: Search & Mod Package Dropdown
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
        searchField.setToolTipText(StringManager.getString("FILTER_WEAPONS_BY_ID_OR_NAME_SUBSTRING"));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && weaponList != null && listModel.getSize() > 0) {
                    weaponList.requestFocusInWindow();
                    if (weaponList.getSelectedIndex() == -1) {
                        weaponList.setSelectedIndex(0);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER && onDoubleClick != null) {
                    confirmSelection();
                }
            }
        });
        row1.add(searchField, gbc);

        javax.swing.SwingUtilities.invokeLater(() -> {
            if (searchField != null) {
                searchField.requestFocusInWindow();
            }
        });

        gbc.gridx = 2;
        gbc.weightx = 0;
        row1.add(new JLabel(StringManager.getString("MOD_PACKAGE")), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.45;
        packageComboBox = new JComboBox<>(buildPackageList());
        packageComboBox.addActionListener(e -> applyFilters());
        row1.add(packageComboBox, gbc);

        filterPanel.add(row1);

        // Row 2: Type, Size, Slot Compatibility, and Item Count
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));

        row2.add(new JLabel(StringManager.getString("TYPE")));
        typeComboBox = new JComboBox<>(new String[]{
                "All Types", "Ballistic", "Energy", "Missile", "Composite",
                "Synergy", "Hybrid", "Universal", "Built-In", "Decorative", "System"
        });
        typeComboBox.addActionListener(e -> applyFilters());
        row2.add(typeComboBox);

        row2.add(new JLabel(StringManager.getString("SIZE_1")));
        sizeComboBox = new JComboBox<>(new String[]{"All Sizes", "Small", "Medium", "Large"});
        sizeComboBox.addActionListener(e -> applyFilters());
        row2.add(sizeComboBox);

        slotCompatibilityCheckBox = new JCheckBox(StringManager.getString("SLOT_MATCH_ONLY"), slotPoint != null);
        slotCompatibilityCheckBox.setToolTipText(slotPoint != null
                ? "Only display weapons compatible with selected slot " + slotPoint.getId() + " (" + slotPoint.getWeaponType() + " / " + slotPoint.getWeaponSize() + ")"
                : "Filter by weapon slot fit");
        slotCompatibilityCheckBox.addActionListener(e -> applyFilters());
        row2.add(slotCompatibilityCheckBox);

        countLabel = new JLabel(StringManager.getString("WEAPONS") + allIndexedWeapons.size());
        countLabel.setForeground(Themes.getDisabledTextColor());
        row2.add(countLabel);

        filterPanel.add(row2);

        return filterPanel;
    }

    private String[] buildPackageList() {
        Set<String> uniquePackages = new HashSet<>(weaponPackageNameMap.values());
        List<String> sortedPackages = new ArrayList<>(uniquePackages);
        sortedPackages.remove("Starsector Core");
        Collections.sort(sortedPackages, String.CASE_INSENSITIVE_ORDER);

        List<String> list = new ArrayList<>();
        list.add("All Indexed Mods (" + allIndexedWeapons.size() + " weapons)");
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
        weaponList = new JList<>(listModel);
        weaponList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        weaponList.setCellRenderer(new WeaponRichCellRenderer());
        ToolTipManager.sharedInstance().registerComponent(weaponList);

        weaponList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                WeaponCSVEntry selected = weaponList.getSelectedValue();
                this.selectedWeapon = selected;
                updatePreview(selected);
            }
        });

        weaponList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    confirmSelection();
                }
            }
        });

        weaponList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    confirmSelection();
                }
            }
        });

        JScrollPane listScroller = new JScrollPane(weaponList);
        listScroller.getVerticalScrollBar().setUnitIncrement(16);
        listScroller.setBorder(new LineBorder(Themes.getBorderColor()));
        listScroller.setMinimumSize(new Dimension(340, 200));

        JPanel previewCard = createPreviewPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroller, previewCard);
        splitPane.setDividerLocation(460);
        splitPane.setResizeWeight(0.6);
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
        previewPanel.setMinimumSize(new Dimension(280, 200));

        previewImageLabel = new JLabel();
        previewImageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        previewImageLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 10, 4));

        previewNameLabel = new JLabel(StringManager.getString("SELECT_A_WEAPON"));
        previewNameLabel.setFont(previewNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        previewNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewIdLabel = new JLabel(" ");
        previewIdLabel.setForeground(Themes.getDisabledTextColor());
        previewIdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewTypeSizeLabel = new JLabel(" ");
        previewTypeSizeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewPackageLabel = new JLabel(" ");
        previewPackageLabel.setForeground(new Color(90, 180, 255));
        previewPackageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewStatsLabel = new JLabel(" ");
        previewStatsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewCompatibilityLabel = new JLabel(" ");
        previewCompatibilityLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewPanel.add(previewImageLabel);
        previewPanel.add(previewNameLabel);
        previewPanel.add(previewIdLabel);
        previewPanel.add(javax.swing.Box.createVerticalStrut(6));
        previewPanel.add(previewTypeSizeLabel);
        previewPanel.add(previewPackageLabel);
        previewPanel.add(javax.swing.Box.createVerticalStrut(6));
        previewPanel.add(previewCompatibilityLabel);
        previewPanel.add(javax.swing.Box.createVerticalStrut(8));
        previewPanel.add(previewStatsLabel);
        previewPanel.add(javax.swing.Box.createVerticalGlue());

        return previewPanel;
    }

    private void updatePreview(WeaponCSVEntry weapon) {
        if (weapon == null) {
            previewImageLabel.setIcon(null);
            previewNameLabel.setText(StringManager.getString("SELECT_A_WEAPON"));
            previewIdLabel.setText(" ");
            previewTypeSizeLabel.setText(" ");
            previewPackageLabel.setText(" ");
            previewStatsLabel.setText(" ");
            previewCompatibilityLabel.setText(" ");
            return;
        }

        // Sprite
        Sprite sprite = weapon.getWeaponImage();
        if (sprite != null && sprite.getImage() != null) {
            Image scaled = ComponentUtilities.resizeImageToSquareLimit(sprite.getImage(), 80);
            previewImageLabel.setIcon(new ImageIcon(scaled));
        } else {
            previewImageLabel.setIcon(null);
        }

        previewNameLabel.setText(weapon.toString());
        previewIdLabel.setText(StringManager.getString("ID_1") + weapon.getWeaponID());

        WeaponType type = weapon.getType();
        WeaponSize size = weapon.getSize();
        String typeSizeText = (type != null ? type.getDisplayedName() : "Unknown") + " / " +
                              (size != null ? size.getDisplayedName() : "Unknown");
        previewTypeSizeLabel.setText(typeSizeText);
        if (type != null && type.getColor() != null) {
            previewTypeSizeLabel.setForeground(type.getColor());
        }

        String pkgName = weaponPackageNameMap.getOrDefault(weapon, "Unknown Mod");
        previewPackageLabel.setText(StringManager.getString("MOD") + pkgName);

        // Compatibility check
        if (slotPoint != null) {
            Optional<String> reason = getIncompatibilityReason(weapon);
            if (reason.isEmpty()) {
                previewCompatibilityLabel.setText(StringManager.getString("FITS_SLOT") + slotPoint.getId() + " (" + slotPoint.getWeaponType() + " " + slotPoint.getWeaponSize() + ")");
                previewCompatibilityLabel.setForeground(new Color(60, 220, 100));
            } else {
                previewCompatibilityLabel.setText("<html>" + StringManager.getString("INCOMPATIBLE_WITH_SLOT")
                        + slotPoint.getId() + " (" + slotPoint.getWeaponType() + " " + slotPoint.getWeaponSize()
                        + ")<br>" + reason.get() + "</html>");
                previewCompatibilityLabel.setForeground(new Color(255, 100, 100));
            }
        } else {
            previewCompatibilityLabel.setText(" ");
        }

        // Stats summary
        Map<String, String> row = weapon.getRowData();
        if (row != null && !row.isEmpty()) {
            StringBuilder sb = new StringBuilder("<html><body style='font-size:11px;'>");
            String op = row.get("OPs");
            String range = row.get("range");
            String dmg = row.get("damage/second");
            if (dmg == null || dmg.isBlank()) dmg = row.get("damage/shot");
            String primaryRole = row.get("primaryRoleStr");

            if (op != null && !op.isBlank()) sb.append("<b>Ordnance Points:</b> ").append(op).append("<br>");
            if (range != null && !range.isBlank()) sb.append("<b>Range:</b> ").append(range).append("<br>");
            if (dmg != null && !dmg.isBlank()) sb.append("<b>Damage:</b> ").append(dmg).append("<br>");
            if (primaryRole != null && !primaryRole.isBlank()) sb.append("<b>Role:</b> ").append(primaryRole).append("<br>");

            WeaponSpecFile spec = weapon.getSpecFile();
            if (spec != null && spec.getWeaponSpecFilePath() != null) {
                sb.append("<b>Spec:</b> ").append(spec.getWeaponSpecFilePath().getFileName()).append("<br>");
            }

            sb.append("</body></html>");
            previewStatsLabel.setText(sb.toString());
        } else {
            previewStatsLabel.setText(" ");
        }

        previewPanel.revalidate();
        previewPanel.repaint();
    }

    private void applyFilters() {
        String filter = searchField != null ? searchField.getText().toLowerCase(Locale.ROOT).trim() : "";
        String selectedPackageFilter = packageComboBox != null ? (String) packageComboBox.getSelectedItem() : null;
        String selectedType = typeComboBox != null ? (String) typeComboBox.getSelectedItem() : "All Types";
        String selectedSize = sizeComboBox != null ? (String) sizeComboBox.getSelectedItem() : "All Sizes";
        boolean matchSlot = slotCompatibilityCheckBox != null && slotCompatibilityCheckBox.isSelected();

        listModel.clear();
        int count = 0;

        for (WeaponCSVEntry weapon : allIndexedWeapons) {
            // Slot fit check
            if (matchSlot && slotPoint != null && !WeaponType.isWeaponFitting(slotPoint, weapon)) {
                continue;
            }

            // Package check
            if (selectedPackageFilter != null && !selectedPackageFilter.startsWith("All Indexed Mods")) {
                String weaponPkg = weaponPackageNameMap.getOrDefault(weapon, "");
                if (selectedPackageFilter.equals("Starsector Core")) {
                    if (!"Starsector Core".equals(weaponPkg)) continue;
                } else if (selectedPackageFilter.startsWith("Ship's Mod")) {
                    String shipPkgName = resolvePackageName(shipPackage);
                    if (!shipPkgName.equals(weaponPkg)) continue;
                } else {
                    if (!selectedPackageFilter.equals(weaponPkg)) continue;
                }
            }

            // Type check
            if (selectedType != null && !"All Types".equals(selectedType)) {
                WeaponType type = weapon.getType();
                if (type == null || !type.getDisplayedName().equalsIgnoreCase(selectedType)) {
                    continue;
                }
            }

            // Size check
            if (selectedSize != null && !"All Sizes".equals(selectedSize)) {
                WeaponSize size = weapon.getSize();
                if (size == null || !size.getDisplayedName().equalsIgnoreCase(selectedSize)) {
                    continue;
                }
            }

            // Search query check
            if (!filter.isEmpty()) {
                String id = weapon.getWeaponID() != null ? weapon.getWeaponID().toLowerCase(Locale.ROOT) : "";
                String name = weapon.toString().toLowerCase(Locale.ROOT);
                String pkg = weaponPackageNameMap.getOrDefault(weapon, "").toLowerCase(Locale.ROOT);
                if (!id.contains(filter) && !name.contains(filter) && !pkg.contains(filter)) {
                    continue;
                }
            }

            listModel.addElement(weapon);
            count++;
        }

        if (countLabel != null) {
            countLabel.setText(StringManager.getString("SHOWING") + count + " of " + allIndexedWeapons.size());
        }

        if (!listModel.isEmpty()) {
            weaponList.setSelectedIndex(0);
        } else {
            updatePreview(null);
        }
    }

    private class WeaponRichCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof WeaponCSVEntry entry) {
                String pkgName = weaponPackageNameMap.getOrDefault(entry, "Core");
                WeaponType type = entry.getType();
                WeaponSize size = entry.getSize();
                String typeStr = type != null ? type.getDisplayedName() : "Unknown";
                String sizeStr = size != null ? size.getDisplayedName() : "Unknown";

                String text = entry.toString() + " (" + entry.getWeaponID() + ")  [" + typeStr + " / " + sizeStr + "]  [" + pkgName + "]";
                setText(text);

                Optional<String> reason = getIncompatibilityReason(entry);
                if (reason.isPresent()) {
                    setForeground(Themes.getDisabledTextColor());
                    setToolTipText(reason.get());
                } else {
                    setToolTipText(null);
                    if (!isSelected && type != null && type.getColor() != null) {
                        setForeground(type.getColor());
                    }
                }

                ImageIcon cachedIcon = listIconCache.get(entry);
                if (cachedIcon == null && !listIconCache.containsKey(entry)) {
                    Sprite sprite = entry.getWeaponImage();
                    if (sprite != null && sprite.getImage() != null) {
                        Image scaled = ComponentUtilities.resizeImageToSquareLimit(sprite.getImage(), 28);
                        cachedIcon = new ImageIcon(scaled);
                    }
                    listIconCache.put(entry, cachedIcon);
                }
                setIcon(cachedIcon);
            }
            return this;
        }
    }

}
