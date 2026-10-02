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
package net.brlns.gdownloader.util;

import jakarta.annotation.Nullable;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import static net.brlns.gdownloader.GDownloader.isWindows;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class FileDropUtils {

    private static final DataFlavor URI_LIST_FLAVOR = createUriListFlavor();

    public static Set<File> getDroppedFiles(@NonNull Transferable transferable) {
        Set<File> files = new LinkedHashSet<>();

        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return files;
        }

        try {
            if (transferable.getTransferData(DataFlavor.javaFileListFlavor) instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof File file) {
                        files.add(file);
                    }
                }
            }
        } catch (Exception e) {
            files.addAll(recoverFiles(transferable, e));

            if (files.isEmpty()) {
                log.warn("Cannot obtain dropped files: {}", e.getMessage());
            }
        }

        return files;
    }

    /**
     * Workaround for https://bugs.openjdk.org/browse/JDK-8067436
     */
    private static List<File> recoverFiles(Transferable transferable, Exception failure) {
        List<File> files = readUriList(transferable);

        for (Throwable cause = failure; files.isEmpty() && cause != null; cause = cause.getCause()) {
            if (cause instanceof URISyntaxException uriException && uriException.getInput() != null) {
                File file = toFile(uriException.getInput());

                if (file != null) {
                    files.add(file);
                }
            }
        }

        return files;
    }

    private static List<File> readUriList(Transferable transferable) {
        List<File> files = new ArrayList<>();

        try {
            if (URI_LIST_FLAVOR != null
                && transferable.isDataFlavorSupported(URI_LIST_FLAVOR)
                && transferable.getTransferData(URI_LIST_FLAVOR) instanceof InputStream inputStream) {
                try (inputStream) {
                    for (String line : new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                        File file = toFile(line.strip());

                        if (file != null) {
                            files.add(file);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Cannot read raw uri-list: {}", e.getMessage());
        }

        return files;
    }

    @Nullable
    private static File toFile(String uri) {
        if (!uri.regionMatches(true, 0, "file:", 0, 5)) {
            return null;
        }

        String path = uri.substring(5);

        if (path.startsWith("//")) {
            int slash = path.indexOf('/', 2);
            path = slash < 0 ? "" : path.substring(slash);
        }

        try {
            String decoded = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);

            return decoded.isEmpty() ? null : new File(decoded);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private static DataFlavor createUriListFlavor() {
        if (isWindows()) {
            return null;
        }

        try {
            DataFlavor flavor = new DataFlavor("text/uri-list;class=java.io.InputStream;charset=UTF-8");

            SystemFlavorMap flavorMap = (SystemFlavorMap)SystemFlavorMap.getDefaultFlavorMap();
            flavorMap.addUnencodedNativeForFlavor(flavor, "text/uri-list");
            flavorMap.addFlavorForUnencodedNative("text/uri-list", flavor);

            return flavor;
        } catch (Throwable e) {
            return null;
        }
    }
}
