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
package net.brlns.gdownloader.updater.git;

import jakarta.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.updater.UvRuntime;
import net.brlns.gdownloader.util.FileUtils;
import net.brlns.gdownloader.util.Pair;
import tools.jackson.databind.JsonNode;

import static net.brlns.gdownloader.updater.UpdateStatusEnum.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractPythonToolUpdater extends AbstractGitUpdater {

    private static final String PYPI_USER = "pypi";

    private final UvRuntime uvRuntime;

    public AbstractPythonToolUpdater(GDownloader mainIn, UvRuntime uvRuntimeIn) {
        super(mainIn);

        uvRuntime = uvRuntimeIn;
    }

    protected abstract String getPackageName();

    protected abstract String getEntryPointName();

    protected abstract String getEnvironmentName();

    protected String getPythonVersion() {
        return "3.12";
    }

    @Override
    protected String getUser() {
        return PYPI_USER;
    }

    @Override
    protected String getRepo() {
        return getPackageName();
    }

    @Override
    @Nullable
    public String getReleaseBinaryName() {
        return uvRuntime.isSupported() ? getPackageName() : null;
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName() {
        return UvRuntime.getScriptPath(getEnvironmentName(), getEntryPointName());
    }

    @Override
    @Nullable
    public String getSystemBinaryName() {
        return FileUtils.getBinaryName(getEntryPointName());
    }

    @Override
    protected String getAPIEndpoint() {
        return String.format("https://pypi.org/pypi/%s/json", getPackageName());
    }

    @Nullable
    @Override
    protected Pair<String, String> getLatestReleaseTag() throws IOException, InterruptedException {
        JsonNode info = fetchLatestRelease().get("info");
        if (info == null || info.get("version") == null) {
            return null;
        }

        String version = info.get("version").asString();

        return new Pair<>(version, version);
    }

    @Override
    protected void init() throws Exception {

    }

    @Override
    protected File doDownload(String version, File workDir) throws Exception {
        File environmentDir = new File(workDir, getEnvironmentName());
        File outputFile = new File(workDir, getRuntimeBinaryName());

        notifyProgress(DOWNLOADING, 0);
        uvRuntime.createVirtualEnvironment(environmentDir, getPythonVersion());
        notifyProgress(DOWNLOADING, 100);

        notifyProgress(UNPACKING, 0);
        uvRuntime.installPackage(environmentDir, getPackageName(), version,
            (progress) -> notifyProgress(UNPACKING, progress));
        notifyProgress(UNPACKING, 100);

        if (!outputFile.exists()) {
            throw new FileNotFoundException(
                "Could not find " + getEntryPointName() + " after installing " + getPackageName());
        }

        return outputFile;
    }
}
