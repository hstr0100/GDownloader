/*
 * Copyright (C) 2026 hstr0100
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
package net.brlns.gdownloader.downloader.hosts;

import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Builder
@SuppressWarnings("cast")
public class ResolvedFile {

    /**
     * Direct, CDN-level URL.
     */
    private final String url;

    /**
     * The file name to save the download as, if known.
     */
    @Nullable
    private final String fileName;

    /**
     * Known file size in bytes, or -1 if unknown.
     */
    @Builder.Default
    private final long size = -1L;

    /**
     * Referer header if the host requires one.
     */
    @Nullable
    private final String referer;

    /**
     * Any additional headers required to fetch the final direct link.
     */
    @Singular
    private final Map<String, String> extraHeaders;

    /**
     * True for hosts that fumble on chunked downloads.
     */
    private final boolean forceSingleChunk;

    /**
     * Skips all checks and jumps straight to downloading.
     */
    private final boolean singleUse;

    /**
     * Invoked when a singleUse download fails partway and needs to be retried.
     */
    @Nullable
    private final Supplier<ResolvedFile> reissueSupplier;

    public Map<String, String> getExtraHeaders() {
        return extraHeaders == null ? Collections.emptyMap() : extraHeaders;
    }
}
