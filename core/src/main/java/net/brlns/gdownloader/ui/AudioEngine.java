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

import com.adonax.audiocue.AudioCue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.ui.message.MessageTypeEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class AudioEngine {

    private static final float SAMPLE_RATE = 8000f;

    private static final AudioEngine engine = new AudioEngine();

    private final ConcurrentMap<AudioResource, AudioCue> soundMap = new ConcurrentHashMap<>();

    private int playSound(AudioResource audio) {
        return playSound(audio, 1d, 1.2d);
    }

    private int playSound(AudioResource audio, double volume, double pitch) {
        try {
            AudioCue clip = soundMap.computeIfAbsent(audio, res -> {
                try {
                    // Java's native audio clips are atrociously unreliable. AudioCue just works.
                    AudioCue newClip = AudioCue.makeStereoCue(getClass().getResource(res.getResource()), 4);
                    newClip.setName(res.getResource());
                    newClip.open(4096);

                    return newClip;
                } catch (Exception e) {
                    log.error("Failed to create audio cue for {}: {}", res, e.getMessage());
                    return null;
                }
            });

            if (clip == null) {
                return -1;
            }

            int instance = clip.obtainInstance();

            clip.stop(instance);
            clip.setFramePosition(instance, 0);
            clip.setVolume(instance, volume);
            clip.setSpeed(instance, pitch);
            clip.setRecycleWhenDone(instance, true);
            clip.start(instance);

            return instance;
        } catch (Exception e) {
            log.error("Failed to play audio: {}: {}", audio, e.getMessage());
        }

        return -1;
    }

    public static void playNotificationTone(MessageTypeEnum type) {
        CompletableFuture.runAsync(() -> {
            switch (type) {
                case INFO ->
                    engine.playSound(AudioResource.POSITIVE_TONE);
                case ERROR ->
                    engine.playSound(AudioResource.NEGATIVE_TONE);
                default -> {
                    try {
                        tone(1500, 50, 0.05);
                    } catch (LineUnavailableException e) {
                        log.error("Audio data line error", e);
                    }
                }
            }
        }).exceptionally(e -> {
            GDownloader.handleException(e);
            return null;
        });
    }

    private static void tone(int hertz, int millis, double volume) throws LineUnavailableException {
        byte[] buf = new byte[1];
        AudioFormat audioFormat = new AudioFormat(
            SAMPLE_RATE, // sampleRate
            8, // sampleSizeInBits
            1, // channels
            true, // signed
            false // bigEndian
        );

        try (SourceDataLine sdl = AudioSystem.getSourceDataLine(audioFormat)) {
            sdl.open(audioFormat);
            sdl.start();

            for (int i = 0; i < millis * 8; i++) {
                double angle = i / (SAMPLE_RATE / hertz) * 2.0 * Math.PI;
                buf[0] = (byte)(Math.sin(angle) * 127.0 * volume);
                sdl.write(buf, 0, 1);
            }

            sdl.drain();
            sdl.stop();
        }
    }

    @Getter
    private static enum AudioResource {
        POSITIVE_TONE("/assets/audio/positive_tone.wav"),
        NEGATIVE_TONE("/assets/audio/negative_tone.wav");

        private final String resource;

        private AudioResource(String resourceIn) {
            resource = resourceIn;
        }
    }
}
