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
import java.util.Map;
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
import static net.brlns.gdownloader.Language.*;
import net.brlns.gdownloader.settings.QualitySettings;
import net.brlns.gdownloader.settings.Settings;
import net.brlns.gdownloader.settings.enums.*;
import net.brlns.gdownloader.ui.custom.CustomButton;
import net.brlns.gdownloader.ui.custom.CustomCheckBoxUI;
import net.brlns.gdownloader.ui.custom.CustomComboBoxUI;
import net.brlns.gdownloader.ui.custom.CustomScrollBarUI;
import net.brlns.gdownloader.ui.custom.CustomSliderUI;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class SettingsPanel{

    private static final Color SIDE_PANEL_COLOR = new Color(134, 134, 134);
    private static final Color SIDE_PANEL_SELECTED_COLOR = new Color(93, 93, 93);

    private final GDownloader main;

    private final List<SettingsMenuEntry> contentPanels = new ArrayList<>();

    private Settings settings;

    private JFrame frame;

    private JPanel sidebarPanel;
    private JPanel contentPanel;
    private CardLayout cardLayout;

    public SettingsPanel(GDownloader mainIn){
        main = mainIn;

        contentPanels.add(new SettingsMenuEntry(
            "settings.general",
            "assets/settings.png",
            () -> createGeneralSettings()
        ));
        contentPanels.add(new SettingsMenuEntry(
            "settings.downloads",
            "assets/download.png",
            () -> createDownloadSettings()
        ));
        contentPanels.add(new SettingsMenuEntry(
            "settings.resolution",
            "assets/resolution.png",
            () -> createResolutionSettings()
        ));
    }

    public void createAndShowGUI(){
        if(frame != null){
            if(!frame.isVisible()){
                settings = GDownloader.OBJECT_MAPPER.convertValue(main.getConfig(), Settings.class);
            }

            reloadSettings();

            cardLayout.show(contentPanel, String.valueOf(0));

            frame.setVisible(true);

            return;
        }

        settings = GDownloader.OBJECT_MAPPER.convertValue(main.getConfig(), Settings.class);

        frame = new JFrame(get("settings.title", GDownloader.REGISTRY_APP_NAME));
        frame.setSize(800, 500);
        frame.setLayout(new BorderLayout());
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);

        try{
            frame.setIconImage(main.getGuiManager().getAppIcon());
        }catch(IOException e){
            main.handleException(e);
        }

        sidebarPanel = new JPanel();
        sidebarPanel.setBackground(SIDE_PANEL_COLOR);
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
            headerPanel.setBackground(SIDE_PANEL_COLOR);

            JLabel headerLabel = new JLabel(get("settings.sidebar_title"));
            headerLabel.setForeground(Color.WHITE);
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
            contentPanel.setBackground(Color.DARK_GRAY);

            JPanel headerPanel = new JPanel(new GridBagLayout());
            headerPanel.setBackground(SIDE_PANEL_SELECTED_COLOR);

            JLabel headerLabel = new JLabel(contentPanels.get(0).getDisplayName());
            headerLabel.setForeground(Color.WHITE);
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
                button.setPreferredSize(new Dimension(100, 100));
                button.setBorderPainted(false);
                button.setContentAreaFilled(false);
                button.setOpaque(true);
                button.setFocusPainted(false);
                button.setBackground(index == 0 ? SIDE_PANEL_SELECTED_COLOR : SIDE_PANEL_COLOR);

                button.setIcon(GUIManager.loadIcon(entry.getIcon(), 48));
                button.addActionListener((ActionEvent e) -> {
                    cardLayout.show(contentPanel, String.valueOf(index));
                    headerLabel.setText(entry.getDisplayName());

                    Component[] buttons = sidebarPanel.getComponents();
                    for(Component button1 : buttons){
                        button1.setBackground(SIDE_PANEL_COLOR);
                    }

                    button.setBackground(SIDE_PANEL_SELECTED_COLOR);
                });

                sidebarPanel.add(button, gbc);

                gbc.gridy++;

                contentPanel.add(entry.getPanel().get(), String.valueOf(index));

                i++;
            }

            mainContentPanel.add(contentPanel, BorderLayout.CENTER);

            {
                JPanel bottomPanel = new JPanel();
                bottomPanel.setBackground(SIDE_PANEL_SELECTED_COLOR);
                bottomPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 10));
                bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

                JButton resetButton = createButton(
                    "settings.restore_defaults",
                    "settings.restore_defaults.tooltip",
                    Color.WHITE, Color.DARK_GRAY, new Color(128, 128, 128));

                resetButton.setPreferredSize(new Dimension(200, 30));
                resetButton.addActionListener((ActionEvent e) -> {
                    settings = new Settings();

                    reloadSettings();

                    main.updateConfig(settings);
                });

                bottomPanel.add(resetButton);

                JButton saveButton = createButton(
                    "settings.save",
                    "settings.save.tooltip",
                    Color.WHITE, Color.DARK_GRAY, new Color(128, 128, 128));

                saveButton.setPreferredSize(new Dimension(200, 30));
                saveButton.addActionListener((ActionEvent e) -> {
                    if(!settings.getDownloadsPath().isEmpty()
                        && !main.getConfig().getDownloadsPath().equals(settings.getDownloadsPath())){

                        File file = new File(settings.getDownloadsPath());
                        if(file.exists() && file.canWrite()){
                            main.setDownloadsPath(file);
                        }else{
                            settings.setDownloadsPath("");

                            main.getGuiManager().showMessage(
                                get("gui.error_popup_title"),
                                get("gui.error_download_path_not_writable", file.getAbsolutePath()),
                                4000,
                                GUIManager.MessageType.ERROR,
                                true);

                            log.error("Selected path not writable {}", file);
                        }
                    }

                    main.updateConfig(settings);

                    main.getGuiManager().refreshWindow();
                    main.updateStartupStatus();

                    frame.setVisible(false);
                });

                bottomPanel.add(saveButton);

                mainContentPanel.add(bottomPanel, BorderLayout.SOUTH);
                frame.add(mainContentPanel, BorderLayout.CENTER);

            }
        }

        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        sidebarPanel.add(Box.createVerticalGlue(), gbc);

        {
            JButton gabButton = new JButton("@hstr0100");
            gabButton.setToolTipText("Gabriel's GitHub");
            gabButton.setUI(new BasicButtonUI());
            gabButton.setForeground(Color.WHITE);
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
                    gabButton.setForeground(Color.DARK_GRAY);
                }

                @Override
                public void mouseExited(MouseEvent e){
                    gabButton.setForeground(Color.WHITE);
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
    }

    private void reloadSettings(){
        contentPanel.removeAll();

        int i = 0;
        for(SettingsMenuEntry entry : contentPanels){
            contentPanel.add(entry.getPanel().get(), String.valueOf(i++));
        }
    }

    private JPanel createGeneralSettings(){
        JPanel generalSettingsPanel = new JPanel(new GridBagLayout());
        generalSettingsPanel.setBackground(Color.DARK_GRAY);
        generalSettingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbcPanel = new GridBagConstraints();
        gbcPanel.insets = new Insets(5, 5, 5, 5);
        gbcPanel.anchor = GridBagConstraints.WEST;
        gbcPanel.fill = GridBagConstraints.HORIZONTAL;
        gbcPanel.weightx = 1;

        {
            JLabel label = createLabel("settings.language", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            generalSettingsPanel.add(label, gbcPanel);

            JComboBox<String> comboBox = new JComboBox<>(SettingsEnum.getDisplayNames(LanguageEnum.class));
            comboBox.setToolTipText("settings.requires_restart.tooltip");
            comboBox.setSelectedIndex(settings.getLanguage().ordinal());

            comboBox.addActionListener((ActionEvent e) -> {
                settings.setLanguage(SettingsEnum.getEnumByIndex(LanguageEnum.class, comboBox.getSelectedIndex()));
            });

            customizeComponent(comboBox, Color.LIGHT_GRAY, Color.DARK_GRAY);

            gbcPanel.gridx = 1;
            gbcPanel.gridwidth = 2;
            generalSettingsPanel.add(comboBox, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.always_on_top", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            generalSettingsPanel.add(label, gbcPanel);

            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(settings.isKeepWindowAlwaysOnTop());

            checkBox.addActionListener((ActionEvent e) -> {
                settings.setKeepWindowAlwaysOnTop(checkBox.isSelected());
            });

            customizeComponent(checkBox, Color.DARK_GRAY, Color.LIGHT_GRAY);

            gbcPanel.gridx = 1;
            generalSettingsPanel.add(checkBox, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.exit_on_close", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            generalSettingsPanel.add(label, gbcPanel);

            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(settings.isExitOnClose());

            checkBox.addActionListener((ActionEvent e) -> {
                settings.setExitOnClose(checkBox.isSelected());
            });

            customizeComponent(checkBox, Color.DARK_GRAY, Color.LIGHT_GRAY);

            gbcPanel.gridx = 1;
            generalSettingsPanel.add(checkBox, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.debug_mode", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            generalSettingsPanel.add(label, gbcPanel);

            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(settings.isDebugMode());

            checkBox.addActionListener((ActionEvent e) -> {
                settings.setDebugMode(checkBox.isSelected());
            });

            customizeComponent(checkBox, Color.DARK_GRAY, Color.LIGHT_GRAY);

            gbcPanel.gridx = 1;
            generalSettingsPanel.add(checkBox, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.start_on_system_startup", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            generalSettingsPanel.add(label, gbcPanel);

            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(settings.isAutoStart());

            checkBox.addActionListener((ActionEvent e) -> {
                settings.setAutoStart(checkBox.isSelected());
            });

            customizeComponent(checkBox, Color.DARK_GRAY, Color.LIGHT_GRAY);

            gbcPanel.gridx = 1;
            generalSettingsPanel.add(checkBox, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.play_sounds", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            generalSettingsPanel.add(label, gbcPanel);

            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(settings.isPlaySounds());

            checkBox.addActionListener((ActionEvent e) -> {
                settings.setPlaySounds(checkBox.isSelected());
            });

            customizeComponent(checkBox, Color.DARK_GRAY, Color.LIGHT_GRAY);

            gbcPanel.gridx = 1;
            generalSettingsPanel.add(checkBox, gbcPanel);
        }

        {
            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            gbcPanel.weightx = 1;
            gbcPanel.weighty = 1;
            gbcPanel.gridwidth = 1;
            gbcPanel.fill = GridBagConstraints.BOTH;
            JPanel filler = new JPanel();
            filler.setBackground(Color.DARK_GRAY);
            generalSettingsPanel.add(filler, gbcPanel);
        }

        return generalSettingsPanel;
    }

    private JPanel createDownloadSettings(){
        JPanel downloadSettingsPanel = new JPanel(new GridBagLayout());
        downloadSettingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        downloadSettingsPanel.setBackground(Color.DARK_GRAY);

        GridBagConstraints gbcPanel = new GridBagConstraints();
        gbcPanel.insets = new Insets(5, 5, 5, 5);
        gbcPanel.anchor = GridBagConstraints.WEST;
        gbcPanel.fill = GridBagConstraints.HORIZONTAL;
        gbcPanel.weightx = 1;

        {
            JLabel label = createLabel("settings.read_cookies", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            downloadSettingsPanel.add(label, gbcPanel);

            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(settings.isReadCookies());

            checkBox.addActionListener((ActionEvent e) -> {
                settings.setReadCookies(checkBox.isSelected());
            });

            customizeComponent(checkBox, Color.DARK_GRAY, Color.LIGHT_GRAY);

            gbcPanel.gridx = 1;
            downloadSettingsPanel.add(checkBox, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.browser_for_cookies", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            downloadSettingsPanel.add(label, gbcPanel);

            JComboBox<String> comboBox = new JComboBox<>(SettingsEnum.getDisplayNames(BrowserEnum.class));
            comboBox.setToolTipText("settings.requires_restart.tooltip");
            comboBox.setSelectedIndex(settings.getBrowser().ordinal());

            comboBox.addActionListener((ActionEvent e) -> {
                settings.setBrowser(SettingsEnum.getEnumByIndex(BrowserEnum.class, comboBox.getSelectedIndex()));
            });

            customizeComponent(comboBox, Color.LIGHT_GRAY, Color.DARK_GRAY);

            gbcPanel.gridx = 1;
            gbcPanel.gridwidth = 2;
            downloadSettingsPanel.add(comboBox, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.downloads_path", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            downloadSettingsPanel.add(label, gbcPanel);

            JTextField downloadPathField = new JTextField(20);
            downloadPathField.setText(main.getOrCreateDownloadsDirectory().getAbsolutePath());
            downloadPathField.setForeground(Color.BLACK);
            downloadPathField.setBackground(Color.LIGHT_GRAY);
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
                Color.WHITE, Color.DARK_GRAY, new Color(128, 128, 128));

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

        {
            JLabel label = createLabel("settings.capture_any_clipboard_link", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            gbcPanel.weightx = 0;
            downloadSettingsPanel.add(label, gbcPanel);

            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(settings.isCaptureAnyLinks());

            checkBox.addActionListener((ActionEvent e) -> {
                settings.setCaptureAnyLinks(checkBox.isSelected());
            });

            customizeComponent(checkBox, Color.DARK_GRAY, Color.LIGHT_GRAY);

            gbcPanel.gridx = 1;
            gbcPanel.gridwidth = 2;
            gbcPanel.weightx = 1;
            downloadSettingsPanel.add(checkBox, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.download_audio_only", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            downloadSettingsPanel.add(label, gbcPanel);

            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(settings.isDownloadAudioOnly());

            checkBox.addActionListener((ActionEvent e) -> {
                settings.setDownloadAudioOnly(checkBox.isSelected());
            });

            customizeComponent(checkBox, Color.DARK_GRAY, Color.LIGHT_GRAY);

            gbcPanel.gridx = 1;
            downloadSettingsPanel.add(checkBox, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.maximum_simultaneous_downloads", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            downloadSettingsPanel.add(label, gbcPanel);

            JSlider slider = new JSlider(1, 10, settings.getMaxSimultaneousDownloads());
            slider.setMajorTickSpacing(1);
            slider.setPaintTicks(true);
            slider.setSnapToTicks(true);
            slider.setPaintLabels(true);

            slider.addChangeListener((ChangeEvent e) -> {
                JSlider source = (JSlider)e.getSource();
                if(!source.getValueIsAdjusting()){
                    int sliderValue = source.getValue();

                    settings.setMaxSimultaneousDownloads(sliderValue);
                }
            });

            customizeSlider(slider, Color.DARK_GRAY, Color.LIGHT_GRAY);

            gbcPanel.gridx = 1;
            downloadSettingsPanel.add(slider, gbcPanel);
        }

        {
            JLabel label = createLabel("settings.playlist_download_option", Color.LIGHT_GRAY);

            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            downloadSettingsPanel.add(label, gbcPanel);

            JComboBox<String> comboBox = new JComboBox<>(SettingsEnum.getDisplayNames(PlayListOptionEnum.class));
            comboBox.setSelectedIndex(settings.getPlaylistDownloadOption().ordinal());

            comboBox.addActionListener((ActionEvent e) -> {
                settings.setPlaylistDownloadOption(SettingsEnum.getEnumByIndex(PlayListOptionEnum.class, comboBox.getSelectedIndex()));
            });

            customizeComponent(comboBox, Color.LIGHT_GRAY, Color.DARK_GRAY);

            gbcPanel.gridx = 1;
            downloadSettingsPanel.add(comboBox, gbcPanel);
        }

        {
            gbcPanel.gridx = 0;
            gbcPanel.gridy++;
            gbcPanel.weightx = 1;
            gbcPanel.weighty = 1;
            gbcPanel.gridwidth = 1;
            gbcPanel.fill = GridBagConstraints.BOTH;
            JPanel filler = new JPanel();
            filler.setBackground(Color.DARK_GRAY);
            downloadSettingsPanel.add(filler, gbcPanel);
        }

        return downloadSettingsPanel;
    }

    private JPanel createResolutionSettings(){
        JPanel resolutionPanel = new JPanel();
        resolutionPanel.setLayout(new BoxLayout(resolutionPanel, BoxLayout.Y_AXIS));
        resolutionPanel.setBackground(Color.DARK_GRAY);

        for(Map.Entry<WebFilterEnum, QualitySettings> entry : settings.getQualitySettings().entrySet()){
            WebFilterEnum filter = entry.getKey();
            QualitySettings qualitySettings = entry.getValue();

            JPanel itemPanel = new JPanel(new GridBagLayout());
            TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY), filter.getDisplayName());
            border.setTitleColor(Color.WHITE);
            itemPanel.setBorder(border);

            itemPanel.setBackground(Color.DARK_GRAY);

            GridBagConstraints gbcItem = new GridBagConstraints();
            gbcItem.insets = new Insets(5, 10, 5, 10);
            gbcItem.anchor = GridBagConstraints.WEST;
            gbcItem.fill = GridBagConstraints.HORIZONTAL;
            gbcItem.weightx = 1;

            gbcItem.gridy = 1;

            {
                JLabel label = createLabel("settings.quality_selector", Color.LIGHT_GRAY);

                gbcItem.gridy++;
                itemPanel.add(label, gbcItem);

                JComboBox<String> comboBox = new JComboBox<>(SettingsEnum.getDisplayNames(QualitySelectorEnum.class));
                comboBox.setSelectedIndex(qualitySettings.getSelector().ordinal());

                comboBox.addActionListener((ActionEvent e) -> {
                    qualitySettings.setSelector(SettingsEnum.getEnumByIndex(QualitySelectorEnum.class, comboBox.getSelectedIndex()));
                });

                customizeComponent(comboBox, Color.LIGHT_GRAY, Color.DARK_GRAY);

                gbcItem.gridx = 1;
                itemPanel.add(comboBox, gbcItem);
            }

            JComboBox<String> minHeightComboBox;
            JComboBox<String> maxHeightComboBox;

            {
                JLabel label = createLabel("settings.minimum_quality", Color.LIGHT_GRAY);

                gbcItem.gridx = 0;
                gbcItem.gridy++;
                itemPanel.add(label, gbcItem);

                minHeightComboBox = new JComboBox<>(SettingsEnum.getDisplayNames(ResolutionEnum.class));
                minHeightComboBox.setSelectedIndex(qualitySettings.getMinHeight().ordinal());

                customizeComponent(minHeightComboBox, Color.LIGHT_GRAY, Color.DARK_GRAY);

                gbcItem.gridx = 1;
                itemPanel.add(minHeightComboBox, gbcItem);
            }

            {
                JLabel label = createLabel("settings.maximum_quality", Color.LIGHT_GRAY);

                gbcItem.gridx = 0;
                gbcItem.gridy++;
                itemPanel.add(label, gbcItem);

                maxHeightComboBox = new JComboBox<>(SettingsEnum.getDisplayNames(ResolutionEnum.class));
                maxHeightComboBox.setSelectedIndex(qualitySettings.getMaxHeight().ordinal());

                customizeComponent(maxHeightComboBox, Color.LIGHT_GRAY, Color.DARK_GRAY);

                gbcItem.gridx = 1;
                itemPanel.add(maxHeightComboBox, gbcItem);
            }

            {
                JLabel label = createLabel("settings.video_container", Color.LIGHT_GRAY);

                gbcItem.gridx = 0;
                gbcItem.gridy++;
                itemPanel.add(label, gbcItem);

                JComboBox<String> comboBox = new JComboBox<>(SettingsEnum.getDisplayNames(VideoContainerEnum.class));
                comboBox.setSelectedIndex(qualitySettings.getContainer().ordinal());

                comboBox.addActionListener((ActionEvent e) -> {
                    qualitySettings.setContainer(SettingsEnum.getEnumByIndex(VideoContainerEnum.class, comboBox.getSelectedIndex()));
                });

                customizeComponent(comboBox, Color.LIGHT_GRAY, Color.DARK_GRAY);

                gbcItem.gridx = 1;
                itemPanel.add(comboBox, gbcItem);
            }

            {
                minHeightComboBox.addActionListener((ActionEvent e) -> {
                    ResolutionEnum minResolution = SettingsEnum.getEnumByIndex(ResolutionEnum.class, minHeightComboBox.getSelectedIndex());
                    ResolutionEnum maxResolution = minResolution.getValidMax(qualitySettings.getMaxHeight());

                    qualitySettings.setMinHeight(minResolution);
                    qualitySettings.setMaxHeight(maxResolution);

                    maxHeightComboBox.setSelectedIndex(SettingsEnum.getEnumIndex(ResolutionEnum.class, maxResolution));
                });

                maxHeightComboBox.addActionListener((ActionEvent e) -> {
                    ResolutionEnum maxResolution = SettingsEnum.getEnumByIndex(ResolutionEnum.class, maxHeightComboBox.getSelectedIndex());
                    ResolutionEnum minResolution = maxResolution.getValidMin(qualitySettings.getMinHeight());

                    qualitySettings.setMinHeight(minResolution);
                    qualitySettings.setMaxHeight(maxResolution);

                    minHeightComboBox.setSelectedIndex(SettingsEnum.getEnumIndex(ResolutionEnum.class, minResolution));
                });
            }

            {
                JLabel label = createLabel("settings.fps", Color.LIGHT_GRAY);

                gbcItem.gridx = 0;
                gbcItem.gridy++;
                itemPanel.add(label, gbcItem);

                JComboBox<String> comboBox = new JComboBox<>(SettingsEnum.getDisplayNames(FPSEnum.class));
                comboBox.setSelectedIndex(qualitySettings.getFps().ordinal());

                comboBox.addActionListener((ActionEvent e) -> {
                    qualitySettings.setFps(SettingsEnum.getEnumByIndex(FPSEnum.class, comboBox.getSelectedIndex()));
                });

                customizeComponent(comboBox, Color.LIGHT_GRAY, Color.DARK_GRAY);

                gbcItem.gridx = 1;
                itemPanel.add(comboBox, gbcItem);
            }

            {
                JLabel label = createLabel("settings.audio_bitrate", Color.LIGHT_GRAY);

                gbcItem.gridx = 0;
                gbcItem.gridy++;
                itemPanel.add(label, gbcItem);

                JComboBox<String> comboBox = new JComboBox<>(SettingsEnum.getDisplayNames(AudioBitrateEnum.class));
                comboBox.setSelectedIndex(qualitySettings.getAudioBitrate().ordinal());

                comboBox.addActionListener((ActionEvent e) -> {
                    qualitySettings.setAudioBitrate(SettingsEnum.getEnumByIndex(AudioBitrateEnum.class, comboBox.getSelectedIndex()));
                });

                customizeComponent(comboBox, Color.LIGHT_GRAY, Color.DARK_GRAY);

                gbcItem.gridx = 1;
                itemPanel.add(comboBox, gbcItem);
            }

            resolutionPanel.add(itemPanel);
        }

        JScrollPane resolutionScrollPane = new JScrollPane(resolutionPanel);
        resolutionScrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        resolutionScrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        resolutionScrollPane.setBorder(BorderFactory.createEmptyBorder());
        resolutionScrollPane.setBackground(Color.DARK_GRAY);
        resolutionScrollPane.getVerticalScrollBar().setUnitIncrement(8);
        resolutionScrollPane.setPreferredSize(new Dimension(Integer.MAX_VALUE, 200));

        JPanel resolutionPanelWrapper = new JPanel(new BorderLayout());
        resolutionPanelWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 2));
        resolutionPanelWrapper.setBackground(Color.DARK_GRAY);

        resolutionPanelWrapper.add(resolutionScrollPane, BorderLayout.CENTER);

        return resolutionPanelWrapper;
    }

    private JLabel createLabel(String text, Color color){
        JLabel label = new JLabel(get(text));
        label.setForeground(color);

        return label;
    }

    private JButton createButton(String text, String tooltipText, Color backgroundColor, Color textColor, Color hoverColor){
        CustomButton button = new CustomButton(get(text));
        button.setToolTipText(tooltipText);
        button.setHoverBackgroundColor(hoverColor);
        button.setPressedBackgroundColor(hoverColor.brighter());

        button.setFocusPainted(false);
        button.setForeground(textColor);
        button.setBackground(backgroundColor);
        button.setBorder(BorderFactory.createEmptyBorder(10, 25, 10, 25));

        return button;
    }

    private void customizeComponent(JComponent component, Color backgroundColor, Color textColor){
        component.setForeground(textColor);
        component.setBackground(backgroundColor);

        if(component instanceof JComboBox){
            ((JComboBox)component).setUI(new CustomComboBoxUI());
        }else if(component instanceof JCheckBox){
            ((JCheckBox)component).setUI(new CustomCheckBoxUI());
        }
    }

    private void customizeSlider(JSlider slider, Color backgroundColor, Color textColor){
        slider.setForeground(textColor);
        slider.setBackground(backgroundColor);
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
            displayName = get(translationKey);
            icon = iconIn;
            panel = panelIn;
        }
    }
}
