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
package net.brlns.gdownloader.system;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.DirectoryUtils;

import static net.brlns.gdownloader.GDownloader.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class StartupManager {

    public static void updateAutoStartupState(GDownloader main) {
        try {
            boolean currentState = main.getConfig().isAutoStart();
            List<String> launchCommand = main.getLaunchCommand();

            log.debug("Launch command is: {}", launchCommand);

            if (launchCommand == null || launchCommand.isEmpty()) {
                log.error("Cannot locate runtime binary.");
                return;
            }

            if (isWindows()) {
                handleWindowsStartupState(main, currentState, launchCommand);
            } else if (isLinux()) {
                handleLinuxStartupState(main, currentState, launchCommand);
            } else {
                log.error("Unsupported operation.");
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    private static void handleLinuxStartupState(GDownloader main, boolean currentState,
        List<String> launchArguments) throws Exception {

        File autostartDirectory = DirectoryUtils.getOrCreate(
            System.getProperty("user.home"), "/.config/autostart");

        File desktopFile = new File(autostartDirectory, REGISTRY_APP_NAME + ".desktop");
        if (!desktopFile.exists() && currentState) {
            Path iconPath = getWorkDirectory().toPath().resolve("icon.png");
            if (!Files.exists(iconPath)) {
                try (
                    InputStream imageStream = StartupManager.class.getResourceAsStream(
                        main.getGuiManager().getCurrentAppIconPath())) {
                    if (imageStream == null) {
                        throw new FileNotFoundException("Resource not found: "
                            + main.getGuiManager().getCurrentAppIconPath());
                    }

                    Files.copy(imageStream, iconPath);
                }
            }

            List<String> programArgs = new ArrayList<>(launchArguments);
            programArgs.add("--no-gui");

            StringBuilder builder = new StringBuilder();

            for (String arg : programArgs) {
                if (arg.contains(" ")) {
                    builder.append("\"").append(arg).append("\"").append(" ");
                } else {
                    builder.append(arg).append(" ");
                }
            }

            if (builder.charAt(builder.length() - 1) == ' ') {
                builder.deleteCharAt(builder.length() - 1);
            }

            try (FileWriter writer = new FileWriter(desktopFile)) {
                writer.write("[Desktop Entry]\n");
                writer.write("Categories=Network;\n");
                writer.write("Comment=Start " + REGISTRY_APP_NAME + "\n");
                writer.write("Exec=" + builder.toString() + "\n");
                writer.write("Icon=" + iconPath + "\n");
                writer.write("Terminal=false\n");
                writer.write("MimeType=\n");
                writer.write("X-GNOME-Autostart-enabled=true\n");
                writer.write("Name=" + REGISTRY_APP_NAME + "\n");
                writer.write("Type=Application\n");
            }

            Set<PosixFilePermission> permissions
                = Files.getPosixFilePermissions(desktopFile.toPath());

            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);

            Files.setPosixFilePermissions(desktopFile.toPath(), permissions);

            log.info("Registered as a startup program.");
        } else if (desktopFile.exists() && !currentState) {
            Files.delete(desktopFile.toPath());

            log.info("Startup entry removed.");
        }
    }

    private static void handleWindowsStartupState(GDownloader main, boolean currentState,
        List<String> launchArguments) throws Exception {
        ProcessBuilder checkBuilder = new ProcessBuilder("reg", "query",
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
            "/v", REGISTRY_APP_NAME);

        Process checkProcess = checkBuilder.start();
        checkProcess.waitFor();

        int checkExitValue = checkProcess.exitValue();

        log.debug("Check startup status: {}", checkExitValue);

        if (checkExitValue == 0 && !currentState) {
            ProcessBuilder deleteBuilder = new ProcessBuilder("reg", "delete",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", REGISTRY_APP_NAME, "/f");

            Process updateProcess = deleteBuilder.start();
            updateProcess.waitFor();

            if (log.isDebugEnabled()) {
                int updateExitValue = updateProcess.exitValue();
                if (updateExitValue == 0) {
                    log.info("Startup entry updated successfully.");
                } else {
                    log.error("Failed to update startup entry.");
                }
            }
        } else if (checkExitValue != 0 && currentState) {
            List<String> regArgs = new ArrayList<>(List.of("reg", "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                "/v", REGISTRY_APP_NAME,
                "/t", "REG_SZ",
                "/d"));

            List<String> programArgs = new ArrayList<>(launchArguments);
            programArgs.add("--no-gui");

            StringBuilder builder = new StringBuilder();
            builder.append("\"");

            for (String arg : programArgs) {
                if (arg.contains(File.separator)) {
                    builder.append("\\\"").append(arg).append("\\\"").append(" ");
                } else {
                    builder.append(arg).append(" ");
                }
            }

            if (builder.charAt(builder.length() - 1) == ' ') {
                builder.deleteCharAt(builder.length() - 1);
            }

            builder.append("\"");

            regArgs.add(builder.toString());
            regArgs.add("/f");

            ProcessBuilder createBuilder = new ProcessBuilder(regArgs);

            Process process = createBuilder.start();
            int exitCode = process.waitFor();

            if (log.isDebugEnabled()) {
                log.info("Program args: {}", programArgs);
                log.info("Startup command args: {}", regArgs);

                if (exitCode == 0) {
                    log.info("Registry entry added successfully.");
                } else {
                    log.error("Failed to add registry entry. Exit code: {} Command list: {}",
                        exitCode, createBuilder.command());
                }
            }
        }
    }
}
