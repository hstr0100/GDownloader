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

    public static double calculateAveragePercentage(Collection<Double> percentages) {
        return Math.min(100d,
            percentages.stream()
                .filter(percentage -> percentage >= 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0));
    }
}
