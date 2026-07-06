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

import java.util.Collection;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class MathUtils {

    private static final long ONE_MIB = 1024L * 1024L;
    private static final long TEN_MIB = 10L * ONE_MIB;
    private static final long HUNDRED_MIB = 100L * ONE_MIB;
    private static final long ONE_GIB = 1024L * ONE_MIB;
    private static final long TEN_GIB = 10L * ONE_GIB;

    public static double calculateAveragePercentage(Collection<Double> percentages) {
        return Math.min(100d,
            percentages.stream()
                .filter(percentage -> percentage >= 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0));
    }

    public static long convertSliderValueToBytesPerSecond(int sliderValue) {
        sliderValue = Math.clamp(sliderValue, 0, 100);

        if (sliderValue <= 0) {
            return 0L;
        } else if (sliderValue <= 20) {
            return Math.round(sliderValue * (double)ONE_MIB / 20);
        } else if (sliderValue <= 40) {
            return Math.round(ONE_MIB + (sliderValue - 20) * (double)(TEN_MIB - ONE_MIB) / 20);
        } else if (sliderValue <= 60) {
            return Math.round(TEN_MIB + (sliderValue - 40) * (double)(HUNDRED_MIB - TEN_MIB) / 20);
        } else if (sliderValue <= 80) {
            return Math.round(HUNDRED_MIB + (sliderValue - 60) * (double)(ONE_GIB - HUNDRED_MIB) / 20);
        }

        return Math.round(ONE_GIB + (sliderValue - 80) * (double)(TEN_GIB - ONE_GIB) / 20);
    }

    public static int convertBytesPerSecondToSliderValue(long bytesPerSecond) {
        if (bytesPerSecond <= 0) {
            return 0;
        } else if (bytesPerSecond <= ONE_MIB) {
            return (int)Math.ceil(bytesPerSecond * 20.0 / ONE_MIB);
        } else if (bytesPerSecond <= TEN_MIB) {
            return (int)Math.ceil(20 + (bytesPerSecond - ONE_MIB) * 20.0 / (TEN_MIB - ONE_MIB));
        } else if (bytesPerSecond <= HUNDRED_MIB) {
            return (int)Math.ceil(40 + (bytesPerSecond - TEN_MIB) * 20.0 / (HUNDRED_MIB - TEN_MIB));
        } else if (bytesPerSecond <= ONE_GIB) {
            return (int)Math.ceil(60 + (bytesPerSecond - HUNDRED_MIB) * 20.0 / (ONE_GIB - HUNDRED_MIB));
        }

        return Math.clamp((int)Math.ceil(80 + (bytesPerSecond - ONE_GIB) * 20.0 / (TEN_GIB - ONE_GIB)), 0, 100);
    }

    public static long getMaxThrottleBytesPerSecond() {
        return TEN_GIB;
    }
}
