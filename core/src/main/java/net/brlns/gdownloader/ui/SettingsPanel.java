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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.ffmpeg.enums.AudioBitrateEnum;
import net.brlns.gdownloader.ffmpeg.enums.AudioCodecEnum;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.*;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.ui.custom.CustomButton;
import net.brlns.gdownloader.ui.custom.CustomCheckBoxUI;
import net.brlns.gdownloader.ui.custom.CustomComboBoxUI;
import net.brlns.gdownloader.ui.custom.CustomScrollBarUI;
import net.brlns.gdownloader.ui.custom.CustomSliderUI;
import net.brlns.gdownloader.ui.custom.CustomSpinnerUI;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;
import net.brlns.gdownloader.ui.message.PopupMessenger;
import net.brlns.gdownloader.ui.themes.UIColors;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.ui.GUIManager.createIconButton;
import static net.brlns.gdownloader.ui.GUIManager.loadIcon;
import static net.brlns.gdownloader.ui.GUIManager.runOnEDT;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: fix column width
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

                PopupMessenger.show(
                    l10n("gui.error_popup_title"),
                    l10n("gui.error_download_path_not_writable", file.getAbsolutePath()),
                    4000,
                    MessageTypeEnum.ERROR,
                    true, false);

                log.error("Selected path not writable {}", file);
            }
        }

        main.updateConfig(settings);

        main.getGuiManager().refreshAppWindow();
        main.updateStartupStatus();
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
            frame.setSize(950, 623);
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
                            "gui.open_config.tooltip",
                            e -> main.openConfigFile()
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
                            e -> main.checkForUpdates()
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
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(color(BACKGROUND));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbcPanel = new GridBagConstraints();
        gbcPanel.insets = new Insets(5, 5, 5, 5);
        gbcPanel.anchor = GridBagConstraints.WEST;
        gbcPanel.fill = GridBagConstraints.HORIZONTAL;
        //gbcPanel.weightx = 1;

        addComboBox(panel, gbcPanel,
            "settings.language",
            LanguageEnum.class,
            settings::getLanguage,
            settings::setLanguage,
            true
        );

        addComboBox(panel, gbcPanel,
            "settings.theme",
            ThemeEnum.class,
            settings::getTheme,
            settings::setTheme,
            true
        );

        addCheckBox(panel, gbcPanel,
            "settings.always_on_top",
            settings::isKeepWindowAlwaysOnTop,
            settings::setKeepWindowAlwaysOnTop,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.exit_on_close",
            settings::isExitOnClose,
            settings::setExitOnClose,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.automatic_updates",
            settings::isAutomaticUpdates,
            settings::setAutomaticUpdates,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.debug_mode",
            settings::isDebugMode,
            settings::setDebugMode,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.restore_session_after_restart",
            settings::isRestoreSessionAfterRestart,
            settings::setRestoreSessionAfterRestart,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.start_on_system_startup",
            settings::isAutoStart,
            settings::setAutoStart,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.play_sounds",
            settings::isPlaySounds,
            settings::setPlaySounds,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.display_link_capture_notifications",
            settings::isDisplayLinkCaptureNotifications,
            settings::setDisplayLinkCaptureNotifications,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.auto_scroll_to_bottom",
            settings::isAutoScrollToBottom,
            settings::setAutoScrollToBottom,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.use_system_font",
            settings::isUseSystemFont,
            settings::setUseSystemFont,
            true
        );

        {
            JLabel label = createLabel("settings.font_size", LIGHT_TEXT);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            gbcPanel.weightx = 0.5;
            panel.add(label, gbcPanel);

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

            customizeSlider(slider, BACKGROUND, SLIDER_FOREGROUND);

            gbcPanel.gridx = 1;
            gbcPanel.weightx = 1;
            panel.add(slider, gbcPanel);
        }

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 1;
        gbcPanel.weighty = 1;
        gbcPanel.gridwidth = GridBagConstraints.REMAINDER;
        gbcPanel.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setBackground(color(BACKGROUND));
        panel.add(filler, gbcPanel);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        panelWrapper.setBackground(color(BACKGROUND));

        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    private JPanel createDownloadSettings() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(color(BACKGROUND));

        GridBagConstraints gbcPanel = new GridBagConstraints();
        gbcPanel.insets = new Insets(5, 5, 5, 5);
        gbcPanel.anchor = GridBagConstraints.WEST;
        gbcPanel.fill = GridBagConstraints.HORIZONTAL;
        //gbcPanel.weightx = 1;

        addCheckBox(panel, gbcPanel,
            "settings.read_cookies",
            settings::isReadCookiesFromBrowser,
            settings::setReadCookiesFromBrowser,
            false
        );

        addComboBox(panel, gbcPanel,
            "settings.browser_for_cookies",
            BrowserEnum.class,
            settings::getBrowser,
            settings::setBrowser,
            false
        );

        {
            JLabel label = createLabel("settings.downloads_path", LIGHT_TEXT);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            panel.add(label, gbcPanel);

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

            gbcPanel.gridx = 1;
            gbcPanel.gridwidth = 1;
            gbcPanel.weightx = 0.9;
            panel.add(downloadPathField, gbcPanel);

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

            selectButton.setPreferredSize(new Dimension(20, downloadPathField.getPreferredSize().height));

            gbcPanel.gridx = 2;
            gbcPanel.weightx = 0.1;
            panel.add(selectButton, gbcPanel);
        }

        addCheckBox(panel, gbcPanel,
            "settings.read_cookies_txt",
            settings::isReadCookiesFromCookiesTxt,
            settings::setReadCookiesFromCookiesTxt,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.impersonate_browser",
            settings::isImpersonateBrowser,
            settings::setImpersonateBrowser,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.capture_any_clipboard_link",
            settings::isCaptureAnyLinks,
            settings::setCaptureAnyLinks,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.auto_download_start",
            settings::isAutoDownloadStart,
            settings::setAutoDownloadStart,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.auto_download_retry",
            settings::isAutoDownloadRetry,
            settings::setAutoDownloadRetry,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.download_audio",
            settings::isDownloadAudio,
            settings::setDownloadAudio,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.download_video",
            settings::isDownloadVideo,
            settings::setDownloadVideo,
            false
        );

        addSlider(panel, gbcPanel,
            "settings.maximum_simultaneous_downloads",
            1, 10,
            settings::getMaxSimultaneousDownloads,
            settings::setMaxSimultaneousDownloads
        );

        addCheckBox(panel, gbcPanel,
            "settings.prefer_system_executables",
            settings::isPreferSystemExecutables,
            settings::setPreferSystemExecutables,
            true
        );

        addCheckBox(panel, gbcPanel,
            "settings.random_interval_between_downloads",
            settings::isRandomIntervalBetweenDownloads,
            settings::setRandomIntervalBetweenDownloads,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.remove_successful_downloads",
            settings::isRemoveSuccessfulDownloads,
            settings::setRemoveSuccessfulDownloads,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.record_to_download_archive",
            settings::isRecordToDownloadArchive,
            settings::setRecordToDownloadArchive,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.remove_from_download_archive",
            settings::isRemoveFromDownloadArchive,
            settings::setRemoveFromDownloadArchive,
            false
        );

        addLabel(panel, gbcPanel, "settings.downloader.yt_dlp");

        addComboBox(panel, gbcPanel,
            "settings.playlist_download_option",
            PlayListOptionEnum.class,
            settings::getPlaylistDownloadOption,
            settings::setPlaylistDownloadOption,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.use_sponsor_block",
            settings::isUseSponsorBlock,
            settings::setUseSponsorBlock,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.download_youtube_channels",
            settings::isDownloadYoutubeChannels,
            settings::setDownloadYoutubeChannels,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.transcode_audio_to_aac",
            settings::isTranscodeAudioToAAC,
            settings::setTranscodeAudioToAAC,
            false
        );

        // TODO: this will remain hidden until this yt-dlp feature request is addressed:
        // https://github.com/yt-dlp/yt-dlp/issues/1176
        // addCheckBox(panel, gbcPanel,
        //     "settings.merge_all_audio_tracks",
        //     settings::isMergeAllAudioTracks,
        //     settings::setMergeAllAudioTracks,
        //     false
        // );
        addCheckBox(panel, gbcPanel,
            "settings.download_subtitles",
            settings::isDownloadSubtitles,
            settings::setDownloadSubtitles,
            false
        );

        // TODO: this should be grayed out when download_subtitles is disabled
        addCheckBox(panel, gbcPanel,
            "settings.download_auto_generated_subtitles",
            settings::isDownloadAutoGeneratedSubtitles,
            settings::setDownloadAutoGeneratedSubtitles,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.download_thumbnails",
            settings::isDownloadThumbnails,
            settings::setDownloadThumbnails,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.query_metadata",
            settings::isQueryMetadata,
            settings::setQueryMetadata,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.respect_ytdlp_config_file",
            settings::isRespectYtDlpConfigFile,
            settings::setRespectYtDlpConfigFile,
            false
        );

        // https://github.com/yt-dlp/yt-dlp/issues/12746
        // Might also apply for any future hiccups
        addCheckBox(panel, gbcPanel,
            "settings.missing_formats_workaround",
            settings::isMissingFormatsWorkaround,
            settings::setMissingFormatsWorkaround,
            false
        );

        addLabel(panel, gbcPanel, "settings.downloader.gallery_dl");

        addCheckBox(panel, gbcPanel,
            "settings.downloader.gallery_dl.enabled",
            settings::isGalleryDlEnabled,
            settings::setGalleryDlEnabled,
            true
        );

        addCheckBox(panel, gbcPanel,
            "settings.downloader.gallery_dl.respect_config_file",
            settings::isRespectGalleryDlConfigFile,
            settings::setRespectGalleryDlConfigFile,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.downloader.gallery_dl.deduplicate_files",
            settings::isGalleryDlDeduplication,
            settings::setGalleryDlDeduplication,
            false
        );

        addLabel(panel, gbcPanel, "settings.downloader.spotdl");

        addCheckBox(panel, gbcPanel,
            "settings.downloader.spotdl.enabled",
            settings::isSpotDLEnabled,
            settings::setSpotDLEnabled,
            true
        );

        addCheckBox(panel, gbcPanel,
            "settings.downloader.spotdl.respect_config_file",
            settings::isRespectSpotDLConfigFile,
            settings::setRespectSpotDLConfigFile,
            false
        );

        addLabel(panel, gbcPanel, "settings.downloader.direct_http");

        addCheckBox(panel, gbcPanel,
            "settings.downloader.direct_http.enabled",
            settings::isDirectHttpEnabled,
            settings::setDirectHttpEnabled,
            true
        );

        addSlider(panel, gbcPanel,
            "settings.downloader.direct_http.max_download_chunks",
            1, 15,
            settings::getDirectHttpMaxDownloadChunks,
            settings::setDirectHttpMaxDownloadChunks
        );

        addLabel(panel, gbcPanel, "settings.label.advanced");

        addSlider(panel, gbcPanel,
            "settings.maximum_download_retries",
            0, 50,
            settings::getMaxDownloadRetries,
            settings::setMaxDownloadRetries
        );

        addSlider(panel, gbcPanel,
            "settings.maximum_fragment_retries",
            0, 50,
            settings::getMaxFragmentRetries,
            settings::setMaxFragmentRetries
        );

        addCheckBox(panel, gbcPanel,
            "settings.transcode.keep_raw_media_files_after_transcode",
            settings::isKeepRawMediaFilesAfterTranscode,
            settings::setKeepRawMediaFilesAfterTranscode,
            false
        );

        addCheckBox(panel, gbcPanel,
            "settings.transcode.fail_downloads_on_transcoding_failures",
            settings::isFailDownloadsOnTranscodingFailures,
            settings::setFailDownloadsOnTranscodingFailures,
            false
        );

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 1;
        gbcPanel.weighty = 1;
        gbcPanel.gridwidth = GridBagConstraints.REMAINDER;
        gbcPanel.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setBackground(color(BACKGROUND));
        panel.add(filler, gbcPanel);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));
        scrollPane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        panelWrapper.setBackground(color(BACKGROUND));

        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    private JPanel createResolutionSettings() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(color(BACKGROUND));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

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

        JPanel itemPanel = new JPanel(new GridBagLayout());
        itemPanel.setOpaque(false);
        itemPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        itemPanel.setVisible(false);

        GridBagConstraints gbcItem = new GridBagConstraints();
        gbcItem.insets = new Insets(5, 10, 5, 10);
        gbcItem.anchor = GridBagConstraints.WEST;
        gbcItem.fill = GridBagConstraints.HORIZONTAL;
        //gbcItem.weightx = 1;

        gbcItem.gridy = 1;

        List<Component> toggleableComponents = new ArrayList<>();

        AtomicReference<JCheckBox> useGlobalCheckbox = new AtomicReference<>();
        if (filter == null || !filter.isAudioOnly()) {
            if (filter != null) {
                useGlobalCheckbox.set(addCheckBox(itemPanel, gbcItem,
                    "settings.use_global_settings",
                    qualitySettings::isUseGlobalSettings,
                    qualitySettings::setUseGlobalSettings,
                    true
                ));
            }

            toggleableComponents.add(addComboBox(itemPanel, gbcItem,
                "settings.quality_selector",
                QualitySelectorEnum.class,
                qualitySettings::getSelector,
                qualitySettings::setSelector,
                false
            ));

            JComboBox<String> minHeightComboBox = addComboBox(itemPanel, gbcItem,
                "settings.minimum_quality",
                ResolutionEnum.class,
                qualitySettings::getMinHeight,
                qualitySettings::setMinHeight,
                false
            );
            toggleableComponents.add(minHeightComboBox);

            JComboBox<String> maxHeightComboBox = addComboBox(itemPanel, gbcItem,
                "settings.maximum_quality",
                ResolutionEnum.class,
                qualitySettings::getMaxHeight,
                qualitySettings::setMaxHeight,
                false
            );
            toggleableComponents.add(maxHeightComboBox);

            {
                Arrays.stream(minHeightComboBox.getActionListeners())
                    .forEach(minHeightComboBox::removeActionListener);

                minHeightComboBox.addActionListener((ActionEvent e) -> {
                    ResolutionEnum minResolution = ISettingsEnum.getEnumByIndex(ResolutionEnum.class, minHeightComboBox.getSelectedIndex());
                    ResolutionEnum maxResolution = minResolution.getValidMax(qualitySettings.getMaxHeight());

                    qualitySettings.setMinHeight(minResolution);
                    qualitySettings.setMaxHeight(maxResolution);

                    maxHeightComboBox.setSelectedIndex(ISettingsEnum.getEnumIndex(ResolutionEnum.class, maxResolution));
                });

                Arrays.stream(maxHeightComboBox.getActionListeners())
                    .forEach(maxHeightComboBox::removeActionListener);

                maxHeightComboBox.addActionListener((ActionEvent e) -> {
                    ResolutionEnum maxResolution = ISettingsEnum.getEnumByIndex(ResolutionEnum.class, maxHeightComboBox.getSelectedIndex());
                    ResolutionEnum minResolution = maxResolution.getValidMin(qualitySettings.getMinHeight());

                    qualitySettings.setMinHeight(minResolution);
                    qualitySettings.setMaxHeight(maxResolution);

                    minHeightComboBox.setSelectedIndex(ISettingsEnum.getEnumIndex(ResolutionEnum.class, minResolution));
                });
            }

            toggleableComponents.add(addComboBox(itemPanel, gbcItem,
                "settings.video_container",
                VideoContainerEnum.class,
                qualitySettings::getVideoContainer,
                qualitySettings::setVideoContainer,
                false
            ));

            toggleableComponents.add(addComboBox(itemPanel, gbcItem,
                "settings.fps",
                FPSEnum.class,
                qualitySettings::getFps,
                qualitySettings::setFps,
                false
            ));

            toggleableComponents.add(addComboBox(itemPanel, gbcItem,
                "settings.subtitle_container",
                SubtitleContainerEnum.class,
                qualitySettings::getSubtitleContainer,
                qualitySettings::setSubtitleContainer,
                false
            ));

            toggleableComponents.add(addComboBox(itemPanel, gbcItem,
                "settings.thumbnail_container",
                ThumbnailContainerEnum.class,
                qualitySettings::getThumbnailContainer,
                qualitySettings::setThumbnailContainer,
                false
            ));
        }

        if (filter == null || !filter.isVideoOnly()) {
            toggleableComponents.add(addComboBox(itemPanel, gbcItem,
                "settings.audio_container",
                AudioContainerEnum.class,
                qualitySettings::getAudioContainer,
                qualitySettings::setAudioContainer,
                false
            ));

            toggleableComponents.add(addComboBox(itemPanel, gbcItem,
                "settings.audio_bitrate",
                AudioBitrateEnum.class,
                qualitySettings::getAudioBitrate,
                qualitySettings::setAudioBitrate,
                false
            ));

            // TODO: remove
            toggleableComponents.add(addComboBox(itemPanel, gbcItem,
                "settings.audio_codec",
                AudioCodecEnum.class,
                qualitySettings::getAudioCodec,
                qualitySettings::setAudioCodec,
                false
            ));
        }

        JCheckBox globalCheckbox;
        if ((globalCheckbox = useGlobalCheckbox.get()) != null) {
            enableComponents(toggleableComponents, !globalCheckbox.isSelected());

            globalCheckbox.addActionListener(e -> {
                enableComponents(toggleableComponents, !globalCheckbox.isSelected());
            });
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
                        ? color(MEDIA_CARD_HOVER) : color(MEDIA_CARD));

                    card.revalidate();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isExpanded) {
                    card.setBackground(color(MEDIA_CARD_HOVER));
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

    public static void enableComponents(List<Component> components, boolean enable) {
        for (Component component : components) {
            enableComponents(component, enable);
        }
    }

    public static void enableComponents(Component component, boolean enable) {
        component.setEnabled(enable);

        if (component instanceof Container container) {
            for (Component c : container.getComponents()) {
                enableComponents(c, enable);
            }
        }
    }

    public static <T extends Enum<T> & ISettingsEnum> JComboBox<String> addComboBox(JPanel panel,
        GridBagConstraints gbcPanel, String labelString, Class<T> enumClass,
        Supplier<T> getter, Consumer<T> setter, boolean requiresRestart) {

        JLabel label = createLabel(labelString, LIGHT_TEXT);

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 0.5;
        gbcPanel.gridwidth = 1;
        panel.add(label, gbcPanel);

        JComboBox<String> comboBox = new JComboBox<>(ISettingsEnum.getDisplayNames(enumClass));
        if (requiresRestart) {
            comboBox.setToolTipText(l10n("settings.requires_restart.tooltip"));
        }

        comboBox.setSelectedIndex(getter.get().ordinal());

        comboBox.addActionListener((ActionEvent e) -> {
            setter.accept(ISettingsEnum.getEnumByIndex(enumClass, comboBox.getSelectedIndex()));
        });

        customizeComboBox(comboBox);

        gbcPanel.gridx = 1;
        gbcPanel.weightx = 0.5;
        gbcPanel.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(comboBox, gbcPanel);

        return comboBox;
    }

    public static JCheckBox addCheckBox(JPanel panel, GridBagConstraints gbcPanel, String labelString,
        Supplier<Boolean> getter, Consumer<Boolean> setter, boolean requiresRestart) {

        JLabel label = createLabel(labelString, LIGHT_TEXT);

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 0.5;
        gbcPanel.gridwidth = 1;
        panel.add(label, gbcPanel);

        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(getter.get());
        if (requiresRestart) {
            checkBox.setToolTipText(l10n("settings.requires_restart.tooltip"));
        }

        checkBox.addActionListener((ActionEvent e) -> {
            setter.accept(checkBox.isSelected());
        });

        customizeComponent(checkBox, BACKGROUND, LIGHT_TEXT);

        gbcPanel.gridx = 1;
        gbcPanel.weightx = 0.5;
        gbcPanel.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(checkBox, gbcPanel);

        return checkBox;
    }

    public static JLabel addLabel(JPanel panel, GridBagConstraints gbcPanel, String labelString) {
        JLabel label = createLabel(labelString, FOREGROUND);

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 0.5;
        gbcPanel.gridwidth = 1;
        gbcPanel.insets = new Insets(20, 5, 10, 5);

        panel.add(label, gbcPanel);

        gbcPanel.insets = new Insets(5, 5, 5, 5);

        return label;
    }

    public static JSlider addSlider(JPanel panel, GridBagConstraints gbcPanel, String labelString,
        int min, int max, Supplier<Integer> getter, Consumer<Integer> setter) {

        JLabel label = createLabel(labelString, LIGHT_TEXT);

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 0.5;
        gbcPanel.gridwidth = 1;
        panel.add(label, gbcPanel);

        JSlider slider = new JSlider(min, max, getter.get());
        int tickSpacing = max <= 20 ? 1
            : Math.min(100, Math.max(5, (int)(5 * Math.pow(2, Math.floor(Math.log10(max / 50))))));
        slider.setMajorTickSpacing(tickSpacing);
        slider.setSnapToTicks(tickSpacing == 1);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);

        slider.addChangeListener((ChangeEvent e) -> {
            JSlider source = (JSlider)e.getSource();
            if (!source.getValueIsAdjusting()) {
                int sliderValue = source.getValue();
                setter.accept(sliderValue);
            }
        });

        customizeSlider(slider, BACKGROUND, SLIDER_FOREGROUND);

        gbcPanel.gridx = 1;
        gbcPanel.weightx = 0.5;
        gbcPanel.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(slider, gbcPanel);

        return slider;
    }

    public static JLabel createLabel(String text, UIColors uiColor) {
        JLabel label = new JLabel(l10n(text));
        label.setForeground(color(uiColor));

        return label;
    }

    public static JButton createButton(String text, String tooltipText,
        UIColors backgroundColor, UIColors textColor, UIColors hoverColor) {
        CustomButton button = new CustomButton(l10n(text),
            color(hoverColor),
            color(hoverColor).brighter());

        button.setToolTipText(l10n(tooltipText));

        button.setFocusPainted(false);
        button.setForeground(color(textColor));
        button.setBackground(color(backgroundColor));
        button.setBorder(BorderFactory.createEmptyBorder(10, 25, 10, 25));

        return button;
    }

    public static void customizeComboBox(JComboBox<?> component) {
        component.setUI(new CustomComboBoxUI());
    }

    public static void customizeComponent(JComponent component, UIColors backgroundColor, UIColors textColor) {
        component.setForeground(color(textColor));
        component.setBackground(color(backgroundColor));

        switch (component) {
            case JCheckBox jCheckBox ->
                jCheckBox.setUI(new CustomCheckBoxUI());
            case JSpinner jSpinner ->
                jSpinner.setUI(new CustomSpinnerUI());
            default -> {
            }
        }
    }

    public static void customizeSlider(JSlider slider, UIColors backgroundColor, UIColors textColor) {
        slider.setForeground(color(textColor));
        slider.setBackground(color(backgroundColor));
        slider.setOpaque(true);
        slider.setBorder(BorderFactory.createEmptyBorder());
        slider.setUI(new CustomSliderUI(slider));
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
