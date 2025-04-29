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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.ffmpeg.enums.AudioBitrateEnum;
import net.brlns.gdownloader.ffmpeg.structs.FFmpegConfig;
import net.brlns.gdownloader.lang.ITranslatable;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.*;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.ui.builder.CheckBoxBuilder;
import net.brlns.gdownloader.ui.builder.ComboBoxBuilder;
import net.brlns.gdownloader.ui.builder.SliderBuilder;
import net.brlns.gdownloader.ui.builder.TextFieldBuilder;
import net.brlns.gdownloader.ui.custom.CustomScrollBarUI;
import net.brlns.gdownloader.ui.custom.CustomTranscodePanel;
import net.brlns.gdownloader.ui.message.Message;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.ui.themes.UIColors;
import net.brlns.gdownloader.util.StartupManager;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.ui.GUIManager.createIconButton;
import static net.brlns.gdownloader.ui.UIUtils.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

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
            frame.setSize(1000, 655);
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
                headerPanel.setBackground(color(SIDE_PANEL));

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
                            button1.setBackground(color(SIDE_PANEL));
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
                JButton gabButton = new JButton("@hstr0100");
                gabButton.setToolTipText("Gabriel's GitHub - #hireme");
                gabButton.setUI(new BasicButtonUI());
                gabButton.setForeground(color(FOREGROUND));
                gabButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
                gabButton.setFocusPainted(false);
                gabButton.setBorderPainted(false);
                gabButton.setContentAreaFilled(false);
                gabButton.setOpaque(false);

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

                gabButton.setPreferredSize(new Dimension(64, 64));
                gbc.gridy++;
                gbc.weighty = 0;
                gbc.fill = GridBagConstraints.HORIZONTAL;
                sidebarPanel.add(gabButton, gbc);
            }

            frame.add(sidebarPanel, BorderLayout.WEST);

            frame.add(mainContentPanel, BorderLayout.CENTER);

            cardLayout.show(contentPanel, String.valueOf(0));

            frame.setVisible(true);
        });
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

        panel.add(getFillerPanel());

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 5));
        panelWrapper.setBackground(color(BACKGROUND));

        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    private JPanel createDownloadSettings() {
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

        addLabel(panel, "settings.downloader.yt_dlp");

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
            .getter(settings::isUseSponsorBlock)
            .setter(settings::setUseSponsorBlock)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_youtube_channels")
            .getter(settings::isDownloadYoutubeChannels)
            .setter(settings::setDownloadYoutubeChannels)
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
        //    .getter(settings::isMergeAllAudioTracks)
        //    .setter(settings::setMergeAllAudioTracks)
        //    .build());
        AtomicReference<JCheckBox> autoGenSubtitlesCheckBoxRef = new AtomicReference<>();
        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_subtitles")
            .getter(settings::isDownloadSubtitles)
            .setter(settings::setDownloadSubtitles)
            .onSet((selected) -> {
                enableComponentAndLabel(autoGenSubtitlesCheckBoxRef.get(), selected);
            })
            .build());

        autoGenSubtitlesCheckBoxRef.set(addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_auto_generated_subtitles")
            .getter(settings::isDownloadAutoGeneratedSubtitles)
            .setter(settings::setDownloadAutoGeneratedSubtitles)
            .enabled(settings.isDownloadSubtitles())
            .build()));

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.download_thumbnails")
            .getter(settings::isDownloadThumbnails)
            .setter(settings::setDownloadThumbnails)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.respect_ytdlp_config_file")
            .getter(settings::isRespectYtDlpConfigFile)
            .setter(settings::setRespectYtDlpConfigFile)
            .build());

        // https://github.com/yt-dlp/yt-dlp/issues/12746
        // Might also apply for any future hiccups
        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.missing_formats_workaround")
            .getter(settings::isMissingFormatsWorkaround)
            .setter(settings::setMissingFormatsWorkaround)
            .build());

        addLabel(panel, "settings.downloader.gallery_dl");

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.gallery_dl.enabled")
            .getter(settings::isGalleryDlEnabled)
            .setter(settings::setGalleryDlEnabled)
            .requiresRestart(true)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.gallery_dl.respect_config_file")
            .getter(settings::isRespectGalleryDlConfigFile)
            .setter(settings::setRespectGalleryDlConfigFile)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.gallery_dl.deduplicate_files")
            .getter(settings::isGalleryDlDeduplication)
            .setter(settings::setGalleryDlDeduplication)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.gallery_dl.enable_transcoding")
            .getter(settings::isGalleryDlTranscoding)
            .setter(settings::setGalleryDlTranscoding)
            .build());

        addLabel(panel, "settings.downloader.spotdl");

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.spotdl.enabled")
            .getter(settings::isSpotDLEnabled)
            .setter(settings::setSpotDLEnabled)
            .requiresRestart(true)
            .build());

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.spotdl.respect_config_file")
            .getter(settings::isRespectSpotDLConfigFile)
            .setter(settings::setRespectSpotDLConfigFile)
            .build());

        addLabel(panel, "settings.downloader.direct_http");

        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.direct_http.enabled")
            .getter(settings::isDirectHttpEnabled)
            .setter(settings::setDirectHttpEnabled)
            .requiresRestart(true)
            .build());

        addSlider(panel, SliderBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.downloader.direct_http.max_download_chunks")
            .min(1).max(15).majorTickSpacing(1)
            .getter(settings::getDirectHttpMaxDownloadChunks)
            .setter(settings::setDirectHttpMaxDownloadChunks)
            .build());

        addLabel(panel, "settings.label.advanced");

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

        List<JComponent> extraArgumentFields = new ArrayList<>();
        addCheckBox(panel, CheckBoxBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.enable_extra_arguments")
            .getter(settings::isEnableExtraArguments)
            .setter(settings::setEnableExtraArguments)
            .onSet((selected) -> {
                enableComponentsAndLabels(extraArgumentFields, selected);
            })
            .build());

        extraArgumentFields.add(addTextField(panel, TextFieldBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.extra_yt_dlp_arguments")
            .getter(settings::getExtraYtDlpArguments)
            .setter(settings::setExtraYtDlpArguments)
            .enabled(settings.isEnableExtraArguments())
            .placeholderText("E.g: --ignore-config --proxy http://example.com:1234")
            .build()));

        extraArgumentFields.add(addTextField(panel, TextFieldBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.extra_gallery_dl_arguments")
            .getter(settings::getExtraGalleryDlArguments)
            .setter(settings::setExtraGalleryDlArguments)
            .enabled(settings.isEnableExtraArguments())
            .placeholderText("E.g: --config-ignore --proxy http://example.com:1234")
            .build()));

        extraArgumentFields.add(addTextField(panel, TextFieldBuilder.builder()
            .background(resolveColor(panel))
            .labelKey("settings.extra_spotdl_arguments")
            .getter(settings::getExtraSpotDLArguments)
            .setter(settings::setExtraSpotDLArguments)
            .enabled(settings.isEnableExtraArguments())
            .placeholderText("E.g: --proxy http://example.com:1234")
            .build()));

        panel.add(getFillerPanel());

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 5));
        panelWrapper.setBackground(color(BACKGROUND));

        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
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
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 400));
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);

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
                    .placeholderText("E.g: %(title)s (%(uploader_id)s %(upload_date)s %(resolution)s).%(ext)s")
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
                    .placeholderText("E.g: %(title)s (%(audio_bitrate)s).%(ext)s")
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

    public static JCheckBox addCheckBox(JPanel panel, CheckBoxBuilder builder) {
        JLabel label = createLabel(builder.getLabelKey(), LIGHT_TEXT);

        JCheckBox checkBox = new JCheckBox();
        if (builder.isRequiresRestart()) {
            checkBox.setToolTipText(l10n("settings.requires_restart.tooltip"));
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
