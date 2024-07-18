package net.brlns.gdownloader.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class AudioEngine{

    private static final float SAMPLE_RATE = 8000f;

    public static void playNotificationTone(){
        try{
            tone(1500, 50, 0.05);
        }catch(LineUnavailableException e){
            log.error("Audio data line error {}", e.getLocalizedMessage());
        }
    }

    private static void tone(int hertz, int millis, double volume) throws LineUnavailableException{
        byte[] buf = new byte[1];
        AudioFormat audioFormat = new AudioFormat(
            SAMPLE_RATE, // sampleRate
            8, // sampleSizeInBits
            1, // channels
            true, // signed
            false // bigEndian
        );

        try(SourceDataLine sdl = AudioSystem.getSourceDataLine(audioFormat)){
            sdl.open(audioFormat);
            sdl.start();

            for(int i = 0; i < millis * 8; i++){
                double angle = i / (SAMPLE_RATE / hertz) * 2.0 * Math.PI;
                buf[0] = (byte)(Math.sin(angle) * 127.0 * volume);
                sdl.write(buf, 0, 1);
            }

            sdl.drain();
            sdl.stop();
        }
    }
}
