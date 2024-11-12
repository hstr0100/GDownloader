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
package net.brlns.gdownloader.downloader.enums;

import java.util.BitSet;
import lombok.Getter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum DownloadFlagsEnum {
    FLAG_SUCCESS((byte)0x00),
    FLAG_STOPPED((byte)0x01),
    FLAG_UNSUPPORTED((byte)0x02),
    FLAG_MAIN_CATEGORY_FAILED((byte)0x03),
    FLAG_NO_METHOD((byte)0x04),
    FLAG_NO_METHOD_VIDEO((byte)0x05),
    FLAG_NO_METHOD_AUDIO((byte)0x06);

    private final byte flag;

    private DownloadFlagsEnum(byte flag) {
        this.flag = flag;
    }

    public boolean isSet(BitSet bitSet) {
        return bitSet.get(flag);
    }

    public void set(BitSet bitSet) {
        bitSet.set(flag);
    }

    public void clear(BitSet bitSet) {
        bitSet.clear(flag);
    }

    //
    // For future use
    //
    public BitSet asBitSet() {
        BitSet bitSet = new BitSet();
        bitSet.set(getFlag());

        return bitSet;
    }

    public static BitSet combineFlags(DownloadFlagsEnum... flags) {
        BitSet bitSet = new BitSet();
        for (DownloadFlagsEnum flag : flags) {
            bitSet.set(flag.getFlag());
        }

        return bitSet;
    }

    public static boolean areFlagsNotSet(BitSet flags, DownloadFlagsEnum... flagsToCheck) {
        for (DownloadFlagsEnum flag : flagsToCheck) {
            if (flags.get(flag.getFlag())) {
                return false;
            }
        }

        return true;
    }

    public static boolean areFlagsSet(BitSet flags, DownloadFlagsEnum... flagsToCheck) {
        for (DownloadFlagsEnum flag : flagsToCheck) {
            if (!flags.get(flag.getFlag())) {
                return false;
            }
        }

        return true;
    }
}
