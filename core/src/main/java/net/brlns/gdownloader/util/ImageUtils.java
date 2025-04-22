/*
 * Copyright (C) 2025 @hstr0100
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
package net.brlns.gdownloader.util;

import jakarta.annotation.Nullable;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class ImageUtils {

    @Nullable
    public static String bufferedImageToBase64(@NonNull BufferedImage image, String format) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, bos);
            byte[] imageBytes = bos.toByteArray();

            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            GDownloader.handleException(e);
            return null;
        }
    }

    @Nullable
    public static BufferedImage base64ToBufferedImage(@Nullable String base64Image) {
        if (base64Image == null || base64Image.isEmpty()) {
            return null;
        }

        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Image);

            try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                return ImageIO.read(bis);
            }
        } catch (Exception e) {
            GDownloader.handleException(e);
            return null;
        }
    }

    public static BufferedImage downscaleImage(BufferedImage originalImage, int maxWidth) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        if (originalWidth <= maxWidth) {
            return originalImage;
        }

        float aspectRatio = (float)originalHeight / originalWidth;
        int newWidth = maxWidth;
        int newHeight = Math.round(maxWidth * aspectRatio);

        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, originalImage.getType());

        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resizedImage;
    }

    public static BufferedImage cropToSixteenByNineIfHorizontal(BufferedImage originalImage) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        if (originalHeight >= originalWidth) {
            return originalImage;
        }

        int targetHeight = originalWidth * 9 / 16;

        if (originalHeight == targetHeight) {
            return originalImage;
        }

        if (originalHeight > targetHeight) {
            int y = (originalHeight - targetHeight) / 2;
            return originalImage.getSubimage(0, y, originalWidth, targetHeight);
        } else {
            int targetWidth = originalHeight * 16 / 9;
            int x = (originalWidth - targetWidth) / 2;
            return originalImage.getSubimage(x, 0, targetWidth, originalHeight);
        }
    }

    @Nullable
    public static File writeImageToTempFile(BufferedImage image) {
        try {
            File tempFile = File.createTempFile("img-",
                FileUtils.TMP_FILE_IDENTIFIER + ".png");
            tempFile.deleteOnExit();

            ImageIO.write(image, "png", tempFile);

            return tempFile;
        } catch (IOException e) {
            log.error("Unable to create temporary image", e);
        }

        return null;
    }
}
