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
package net.brlns.gdownloader.downloader.structs;

import java.util.BitSet;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.brlns.gdownloader.downloader.enums.DownloadFlagsEnum;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@AllArgsConstructor
public class DownloadResult {

    private final BitSet flags;
    private final String lastOutput;

    public DownloadResult(DownloadFlagsEnum flagsEnumIn) {
        this(flagsEnumIn, "");
    }

    public DownloadResult(DownloadFlagsEnum flagsEnumIn, String lastOutputIn) {
        this(flagsEnumIn.asBitSet(), lastOutputIn);
    }

    public DownloadResult(BitSet flagsIn) {
        this(flagsIn, "");
    }
}
