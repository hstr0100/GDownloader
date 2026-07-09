/*
 * Copyright (C) 2024 hstr0100
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

import jakarta.annotation.Nullable;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.downloader.AbstractDownloader;
import net.brlns.gdownloader.downloader.enums.DownloaderIdEnum;
import net.brlns.gdownloader.ffmpeg.enums.AudioBitrateEnum;
import net.brlns.gdownloader.ffmpeg.structs.FFmpegConfig;
import net.brlns.gdownloader.filters.AbstractUrlFilter;
import net.brlns.gdownloader.lang.ITranslatable;
import net.brlns.gdownloader.settings.ProxySettings;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.downloader.AbstractDownloaderSettings;
import net.brlns.gdownloader.settings.enums.*;
import net.brlns.gdownloader.system.StartupManager;
import net.brlns.gdownloader.ui.builder.CheckBoxBuilder;
import net.brlns.gdownloader.ui.builder.ComboBoxBuilder;
import net.brlns.gdownloader.ui.builder.LongFieldBuilder;
import net.brlns.gdownloader.ui.builder.SliderBuilder;
import net.brlns.gdownloader.ui.builder.TextFieldBuilder;
import net.brlns.gdownloader.ui.custom.CustomScrollBarUI;
import net.brlns.gdownloader.ui.custom.CustomTabButton;
import net.brlns.gdownloader.ui.custom.CustomTranscodePanel;
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.ui.themes.UIColors;
import net.brlns.gdownloader.util.MathUtils;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.ui.GUIManager.createIconButton;
import static net.brlns.gdownloader.ui.UIUtils.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;
import static net.brlns.gdownloader.util.StringUtils.getHumanReadableFileSize;
import static net.brlns.gdownloader.util.StringUtils.notNullOrEmpty;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SettingsPanel {

    private final GDownloader main;
    private final GUIManager manager;

    private final List<SettingsMenuEntry> contentPanels = new ArrayList<>();

    private Settings settings;

    private JFrame frame;

    private JPanel sidebarPanel;
    private JPanel contentPanel;
    private CardLayout cardLayout;

    private Runnable _resetAction;

    public SettingsPanel(GDownloader mainIn, GUIManager managerIn) {
        main = mainIn;
        manager = managerIn;

        contentPanels.add(new SettingsMenuEntry(
            "settings.general",
            "/assets/settings.png",
            () -> createGeneralSettings()
        ));
        contentPanels.add(new SettingsMenuEntry(
            "settings.downloads",
            "/assets/download.png",
            () -> createDownloadSettings()
        ));
        contentPanels.add(new SettingsMenuEntry(
            "settings.resolution",
            "/assets/resolution.png",
            () -> createResolutionSettings()
        ));
        contentPanels.add(new SettingsMenuEntry(
            "settings.network",
            "/assets/connection.png",
            () -> createNetworkSettings()
        ));
    }

    private void saveSettings() {
        if (!settings.getDownloadsPath().isEmpty()
            && !main.getConfig().getDownloadsPath().equals(settings.getDownloadsPath())) {

            File file = new File(settings.getDownloadsPath());
            if (file.exists() && file.canWrite()) {
                main.setDownloadsPath(file);// We are uselessly calling on updateConfig() twice here, but should cause no issues.
            } else {
                settings.setDownloadsPath("");

                PopupMessenger.show(Message.builder()
                    .title("gui.error_popup_title")
                    .message("gui.error_download_path_not_writable", file.getAbsolutePath())
                    .durationMillis(4000)
                    .messageType(MessageTypeEnum.ERROR)
                    .playTone(true)
                    .build());

                log.error("Selected path not writable {}", file);
            }
        }

        validateCustomDirectory(settings.getYtDlpSettings());
        validateCustomDirectory(settings.getGalleryDLSettings());
        validateCustomDirectory(settings.getSpotDLSettings());
        validateCustomDirectory(settings.getDirectHttpSettings());

        main.updateConfig(settings);

        main.getGuiManager().refreshAppWindow();
        StartupManager.updateAutoStartupState(main);
    }

    private void reloadSettings() {
        contentPanel.removeAll();

        int i = 0;
        for (SettingsMenuEntry entry : contentPanels) {
            contentPanel.add(entry.getPanel().get(), String.valueOf(i++));
        }

        _resetAction.run();
    }

    public void createAndShowGUI() {
        runOnEDT(() -> {
            if (frame != null) {
                frame.setVisible(true);
                frame.setExtendedState(JFrame.NORMAL);
                frame.requestFocus();
                return;
            }

            settings = GDownloader.OBJECT_MAPPER.convertValue(main.getConfig(), Settings.class);

            frame = new JFrame(l10n("settings.title", GDownloader.REGISTRY_APP_NAME)) {
                @Override
                public void dispose() {
                    frame = null;

                    super.dispose();
                }
            };

            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(1010, 718);
            frame.setLayout(new BorderLayout());
            //frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setMinimumSize(new Dimension(frame.getWidth(), frame.getHeight()));
            frame.setIconImage(main.getGuiManager().getAppIcon());

            sidebarPanel = new JPanel();
            sidebarPanel.setBackground(color(SIDE_PANEL));
            sidebarPanel.setLayout(new GridBagLayout());

            JPanel mainContentPanel = new JPanel(new BorderLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.NORTH;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            gbc.insets = new Insets(0, 0, 0, 0);

            {
                JPanel headerPanel = new JPanel();
                headerPanel.setBackground(color(SIDE_PANEL_HEADER_FOOTER));

                JLabel headerLabel = new JLabel(l10n("settings.sidebar_title"));
                headerLabel.setForeground(color(FOREGROUND));
                headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
                headerPanel.setLayout(new BorderLayout());
                headerPanel.add(headerLabel, BorderLayout.CENTER);
                headerPanel.setPreferredSize(new Dimension(32, 32));

                sidebarPanel.add(headerPanel, gbc);

                gbc.gridy++;
            }

            {
                cardLayout = new CardLayout();
                contentPanel = new JPanel(cardLayout);
                contentPanel.setBackground(color(BACKGROUND));

                JPanel headerPanel = new JPanel(new GridBagLayout());
                headerPanel.setBackground(color(SIDE_PANEL_SELECTED));

                JLabel headerLabel = new JLabel(contentPanels.get(0).getDisplayName());
                headerLabel.setForeground(color(FOREGROUND));
                headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
                headerLabel.setVerticalAlignment(SwingConstants.CENTER);

                headerPanel.setPreferredSize(new Dimension(32, 32));
                headerPanel.add(headerLabel, new GridBagConstraints());

                mainContentPanel.add(headerPanel, BorderLayout.NORTH);

                int i = 0;
                for (SettingsMenuEntry entry : contentPanels) {
                    int index = i;

                    JButton button = new JButton();
                    button.setToolTipText(entry.getDisplayName());
                    button.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    button.setPreferredSize(new Dimension(120, 120));
                    button.setBorderPainted(false);
                    button.setContentAreaFilled(false);
                    button.setOpaque(true);
                    button.setFocusPainted(false);
                    button.setBackground(color(index == 0 ? SIDE_PANEL_SELECTED : SIDE_PANEL));

                    button.setIcon(loadIcon(entry.getIcon(), ICON, 60));

                    Runnable action = () -> {
                        cardLayout.show(contentPanel, String.valueOf(index));
                        headerLabel.setText(entry.getDisplayName());

                        Component[] buttons = sidebarPanel.getComponents();
                        for (Component button1 : buttons) {
                            if (button1.getBackground().equals(color(SIDE_PANEL_SELECTED))) {
                                button1.setBackground(color(SIDE_PANEL));
                            }
                        }

                        button.setBackground(color(SIDE_PANEL_SELECTED));
                    };

                    button.addActionListener((ActionEvent e) -> {
                        action.run();
                    });

                    if (index == 0) {
                        _resetAction = action;
                    }

                    sidebarPanel.add(button, gbc);

                    gbc.gridy++;

                    contentPanel.add(entry.getPanel().get(), String.valueOf(index));

                    i++;
                }

                mainContentPanel.add(contentPanel, BorderLayout.CENTER);

                {
                    JPanel bottomPanel = new JPanel(new BorderLayout());
                    bottomPanel.setBackground(color(SIDE_PANEL_SELECTED));
                    bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                    {
                        JPanel leftPanel = new JPanel();
                        leftPanel.setBackground(color(SIDE_PANEL_SELECTED));
                        leftPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));

                        leftPanel.add(createIconButton(
                            loadIcon("/assets/shutdown.png", ICON, 24),
                            loadIcon("/assets/shutdown.png", ICON_HOVER, 24),
                            "gui.exit.tooltip",
                            e -> main.shutdown()
                        ));

                        leftPanel.add(createIconButton(
                            loadIcon("/assets/restart.png", ICON, 24),
                            loadIcon("/assets/restart.png", ICON_HOVER, 24),
                            "gui.restart.tooltip",
                            e -> {
                                saveSettings();

                                main.restart();
                            }
                        ));

                        leftPanel.add(createIconButton(
                            loadIcon("/assets/bin.png", ICON, 24),
                            loadIcon("/assets/bin.png", ICON_HOVER, 24),
                            "gui.clear_cache.tooltip",
                            e -> main.clearCache(true)
                        ));

                        leftPanel.add(createIconButton(
                            loadIcon("/assets/log.png", ICON, 24),
                            loadIcon("/assets/log.png", ICON_HOVER, 24),
                            "gui.open_log.tooltip",
                            e -> main.openLogFile()
                        ));

                        leftPanel.add(createIconButton(
                            loadIcon("/assets/wrench.png", ICON, 24),
                            loadIcon("/assets/wrench.png", ICON_HOVER, 24),
                            "gui.open_work_directory.tooltip",
                            e -> main.openWorkDirectory()
                        ));

                        leftPanel.add(createIconButton(
                            loadIcon("/assets/deduplicate.png", ICON, 24),
                            loadIcon("/assets/deduplicate.png", ICON_HOVER, 24),
                            "gui.deduplication.deduplicate_downloads_directory",
                            e -> main.deduplicateDownloadsDirectory()
                        ));

                        leftPanel.add(createIconButton(
                            loadIcon("/assets/update.png", ICON, 24),
                            loadIcon("/assets/update.png", ICON_HOVER, 24),
                            "gui.update.tooltip",
                            e -> main.getUpdateManager().checkForUpdates()
                        ));

                        bottomPanel.add(leftPanel, BorderLayout.WEST);
                    }

                    {
                        JPanel rightPanel = new JPanel();
                        rightPanel.setBackground(color(SIDE_PANEL_SELECTED));
                        rightPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 10));

                        JButton resetButton = createButton(
                            "settings.restore_defaults",
                            "settings.restore_defaults.tooltip",
                            BUTTON_BACKGROUND,
                            BUTTON_FOREGROUND,
                            BUTTON_HOVER);

                        resetButton.setPreferredSize(new Dimension(200, 30));
                        resetButton.addActionListener((ActionEvent e) -> {
                            settings = new Settings();
                            settings.setShowWelcomeScreen(false);
                            settings.setPersistenceDatabaseInitialized(true);

                            reloadSettings();

                            main.updateConfig(settings);
                        });

                        rightPanel.add(resetButton);

                        JButton saveButton = createButton(
                            "settings.save",
                            "settings.save.tooltip",
                            BUTTON_BACKGROUND,
                            BUTTON_FOREGROUND,
                            BUTTON_HOVER);

                        saveButton.setPreferredSize(new Dimension(200, 30));
                        saveButton.addActionListener((ActionEvent e) -> {
                            saveSettings();

                            frame.dispose();
                            frame = null;
                        });

                        rightPanel.add(saveButton);

                        bottomPanel.add(rightPanel, BorderLayout.EAST);
                    }

                    mainContentPanel.add(bottomPanel, BorderLayout.SOUTH);
                    frame.add(mainContentPanel, BorderLayout.CENTER);
                }
            }

            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            sidebarPanel.add(Box.createVerticalGlue(), gbc);

            {
                JPanel importExportPanel = new JPanel();
                importExportPanel.setBackground(color(SIDE_PANEL));
                importExportPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 0));
                importExportPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                importExportPanel.add(createIconButton(
                    loadIcon("/assets/export.png", ICON, 20),
                    loadIcon("/assets/export.png", LIGHT_TEXT, 20),
                    "gui.settings_export.tooltip",
                    e -> openExportSettingsDialog()
                ));
                importExportPanel.add(createIconButton(
                    loadIcon("/assets/import.png", ICON, 20),
                    loadIcon("/assets/import.png", LIGHT_TEXT, 20),
                    "gui.settings_import.tooltip",
                    e -> openImportSettingsDialog()
                ));

                gbc.gridy++;
                gbc.weighty = 0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                sidebarPanel.add(importExportPanel, gbc);
            }

            {
                JPanel gabPanel = new JPanel(new BorderLayout());
                gabPanel.setBackground(color(SIDE_PANEL_HEADER_FOOTER));
                gabPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                gabPanel.setPreferredSize(new Dimension(120, 74));

                JButton gabButton = new JButton("@hstr0100");
                gabButton.setToolTipText("Gabriel's GitHub - #hireme");
                gabButton.setUI(new BasicButtonUI());
                gabButton.setForeground(color(FOREGROUND));
                gabButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                gabButton.setFocusPainted(false);
                gabButton.setBorderPainted(false);
                gabButton.setContentAreaFilled(false);
                gabButton.setOpaque(false);
                gabButton.setMargin(new Insets(0, 0, 0, 0));

                gabButton.addActionListener(e -> {
                    main.openUrlInBrowser("https://github.com/hstr0100");
                });

                gabButton.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        gabButton.setForeground(color(LIGHT_TEXT));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        gabButton.setForeground(color(FOREGROUND));
                    }
                });

                gabPanel.add(gabButton, BorderLayout.CENTER);

                gbc.gridy++;
                gbc.weighty = 0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                sidebarPanel.add(gabPanel, gbc);
            }

            frame.add(sidebarPanel, BorderLayout.WEST);

            frame.add(mainContentPanel, BorderLayout.CENTER);

            cardLayout.show(contentPanel, String.valueOf(0));

            frame.setVisible(true);
        });
    }

    private void openImportSettingsDialog() {
        // When importing, we automatically apply and save settings
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(l10n("gui.settings_import.tooltip"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(l10n("gui.file_chooser.json"), "json"));
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);

        String lastDirectory = settings.getLastSettingsExportDirectory();
        if (notNullOrEmpty(lastDirectory)) {
            File directory = new File(lastDirectory);

            if (directory.exists() && directory.isDirectory()) {
                fileChooser.setCurrentDirectory(directory);
            }
        }

        int userSelection = fileChooser.showOpenDialog(frame);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToLoad = fileChooser.getSelectedFile();

            try {
                // Perform a few sanity checks before proceeding
                if (!fileToLoad.getName().endsWith(".json")) {
                    // It might still be a valid json, but only accept if named as such
                    throw new IllegalArgumentException(l10n("gui.import_failed_invalid"));
                }

                long fileSize = fileToLoad.length();
                final long MAX_SIZE = 1024 * 1024;
                if (fileSize > MAX_SIZE) {
                    // A 1MB settings file seems unlikely in the near future
                    throw new IllegalArgumentException(l10n("gui.import_failed_invalid"));
                }

                String fileContent = Files.readString(fileToLoad.toPath());

                if (!fileContent.startsWith("{") || !fileContent.contains("ConfigVersion")) {
                    // Don't know what this is but it sure isn't our settings file
                    throw new IllegalArgumentException(l10n("gui.import_failed_invalid"));
                }

                settings = GDownloader.OBJECT_MAPPER.readValue(fileContent, Settings.class);
                settings.doMigration();

                reloadSettings();

                main.updateConfig(settings);

                JOptionPane.showMessageDialog(frame,
                    l10n("gui.import_success_popup", fileToLoad.getAbsolutePath()),
                    l10n("gui.import_success_popup_title"),
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                log.error("Error importing settings: {}", e.getMessage());

                JOptionPane.showMessageDialog(frame,
                    l10n("gui.error_popup", l10n("gui.settings_import.tooltip"), e.getMessage()),
                    l10n("gui.error_popup_title"),
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openExportSettingsDialog() {
        // This exports the currently open settings file, even if it's not applied.
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(l10n("gui.settings_export.tooltip"));
        fileChooser.setFileFilter(new FileNameExtensionFilter(l10n("gui.file_chooser.json"), "json"));
        fileChooser.setSelectedFile(new File("config.json"));

        int userSelection = fileChooser.showSaveDialog(frame);

        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            if (!fileToSave.getName().toLowerCase().endsWith(".json")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".json");
            }

            try {
                String currentDirString = fileChooser.getCurrentDirectory().getAbsolutePath();
                settings.setLastSettingsExportDirectory(currentDirString);
                main.getConfig().setLastSettingsExportDirectory(currentDirString);
                main.updateConfig();

                GDownloader.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(fileToSave, settings);

                JOptionPane.showMessageDialog(frame,
                    l10n("gui.export_success_popup", fileToSave.getAbsolutePath()),
                    l10n("gui.export_success_popup_title"),
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                log.error("Error exporting settings: {}", e.getMessage());

                JOptionPane.showMessageDialog(frame,
                    l10n("gui.error_popup", l10n("gui.settings_export.tooltip"), e.getMessage()),
                    l10n("gui.error_popup_title"),
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private JPanel createGeneralSettings() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(color(BACKGROUND));

        addComboBox(panel, ComboBoxBuilder.<LanguageEnum>builder()
            .background(resolveColor(panel))
            .labelKey("settings.language")
            .values(LanguageEnum.values())
            .getter(settings::getLanguage)
            .setter(settings::setLanguage)
            .requiresRestart(true)
            .build());

        addComboBox(panel, ComboBoxBuilder.<ThemeEnum>builder()
            .background(resolveColor(panel))
            .labelKey("settings.theme")
            .values(ThemeEnum.values())
            .getter(settings::getTheme)
            .setter(settings::setTheme)
            .requiresRestart(true)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.always_on_top")
            .getter(settings::isKeepWindowAlwaysOnTop)
            .setter(settings::setKeepWindowAlwaysOnTop)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.exit_on_close")
            .getter(settings::isExitOnClose)
            .setter(settings::setExitOnClose)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.automatic_updates")
            .getter(settings::isAutomaticUpdates)
            .setter(settings::setAutomaticUpdates)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.debug_mode")
            .getter(settings::isDebugMode)
            .setter(settings::setDebugMode)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.restore_session_after_restart")
            .getter(settings::isRestoreSessionAfterRestart)
            .setter(settings::setRestoreSessionAfterRestart)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.start_on_system_startup")
            .getter(settings::isAutoStart)
            .setter(settings::setAutoStart)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.play_sounds")
            .getter(settings::isPlaySounds)
            .setter(settings::setPlaySounds)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.display_downloads_complete_notification")
            .getter(settings::isDisplayDownloadsCompleteNotification)
            .setter(settings::setDisplayDownloadsCompleteNotification)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.display_link_capture_notifications")
            .getter(settings::isDisplayLinkCaptureNotifications)
            .setter(settings::setDisplayLinkCaptureNotifications)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.use_native_system_notifications")
            .getter(settings::isUseNativeSystemNotifications)
            .setter(settings::setUseNativeSystemNotifications)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.use_system_font")
            .getter(settings::isUseSystemFont)
            .setter(settings::setUseSystemFont)
            .requiresRestart(true)
            .build());

        {
            UIColors background = resolveColor(panel);
            JLabel label = createLabel("settings.font_size", LIGHT_TEXT);

            // Not a lot of fault tolerance here
            List<String> options = new ArrayList<>();
            for (int i = 12; i <= 20; i++) {
                options.add(String.valueOf(i));
            }

            JSlider slider = new JSlider(0, options.size() - 1, options.indexOf(String.valueOf(settings.getFontSize())));
            slider.setToolTipText(l10n("settings.requires_restart.tooltip"));
            slider.setMajorTickSpacing(1);
            slider.setPaintTicks(true);
            slider.setSnapToTicks(true);
            slider.setPaintLabels(true);

            slider.addChangeListener((ChangeEvent e) -> {
                JSlider source = (JSlider)e.getSource();
                if (!source.getValueIsAdjusting()) {
                    int sliderValue = source.getValue();

                    settings.setFontSize(Integer.parseInt(options.get(sliderValue)));
                    label.setFont(slider.getFont().deriveFont((float)settings.getFontSize()));
                    label.revalidate();
                    label.repaint();
                }
            });

            customizeSlider(slider, background, SLIDER_FOREGROUND);
            wrapComponentRow(panel, label, slider, background);
        }

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.auto_scroll_to_bottom")
            .getter(settings::isAutoScrollToBottom)
            .setter(settings::setAutoScrollToBottom)
            .build());

        addSlider(panel, SliderBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.maximum_download_queue_columns")
            .min(0).max(10).majorTickSpacing(1)
            .snapToTicks(true)
            .getter(settings::getMaxDownloadQueueColumns)
            .setter(settings::setMaxDownloadQueueColumns)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.enable_system_tray")
            .getter(settings::isEnableSystemTray)
            .setter(settings::setEnableSystemTray)
            .requiresRestart(true)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.show_welcome_screen_on_next_restart")
            .getter(settings::isShowWelcomeScreen)
            .setter(settings::setShowWelcomeScreen)
            .requiresRestart(true)
            .build());

        panel.add(getFillerPanel());

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        SmoothScroller.install(scrollPane);

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 5));
        panelWrapper.setBackground(color(BACKGROUND));

        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    private JPanel createDownloadSettings() {
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBackground(color(BACKGROUND));

        List<SettingsMenuEntry> downloadTabs = new ArrayList<>();
        downloadTabs.add(new SettingsMenuEntry(
            "settings.general",
            "/assets/download.png",// TODO: exclusive icon
            () -> createGeneralDownloadSettingsTab()));
        downloadTabs.add(new SettingsMenuEntry(
            "settings.downloader.yt_dlp",
            "/assets/video.png",
            () -> createYtDlpSettingsTab()));
        downloadTabs.add(new SettingsMenuEntry(
            "settings.downloader.gallery_dl",
            "/assets/picture.png",
            () -> createGalleryDLSettingsTab()));
        downloadTabs.add(new SettingsMenuEntry(
            "settings.downloader.spotdl",
            "/assets/music.png",
            () -> createSpotDLSettingsTab()));
        downloadTabs.add(new SettingsMenuEntry(
            "settings.downloader.direct_http",
            "/assets/internet.png",
            () -> createDirectHttpSettingsTab()));
        downloadTabs.add(new SettingsMenuEntry(
            "settings.label.advanced",
            "/assets/wrench.png",// TODO: exclusive icon
            () -> createAdvancedDownloadSettingsTab()));

        CardLayout downloadTabLayout = new CardLayout();
        JPanel downloadTabContent = new JPanel(downloadTabLayout);
        downloadTabContent.setBackground(color(BACKGROUND));

        JPanel tabBar = new JPanel(new GridLayout(1, downloadTabs.size(), 2, 0));
        tabBar.setBackground(color(SETTINGS_TAB_BAR_BACKGROUND));
        tabBar.setPreferredSize(new Dimension(0, 60));
        tabBar.setMinimumSize(new Dimension(0, 60));
        tabBar.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        List<CustomTabButton> tabButtons = new ArrayList<>();

        int i = 0;
        for (SettingsMenuEntry entry : downloadTabs) {
            int index = i;

            CustomTabButton tabButton = createDownloadTabButton(
                entry.getDisplayName(), entry.getIcon(), index == 0);
            tabButton.addActionListener((ActionEvent e) -> {
                downloadTabLayout.show(downloadTabContent, String.valueOf(index));

                for (CustomTabButton other : tabButtons) {
                    updateDownloadTabButtonState(other, other == tabButton);
                }
            });

            tabButtons.add(tabButton);
            tabBar.add(tabButton);

            downloadTabContent.add(entry.getPanel().get(), String.valueOf(index));

            i++;
        }

        wrapperPanel.add(tabBar, BorderLayout.NORTH);
        wrapperPanel.add(downloadTabContent, BorderLayout.CENTER);

        return wrapperPanel;
    }

    private CustomTabButton createDownloadTabButton(
        String displayName, String iconPath, boolean selected) {
        CustomTabButton button = new CustomTabButton(displayName);

        button.setUI(new BasicButtonUI());
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setIconTextGap(8);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setForeground(color(FOREGROUND));
        button.setIcon(loadIcon(iconPath, ICON, 22));
        button.setFont(button.getFont().deriveFont(Font.BOLD));

        button.setColors(
            color(SETTINGS_TAB_BAR_BACKGROUND),
            color(SETTINGS_TAB_SELECTED_BACKGROUND),
            color(SETTINGS_TAB_HOVER));

        updateDownloadTabButtonState(button, selected);

        return button;
    }

    private void updateDownloadTabButtonState(CustomTabButton button, boolean selected) {
        button.setForeground(color(selected ? FOREGROUND : LIGHT_TEXT));
        button.setSelectedState(selected);
    }

    private JPanel wrapInScrollableSettingsPanel(JPanel panel) {
        panel.add(getFillerPanel());

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        SmoothScroller.install(scrollPane);

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 5));
        panelWrapper.setBackground(color(BACKGROUND));
        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    private void addCustomDirectorySettings(JPanel panel, AbstractDownloaderSettings downloaderSettings,
        DownloaderIdEnum downloaderId) {
        UIColors background = resolveColor(panel);

        JLabel label = createLabel("settings.custom_download_directory", LIGHT_TEXT);
        label.setToolTipText(l10n("settings.custom_download_directory.tooltip"));

        JPanel wrapperPanel = new JPanel(new GridBagLayout());
        wrapperPanel.setBackground(color(background));

        GridBagConstraints gbcPanel = new GridBagConstraints();
        gbcPanel.anchor = GridBagConstraints.WEST;
        gbcPanel.fill = GridBagConstraints.HORIZONTAL;

        JTextField directoryField = new JTextField(20);

        String currentValue = downloaderSettings.getCustomDownloadDirectory();
        if (!notNullOrEmpty(currentValue)) {
            AbstractDownloader downloader = main.getDownloadManager().getDownloader(downloaderId);
            if (downloader != null) {
                currentValue = downloader.resolvePreviewDirectory().getAbsolutePath();
            }
        }

        final String customDirectory = currentValue;

        directoryField.setText(currentValue);
        directoryField.setForeground(color(TEXT_AREA_FOREGROUND));
        directoryField.setBackground(color(TEXT_AREA_BACKGROUND));
        directoryField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setForeground(color(LIGHT_TEXT));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        Runnable updateValidationState = () -> {
            String text = directoryField.getText();

            if (text.isEmpty()) {
                statusLabel.setText(" ");
                return;
            }

            File dir = new File(text);
            boolean valid = dir.isAbsolute() && dir.exists()
                && dir.isDirectory() && dir.canWrite()
                || customDirectory != null && text.equals(customDirectory);

            statusLabel.setText(l10n(valid
                ? "settings.custom_download_directory.status_valid"
                : "settings.custom_download_directory.status_invalid"));
            statusLabel.setForeground(valid
                ? color(LIGHT_TEXT)
                : new Color(231, 76, 60));
        };

        directoryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                handleUpdate();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handleUpdate();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                handleUpdate();
            }

            private void handleUpdate() {
                downloaderSettings.setCustomDownloadDirectory(directoryField.getText());

                updateValidationState.run();
            }
        });

        updateValidationState.run();

        gbcPanel.gridx = 0;
        gbcPanel.gridwidth = 1;
        gbcPanel.weightx = 0.9;
        wrapperPanel.add(directoryField, gbcPanel);

        JButton selectButton = createButton(
            "settings.select_download_directory",
            "settings.select_download_directory.tooltip",
            COMBO_BOX_BUTTON_FOREGROUND,
            BACKGROUND,
            BUTTON_HOVER);

        selectButton.addActionListener((ActionEvent e) -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            String current = downloaderSettings.getCustomDownloadDirectory();
            if (notNullOrEmpty(current)) {
                File currentDir = new File(current);
                if (currentDir.isAbsolute() && currentDir.isDirectory()) {
                    fileChooser.setCurrentDirectory(currentDir);
                }
            }

            int result = fileChooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();

                directoryField.setText(selectedPath);
            }
        });

        selectButton.setPreferredSize(new Dimension(30, directoryField.getPreferredSize().height));

        directoryField.putClientProperty("associated-label", label);
        selectButton.putClientProperty("associated-label", label);

        gbcPanel.gridx = 1;
        gbcPanel.weightx = 0.1;
        gbcPanel.insets = new Insets(0, 10, 0, 0);
        wrapperPanel.add(selectButton, gbcPanel);

        JPanel columnPanel = new JPanel(new BorderLayout());
        columnPanel.setBackground(color(background));
        columnPanel.add(wrapperPanel, BorderLayout.NORTH);
        columnPanel.add(statusLabel, BorderLayout.SOUTH);

        wrapComponentRow(panel, label, columnPanel, background);
    }

    private void validateCustomDirectory(AbstractDownloaderSettings downloaderSettings) {
        String customDir = downloaderSettings.getCustomDownloadDirectory();
        if (!notNullOrEmpty(customDir)) {
            return;
        }

        File file = new File(customDir);
        if (!file.exists() || !file.canWrite()) {
            downloaderSettings.setCustomDownloadDirectory("");
        }
    }

    private void addExtraArgumentsSettings(JPanel panel,
        Supplier<Boolean> enabledGetter, Consumer<Boolean> enabledSetter,
        Supplier<String> argsGetter, Consumer<String> argsSetter,
        String argumentsLabelKey, String placeholderExample) {
        List<JComponent> extraArgumentFields = new ArrayList<>();

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.enable_extra_arguments")
            .getter(enabledGetter)
            .setter(enabledSetter)
            .onSet((selected) -> enableComponentsAndLabels(extraArgumentFields, selected))
            .build());

        extraArgumentFields.add(addTextField(panel, TextFieldBuilder.builder()
            .background(resolveColor(panel))
            .labelKey(argumentsLabelKey)
            .getter(argsGetter)
            .setter(argsSetter)
            .enabled(enabledGetter.get())
            .placeholderText(l10n("gui.example") + " " + placeholderExample)
            .build()));
    }

    private JPanel createGeneralDownloadSettingsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(color(BACKGROUND));

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.read_cookies")
            .getter(settings::isReadCookiesFromBrowser)
            .setter(settings::setReadCookiesFromBrowser)
            .build());

        addComboBox(panel, ComboBoxBuilder.<BrowserEnum>builder()
            .background(resolveColor(panel))
            .labelKey("settings.browser_for_cookies")
            .values(BrowserEnum.values())
            .getter(settings::getBrowser)
            .setter(settings::setBrowser)
            .build());

        {
            JLabel label = createLabel("settings.downloads_path", LIGHT_TEXT);

            UIColors background = resolveColor(panel);
            JPanel wrapperPanel = new JPanel(new GridBagLayout());
            wrapperPanel.setBackground(color(background));

            GridBagConstraints gbcPanel = new GridBagConstraints();
            gbcPanel.anchor = GridBagConstraints.WEST;
            gbcPanel.fill = GridBagConstraints.HORIZONTAL;

            JTextField downloadPathField = new JTextField(20);
            downloadPathField.setText(main.getDownloadsDirectory().getAbsolutePath());
            downloadPathField.setForeground(color(TEXT_AREA_FOREGROUND));
            downloadPathField.setBackground(color(TEXT_AREA_BACKGROUND));
            downloadPathField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            downloadPathField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void changedUpdate(DocumentEvent e) {
                    settings.setDownloadsPath(downloadPathField.getText());
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    settings.setDownloadsPath(downloadPathField.getText());
                }

                @Override
                public void insertUpdate(DocumentEvent e) {
                    settings.setDownloadsPath(downloadPathField.getText());
                }
            });

            gbcPanel.gridx = 0;
            gbcPanel.gridwidth = 1;
            gbcPanel.weightx = 0.9;
            wrapperPanel.add(downloadPathField, gbcPanel);

            JButton selectButton = createButton(
                "settings.select_download_directory",
                "settings.select_download_directory.tooltip",
                COMBO_BOX_BUTTON_FOREGROUND,
                BACKGROUND,
                BUTTON_HOVER);

            selectButton.addActionListener((ActionEvent e) -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int result = fileChooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();
                    downloadPathField.setText(selectedPath);
                }
            });

            selectButton.setPreferredSize(new Dimension(30, downloadPathField.getPreferredSize().height));

            downloadPathField.putClientProperty("associated-label", label);
            selectButton.putClientProperty("associated-label", label);

            gbcPanel.gridx = 1;
            gbcPanel.weightx = 0.1;
            gbcPanel.insets = new Insets(0, 10, 0, 0);
            wrapperPanel.add(selectButton, gbcPanel);

            wrapComponentRow(panel, label, wrapperPanel, background);
        }

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.read_cookies_txt")
            .getter(settings::isReadCookiesFromCookiesTxt)
            .setter(settings::setReadCookiesFromCookiesTxt)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.impersonate_browser")
            .getter(settings::isImpersonateBrowser)
            .setter(settings::setImpersonateBrowser)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.capture_any_clipboard_link")
            .getter(settings::isCaptureAnyLinks)
            .setter(settings::setCaptureAnyLinks)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.auto_download_start")
            .getter(settings::isAutoDownloadStart)
            .setter(settings::setAutoDownloadStart)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.auto_download_retry")
            .getter(settings::isAutoDownloadRetry)
            .setter(settings::setAutoDownloadRetry)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_audio")
            .getter(settings::isDownloadAudio)
            .setter(settings::setDownloadAudio)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_video")
            .getter(settings::isDownloadVideo)
            .setter(settings::setDownloadVideo)
            .build());

        addSlider(panel, SliderBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.maximum_simultaneous_downloads")
            .min(1).max(10).majorTickSpacing(1)
            .snapToTicks(true)
            .getter(settings::getMaxSimultaneousDownloads)
            .setter(settings::setMaxSimultaneousDownloads)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.prefer_system_executables")
            .getter(settings::isPreferSystemExecutables)
            .setter(settings::setPreferSystemExecutables)
            .requiresRestart(true)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.random_interval_between_downloads")
            .getter(settings::isRandomIntervalBetweenDownloads)
            .setter(settings::setRandomIntervalBetweenDownloads)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.remove_successful_downloads")
            .getter(settings::isRemoveSuccessfulDownloads)
            .setter(settings::setRemoveSuccessfulDownloads)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.record_to_download_archive")
            .getter(settings::isRecordToDownloadArchive)
            .setter(settings::setRecordToDownloadArchive)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.remove_from_download_archive")
            .getter(settings::isRemoveFromDownloadArchive)
            .setter(settings::setRemoveFromDownloadArchive)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.query_metadata")
            .getter(settings::isQueryMetadata)
            .setter(settings::setQueryMetadata)
            .build());

        addSlider(panel, SliderBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.maximum_simultaneous_query_metadata_tasks")
            .min(1).max(10).majorTickSpacing(1)
            .snapToTicks(true)
            .getter(settings::getMaxSimultaneousQueryMetadataTasks)
            .setter(settings::setMaxSimultaneousQueryMetadataTasks)
            .build());

        return wrapInScrollableSettingsPanel(panel);
    }

    private JPanel createYtDlpSettingsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(color(BACKGROUND));

        addCustomDirectorySettings(panel, settings.getYtDlpSettings(), DownloaderIdEnum.YT_DLP);

        addComboBox(panel, ComboBoxBuilder.<PlayListOptionEnum>builder()
            .background(resolveColor(panel))
            .labelKey("settings.playlist_download_option")
            .values(PlayListOptionEnum.values())
            .getter(settings::getPlaylistDownloadOption)
            .setter(settings::setPlaylistDownloadOption)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.use_sponsor_block")
            .getter(settings.getYtDlpSettings()::isUseSponsorBlock)
            .setter(settings.getYtDlpSettings()::setUseSponsorBlock)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_youtube_channels")
            .getter(settings.getYtDlpSettings()::isDownloadYoutubeChannels)
            .setter(settings.getYtDlpSettings()::setDownloadYoutubeChannels)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.transcode_audio_to_aac")
            .getter(settings::isTranscodeAudioToAAC)
            .setter(settings::setTranscodeAudioToAAC)
            .build());

        // TODO: this will remain hidden until this yt-dlp feature request is addressed:
        // https://github.com/yt-dlp/yt-dlp/issues/1176
        //addCheckBox(panel, CheckBoxBuilder.builder()
        //    .background(resolveColor(panel))
        //    .labelKey("settings.merge_all_audio_tracks")
        //    .getter(settings.getYtDlpSettings()::isMergeAllAudioTracks)
        //    .setter(settings.getYtDlpSettings()::setMergeAllAudioTracks)
        //    .build());
        AtomicReference<JCheckBox> autoGenSubtitlesCheckBoxRef = new AtomicReference<>();
        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_subtitles")
            .getter(settings.getYtDlpSettings()::isDownloadSubtitles)
            .setter(settings.getYtDlpSettings()::setDownloadSubtitles)
            .onSet((selected) -> {
                enableComponentAndLabel(autoGenSubtitlesCheckBoxRef.get(), selected);
            })
            .build());

        autoGenSubtitlesCheckBoxRef.set(addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_auto_generated_subtitles")
            .getter(settings.getYtDlpSettings()::isDownloadAutoGeneratedSubtitles)
            .setter(settings.getYtDlpSettings()::setDownloadAutoGeneratedSubtitles)
            .enabled(settings.getYtDlpSettings().isDownloadSubtitles())
            .build()));

        AtomicReference<JCheckBox> saveAsTxtCheckBoxRef = new AtomicReference<>();
        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_description")
            .getter(settings.getYtDlpSettings()::isDownloadDescription)
            .setter(settings.getYtDlpSettings()::setDownloadDescription)
            .onSet((selected) -> {
                enableComponentAndLabel(saveAsTxtCheckBoxRef.get(), selected);
            })
            .build());

        saveAsTxtCheckBoxRef.set(addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_description_save_as_txt")
            .getter(settings.getYtDlpSettings()::isSaveDescriptionFileAsTxt)
            .setter(settings.getYtDlpSettings()::setSaveDescriptionFileAsTxt)
            .enabled(settings.getYtDlpSettings().isDownloadDescription())
            .build()));

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_thumbnails")
            .getter(settings.getYtDlpSettings()::isDownloadThumbnails)
            .setter(settings.getYtDlpSettings()::setDownloadThumbnails)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.respect_ytdlp_config_file")
            .getter(settings.getYtDlpSettings()::isRespectConfigFile)
            .setter(settings.getYtDlpSettings()::setRespectConfigFile)
            .build());

        // https://github.com/yt-dlp/yt-dlp/issues/12746
        // Might also apply for any future hiccups
        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.missing_formats_workaround")
            .getter(settings.getYtDlpSettings()::isMissingFormatsWorkaround)
            .setter(settings.getYtDlpSettings()::setMissingFormatsWorkaround)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.prefer_system_executable")
            .getter(settings.getYtDlpSettings()::isPreferSystemExecutable)
            .setter(settings.getYtDlpSettings()::setPreferSystemExecutable)
            .requiresRestart(true)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.direct_http.media_transcoding")
            .getter(settings.getYtDlpSettings()::isMediaTranscoding)
            .setter(settings.getYtDlpSettings()::setMediaTranscoding)
            .build());

        addExtraArgumentsSettings(panel,
            settings.getYtDlpSettings()::isEnableExtraArguments,
            settings.getYtDlpSettings()::setEnableExtraArguments,
            settings.getYtDlpSettings()::getExtraCommandLineArguments,
            settings.getYtDlpSettings()::setExtraCommandLineArguments,
            "settings.extra_yt_dlp_arguments",
            "--ignore-config --proxy http://example.com:1234");

        return wrapInScrollableSettingsPanel(panel);
    }

    private JPanel createGalleryDLSettingsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(color(BACKGROUND));

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.gallery_dl.enabled")
            .getter(settings.getGalleryDLSettings()::isEnabled)
            .setter(settings.getGalleryDLSettings()::setEnabled)
            .requiresRestart(true)
            .build());

        addCustomDirectorySettings(panel, settings.getGalleryDLSettings(), DownloaderIdEnum.GALLERY_DL);

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.organize_files_into_directories")
            .getter(settings.getGalleryDLSettings()::isOrganizeFilesIntoFolders)
            .setter(settings.getGalleryDLSettings()::setOrganizeFilesIntoFolders)
            .tooltipText(l10n("settings.organize_files_into_directories.tooltip"))
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.gallery_dl.respect_config_file")
            .getter(settings.getGalleryDLSettings()::isRespectConfigFile)
            .setter(settings.getGalleryDLSettings()::setRespectConfigFile)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.gallery_dl.deduplicate_files")
            .getter(settings.getGalleryDLSettings()::isFileDeduplication)
            .setter(settings.getGalleryDLSettings()::setFileDeduplication)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.gallery_dl.use_original_filenames")
            .getter(settings.getGalleryDLSettings()::isUseOriginalFilenames)
            .setter(settings.getGalleryDLSettings()::setUseOriginalFilenames)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.gallery_dl.enable_transcoding")
            .getter(settings.getGalleryDLSettings()::isMediaTranscoding)
            .setter(settings.getGalleryDLSettings()::setMediaTranscoding)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.prefer_system_executable")
            .getter(settings.getGalleryDLSettings()::isPreferSystemExecutable)
            .setter(settings.getGalleryDLSettings()::setPreferSystemExecutable)
            .requiresRestart(true)
            .build());

        addExtraArgumentsSettings(panel,
            settings.getGalleryDLSettings()::isEnableExtraArguments,
            settings.getGalleryDLSettings()::setEnableExtraArguments,
            settings.getGalleryDLSettings()::getExtraCommandLineArguments,
            settings.getGalleryDLSettings()::setExtraCommandLineArguments,
            "settings.extra_gallery_dl_arguments",
            "--config-ignore --proxy http://example.com:1234");

        return wrapInScrollableSettingsPanel(panel);
    }

    private JPanel createSpotDLSettingsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(color(BACKGROUND));

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.spotdl.enabled")
            .getter(settings.getSpotDLSettings()::isEnabled)
            .setter(settings.getSpotDLSettings()::setEnabled)
            .requiresRestart(true)
            .build());

        addCustomDirectorySettings(panel, settings.getSpotDLSettings(), DownloaderIdEnum.SPOTDL);

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.spotdl.respect_config_file")
            .getter(settings.getSpotDLSettings()::isRespectConfigFile)
            .setter(settings.getSpotDLSettings()::setRespectConfigFile)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.prefer_system_executable")
            .getter(settings.getSpotDLSettings()::isPreferSystemExecutable)
            .setter(settings.getSpotDLSettings()::setPreferSystemExecutable)
            .requiresRestart(true)
            .build());

        addExtraArgumentsSettings(panel,
            settings.getSpotDLSettings()::isEnableExtraArguments,
            settings.getSpotDLSettings()::setEnableExtraArguments,
            settings.getSpotDLSettings()::getExtraCommandLineArguments,
            settings.getSpotDLSettings()::setExtraCommandLineArguments,
            "settings.extra_spotdl_arguments",
            "--proxy http://example.com:1234");

        return wrapInScrollableSettingsPanel(panel);
    }

    private JPanel createDirectHttpSettingsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(color(BACKGROUND));

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.direct_http.enabled")
            .getter(settings.getDirectHttpSettings()::isEnabled)
            .setter(settings.getDirectHttpSettings()::setEnabled)
            .requiresRestart(true)
            .build());

        addCustomDirectorySettings(panel, settings.getDirectHttpSettings(), DownloaderIdEnum.DIRECT_HTTP);

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.organize_files_into_directories")
            .getter(settings.getDirectHttpSettings()::isOrganizeFilesIntoFolders)
            .setter(settings.getDirectHttpSettings()::setOrganizeFilesIntoFolders)
            .tooltipText(l10n("settings.organize_files_into_directories.tooltip"))
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.direct_http.media_transcoding")
            .getter(settings.getDirectHttpSettings()::isMediaTranscoding)
            .setter(settings.getDirectHttpSettings()::setMediaTranscoding)
            .build());

        {
            UIColors background = resolveColor(panel);
            JLabel label = createLabel("settings.downloader.direct_http.max_download_speed", LIGHT_TEXT);

            long currentBytesPerSecond = settings.getDirectHttpSettings().getMaxDownloadSpeedBytesPerSecond();

            JLabel valueLabel = new JLabel(formatSpeedLabel(currentBytesPerSecond));
            valueLabel.setForeground(color(background == SETTINGS_ROW_BACKGROUND_DARK ? LIGHT_TEXT : LIGHT_TEXT));

            JSlider slider = new JSlider(JSlider.HORIZONTAL, 0, 100,
                MathUtils.convertBytesPerSecondToSliderValue(currentBytesPerSecond));
            customizeSlider(slider, background, SLIDER_FOREGROUND);

            Dictionary<Integer, JLabel> sliderLabels = new Hashtable<>();
            sliderLabels.put(0, new JLabel("∞"));
            sliderLabels.put(20, new JLabel("1MB"));
            sliderLabels.put(40, new JLabel("10MB"));
            sliderLabels.put(60, new JLabel("100MB"));
            sliderLabels.put(80, new JLabel("1GB"));
            sliderLabels.put(100, new JLabel("10GB"));
            slider.setLabelTable(sliderLabels);
            slider.setMajorTickSpacing(20);
            slider.setPaintTicks(true);
            slider.setPaintLabels(true);

            SpinnerNumberModel spinnerModel = new SpinnerNumberModel(
                currentBytesPerSecond / 1024L, 0L,
                MathUtils.getMaxThrottleBytesPerSecond() / 1024L, 64L);
            JSpinner speedSpinner = new JSpinner(spinnerModel);
            speedSpinner.setEditor(new JSpinner.NumberEditor(speedSpinner));
            customizeComponent(speedSpinner, background, LIGHT_TEXT);

            final Object syncObj = new Object();

            slider.addChangeListener(e -> {
                long bytesPerSecond = MathUtils.convertSliderValueToBytesPerSecond(slider.getValue());
                settings.getDirectHttpSettings().setMaxDownloadSpeedBytesPerSecond(bytesPerSecond);
                valueLabel.setText(formatSpeedLabel(bytesPerSecond));

                if (!slider.getValueIsAdjusting()) {
                    synchronized (syncObj) {
                        speedSpinner.setValue(bytesPerSecond / 1024L);
                    }
                }
            });

            speedSpinner.addChangeListener(e -> {
                synchronized (syncObj) {
                    long bytesPerSecond = ((Number)speedSpinner.getValue()).longValue() * 1024L;
                    settings.getDirectHttpSettings().setMaxDownloadSpeedBytesPerSecond(bytesPerSecond);
                    valueLabel.setText(formatSpeedLabel(bytesPerSecond));
                    slider.setValue(MathUtils.convertBytesPerSecondToSliderValue(bytesPerSecond));
                }
            });

            JPanel sliderPanel = new JPanel(new BorderLayout(5, 0));
            sliderPanel.setBackground(color(background));
            sliderPanel.add(slider, BorderLayout.CENTER);
            sliderPanel.add(speedSpinner, BorderLayout.EAST);

            JPanel wrapperPanel = new JPanel(new BorderLayout());
            wrapperPanel.setBackground(color(background));
            wrapperPanel.add(valueLabel, BorderLayout.NORTH);
            wrapperPanel.add(sliderPanel, BorderLayout.CENTER);

            wrapComponentRow(panel, label, wrapperPanel, background);
        }

        addSlider(panel, SliderBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.direct_http.max_download_chunks")
            .min(1).max(15).majorTickSpacing(1)
            .getter(settings.getDirectHttpSettings()::getMaxDownloadChunks)
            .setter(settings.getDirectHttpSettings()::setMaxDownloadChunks)
            .build());

        addSlider(panel, SliderBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.direct_http.max_connections_per_host")
            .min(1).max(20).majorTickSpacing(1)
            .getter(settings.getDirectHttpSettings()::getMaxConnectionsPerHost)
            .setter(settings.getDirectHttpSettings()::setMaxConnectionsPerHost)
            .build());

        addSlider(panel, SliderBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.direct_http.web_scanner.max_concurrent_downloads")
            .min(1).max(20).majorTickSpacing(1)
            .getter(settings.getDirectHttpSettings()::getMaxConcurrentCrawledDownloads)
            .setter(settings.getDirectHttpSettings()::setMaxConcurrentCrawledDownloads)
            .build());

        {
            List<JComponent> scannerFields = new ArrayList<>();

            addCheckBox(panel, CheckBoxBuilder.builder()
                .background(resolveColor(panel))
                .labelKey("settings.downloader.direct_http.web_scanner_enabled")
                .getter(settings.getDirectHttpSettings()::isWebScannerEnabled)
                .setter(settings.getDirectHttpSettings()::setWebScannerEnabled)
                .onSet((selected) -> {
                    enableComponentsAndLabels(scannerFields, selected);
                })
                .build());

            scannerFields.add(addCheckBox(panel, CheckBoxBuilder.builder()
                .background(resolveColor(panel))
                .labelKey("settings.downloader.direct_http.web_scanner_strict_host")
                .getter(settings.getDirectHttpSettings()::isWebScannerStrictHost)
                .setter(settings.getDirectHttpSettings()::setWebScannerStrictHost)
                .enabled(settings.getDirectHttpSettings().isWebScannerEnabled())
                .build()));

            scannerFields.add(addSlider(panel, SliderBuilder.builder()
                .background(resolveColor(panel))
                .labelKey("settings.downloader.direct_http.web_scanner_max_depth")
                .min(0).max(10).majorTickSpacing(1)
                .snapToTicks(true)
                .getter(settings.getDirectHttpSettings()::getWebScannerMaxDepth)
                .setter(settings.getDirectHttpSettings()::setWebScannerMaxDepth)
                .enabled(settings.getDirectHttpSettings().isWebScannerEnabled())
                .build()));

            scannerFields.add(addTextField(panel, TextFieldBuilder.builder()
                .background(resolveColor(panel))
                .labelKey("settings.downloader.direct_http.web_scanner_allowed_extensions")
                .getter(settings.getDirectHttpSettings()::getWebScannerAllowedExtensions)
                .setter(settings.getDirectHttpSettings()::setWebScannerAllowedExtensions)
                .enabled(settings.getDirectHttpSettings().isWebScannerEnabled())
                .placeholderText(l10n("gui.example") + " jpg,png,mp4,zip")
                .build()));

            scannerFields.add(addTextField(panel, TextFieldBuilder.builder()
                .background(resolveColor(panel))
                .labelKey("settings.downloader.direct_http.web_scanner_blacklisted_extensions")
                .getter(settings.getDirectHttpSettings()::getWebScannerBlacklistedExtensions)
                .setter(settings.getDirectHttpSettings()::setWebScannerBlacklistedExtensions)
                .enabled(settings.getDirectHttpSettings().isWebScannerEnabled())
                .placeholderText(l10n("gui.example") + " js,html,htm")
                .build()));

            scannerFields.add(addMegabyteTextField(panel, LongFieldBuilder.builder()
                .background(resolveColor(panel))
                .labelKey("settings.downloader.direct_http.web_scanner_max_page_size_mb")
                .getter(settings.getDirectHttpSettings()::getMaxPageSizeBytes)
                .setter(settings.getDirectHttpSettings()::setMaxPageSizeBytes)
                .placeholderText(l10n("gui.example") + " 100")
                .build()));
        }

        return wrapInScrollableSettingsPanel(panel);
    }

    private JPanel createAdvancedDownloadSettingsTab() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(color(BACKGROUND));

        addSlider(panel, SliderBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.maximum_download_retries")
            .min(0).max(50).majorTickSpacing(5)
            .getter(settings::getMaxDownloadRetries)
            .setter(settings::setMaxDownloadRetries)
            .build());

        addSlider(panel, SliderBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.maximum_fragment_retries")
            .min(0).max(50).majorTickSpacing(5)
            .getter(settings::getMaxFragmentRetries)
            .setter(settings::setMaxFragmentRetries)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.transcode.keep_raw_media_files_after_transcode")
            .getter(settings::isKeepRawMediaFilesAfterTranscode)
            .setter(settings::setKeepRawMediaFilesAfterTranscode)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.transcode.fail_downloads_on_transcoding_failures")
            .getter(settings::isFailDownloadsOnTranscodingFailures)
            .setter(settings::setFailDownloadsOnTranscodingFailures)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.use_upload_time_as_file_time")
            .getter(settings::isUseUploadTimeAsFileTime)
            .setter(settings::setUseUploadTimeAsFileTime)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.disable_aac_pns")
            .getter(settings::isDisableAACPns)
            .setter(settings::setDisableAACPns)
            .build());

        return wrapInScrollableSettingsPanel(panel);
    }

    private JPanel createResolutionSettings() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(color(BACKGROUND));

        mainPanel.add(createQualitySettingsCard(settings.getGlobalQualitySettings(), null));
        for (AbstractUrlFilter filter : settings.getUrlFilters()) {
            QualitySettings qualitySettings = filter.getQualitySettings();
            mainPanel.add(createQualitySettingsCard(qualitySettings, filter));
        }

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 400));
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        SmoothScroller.install(scrollPane);

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBackground(color(BACKGROUND));

        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    private JPanel createQualitySettingsCard(QualitySettings qualitySettings, @Nullable AbstractUrlFilter filter) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D)g.create();

                int arcSize = 10;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(5, 5, getWidth() - 10, getHeight() - 10, arcSize, arcSize);

                g2d.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BorderLayout());
        card.setBackground(color(MEDIA_CARD));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color(BACKGROUND), 5),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel(filter == null
            ? l10n("settings.global_quality_settings.title")
            : filter.getDisplayName());
        titleLabel.setForeground(color(FOREGROUND));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));

        JLabel expandLabel = new JLabel("▼");
        expandLabel.setForeground(color(FOREGROUND));
        expandLabel.setFont(expandLabel.getFont().deriveFont(Font.BOLD));

        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(expandLabel, BorderLayout.EAST);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        JPanel itemPanel = new JPanel();
        itemPanel.setLayout(new BoxLayout(itemPanel, BoxLayout.Y_AXIS));
        itemPanel.setOpaque(false);
        itemPanel.setVisible(false);

        addLabel(itemPanel, "settings.selector.panel.title");

        List<JComponent> toggleableComponents = new ArrayList<>();

        AtomicReference<JCheckBox> useGlobalCheckbox = new AtomicReference<>();
        AtomicReference<FFmpegConfig> currentFFmpegConfig = new AtomicReference<>(
            qualitySettings.getTranscodingSettings());
        AtomicReference<CustomTranscodePanel> transcodePanel = new AtomicReference<>();

        if (filter == null || !filter.isAudioOnly()) {
            if (filter != null) {
                useGlobalCheckbox.set(addCheckBox(itemPanel, CheckBoxBuilder.builder()
                    .background(resolveColor(itemPanel))
                    .labelKey("settings.use_global_settings")
                    .getter(qualitySettings::isUseGlobalSettings)
                    .setter(qualitySettings::setUseGlobalSettings)
                    .onSet((value) -> enableComponentsAndLabels(toggleableComponents, !value))
                    .build()));
            }

            toggleableComponents.add(addComboBox(itemPanel, ComboBoxBuilder.<QualitySelectorEnum>builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.quality_selector")
                .values(QualitySelectorEnum.values())
                .getter(qualitySettings::getSelector)
                .setter(qualitySettings::setSelector)
                // TODO: reimplement as different selectors
                .build()
            ));

            AtomicReference<JComboBox<ResolutionEnum>> minHeightComboBoxRef = new AtomicReference<>();
            AtomicReference<JComboBox<ResolutionEnum>> maxHeightComboBoxRef = new AtomicReference<>();

            minHeightComboBoxRef.set(addComboBox(itemPanel, ComboBoxBuilder.<ResolutionEnum>builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.minimum_quality")
                .values(ResolutionEnum.values())
                .getter(qualitySettings::getMinHeight)
                .setter(qualitySettings::setMinHeight)
                .onSet((selected) -> {
                    ResolutionEnum maxResolution = selected.getValidMax(qualitySettings.getMaxHeight());
                    qualitySettings.setMaxHeight(maxResolution);

                    maxHeightComboBoxRef.get().setSelectedItem(maxResolution);
                })
                .build()));
            toggleableComponents.add(minHeightComboBoxRef.get());

            maxHeightComboBoxRef.set(addComboBox(itemPanel, ComboBoxBuilder.<ResolutionEnum>builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.maximum_quality")
                .values(ResolutionEnum.values())
                .getter(qualitySettings::getMaxHeight)
                .setter(qualitySettings::setMaxHeight)
                .onSet((selected) -> {
                    ResolutionEnum minResolution = selected.getValidMin(qualitySettings.getMinHeight());
                    qualitySettings.setMinHeight(minResolution);

                    minHeightComboBoxRef.get().setSelectedItem(minResolution);
                })
                .build()));
            toggleableComponents.add(maxHeightComboBoxRef.get());

            toggleableComponents.add(addComboBox(itemPanel, ComboBoxBuilder.<VideoContainerEnum>builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.video_container")
                .values(VideoContainerEnum.getYtDlpContainers())
                .getter(qualitySettings::getVideoContainer)
                .setter(qualitySettings::setVideoContainer)
                .build()));

            toggleableComponents.add(addComboBox(itemPanel, ComboBoxBuilder.<FPSEnum>builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.fps")
                .values(FPSEnum.values())
                .getter(qualitySettings::getFps)
                .setter(qualitySettings::setFps)
                .build()));

            toggleableComponents.add(addComboBox(itemPanel, ComboBoxBuilder.<SubtitleContainerEnum>builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.subtitle_container")
                .values(SubtitleContainerEnum.values())
                .getter(qualitySettings::getSubtitleContainer)
                .setter(qualitySettings::setSubtitleContainer)
                .build()));

            toggleableComponents.add(addComboBox(itemPanel, ComboBoxBuilder.<ThumbnailContainerEnum>builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.thumbnail_container")
                .values(ThumbnailContainerEnum.values())
                .getter(qualitySettings::getThumbnailContainer)
                .setter(qualitySettings::setThumbnailContainer)
                .build()));
        }

        if (filter == null || !filter.isVideoOnly()) {
            toggleableComponents.add(addComboBox(itemPanel, ComboBoxBuilder.<AudioContainerEnum>builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.audio_container")
                .values(AudioContainerEnum.values())
                .getter(qualitySettings::getAudioContainer)
                .setter(qualitySettings::setAudioContainer)
                .build()));

            toggleableComponents.add(addComboBox(itemPanel, ComboBoxBuilder.<AudioBitrateEnum>builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.audio_bitrate")
                .values(AudioBitrateEnum.values())
                .getter(qualitySettings::getAudioBitrate)
                .setter(qualitySettings::setAudioBitrate)
                .build()));
        }

        if (filter == null || filter.isCanTranscodeVideo() && !filter.isAudioOnly()) {
            CustomTranscodePanel panel = new CustomTranscodePanel(
                currentFFmpegConfig.get(), main.getFfmpegTranscoder(),
                SETTINGS_ROW_BACKGROUND_LIGHT);

            itemPanel.add(panel);

            toggleableComponents.add(panel);
            transcodePanel.set(panel);
        }

        if (filter != null) {
            addLabel(itemPanel, "settings.metadata.panel.title");

            addCheckBox(itemPanel, CheckBoxBuilder.builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.metadata.embed_thumbnail")
                .getter(filter::isEmbedThumbnail)
                .setter(filter::setEmbedThumbnail)
                .build());

            addCheckBox(itemPanel, CheckBoxBuilder.builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.metadata.embed_subtitles")
                .getter(filter::isEmbedSubtitles)
                .setter(filter::setEmbedSubtitles)
                .build());

            addCheckBox(itemPanel, CheckBoxBuilder.builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.metadata.embed_metadata")
                .getter(filter::isEmbedMetadata)
                .setter(filter::setEmbedMetadata)
                .build());

            addLabel(itemPanel, "settings.advanced.panel.title");

            List<JComponent> namePatternFields = new ArrayList<>();
            addCheckBox(itemPanel, CheckBoxBuilder.builder()
                .background(resolveColor(itemPanel))
                .labelKey("settings.advanced.edit_name_patterns")
                // Stateless checkbox
                .onSet((selected) -> {
                    enableComponentsAndLabels(namePatternFields, selected);
                })
                .build());

            if (!filter.isAudioOnly()) {
                AtomicReference<String> previousPattern = new AtomicReference<>(filter.getVideoNamePattern());
                namePatternFields.add(addTextField(itemPanel, TextFieldBuilder.builder()
                    .background(resolveColor(itemPanel))
                    .labelKey("settings.advanced.video_name_pattern")
                    .getter(filter::getVideoNamePattern)
                    .setter(filter::setVideoNamePattern)
                    .enabled(false)
                    .placeholderText(l10n("gui.example") + " %(title)s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s")
                    .onSet(newText -> {
                        if (newText.isEmpty()) {// Some hand-holding because this would break things.
                            log.error("New video name pattern was empty, reverting...");
                            filter.setVideoNamePattern(previousPattern.get());
                        }
                    })
                    .build()));
            }

            if (!filter.isVideoOnly()) {
                AtomicReference<String> previousPattern = new AtomicReference<>(filter.getAudioNamePattern());
                namePatternFields.add(addTextField(itemPanel, TextFieldBuilder.builder()
                    .background(resolveColor(itemPanel))
                    .labelKey("settings.advanced.audio_name_pattern")
                    .getter(filter::getAudioNamePattern)
                    .setter(filter::setAudioNamePattern)
                    .enabled(false)
                    .placeholderText(l10n("gui.example") + " %(title)s (%(audio_bitrate)s).%(ext)s")
                    .onSet(newText -> {
                        if (newText.isEmpty()) {
                            log.error("New audio name pattern was empty, reverting...");
                            filter.setAudioNamePattern(previousPattern.get());
                        }
                    })
                    .build()));
            }
        }

        JCheckBox globalCheckbox;
        if ((globalCheckbox = useGlobalCheckbox.get()) != null) {
            enableComponentsAndLabels(toggleableComponents, !globalCheckbox.isSelected());
        } else if (filter == null) {
            enableComponentsAndLabels(toggleableComponents, true);
        }

        MouseAdapter cardListener = new MouseAdapter() {
            private boolean isExpanded = false;

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getSource() == titlePanel) {
                    isExpanded = !itemPanel.isVisible();
                    itemPanel.setVisible(isExpanded);
                    expandLabel.setText(isExpanded ? "▲" : "▼");

                    card.setBackground(isExpanded
                        ? color(SETTINGS_ROW_BACKGROUND_LIGHT) : color(MEDIA_CARD));

                    card.revalidate();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isExpanded) {
                    card.setBackground(color(SETTINGS_ROW_BACKGROUND_LIGHT));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isExpanded) {
                    card.setBackground(color(MEDIA_CARD));
                }
            }
        };

        titlePanel.addMouseListener(cardListener);

        card.add(titlePanel, BorderLayout.NORTH);
        card.add(itemPanel, BorderLayout.CENTER);

        return card;
    }

    private JPanel getFillerPanel() {
        JPanel fillerPanel = new JPanel(new GridBagLayout());
        fillerPanel.setBackground(color(BACKGROUND));
        GridBagConstraints fillerGbc = new GridBagConstraints();
        fillerGbc.weightx = 1;
        fillerGbc.weightx = 1;
        fillerGbc.weighty = 1;
        fillerGbc.gridwidth = GridBagConstraints.REMAINDER;
        fillerGbc.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setBackground(color(BACKGROUND));
        fillerPanel.add(filler, fillerGbc);

        return fillerPanel;
    }

    private JPanel createNetworkSettings() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(color(BACKGROUND));

        addLabel(panel, "settings.network.proxy.title");

        ProxySettings proxySettings = settings.getProxySettings();
        List<JComponent> proxyFields = new ArrayList<>();

        JLabel statusLabel = new JLabel("");
        statusLabel.setForeground(color(LIGHT_TEXT));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 15, 10, 5));

        Runnable updateValidationState = () -> {
            if (!proxySettings.isEnabled()) {
                statusLabel.setText(" ");
            } else {
                boolean valid = proxySettings.isValid();
                statusLabel.setText(l10n(valid
                    ? "settings.network.proxy.status_valid"
                    : "settings.network.proxy.status_invalid"
                ));
                statusLabel.setForeground(valid
                    ? new Color(46, 204, 113)
                    : new Color(231, 76, 60)
                );
            }
        };

        JPanel proxyFieldsPanel = new JPanel();
        proxyFieldsPanel.setLayout(new BoxLayout(proxyFieldsPanel, BoxLayout.Y_AXIS));
        proxyFieldsPanel.setBackground(color(BACKGROUND));
        proxyFieldsPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        addCheckBox(proxyFieldsPanel, CheckBoxBuilder.builder()
            .background(resolveColor(proxyFieldsPanel))
            .labelKey("settings.network.proxy.enabled")
            .getter(proxySettings::isEnabled)
            .setter(proxySettings::setEnabled)
            .onSet(selected -> {
                enableComponentsAndLabels(proxyFields, selected);
                updateValidationState.run();
            })
            .build());

        proxyFields.add(addComboBox(proxyFieldsPanel, ComboBoxBuilder.<ProxyTypeEnum>builder()
            .background(resolveColor(proxyFieldsPanel))
            .labelKey("settings.network.proxy.type")
            .values(ProxyTypeEnum.values())
            .getter(proxySettings::getProxyType)
            .setter(proxySettings::setProxyType)
            .onSet(val -> updateValidationState.run())
            .build()));

        proxyFields.add(addTextField(proxyFieldsPanel, TextFieldBuilder.builder()
            .background(resolveColor(proxyFieldsPanel))
            .labelKey("settings.network.proxy.host")
            .getter(proxySettings::getHost)
            .setter(proxySettings::setHost)
            .onSet(val -> updateValidationState.run())
            .placeholderText("127.0.0.1")
            .build()));

        proxyFields.add(addLongField(proxyFieldsPanel, LongFieldBuilder.builder()
            .background(resolveColor(proxyFieldsPanel))
            .labelKey("settings.network.proxy.port")
            .getter(() -> (long)proxySettings.getPort())// not great, but will do
            .setter(val -> proxySettings.setPort(val.intValue()))
            .zeroIsEmpty(true)
            .clampMin(0L)
            .clampMax(65535L)
            .onSet(val -> updateValidationState.run())
            .placeholderText("8080")
            .build()));

        proxyFields.add(addTextField(proxyFieldsPanel, TextFieldBuilder.builder()
            .background(resolveColor(proxyFieldsPanel))
            .placeholderText(l10n("settings.network.proxy.optional"))
            .labelKey("settings.network.proxy.username")
            .getter(proxySettings::getUsername)
            .setter(proxySettings::setUsername)
            .onSet(val -> updateValidationState.run())
            .build()));

        proxyFields.add(addTextField(proxyFieldsPanel, TextFieldBuilder.builder()
            .background(resolveColor(proxyFieldsPanel))
            .placeholderText(l10n("settings.network.proxy.optional"))
            .labelKey("settings.network.proxy.password")
            .getter(proxySettings::getPassword)
            .setter(proxySettings::setPassword)
            .onSet(val -> updateValidationState.run())
            .build()));

        panel.add(proxyFieldsPanel);

        JPanel statusWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusWrapper.setBackground(color(BACKGROUND));
        statusWrapper.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        statusWrapper.add(statusLabel);
        panel.add(statusWrapper);

        enableComponentsAndLabels(proxyFields, proxySettings.isEnabled());
        updateValidationState.run();

        panel.add(getFillerPanel());

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        SmoothScroller.install(scrollPane);

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 5));
        panelWrapper.setBackground(color(BACKGROUND));
        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    public static UIColors resolveColor(JPanel panel) {
        return panel.getComponentCount() % 2 == 0
            ? SETTINGS_ROW_BACKGROUND_DARK : SETTINGS_ROW_BACKGROUND_LIGHT;
    }

    public static void wrapComponentRow(JPanel panel, JLabel label,
        @NonNull JComponent component, UIColors background) {
        Color rowColor = color(background);
        JPanel rowPanel = new JPanel(new GridLayout(1, 2, 0, 0));
        rowPanel.setBackground(rowColor);

        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 5));

        JPanel labelPanel = new JPanel(new BorderLayout());
        labelPanel.setBackground(rowColor);
        labelPanel.add(label, BorderLayout.CENTER);

        JPanel componentPanel = new JPanel(new BorderLayout());
        componentPanel.setBackground(rowColor);
        componentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        componentPanel.add(component, BorderLayout.CENTER);

        rowPanel.add(labelPanel);
        rowPanel.add(componentPanel);
        panel.add(rowPanel);

        component.putClientProperty("associated-label", label);
    }

    public static <T extends ITranslatable> JComboBox<T> addComboBox(JPanel panel, ComboBoxBuilder<T> builder) {
        JLabel label = createLabel(builder.getLabelKey(), LIGHT_TEXT);

        JComboBox<T> comboBox = new JComboBox<>(builder.getValues());
        if (builder.isRequiresRestart()) {
            comboBox.setToolTipText(l10n("settings.requires_restart.tooltip"));
        }

        if (builder.getGetter() != null) {
            comboBox.setSelectedItem(builder.getGetter().get());
        }

        comboBox.addActionListener((ActionEvent e) -> {
            @SuppressWarnings("unchecked")
            T selected = (T)comboBox.getSelectedItem();
            if (builder.getSetter() != null) {
                builder.getSetter().accept(selected);
            }

            if (builder.getOnSet() != null) {
                builder.getOnSet().accept(selected);
            }
        });

        customizeComboBox(comboBox);
        wrapComponentRow(panel, label, comboBox, builder.getBackground());
        enableComponentAndLabel(comboBox, builder.isEnabled());

        return comboBox;
    }

    public static JTextField addTextField(JPanel panel, TextFieldBuilder builder) {
        JLabel label = createLabel(builder.getLabelKey(), LIGHT_TEXT);

        JTextField textField = new JTextField(builder.getColumns());
        textField.setBackground(color(TEXT_AREA_BACKGROUND));
        textField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        if (builder.isRequiresRestart()) {
            textField.setToolTipText(l10n("settings.requires_restart.tooltip"));
        }

        String placeholder = builder.getPlaceholderText();

        String value = builder.getGetter().get();
        if (value.isEmpty() && placeholder != null) {
            textField.setText(placeholder);
            textField.setForeground(Color.GRAY);
        } else {
            textField.setText(value);
            textField.setForeground(color(TEXT_AREA_FOREGROUND));
        }

        if (placeholder != null) {
            textField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    if (textField.getText().equals(placeholder)) {
                        textField.setForeground(color(TEXT_AREA_FOREGROUND));
                        textField.setText("");
                    }
                }

                @Override
                public void focusLost(FocusEvent e) {
                    if (textField.getText().isEmpty()) {
                        textField.setForeground(Color.GRAY);
                        textField.setText(placeholder);
                    }
                }
            });
        }

        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String currentText = textField.getText();
                if (placeholder != null && currentText.equals(placeholder)) {
                    return;
                }

                String canonical = builder.getGetter().get();
                if (!canonical.equals(currentText)) {
                    textField.setText(canonical);
                    textField.setForeground(color(TEXT_AREA_FOREGROUND));
                }
            }
        });

        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                handleNewText(textField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                handleNewText(textField.getText());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                handleNewText(textField.getText());
            }

            private void handleNewText(String text) {
                if (placeholder == null || !text.equals(placeholder)) {
                    if (builder.getSetter() != null) {
                        builder.getSetter().accept(text);
                    }

                    if (builder.getOnSet() != null) {
                        builder.getOnSet().accept(text);
                    }
                }
            }
        });

        wrapComponentRow(panel, label, textField, builder.getBackground());
        enableComponentAndLabel(textField, builder.isEnabled());

        return textField;
    }

    private static long clampAndParse(LongFieldBuilder builder, String val) {
        String trimmed = val.trim();
        if (builder.isZeroIsEmpty() && trimmed.isEmpty()) {
            trimmed = "0";
        }

        long result = Long.parseLong(trimmed);
        Long clampMin = builder.getClampMin();
        Long clampMax = builder.getClampMax();

        if (clampMin != null && result < clampMin) {
            result = clampMin;
        }

        if (clampMax != null && result > clampMax) {
            result = clampMax;
        }

        return result;
    }

    public static JTextField addLongField(JPanel panel, LongFieldBuilder builder) {
        return addTextField(panel, TextFieldBuilder.builder()
            .background(builder.getBackground())
            .labelKey(builder.getLabelKey())
            .columns(builder.getColumns())
            .requiresRestart(builder.isRequiresRestart())
            .enabled(builder.isEnabled())
            .placeholderText(builder.getPlaceholderText())
            .getter(() -> {
                long val = builder.getGetter().get();
                if (builder.isZeroIsEmpty() && val == 0) {
                    return "";
                }

                return String.valueOf(val);
            })
            .setter(val -> {
                try {
                    builder.getSetter().accept(clampAndParse(builder, val));
                } catch (Exception e) {
                    // ignore
                }
            })
            .onSet(val -> {
                if (builder.getOnSet() != null) {
                    try {
                        builder.getOnSet().accept(clampAndParse(builder, val));
                    } catch (Exception e) {
                        // ignore
                    }
                }
            })
            .build());
    }

    private static JTextField addMegabyteTextField(JPanel panel, LongFieldBuilder builder) {
        return addTextField(panel, TextFieldBuilder.builder()
            .background(builder.getBackground())
            .labelKey(builder.getLabelKey())
            .columns(builder.getColumns())
            .requiresRestart(builder.isRequiresRestart())
            .enabled(builder.isEnabled())
            .placeholderText(builder.getPlaceholderText())
            .getter(() -> {
                long val = builder.getGetter().get() / 1048576L;
                if (builder.isZeroIsEmpty() && val == 0) {
                    return "";
                }

                return String.valueOf(val);
            })
            .setter(val -> {
                try {
                    String trimmed = val.trim();
                    if (builder.isZeroIsEmpty() && trimmed.isEmpty()) {
                        trimmed = "0";
                    }

                    long bytes = Long.parseLong(trimmed) * 1048576L;

                    Long clampMin = builder.getClampMin();
                    Long clampMax = builder.getClampMax();

                    if (clampMin != null && bytes < clampMin) {
                        bytes = clampMin;
                    }

                    if (clampMax != null && bytes > clampMax) {
                        bytes = clampMax;
                    }

                    builder.getSetter().accept(bytes);
                } catch (NumberFormatException ignored) {
                }
            })
            .build());
    }

    private static String formatSpeedLabel(long bytesPerSecond) {
        return bytesPerSecond <= 0 ? "∞"
            : getHumanReadableFileSize(bytesPerSecond) + "/s";
    }

    public static JCheckBox addCheckBox(JPanel panel, CheckBoxBuilder builder) {
        JLabel label = createLabel(builder.getLabelKey(), LIGHT_TEXT);

        JCheckBox checkBox = new JCheckBox();
        if (builder.isRequiresRestart()) {
            checkBox.setToolTipText(l10n("settings.requires_restart.tooltip"));
        } else if (builder.getTooltipText() != null) {
            checkBox.setToolTipText(builder.getTooltipText());
        }

        if (builder.getGetter() != null) {
            checkBox.setSelected(builder.getGetter().get());
        }

        checkBox.addActionListener((ActionEvent e) -> {
            if (builder.getSetter() != null) {
                builder.getSetter().accept(checkBox.isSelected());
            }

            if (builder.getOnSet() != null) {
                builder.getOnSet().accept(checkBox.isSelected());
            }
        });

        customizeComponent(checkBox, builder.getBackground(), LIGHT_TEXT);
        wrapComponentRow(panel, label, checkBox, builder.getBackground());
        enableComponentAndLabel(checkBox, builder.isEnabled());

        return checkBox;
    }

    public static JSlider addSlider(JPanel panel, SliderBuilder builder) {
        JLabel label = createLabel(builder.getLabelKey(), LIGHT_TEXT);

        JSlider slider = new JSlider(builder.getMin(), builder.getMax(), builder.getGetter().get());
        if (builder.isRequiresRestart()) {
            slider.setToolTipText(l10n("settings.requires_restart.tooltip"));
        }

        slider.setMinorTickSpacing(builder.getMinorTickSpacing());
        slider.setMajorTickSpacing(builder.getMajorTickSpacing());
        slider.setSnapToTicks(builder.isSnapToTicks());
        slider.setPaintTicks(builder.isPaintTicks());
        slider.setPaintLabels(builder.isPaintLabels());

        slider.addChangeListener((ChangeEvent e) -> {
            JSlider source = (JSlider)e.getSource();
            int sliderValue = source.getValue();

            if (builder.getOnAdjust() != null) {
                builder.getOnAdjust().accept(sliderValue);
            }

            if (!source.getValueIsAdjusting()) {
                if (builder.getSetter() != null) {
                    builder.getSetter().accept(sliderValue);
                }

                if (builder.getOnSet() != null) {
                    builder.getOnSet().accept(sliderValue);
                }
            }
        });

        customizeSlider(slider, builder.getBackground(), SLIDER_FOREGROUND);
        wrapComponentRow(panel, label, slider, builder.getBackground());
        enableComponentAndLabel(slider, builder.isEnabled());

        return slider;
    }

    public static JLabel addLabel(JPanel panel, String labelKey) {
        return addLabel(panel, labelKey, resolveColor(panel));
    }

    private static Font _cachedLabelFont = null;

    public static JLabel addLabel(JPanel panel, String labelKey, UIColors background) {
        Color rowColor = color(background);
        JPanel rowPanel = new JPanel(new GridLayout(1, 1, 0, 0));
        rowPanel.setBackground(rowColor);

        JLabel label = createLabel(labelKey, FOREGROUND);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setBorder(BorderFactory.createEmptyBorder(17, 15, 17, 5));

        if (_cachedLabelFont == null) {
            Font currentFont = label.getFont();
            _cachedLabelFont = currentFont.deriveFont(currentFont.getSize() + 1.2f);
        }

        label.setFont(_cachedLabelFont);

        JPanel labelPanel = new JPanel(new BorderLayout());
        labelPanel.setBackground(rowColor);
        labelPanel.add(label, BorderLayout.CENTER);

        rowPanel.add(labelPanel);
        panel.add(rowPanel);

        return label;
    }

    @Data
    private class SettingsMenuEntry {

        private final String displayName;
        private final String icon;
        private final Supplier<JPanel> panel;

        public SettingsMenuEntry(String translationKey, String iconIn, Supplier<JPanel> panelIn) {
            displayName = l10n(translationKey);
            icon = iconIn;
            panel = panelIn;
        }
    }
}
