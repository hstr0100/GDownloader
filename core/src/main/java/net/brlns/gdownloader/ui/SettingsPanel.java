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

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.*;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.ui.custom.CustomButton;
import net.brlns.gdownloader.ui.custom.CustomCheckBoxUI;
import net.brlns.gdownloader.ui.custom.CustomComboBoxUI;
import net.brlns.gdownloader.ui.custom.CustomScrollBarUI;
import net.brlns.gdownloader.ui.custom.CustomSliderUI;
import net.brlns.gdownloader.ui.themes.UIColors;

import static net.brlns.gdownloader.Language.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.*;
import static net.brlns.gdownloader.ui.themes.UIColors.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SettingsPanel{

    private final GDownloader main;
    private final GUIManager manager;

    private final List<SettingsMenuEntry> contentPanels = new ArrayList<>();

    private Settings settings;

    private JFrame frame;

    private JPanel sidebarPanel;
    private JPanel contentPanel;
    private CardLayout cardLayout;

    private Runnable _resetAction;

    public SettingsPanel(GDownloader mainIn, GUIManager managerIn){
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

    private void saveSettings(){
        if(!settings.getDownloadsPath().isEmpty()
            && !main.getConfig().getDownloadsPath().equals(settings.getDownloadsPath())){

            File file = new File(settings.getDownloadsPath());
            if(file.exists() && file.canWrite()){
                main.setDownloadsPath(file);//We are uselessly calling on updateConfig() here, but should be no problem
            }else{
                settings.setDownloadsPath("");

                main.getGuiManager().showMessage(
                    l10n("gui.error_popup_title"),
                    l10n("gui.error_download_path_not_writable", file.getAbsolutePath()),
                    4000,
                    GUIManager.MessageType.ERROR,
                    true);

                log.error("Selected path not writable {}", file);
            }
        }

        main.updateConfig(settings);

        main.getGuiManager().refreshAppWindow();
        main.updateStartupStatus();
    }

    private void reloadSettings(){
        contentPanel.removeAll();

        int i = 0;
        for(SettingsMenuEntry entry : contentPanels){
            contentPanel.add(entry.getPanel().get(), String.valueOf(i++));
        }

        _resetAction.run();
    }

    public void createAndShowGUI(){
        Runnable createFrame = () -> {
            if(frame != null){
                frame.setVisible(true);
                frame.setExtendedState(JFrame.NORMAL);
                frame.requestFocus();
                return;
            }

            settings = GDownloader.OBJECT_MAPPER.convertValue(main.getConfig(), Settings.class);

            frame = new JFrame(l10n("settings.title", GDownloader.REGISTRY_APP_NAME)){
                @Override
                public void dispose(){
                    frame = null;

                    super.dispose();
                }
            };

            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(950, 600);
            frame.setLayout(new BorderLayout());
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);

            try{
                frame.setIconImage(main.getGuiManager().getAppIcon());
            }catch(IOException e){
                main.handleException(e);
            }

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
                for(SettingsMenuEntry entry : contentPanels){
                    int index = i;

                    JButton button = new JButton();
                    button.setToolTipText(entry.getDisplayName());
                    button.setPreferredSize(new Dimension(120, 120));
                    button.setBorderPainted(false);
                    button.setContentAreaFilled(false);
                    button.setOpaque(true);
                    button.setFocusPainted(false);
                    button.setBackground(color(index == 0 ? SIDE_PANEL_SELECTED : SIDE_PANEL));

                    button.setIcon(manager.loadIcon(entry.getIcon(), ICON, 60));

                    Runnable action = () -> {
                        cardLayout.show(contentPanel, String.valueOf(index));
                        headerLabel.setText(entry.getDisplayName());

                        Component[] buttons = sidebarPanel.getComponents();
                        for(Component button1 : buttons){
                            button1.setBackground(color(SIDE_PANEL));
                        }

                        button.setBackground(color(SIDE_PANEL_SELECTED));
                    };

                    button.addActionListener((ActionEvent e) -> {
                        action.run();
                    });

                    if(index == 0){
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

                        leftPanel.add(manager.createButton(
                            manager.loadIcon("/assets/shutdown.png", ICON, 24),
                            manager.loadIcon("/assets/shutdown.png", ICON_HOVER, 24),
                            "gui.exit.tooltip",
                            e -> System.exit(0)
                        ));

                        leftPanel.add(manager.createButton(
                            manager.loadIcon("/assets/restart.png", ICON, 24),
                            manager.loadIcon("/assets/restart.png", ICON_HOVER, 24),
                            "gui.restart.tooltip",
                            e -> {
                                saveSettings();

                                main.restart();
                            }
                        ));

                        leftPanel.add(manager.createButton(
                            manager.loadIcon("/assets/bin.png", ICON, 24),
                            manager.loadIcon("/assets/bin.png", ICON_HOVER, 24),
                            "gui.clear_cache.tooltip",
                            e -> main.clearCache(true)
                        ));

                        leftPanel.add(manager.createButton(
                            manager.loadIcon("/assets/update.png", ICON, 24),
                            manager.loadIcon("/assets/update.png", ICON_HOVER, 24),
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

                gabButton.addMouseListener(new MouseAdapter(){
                    @Override
                    public void mouseEntered(MouseEvent e){
                        gabButton.setForeground(color(LIGHT_TEXT));
                    }

                    @Override
                    public void mouseExited(MouseEvent e){
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
        };

        if(SwingUtilities.isEventDispatchThread()){
            createFrame.run();
        }else{
            SwingUtilities.invokeLater(createFrame);
        }
    }

    private <T extends Enum<T> & ISettingsEnum> void addComboBox(JPanel panel,
        GridBagConstraints gbcPanel, String labelString, Class<T> enumClass,
        Supplier<T> getter, Consumer<T> setter, boolean requiresRestart){

        JLabel label = createLabel(labelString, LIGHT_TEXT);

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 0.6;
        gbcPanel.gridwidth = 1;
        panel.add(label, gbcPanel);

        JComboBox<String> comboBox = new JComboBox<>(ISettingsEnum.getDisplayNames(enumClass));
        if(requiresRestart){
            comboBox.setToolTipText(l10n("settings.requires_restart.tooltip"));
        }

        comboBox.setSelectedIndex(getter.get().ordinal());

        comboBox.addActionListener((ActionEvent e) -> {
            setter.accept(ISettingsEnum.getEnumByIndex(enumClass, comboBox.getSelectedIndex()));
        });

        customizeComboBox(comboBox);

        gbcPanel.gridx = 1;
        gbcPanel.weightx = 1;
        gbcPanel.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(comboBox, gbcPanel);
    }

    private void addCheckBox(JPanel panel, GridBagConstraints gbcPanel, String labelString,
        Supplier<Boolean> getter, Consumer<Boolean> setter, boolean requiresRestart){

        JLabel label = createLabel(labelString, LIGHT_TEXT);

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 0.6;
        gbcPanel.gridwidth = 1;
        panel.add(label, gbcPanel);

        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(getter.get());
        if(requiresRestart){
            checkBox.setToolTipText(l10n("settings.requires_restart.tooltip"));
        }

        checkBox.addActionListener((ActionEvent e) -> {
            setter.accept(checkBox.isSelected());
        });

        customizeComponent(checkBox, BACKGROUND, LIGHT_TEXT);

        gbcPanel.gridx = 1;
        gbcPanel.weightx = 1;
        gbcPanel.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(checkBox, gbcPanel);
    }

    private void addSlider(JPanel panel, GridBagConstraints gbcPanel, String labelString,
        int min, int max, Supplier<Integer> getter, Consumer<Integer> setter){

        JLabel label = createLabel(labelString, LIGHT_TEXT);

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 0.6;
        gbcPanel.gridwidth = 1;
        panel.add(label, gbcPanel);

        JSlider slider = new JSlider(min, max, getter.get());
        slider.setMajorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setSnapToTicks(true);
        slider.setPaintLabels(true);

        slider.addChangeListener((ChangeEvent e) -> {
            JSlider source = (JSlider)e.getSource();
            if(!source.getValueIsAdjusting()){
                int sliderValue = source.getValue();
                setter.accept(sliderValue);
            }
        });

        customizeSlider(slider, BACKGROUND, SLIDER_FOREGROUND);

        gbcPanel.gridx = 1;
        gbcPanel.weightx = 1;
        gbcPanel.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(slider, gbcPanel);
    }

    private JPanel createGeneralSettings(){
        JPanel generalSettingsPanel = new JPanel(new GridBagLayout());
        generalSettingsPanel.setBackground(color(BACKGROUND));
        generalSettingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbcPanel = new GridBagConstraints();
        gbcPanel.insets = new Insets(5, 5, 5, 5);
        gbcPanel.anchor = GridBagConstraints.WEST;
        gbcPanel.fill = GridBagConstraints.HORIZONTAL;
        //gbcPanel.weightx = 1;

        addComboBox(generalSettingsPanel, gbcPanel,
            "settings.language",
            LanguageEnum.class,
            settings::getLanguage,
            settings::setLanguage,
            true
        );

        addComboBox(generalSettingsPanel, gbcPanel,
            "settings.theme",
            ThemeEnum.class,
            settings::getTheme,
            settings::setTheme,
            true
        );

        addCheckBox(generalSettingsPanel, gbcPanel,
            "settings.always_on_top",
            settings::isKeepWindowAlwaysOnTop,
            settings::setKeepWindowAlwaysOnTop,
            false
        );

        addCheckBox(generalSettingsPanel, gbcPanel,
            "settings.exit_on_close",
            settings::isExitOnClose,
            settings::setExitOnClose,
            false
        );

        addCheckBox(generalSettingsPanel, gbcPanel,
            "settings.automatic_updates",
            settings::isAutomaticUpdates,
            settings::setAutomaticUpdates,
            false
        );

        addCheckBox(generalSettingsPanel, gbcPanel,
            "settings.debug_mode",
            settings::isDebugMode,
            settings::setDebugMode,
            false
        );

        addCheckBox(generalSettingsPanel, gbcPanel,
            "settings.start_on_system_startup",
            settings::isAutoStart,
            settings::setAutoStart,
            false
        );

        addCheckBox(generalSettingsPanel, gbcPanel,
            "settings.play_sounds",
            settings::isPlaySounds,
            settings::setPlaySounds,
            false
        );

        addCheckBox(generalSettingsPanel, gbcPanel,
            "settings.display_link_capture_notifications",
            settings::isDisplayLinkCaptureNotifications,
            settings::setDisplayLinkCaptureNotifications,
            false
        );

        addCheckBox(generalSettingsPanel, gbcPanel,
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
            generalSettingsPanel.add(label, gbcPanel);

            //Not a lot of fault tolerance here
            List<String> options = new ArrayList<>();
            for(int i = 12; i <= 20; i++){
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
                if(!source.getValueIsAdjusting()){
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
            generalSettingsPanel.add(slider, gbcPanel);
        }

        gbcPanel.gridx = 0;
        gbcPanel.gridy++;
        gbcPanel.weightx = 1;
        gbcPanel.weighty = 1;
        gbcPanel.gridwidth = GridBagConstraints.REMAINDER;
        gbcPanel.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setBackground(color(BACKGROUND));
        generalSettingsPanel.add(filler, gbcPanel);

        JScrollPane scrollPane = new JScrollPane(generalSettingsPanel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        panelWrapper.setBackground(color(BACKGROUND));

        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    private JPanel createDownloadSettings(){
        JPanel downloadSettingsPanel = new JPanel(new GridBagLayout());
        downloadSettingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        downloadSettingsPanel.setBackground(color(BACKGROUND));

        GridBagConstraints gbcPanel = new GridBagConstraints();
        gbcPanel.insets = new Insets(5, 5, 5, 5);
        gbcPanel.anchor = GridBagConstraints.WEST;
        gbcPanel.fill = GridBagConstraints.HORIZONTAL;
        //gbcPanel.weightx = 1;

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.read_cookies",
            settings::isReadCookiesFromBrowser,
            settings::setReadCookiesFromBrowser,
            false
        );

        addComboBox(downloadSettingsPanel, gbcPanel,
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
            downloadSettingsPanel.add(label, gbcPanel);

            JTextField downloadPathField = new JTextField(20);
            downloadPathField.setText(main.getDownloadsDirectory().getAbsolutePath());
            downloadPathField.setForeground(color(TEXT_AREA_FOREGROUND));
            downloadPathField.setBackground(color(TEXT_AREA_BACKGROUND));
            downloadPathField.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            downloadPathField.getDocument().addDocumentListener(new DocumentListener(){
                @Override
                public void changedUpdate(DocumentEvent e){
                    settings.setDownloadsPath(downloadPathField.getText());
                }

                @Override
                public void removeUpdate(DocumentEvent e){
                    settings.setDownloadsPath(downloadPathField.getText());
                }

                @Override
                public void insertUpdate(DocumentEvent e){
                    settings.setDownloadsPath(downloadPathField.getText());
                }
            });

            gbcPanel.gridx = 1;
            gbcPanel.gridwidth = 1;
            gbcPanel.weightx = 0.9;
            downloadSettingsPanel.add(downloadPathField, gbcPanel);

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
                if(result == JFileChooser.APPROVE_OPTION){
                    String selectedPath = fileChooser.getSelectedFile().getAbsolutePath();
                    downloadPathField.setText(selectedPath);
                }
            });

            selectButton.setPreferredSize(new Dimension(20, downloadPathField.getPreferredSize().height));

            gbcPanel.gridx = 2;
            gbcPanel.weightx = 0.1;
            downloadSettingsPanel.add(selectButton, gbcPanel);
        }

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.capture_any_clipboard_link",
            settings::isCaptureAnyLinks,
            settings::setCaptureAnyLinks,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.auto_download_start",
            settings::isAutoDownloadStart,
            settings::setAutoDownloadStart,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.auto_download_retry",
            settings::isAutoDownloadRetry,
            settings::setAutoDownloadRetry,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.download_audio",
            settings::isDownloadAudio,
            settings::setDownloadAudio,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.download_video",
            settings::isDownloadVideo,
            settings::setDownloadVideo,
            false
        );

        addSlider(downloadSettingsPanel, gbcPanel,
            "settings.maximum_simultaneous_downloads",
            1, 10,
            settings::getMaxSimultaneousDownloads,
            settings::setMaxSimultaneousDownloads
        );

        addComboBox(downloadSettingsPanel, gbcPanel,
            "settings.playlist_download_option",
            PlayListOptionEnum.class,
            settings::getPlaylistDownloadOption,
            settings::setPlaylistDownloadOption,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.download_youtube_channels",
            settings::isDownloadYoutubeChannels,
            settings::setDownloadYoutubeChannels,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.use_sponsor_block",
            settings::isUseSponsorBlock,
            settings::setUseSponsorBlock,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.transcode_audio_to_aac",
            settings::isTranscodeAudioToAAC,
            settings::setTranscodeAudioToAAC,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.download_subtitles",
            settings::isDownloadSubtitles,
            settings::setDownloadSubtitles,
            false
        );

        //TODO: this should be grayed out when download_subtitles is disabled
        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.download_auto_generated_subtitles",
            settings::isDownloadAutoGeneratedSubtitles,
            settings::setDownloadAutoGeneratedSubtitles,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.download_thumbnails",
            settings::isDownloadThumbnails,
            settings::setDownloadThumbnails,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.random_interval_between_downloads",
            settings::isRandomIntervalBetweenDownloads,
            settings::setRandomIntervalBetweenDownloads,
            false
        );

        addCheckBox(downloadSettingsPanel, gbcPanel,
            "settings.respect_ytdlp_config_file",
            settings::isRespectYtDlpConfigFile,
            settings::setRespectYtDlpConfigFile,
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
        downloadSettingsPanel.add(filler, gbcPanel);

        JScrollPane scrollPane = new JScrollPane(downloadSettingsPanel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        panelWrapper.setBackground(color(BACKGROUND));

        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    private JPanel createResolutionSettings(){
        JPanel resolutionPanel = new JPanel();
        resolutionPanel.setLayout(new BoxLayout(resolutionPanel, BoxLayout.Y_AXIS));
        resolutionPanel.setBackground(color(BACKGROUND));

        //TODO: weight the display order?
        for(AbstractUrlFilter filter : settings.getUrlFilters()){
            QualitySettings qualitySettings = filter.getQualitySettings();

            JPanel itemPanel = new JPanel(new GridBagLayout());
            TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(color(LIGHT_TEXT)), filter.getDisplayName());

            border.setTitleColor(color(FOREGROUND));
            itemPanel.setBorder(border);

            itemPanel.setBackground(color(BACKGROUND));

            GridBagConstraints gbcItem = new GridBagConstraints();
            gbcItem.insets = new Insets(5, 10, 5, 10);
            gbcItem.anchor = GridBagConstraints.WEST;
            gbcItem.fill = GridBagConstraints.HORIZONTAL;
            //gbcItem.weightx = 1;

            gbcItem.gridy = 1;

            addComboBox(itemPanel, gbcItem,
                "settings.quality_selector",
                QualitySelectorEnum.class,
                qualitySettings::getSelector,
                qualitySettings::setSelector,
                false
            );

            JComboBox<String> minHeightComboBox;
            JComboBox<String> maxHeightComboBox;

            {
                JLabel label = createLabel("settings.minimum_quality", LIGHT_TEXT);

                gbcItem.gridx = 0;
                gbcItem.weightx = 0.8;
                gbcItem.gridwidth = 1;
                gbcItem.gridy++;
                itemPanel.add(label, gbcItem);

                minHeightComboBox = new JComboBox<>(ISettingsEnum.getDisplayNames(ResolutionEnum.class));
                minHeightComboBox.setSelectedIndex(qualitySettings.getMinHeight().ordinal());

                customizeComboBox(minHeightComboBox);

                gbcItem.gridx = 1;
                gbcItem.weightx = 1;
                gbcItem.gridwidth = GridBagConstraints.REMAINDER;
                itemPanel.add(minHeightComboBox, gbcItem);
            }

            {
                JLabel label = createLabel("settings.maximum_quality", LIGHT_TEXT);

                gbcItem.gridx = 0;
                gbcItem.weightx = 0.8;
                gbcItem.gridwidth = 1;
                gbcItem.gridy++;
                itemPanel.add(label, gbcItem);

                maxHeightComboBox = new JComboBox<>(ISettingsEnum.getDisplayNames(ResolutionEnum.class));
                maxHeightComboBox.setSelectedIndex(qualitySettings.getMaxHeight().ordinal());

                customizeComboBox(maxHeightComboBox);

                gbcItem.gridx = 1;
                gbcItem.weightx = 1;
                gbcItem.gridwidth = GridBagConstraints.REMAINDER;
                itemPanel.add(maxHeightComboBox, gbcItem);
            }

            {
                minHeightComboBox.addActionListener((ActionEvent e) -> {
                    ResolutionEnum minResolution = ISettingsEnum.getEnumByIndex(ResolutionEnum.class, minHeightComboBox.getSelectedIndex());
                    ResolutionEnum maxResolution = minResolution.getValidMax(qualitySettings.getMaxHeight());

                    qualitySettings.setMinHeight(minResolution);
                    qualitySettings.setMaxHeight(maxResolution);

                    maxHeightComboBox.setSelectedIndex(ISettingsEnum.getEnumIndex(ResolutionEnum.class, maxResolution));
                });

                maxHeightComboBox.addActionListener((ActionEvent e) -> {
                    ResolutionEnum maxResolution = ISettingsEnum.getEnumByIndex(ResolutionEnum.class, maxHeightComboBox.getSelectedIndex());
                    ResolutionEnum minResolution = maxResolution.getValidMin(qualitySettings.getMinHeight());

                    qualitySettings.setMinHeight(minResolution);
                    qualitySettings.setMaxHeight(maxResolution);

                    minHeightComboBox.setSelectedIndex(ISettingsEnum.getEnumIndex(ResolutionEnum.class, minResolution));
                });
            }

            addComboBox(itemPanel, gbcItem,
                "settings.video_container",
                VideoContainerEnum.class,
                qualitySettings::getVideoContainer,
                qualitySettings::setVideoContainer,
                false
            );

            addComboBox(itemPanel, gbcItem,
                "settings.fps",
                FPSEnum.class,
                qualitySettings::getFps,
                qualitySettings::setFps,
                false
            );

            addComboBox(itemPanel, gbcItem,
                "settings.audio_container",
                AudioContainerEnum.class,
                qualitySettings::getAudioContainer,
                qualitySettings::setAudioContainer,
                false
            );

            addComboBox(itemPanel, gbcItem,
                "settings.audio_bitrate",
                AudioBitrateEnum.class,
                qualitySettings::getAudioBitrate,
                qualitySettings::setAudioBitrate,
                false
            );

            addComboBox(itemPanel, gbcItem,
                "settings.subtitle_container",
                SubtitleContainerEnum.class,
                qualitySettings::getSubtitleContainer,
                qualitySettings::setSubtitleContainer,
                false
            );

            addComboBox(itemPanel, gbcItem,
                "settings.thumbnail_container",
                ThumbnailContainerEnum.class,
                qualitySettings::getThumbnailContainer,
                qualitySettings::setThumbnailContainer,
                false
            );

            resolutionPanel.add(itemPanel);
        }

        JScrollPane scrollPane = new JScrollPane(resolutionPanel);
        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(color(BACKGROUND));
        scrollPane.getVerticalScrollBar().setUnitIncrement(8);
        scrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));

        JPanel panelWrapper = new JPanel(new BorderLayout());
        panelWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 2));
        panelWrapper.setBackground(color(BACKGROUND));

        panelWrapper.add(scrollPane, BorderLayout.CENTER);

        return panelWrapper;
    }

    private JLabel createLabel(String text, UIColors uiColor){
        JLabel label = new JLabel(l10n(text));
        label.setForeground(color(uiColor));

        return label;
    }

    private JButton createButton(String text, String tooltipText, UIColors backgroundColor, UIColors textColor, UIColors hoverColor){
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

    private void customizeComboBox(JComboBox<String> component){
        component.setUI(new CustomComboBoxUI());
    }

    private void customizeComponent(JComponent component, UIColors backgroundColor, UIColors textColor){
        component.setForeground(color(textColor));
        component.setBackground(color(backgroundColor));

        if(component instanceof JCheckBox jCheckBox){
            jCheckBox.setUI(new CustomCheckBoxUI());
        }
    }

    private void customizeSlider(JSlider slider, UIColors backgroundColor, UIColors textColor){
        slider.setForeground(color(textColor));
        slider.setBackground(color(backgroundColor));
        slider.setOpaque(true);
        slider.setBorder(BorderFactory.createEmptyBorder());
        slider.setUI(new CustomSliderUI(slider));
    }

    @Data
    private class SettingsMenuEntry{

        private final String displayName;
        private final String icon;
        private final Supplier<JPanel> panel;

        public SettingsMenuEntry(String translationKey, String iconIn, Supplier<JPanel> panelIn){
            displayName = l10n(translationKey);
            icon = iconIn;
            panel = panelIn;
        }
    }
}
