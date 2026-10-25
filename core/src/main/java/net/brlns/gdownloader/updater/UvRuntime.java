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
package net.brlns.gdownloader.updater;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.process.ProcessArguments;
import net.brlns.gdownloader.updater.git.UvUpdater;
import net.brlns.gdownloader.util.CancelHook;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class UvRuntime {

    private final GDownloader main;
    private final UvUpdater updater;

    public UvRuntime(GDownloader mainIn) {
        main = mainIn;

        updater = new UvUpdater(mainIn);
    }

    public boolean isSupported() {
        return updater.isEnabled();
    }

    public synchronized File provision() throws Exception {
        File executable = updater.getExecutable();
        if (executable != null && executable.exists()) {
            return executable;
        }

        updater.check(false);

        executable = updater.getExecutable();
        if (executable == null) {
            throw new IllegalStateException("uv is not available");
        }

        return executable;
    }

    public void createVirtualEnvironment(File environmentDir, String pythonVersion) throws Exception {
        if (getPythonExecutable(environmentDir).exists()) {
            return;
        }

        run(new ProcessArguments(
            provision().getAbsolutePath(),
            "venv",
            environmentDir.getAbsolutePath(),
            "--python", pythonVersion,
            "--managed-python",
            "--allow-existing"));
    }

    public void installPackage(File environmentDir, String packageName, String version) throws Exception {
        run(new ProcessArguments(
            provision().getAbsolutePath(),
            "pip",
            "install",
            "--python", getPythonExecutable(environmentDir).getAbsolutePath(),
            "--reinstall-package", packageName,
            packageName + "==" + version));
    }

    public static File getPythonExecutable(File environmentDir) {
        return new File(environmentDir, getScriptsDirectoryName() + "/"
            + (GDownloader.isWindows() ? "python.exe" : "python"));
    }

    public static String getScriptPath(String environmentName, String scriptName) {
        return environmentName + "/" + getScriptsDirectoryName() + "/"
            + (GDownloader.isWindows() ? scriptName + ".exe" : scriptName);
    }

    private static String getScriptsDirectoryName() {
        return GDownloader.isWindows() ? "Scripts" : "bin";
    }

    public static Map<String, String> getPythonEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("PYTHONUTF8", "1");
        environment.put("PYTHONIOENCODING", "utf-8");
        environment.put("NO_COLOR", "1");

        environment.put("PYTHONHOME", null);
        environment.put("PYTHONPATH", null);
        environment.put("VIRTUAL_ENV", null);
        environment.put("LD_LIBRARY_PATH", null);

        return environment;
    }

    private void run(ProcessArguments arguments) throws Exception {
        log.info("Running: {}", arguments);

        Map<String, String> environment = getPythonEnvironment();
        environment.put("UV_PYTHON_INSTALL_DIR", new File(GDownloader.getWorkDirectory(), "python").getAbsolutePath());
        environment.put("UV_PYTHON_DOWNLOADS", "automatic");
        environment.put("UV_LINK_MODE", "copy");
        environment.put("UV_NO_CONFIG", "1");
        environment.put("UV_NO_PROGRESS", "1");

        String proxyUrl = main.getHttpManager().getUpstreamProxyUrl();
        if (proxyUrl != null) {
            environment.put("ALL_PROXY", proxyUrl);
        }

        Process process = main.getProcessMonitor().startProcess(arguments, environment, new CancelHook());

        String lastOutput = "";
        long deadline = System.nanoTime() + Duration.ofMinutes(20).toNanos();

        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (process.isAlive() || reader.ready()) {
                if (System.nanoTime() > deadline) {
                    process.destroyForcibly();

                    throw new IOException("Command timed out: " + arguments.getFirst());
                }

                if (reader.ready()) {
                    String line = reader.readLine();

                    if (line != null) {
                        log.debug("[uv] {}", line);

                        lastOutput = line;
                    }
                } else {
                    Thread.sleep(100);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command exited with code " + exitCode + ": " + lastOutput);
        }
    }
}
