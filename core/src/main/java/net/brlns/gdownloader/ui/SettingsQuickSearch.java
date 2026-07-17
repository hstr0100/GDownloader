/*
 * Copyright (C) 2026 hstr0100
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.brlns.gdownloader.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.Value;
import net.brlns.gdownloader.ui.custom.CustomMenuButton;
import net.brlns.gdownloader.ui.menu.RightClickMenu;

import static net.brlns.gdownloader.lang.Language.l10n;
import static net.brlns.gdownloader.ui.UIUtils.installPlaceholder;
import static net.brlns.gdownloader.ui.UIUtils.isPlaceholder;
import static net.brlns.gdownloader.ui.UIUtils.loadIcon;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.*;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class SettingsQuickSearch extends JPanel {

    private final JTextField searchField;
    private final Timer debounceTimer;

    private final List<SearchEntry> searchIndex = new ArrayList<>();

    private final JPanel rootPanel;

    private JWindow popupWindow;
    private final List<SearchEntry> currentMatches = new ArrayList<>();
    private final List<CustomMenuButton> currentButtons = new ArrayList<>();
    private int selectedIndex = -1;

    @SuppressWarnings("this-escape")
    public SettingsQuickSearch(JPanel rootPanelIn) {
        rootPanel = rootPanelIn;

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(color(SIDE_PANEL));
        setBorder(BorderFactory.createLineBorder(color(SIDE_PANEL_HEADER_FOOTER)));

        JLabel searchIcon = new JLabel(loadIcon("/assets/search.png", ICON, 16));
        searchIcon.setOpaque(false);
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));

        searchField = new JTextField();
        searchField.setToolTipText(l10n("gui.settings.search.tooltip"));
        searchField.setBackground(color(SIDE_PANEL));
        searchField.setForeground(color(FOREGROUND));
        searchField.setCaretColor(color(FOREGROUND));
        searchField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        searchField.setMargin(new Insets(2, 5, 2, 5));

        installPlaceholder(searchField,
            l10n("gui.settings.search.tooltip"),
            color(FOREGROUND), color(ICON));

        JPanel clearWrapper = new JPanel(new BorderLayout());
        clearWrapper.setOpaque(false);
        clearWrapper.setPreferredSize(new Dimension(26, 30));

        JLabel clearIcon = new JLabel(loadIcon("/assets/delete.png", ICON, 22));
        clearIcon.setOpaque(false);
        clearIcon.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        clearIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearIcon.setVisible(false);

        clearIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                searchField.setText("");
                searchField.requestFocusInWindow();
            }
        });

        clearWrapper.add(clearIcon, BorderLayout.CENTER);

        debounceTimer = new Timer(200, e -> performSearch());
        debounceTimer.setRepeats(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateClearIcon() {
                String text = searchField.getText();
                boolean hasText = !text.isEmpty()
                    && !isPlaceholder(searchField, l10n("gui.settings.search.tooltip"));
                clearIcon.setVisible(hasText);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateClearIcon();
                debounceTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateClearIcon();
                debounceTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateClearIcon();
                debounceTimer.restart();
            }
        });

        InputMap inputMap = searchField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = searchField.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("DOWN"), "quickSearchSelectNext");
        actionMap.put("quickSearchSelectNext", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelection(1);
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("UP"), "quickSearchSelectPrevious");
        actionMap.put("quickSearchSelectPrevious", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveSelection(-1);
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "quickSearchConfirm");
        actionMap.put("quickSearchConfirm", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (popupWindow != null && selectedIndex >= 0 && selectedIndex < currentMatches.size()) {
                    SearchEntry entry = currentMatches.get(selectedIndex);

                    closePopup();
                    navigateTo(entry);
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke("ESCAPE"), "quickSearchCancel");
        actionMap.put("quickSearchCancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closePopup();
            }
        });

        add(searchIcon, BorderLayout.WEST);
        add(searchField, BorderLayout.CENTER);
        add(clearWrapper, BorderLayout.EAST);
    }

    public void buildIndex() {
        searchIndex.clear();
        buildIndexRecursive(rootPanel, new ArrayList<>());
    }

    private void buildIndexRecursive(Component comp, List<Runnable> path) {
        List<Runnable> currentPath = new ArrayList<>(path);

        if (comp instanceof JComponent jcomp) {
            Runnable navAction = (Runnable)jcomp.getClientProperty("nav-action");
            if (navAction != null) {
                currentPath.add(navAction);
            }
        }

        String text = extractText(comp);
        if (notNullOrEmpty(text)) {
            searchIndex.add(new SearchEntry(text, currentPath, (JComponent)comp));
        }

        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                buildIndexRecursive(child, currentPath);
            }
        }
    }

    private String extractText(Component comp) {
        String text = null;
        if (comp instanceof JLabel label) {
            text = label.getText();
        } else if (comp instanceof JCheckBox cb) {
            text = cb.getText();
        } else if (comp instanceof AbstractButton btn) {
            text = btn.getText();
        }

        if (text != null) {
            return text.replaceAll("<[^>]*>", "").trim();
        }

        return null;
    }

    private void performSearch() {
        SwingUtilities.invokeLater(() -> {
            if (isPlaceholder(searchField, l10n("gui.settings.search.tooltip"))) {
                return;
            }

            String query = searchField.getText().toLowerCase(Locale.ROOT).trim();

            closePopup();

            if (query.isEmpty()) {
                return;
            }

            for (SearchEntry entry : searchIndex) {
                if (entry.getText().toLowerCase(Locale.ROOT).contains(query)) {
                    currentMatches.add(entry);

                    if (currentMatches.size() >= 12) {
                        break;
                    }
                }
            }

            if (!currentMatches.isEmpty()) {
                showResultsPopup();
            }
        });
    }

    private void showResultsPopup() {
        popupWindow = new JWindow(SwingUtilities.getWindowAncestor(searchField));
        popupWindow.setFocusableWindowState(false);
        popupWindow.setAlwaysOnTop(true);
        popupWindow.setLayout(new BorderLayout());

        JPanel popupPanel = new JPanel(new GridLayout(currentMatches.size(), 1));
        popupPanel.setBackground(Color.DARK_GRAY);
        popupPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        popupPanel.setOpaque(true);

        for (SearchEntry entry : currentMatches) {
            CustomMenuButton button = new CustomMenuButton(entry.getText(), null);

            button.addActionListener(e -> {
                closePopup();
                navigateTo(entry);
            });

            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    int index = currentButtons.indexOf(button);
                    if (index != -1) {
                        moveSelectionTo(index);
                    }
                }
            });

            currentButtons.add(button);
            popupPanel.add(button);
        }

        popupWindow.add(popupPanel, BorderLayout.CENTER);
        popupWindow.pack();

        RightClickMenu.positionPopupOnScreen(popupWindow, searchField, 0, searchField.getHeight());
        RightClickMenu.attachAutoDismiss(popupWindow);

        popupWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (popupWindow == e.getWindow()) {
                    popupWindow = null;
                }

                currentButtons.clear();
                currentMatches.clear();
                selectedIndex = -1;
            }
        });

        popupWindow.setVisible(true);

        moveSelectionTo(0);
    }

    private void closePopup() {
        if (popupWindow != null) {
            popupWindow.setVisible(false);
            popupWindow.dispose();
            popupWindow = null;
        }

        currentButtons.clear();
        currentMatches.clear();
        selectedIndex = -1;
    }

    private void moveSelection(int delta) {
        if (currentButtons.isEmpty()) {
            return;
        }

        int size = currentButtons.size();
        int newIndex = selectedIndex == -1
            ? (delta > 0 ? 0 : size - 1)
            : (selectedIndex + delta + size) % size;

        moveSelectionTo(newIndex);
    }

    private void moveSelectionTo(int index) {
        if (index < 0 || index >= currentButtons.size()) {
            return;
        }

        if (selectedIndex >= 0 && selectedIndex < currentButtons.size()) {
            setButtonSelected(currentButtons.get(selectedIndex), false);
        }

        selectedIndex = index;
        setButtonSelected(currentButtons.get(selectedIndex), true);
    }

    private void setButtonSelected(CustomMenuButton button, boolean selected) {
        button.getModel().setRollover(selected);
        button.repaint();
    }

    private void navigateTo(SearchEntry entry) {
        for (Runnable action : entry.getPath()) {
            if (action != null) {
                action.run();
            }
        }

        SwingUtilities.invokeLater(() -> {
            entry.getTarget().scrollRectToVisible(
                new Rectangle(0, 0, entry.getTarget().getWidth(), entry.getTarget().getHeight())
            );

            highlightComponent(entry.getTarget());
        });
    }

    private void highlightComponent(JComponent component) {
        Color originalBackground = component.getForeground();

        component.setForeground(color(ICON_ACTIVE));
        component.repaint();

        Timer highlightTimer = new Timer(3000, e -> {
            component.setForeground(originalBackground);
            component.repaint();
        });

        highlightTimer.setRepeats(false);
        highlightTimer.start();
    }

    @Value
    private class SearchEntry {

        private final String text;
        private final List<Runnable> path;
        private final JComponent target;
    }

}
