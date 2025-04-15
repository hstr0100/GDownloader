/*
 * Copyright (C) 2025 hstr0100
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
package net.brlns.gdownloader.ui.custom;

import java.awt.*;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.ffmpeg.FFmpegTranscoder;
import net.brlns.gdownloader.ffmpeg.enums.*;
import net.brlns.gdownloader.ffmpeg.structs.*;
import net.brlns.gdownloader.settings.enums.VideoContainerEnum;
import net.brlns.gdownloader.ui.builder.ComboBoxBuilder;
import net.brlns.gdownloader.ui.themes.UIColors;

import static net.brlns.gdownloader.lang.Language.*;
import static net.brlns.gdownloader.settings.enums.VideoContainerEnum.*;
import static net.brlns.gdownloader.ui.GUIManager.runOnEDT;
import static net.brlns.gdownloader.ui.SettingsPanel.addComboBox;
import static net.brlns.gdownloader.ui.SettingsPanel.addLabel;
import static net.brlns.gdownloader.ui.SettingsPanel.wrapComponentRow;
import static net.brlns.gdownloader.ui.UIUtils.*;
import static net.brlns.gdownloader.ui.themes.ThemeProvider.color;
import static net.brlns.gdownloader.ui.themes.UIColors.*;
import static net.brlns.gdownloader.util.StringUtils.formatBitrate;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: investigate erratic behavior of CRF value when selecting VBR
// TODO: enable transcoding checkbox
@Slf4j
public final class CustomTranscodePanel extends JPanel {

    // 300 Mbps seems like a reasonable spot to stop, plenty of headroom for 8K.
    // We won't stop you from rising it further by editing the config manually though.
    private static final int MAX_BITRATE = 300000;
    // Some encoders can go up to 63, TODO investigate use cases
    private static final int MAX_QP = 51;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private boolean enabled = false;
    private boolean updatingFromPreset = false;
    private boolean ignoreSettingChanges = false;

    @Getter
    private final FFmpegConfig config;
    private final FFmpegTranscoder transcoder;

    private JPanel controlsPanel;
    private JPanel loadingPanel;

    private JComboBox<PresetEnum> presetCombo;
    private JComboBox<EncoderEnum> videoEncoderCombo;
    private JComboBox<VideoContainerEnum> videoContainerCombo;
    private JComboBox<EncoderPreset> speedPresetCombo;
    private JComboBox<EncoderProfile> profileCombo;
    private JComboBox<RateControlModeEnum> rcModeCombo;
    private JComboBox<AudioCodecEnum> audioCodecCombo;
    private JComboBox<AudioBitrateEnum> audioBitrateCombo;

    private JPanel rateControlPanel;
    private JSlider rateControlSlider;
    private JPanel bitRatePanel;
    private JSlider bitRateSlider;

    public CustomTranscodePanel(FFmpegConfig configIn, FFmpegTranscoder transcoderIn,
        UIColors backgroundIn) {
        config = configIn;
        transcoder = transcoderIn;

        setLayout(new BorderLayout());
        setBackground(color(backgroundIn));

        initLoadingPanel();
        initComponentsAndLayout();
        startInitializationWorker();
    }

    private void initLoadingPanel() {
        JLabel statusLabel = createLabel("settings.transcode.loading.status", LIGHT_TEXT);
        loadingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        loadingPanel.setBackground(getBackground());
        loadingPanel.add(statusLabel);
        add(loadingPanel, BorderLayout.NORTH);
    }

    private void startInitializationWorker() {
        enableComponents(controlsPanel, false);

        SwingWorker<Void, Void> initWorker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // Populating FFmpeg components can be expensive depending
                // on timimg. Instead, lazy load in the background.
                populateComponentsFromScanner();
                return null;
            }

            @Override
            protected void done() {
                runOnEDT(() -> {
                    if (transcoder.hasFFmpeg()) {
                        loadingPanel.setVisible(false);
                    } else {
                        JLabel statusLabel = createLabel("gui.ffmpeg.not_found", UIColors.ICON_INACTIVE);
                        loadingPanel.removeAll();
                        loadingPanel.add(statusLabel);
                        loadingPanel.revalidate();
                        loadingPanel.repaint();
                    }

                    initialized.set(true);

                    videoEncoderCombo.setSelectedItem(config.getVideoEncoder());

                    updatePresetAndProfileDropdowns(config.getVideoEncoder());
                    updatePresetDropdownBasedOnConfig();

                    enableComponents(controlsPanel, enabled);
                    updateControlVisibility();
                    updateVideoControls();
                    updateAudioControls();

                    revalidate();
                    repaint();
                });
            }
        };

        initWorker.execute();
    }

    private UIColors getBgColor(int row) {
        return row % 2 == 0 ? SETTINGS_ROW_BACKGROUND_LIGHT : SETTINGS_ROW_BACKGROUND_DARK;
    }

    private void initComponentsAndLayout() {
        controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBackground(getBackground());

        addLabel(controlsPanel, "settings.transcode.panel.title");

        int row = -1;
        presetCombo = addComboBox(controlsPanel, ComboBoxBuilder.<PresetEnum>builder()
            .background(getBgColor(++row))
            .labelKey("settings.transcode.label.preset")
            .values(new PresetEnum[] {PresetEnum.CUSTOM})
            .onSet((preset) -> {
                if (initialized.get() && !ignoreSettingChanges) {
                    if (preset != null && !preset.isDefault()) {
                        applyPreset(preset);
                    }
                }
            })
            .build());

        videoEncoderCombo = addComboBox(controlsPanel, ComboBoxBuilder.<EncoderEnum>builder()
            .background(getBgColor(++row))
            .labelKey("settings.transcode.label.video_encoder")
            .values(new EncoderEnum[] {EncoderEnum.NO_ENCODER})
            //.getter(config::getVideoEncoder)
            .onSet((encoder) -> {
                if (initialized.get() && !updatingFromPreset && !ignoreSettingChanges) {
                    config.setVideoEncoder(encoder);

                    updatePresetAndProfileDropdowns(encoder);
                    setToCustomPreset();
                    updateVideoControls();
                }
            })
            .build());

        videoContainerCombo = addComboBox(controlsPanel, ComboBoxBuilder.<VideoContainerEnum>builder()
            .background(getBgColor(++row))
            .labelKey("settings.transcode.label.video_container")
            // For now, lets support the formats we know work well with each other.
            .values(new VideoContainerEnum[] {DEFAULT, MP4, MKV, WEBM, MOV})
            .getter(config::getVideoContainer)
            .setter(config::setVideoContainer)
            .build());

        speedPresetCombo = addComboBox(controlsPanel, ComboBoxBuilder.<EncoderPreset>builder()
            .background(getBgColor(++row))
            .labelKey("settings.transcode.label.speed_preset")
            .values(new EncoderPreset[] {EncoderPreset.NO_PRESET})
            .getter(config::getSpeedPreset)
            .onSet((speedPreset) -> {
                if (initialized.get() && !updatingFromPreset && !ignoreSettingChanges) {
                    config.setSpeedPreset(speedPreset);
                    setToCustomPreset();
                }
            })
            .build());

        profileCombo = addComboBox(controlsPanel, ComboBoxBuilder.<EncoderProfile>builder()
            .background(getBgColor(++row))
            .labelKey("settings.transcode.label.encoder_profile")
            .values(new EncoderProfile[] {EncoderProfile.NO_PROFILE})
            .getter(config::getProfile)
            .onSet((profile) -> {
                if (initialized.get() && !updatingFromPreset && !ignoreSettingChanges) {
                    config.setProfile(profile);
                    setToCustomPreset();
                }
            })
            .build());

        rcModeCombo = addComboBox(controlsPanel, ComboBoxBuilder.<RateControlModeEnum>builder()
            .background(getBgColor(++row))
            .labelKey("settings.transcode.label.rate_control_mode")
            .values(RateControlModeEnum.values())
            .getter(config::getRateControlMode)
            .onSet((rateControlMode) -> {
                config.setRateControlMode(rateControlMode);
                updateControlVisibility();
            })
            .build());

        addLabeledComponent(controlsPanel,
            "settings.transcode.label.rate_control_value",
            createRateControlPanel(getBgColor(++row)),
            getBgColor(row));

        addLabeledComponent(controlsPanel,
            "settings.transcode.label.video_bitrate",
            createBitratePanel(getBgColor(row)),
            getBgColor(row));

        audioCodecCombo = addComboBox(controlsPanel, ComboBoxBuilder.<AudioCodecEnum>builder()
            .background(getBgColor(++row))
            .labelKey("settings.transcode.label.audio_codec")
            .values(AudioCodecEnum.values())
            .getter(config::getAudioCodec)
            .setter(config::setAudioCodec)
            .onSet((audioCodec) -> updateAudioControls())
            .build());

        audioBitrateCombo = addComboBox(controlsPanel, ComboBoxBuilder.<AudioBitrateEnum>builder()
            .background(getBgColor(++row))
            .labelKey("settings.transcode.label.audio_bitrate")
            .values(AudioBitrateEnum.values())
            .getter(config::getAudioBitrate)
            .setter(config::setAudioBitrate)
            .build());

        add(controlsPanel, BorderLayout.CENTER);
    }

    private <T extends JComponent> T addLabeledComponent(JPanel panel,
        String labelKey, @NonNull T component, UIColors rowColorIn) {
        JLabel label = createLabel(labelKey, LIGHT_TEXT);

        wrapComponentRow(panel, label, component, rowColorIn);

        return component;
    }

    private JPanel createRateControlPanel(UIColors backgroundIn) {
        Color background = color(backgroundIn);
        rateControlPanel = new JPanel(new BorderLayout());
        rateControlPanel.setBackground(background);

        JPanel labelPanel = new JPanel(new GridLayout(1, 3));
        labelPanel.setBackground(background);

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        leftPanel.setBackground(background);
        centerPanel.setBackground(background);
        rightPanel.setBackground(background);

        JLabel higherQualityLabel = createLabel("settings.transcode.rate_control.quality", LIGHT_TEXT);
        JLabel rateControlValueLabel = new JLabel(
            l10n("settings.transcode.rate_control.value", config.getRateControlValue()));
        rateControlValueLabel.setForeground(color(LIGHT_TEXT));
        JLabel fasterSpeedLabel = createLabel("settings.transcode.rate_control.speed", LIGHT_TEXT);

        leftPanel.add(higherQualityLabel);
        centerPanel.add(rateControlValueLabel);
        rightPanel.add(fasterSpeedLabel);

        labelPanel.add(leftPanel);
        labelPanel.add(centerPanel);
        labelPanel.add(rightPanel);

        rateControlSlider = new JSlider(JSlider.HORIZONTAL,
            0, MAX_QP, config.getRateControlValue());
        customizeSlider(rateControlSlider, backgroundIn, SLIDER_FOREGROUND);

        rateControlSlider.setMajorTickSpacing(10);
        rateControlSlider.setMinorTickSpacing(2);
        rateControlSlider.setPaintTicks(true);
        rateControlSlider.setPaintLabels(true);

        rateControlSlider.addChangeListener(e -> {
            if (!initialized.get()) {
                return;
            }

            int value = rateControlSlider.getValue();
            config.setRateControlValue(value);

            rateControlValueLabel.setText(l10n("settings.transcode.rate_control.value", value));

            if (!rateControlSlider.getValueIsAdjusting()) {
                setToCustomPreset();
            }
        });

        JPanel sliderPanel = new JPanel(new BorderLayout(5, 0));
        sliderPanel.setBackground(background);
        sliderPanel.add(rateControlSlider, BorderLayout.CENTER);

        rateControlPanel.add(labelPanel, BorderLayout.NORTH);
        rateControlPanel.add(sliderPanel, BorderLayout.CENTER);

        return rateControlPanel;
    }

    private JPanel createBitratePanel(UIColors backgroundIn) {
        Color background = color(backgroundIn);
        bitRatePanel = new JPanel(new BorderLayout());
        bitRatePanel.setBackground(background);

        int bitrate = config.getVideoBitrate();
        int sliderValue = convertBitrateToSliderValue(bitrate);

        JLabel bitRateValueLabel = new JLabel(formatBitrate(bitrate));
        bitRateValueLabel.setForeground(color(LIGHT_TEXT));

        // Non-linear logarithmic-ish bitrate slider
        bitRateSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, sliderValue);
        customizeSlider(bitRateSlider, backgroundIn, SLIDER_FOREGROUND);

        Dictionary<Integer, JLabel> sliderLabels = new Hashtable<>();
        sliderLabels.put(0, new JLabel("0"));
        sliderLabels.put(20, new JLabel("1"));
        sliderLabels.put(40, new JLabel("5"));
        sliderLabels.put(60, new JLabel("20"));
        sliderLabels.put(80, new JLabel("100"));
        sliderLabels.put(100, new JLabel("300"));
        bitRateSlider.setLabelTable(sliderLabels);

        bitRateSlider.setMajorTickSpacing(20);
        bitRateSlider.setPaintTicks(true);
        bitRateSlider.setPaintLabels(true);

        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(
            bitrate, 0, MAX_BITRATE, 50/* step for kbps */);
        JSpinner bitRateSpinner = new JSpinner(spinnerModel);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(bitRateSpinner);
        bitRateSpinner.setEditor(editor);
        customizeComponent(bitRateSpinner, backgroundIn, LIGHT_TEXT);

        final Object _syncObj = new Object();

        bitRateSlider.addChangeListener(e -> {
            if (!initialized.get()) {
                return;
            }

            int sliderVal = bitRateSlider.getValue();
            int bitrateVal = convertSliderValueToBitrate(sliderVal);
            config.setVideoBitrate(bitrateVal);
            bitRateValueLabel.setText(formatBitrate(bitrateVal));

            // Only update spinner if not dragging to avoid a feedback loop
            if (!bitRateSlider.getValueIsAdjusting()) {
                synchronized (_syncObj) {
                    bitRateSpinner.setValue(bitrateVal);
                }

                setToCustomPreset();
            }
        });

        bitRateSpinner.addChangeListener(e -> {
            if (!initialized.get()) {
                return;
            }

            synchronized (_syncObj) {
                int bitrateVal = (Integer)bitRateSpinner.getValue();
                config.setVideoBitrate(bitrateVal);
                bitRateValueLabel.setText(formatBitrate(bitrateVal));
                bitRateSlider.setValue(convertBitrateToSliderValue(bitrateVal));
            }

            setToCustomPreset();
        });

        JPanel sliderPanel = new JPanel(new BorderLayout(5, 0));
        sliderPanel.setBackground(background);
        sliderPanel.add(bitRateSlider, BorderLayout.CENTER);
        sliderPanel.add(bitRateSpinner, BorderLayout.EAST);

        bitRatePanel.add(bitRateValueLabel, BorderLayout.NORTH);
        bitRatePanel.add(sliderPanel, BorderLayout.CENTER);

        return bitRatePanel;
    }

    private void populateComponentsFromScanner() {
        final List<EncoderEnum> encoders = new ArrayList<>();
        encoders.add(EncoderEnum.NO_ENCODER);
        for (EncoderEnum encoder : EncoderEnum.values()) {
            if (!encoder.isDefault()
                && transcoder.getCompatScanner().isEncoderAvailable(encoder)) {
                // Preload presets
                transcoder.getCompatScanner().getAvailablePresets(encoder);
                transcoder.getCompatScanner().getAvailableProfiles(encoder);

                encoders.add(encoder);
            }
        }

        List<PresetEnum> presets = new ArrayList<>();
        presets.add(PresetEnum.CUSTOM);
        for (PresetEnum preset : PresetEnum.values()) {
            if (!preset.isDefault()
                && transcoder.getCompatScanner().isEncoderAvailable(preset.getEncoder())) {
                presets.add(preset);
            }
        }

        runOnEDT(() -> {
            ignoreSettingChanges = true;
            videoEncoderCombo.removeAllItems();
            for (EncoderEnum encoder : encoders) {
                videoEncoderCombo.addItem(encoder);
            }
            ignoreSettingChanges = false;

            presetCombo.removeAllItems();
            for (PresetEnum preset : presets) {
                presetCombo.addItem(preset);
            }
        });
    }

    private void updatePresetAndProfileDropdowns(EncoderEnum encoder) {
        if (!initialized.get()) {
            return;
        }

        ignoreSettingChanges = true;
        speedPresetCombo.removeAllItems();
        speedPresetCombo.addItem(EncoderPreset.NO_PRESET);
        if (!encoder.isDefault()) {
            for (EncoderPreset preset : transcoder.
                getCompatScanner().getAvailablePresets(encoder)) {
                speedPresetCombo.addItem(preset);
            }
        }
        ignoreSettingChanges = false;
        speedPresetCombo.setSelectedItem(config.getSpeedPreset());

        ignoreSettingChanges = true;
        profileCombo.removeAllItems();
        profileCombo.addItem(EncoderProfile.NO_PROFILE);
        if (!encoder.isDefault()) {
            for (EncoderProfile profile : transcoder
                .getCompatScanner().getAvailableProfiles(encoder)) {
                profileCombo.addItem(profile);
            }
        }
        ignoreSettingChanges = false;
        profileCombo.setSelectedItem(config.getProfile());
    }

    private void updatePresetDropdownBasedOnConfig() {
        if (!initialized.get() || updatingFromPreset) {
            return;
        }

        EncoderEnum currentEncoder = config.getVideoEncoder();
        boolean foundMatchingPreset = false;

        for (PresetEnum preset : PresetEnum.values()) {
            if (preset.isDefault() || preset.getEncoder() != currentEncoder) {
                continue;
            }

            if (preset.isPresetMatch(config)) {
                foundMatchingPreset = true;

                ignoreSettingChanges = true;
                presetCombo.setSelectedItem(preset);
                ignoreSettingChanges = false;
                break;
            }
        }

        if (!foundMatchingPreset) {
            ignoreSettingChanges = true;
            presetCombo.setSelectedItem(PresetEnum.CUSTOM);
            ignoreSettingChanges = false;
        }
    }

    private void setToCustomPreset() {
        if (!initialized.get() || updatingFromPreset) {
            return;
        }

        ignoreSettingChanges = true;
        presetCombo.setSelectedItem(PresetEnum.CUSTOM);
        ignoreSettingChanges = false;
    }

    private void applyPreset(PresetEnum preset) {
        if (!initialized.get()) {
            return;
        }

        try {
            updatingFromPreset = true;

            preset.applyToConfig(config);

            videoEncoderCombo.setSelectedItem(preset.getEncoder());
            updatePresetAndProfileDropdowns(preset.getEncoder());
            speedPresetCombo.setSelectedItem(preset.getSpeedPreset());
            profileCombo.setSelectedItem(preset.getProfile());
            rcModeCombo.setSelectedItem(preset.getRateControlMode());

            if (preset.getRateControlMode().isQpValue()) {
                rateControlSlider.setValue(preset.getRateControlValue());
            } else {
                bitRateSlider.setValue(convertBitrateToSliderValue(preset.getBitrate()));
            }

            updateControlVisibility();
            updateVideoControls();
            updateAudioControls();
        } finally {
            updatingFromPreset = false;
        }
    }

    @Override
    public void setEnabled(boolean enabledIn) {
        assert SwingUtilities.isEventDispatchThread();

        // This will be hard-locked if ffmpeg is not found
        enabled = enabledIn && transcoder.hasFFmpeg();// && qualitySettings.isEnableTranscoding();
        enableComponents(controlsPanel, enabled);

        if (initialized.get()) {
            updateControlVisibility();
            updateVideoControls();
            updateAudioControls();
        }
    }

    private void updateContainerControl() {
        if (!initialized.get()) {
            return;
        }

        AudioCodecEnum selectedCodec = (AudioCodecEnum)audioCodecCombo.getSelectedItem();
        boolean hasAudioCodec = !selectedCodec.isDefault();
        EncoderEnum selectedEncoder = (EncoderEnum)videoEncoderCombo.getSelectedItem();
        boolean hasVideoEncoder = !selectedEncoder.isDefault();

        enableComponentAndLabel(videoContainerCombo, (hasAudioCodec || hasVideoEncoder) && enabled);
    }

    private void updateAudioControls() {
        if (!initialized.get()) {
            return;
        }

        AudioCodecEnum selectedCodec = (AudioCodecEnum)audioCodecCombo.getSelectedItem();
        boolean hasAudioCodec = !selectedCodec.isDefault();
        enableComponentAndLabel(audioBitrateCombo, hasAudioCodec && enabled);

        updateContainerControl();
    }

    private void updateVideoControls() {
        if (!initialized.get()) {
            return;
        }

        EncoderEnum selectedEncoder = (EncoderEnum)videoEncoderCombo.getSelectedItem();
        boolean hasVideoEncoder = !selectedEncoder.isDefault();

        enableComponentAndLabel(speedPresetCombo, hasVideoEncoder && enabled);
        enableComponentAndLabel(profileCombo, hasVideoEncoder && enabled);
        enableComponentAndLabel(rcModeCombo, hasVideoEncoder && enabled);

        RateControlModeEnum mode = (RateControlModeEnum)rcModeCombo.getSelectedItem();
        boolean showRateControl = hasVideoEncoder && enabled && mode.isQpValue();
        enableComponentAndLabel(rateControlPanel, showRateControl);

        boolean showBitrate = hasVideoEncoder && enabled && mode.isBitrateValue();
        enableComponentAndLabel(bitRatePanel, showBitrate);

        updateContainerControl();
    }

    private void updateControlVisibility() {
        if (!initialized.get()) {
            enableComponents(controlsPanel, false);
            return;
        }

        if (!enabled) {
            //setComponentAndLabelVisible(rateControlPanel, false);
            setComponentAndLabelVisible(bitRatePanel, false);
            return;
        }

        RateControlModeEnum mode = (RateControlModeEnum)rcModeCombo.getSelectedItem();
        boolean showRateControl = mode.isQpValue();
        setComponentAndLabelVisible(rateControlPanel, showRateControl);
        enableComponentAndLabel(rateControlPanel, showRateControl);

        boolean showBitrate = mode.isBitrateValue();
        setComponentAndLabelVisible(bitRatePanel, showBitrate);
        enableComponentAndLabel(bitRatePanel, showBitrate);

        revalidate();
        repaint();
    }

    private int convertBitrateToSliderValue(int bitrate) {
        if (bitrate <= 0) {
            return 0;
        } else if (bitrate <= 1000) {
            return (int)Math.ceil(bitrate * 20.0 / 1000);
        } else if (bitrate <= 5000) {
            return (int)Math.ceil(20 + (bitrate - 1000) * 20.0 / 4000);
        } else if (bitrate <= 20000) {
            return (int)Math.ceil(40 + (bitrate - 5000) * 20.0 / 15000);
        } else if (bitrate <= 100000) {
            return (int)Math.ceil(60 + (bitrate - 20000) * 20.0 / 80000);
        }

        return Math.clamp((int)Math.ceil(80 + (bitrate - 100000) * 20.0 / 200000), 0, 100);
    }

    private int convertSliderValueToBitrate(int sliderValue) {
        sliderValue = Math.clamp(sliderValue, 0, 100);

        if (sliderValue <= 0) {
            return 0;
        } else if (sliderValue <= 20) {
            // 0-20 maps to 0-1000 Kbps
            return (int)Math.ceil(sliderValue * 1000.0 / 20);
        } else if (sliderValue <= 40) {
            // 20-40 maps to 1-5 Mbps
            return (int)Math.ceil(1000 + (sliderValue - 20) * 4000.0 / 20);
        } else if (sliderValue <= 60) {
            // 40-60 maps to 5-20 Mbps
            return (int)Math.ceil(5000 + (sliderValue - 40) * 15000.0 / 20);
        } else if (sliderValue <= 80) {
            // 60-80 maps to 20-100 Mbps
            return (int)Math.ceil(20000 + (sliderValue - 60) * 80000.0 / 20);
        }

        // 80-100 maps to 100-300 Mbps
        return (int)Math.ceil(100000 + (sliderValue - 80) * 200000.0 / 20);
    }
}
