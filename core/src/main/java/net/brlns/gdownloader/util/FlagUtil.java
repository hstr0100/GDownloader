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
package net.brlns.gdownloader.util;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class FlagUtil {

    public static int set(AtomicReference<Integer> reference, int flagPosition) {
        validateFlagPosition(flagPosition);

        return reference.updateAndGet(currentValue
            -> currentValue | (1 << flagPosition));
    }

    public static int clear(AtomicReference<Integer> reference, int flagPosition) {
        validateFlagPosition(flagPosition);

        return reference.updateAndGet(currentValue
            -> currentValue & ~(1 << flagPosition));
    }

    public static int toggle(AtomicReference<Integer> reference, int flagPosition) {
        validateFlagPosition(flagPosition);

        return reference.updateAndGet(currentValue
            -> currentValue ^ (1 << flagPosition));
    }

    public static boolean isSet(AtomicReference<Integer> reference, int flagPosition) {
        validateFlagPosition(flagPosition);

        return (reference.get() & (1 << flagPosition)) != 0;
    }

    public static int setFlags(AtomicReference<Integer> reference, int flags) {
        return reference.updateAndGet(currentValue
            -> currentValue | flags);
    }

    public static int clearFlags(AtomicReference<Integer> reference, int flags) {
        return reference.updateAndGet(currentValue
            -> currentValue & ~flags);
    }

    private static void validateFlagPosition(int flagPosition) {
        if (flagPosition < 0 || flagPosition > 31) {
            throw new IllegalArgumentException("Flag position must be between 0 and 31 inclusive");
        }
    }
}
