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
package net.brlns.gdownloader.updater;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.NoFallbackAvailableException;
import net.brlns.gdownloader.util.Nullable;
import net.brlns.gdownloader.util.Pair;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public abstract class AbstractGitUpdater{

    private static final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(Duration.ofSeconds(10))
        .version(HttpClient.Version.HTTP_2)
        .build();

    protected final GDownloader main;

    @Getter
    private boolean updated = false;

    @Getter
    protected File executablePath;

    public AbstractGitUpdater(GDownloader mainIn){
        main = mainIn;
    }

    protected abstract String getUser();

    protected abstract String getRepo();

    @Nullable
    protected abstract String getBinaryName();

    @Nullable
    protected abstract String getBinaryFallback();

    @Nullable
    protected abstract String getRuntimeBinaryName();

    @Nullable
    protected abstract String getLockFileName();

    protected void tryFallback(File workDir) throws Exception{
        String fileName = getRuntimeBinaryName();

        if(fileName != null){
            File lock = new File(workDir, getLockFileName());

            File binaryFile = new File(workDir, fileName);
            if(binaryFile.exists() && lockExists(lock)){
                log.info("Selected previous installation as fallback {}", getRepo());
                executablePath = binaryFile;
                return;
            }
        }

        //For included ffmpeg and yt-dlp binaries
        //Will only optionally be available to end user builds
        fileName = getBinaryFallback();

        if(fileName != null){
            String[] resources = fileName.split(";");

            if(resources.length == 1){
                executablePath = copyResource(
                    "/bin/" + fileName,
                    new File(workDir, fileName));
            }else{
                File outFile = new File(workDir, getRuntimeBinaryName());
                if(!outFile.exists()){
                    outFile.mkdirs();
                }

                int successes = 0;
                for(String resource : resources){
                    File output = copyResource(
                        "/bin/" + resource, new File(outFile, resource));

                    if(output != null){
                        successes++;
                    }
                }

                if(successes != 0 && successes == resources.length){
                    executablePath = outFile;
                }
            }

            if(executablePath != null){
                log.info("Selected bundled binary as fallback {}", getRepo());
                return;
            }
        }

        throw new NoFallbackAvailableException();
    }

    public final void check() throws Exception{
        updated = false;
        File workDir = main.getWorkDirectory();

        if(!main.getConfig().isAutomaticUpdates()){
            log.info("Automatic updates are disabled {}", getRepo());

            tryFallback(workDir);
            return;
        }

        if(getBinaryName() == null){
            log.error("Cannot update, no binary configured for {}", getRepo());

            tryFallback(workDir);
            return;
        }

        Pair<String, String> tag = null;

        try{
            tag = getLatestReleaseTag();
        }catch(HttpConnectTimeoutException e){
            log.error("Http timeout for {}", getRepo());
        }

        if(tag == null){
            log.error("Release tag was null {}", getRepo());

            tryFallback(workDir);
            return;
        }

        String url = tag.getValue();

        if(url == null){
            log.error("No {} binary for platform", getRepo());

            tryFallback(workDir);
            return;
        }

        File binaryPath = new File(workDir, getRuntimeBinaryName());

        String lockTag = getLockTag(tag.getKey());

        File lock = new File(workDir, getLockFileName());

        Path tmpDir = null;
        if(binaryPath.exists()){
            if(checkLock(lock, lockTag)){
                executablePath = binaryPath;

                log.info("{} is up to date", getRepo());
                return;
            }

            tmpDir = Paths.get(workDir.getAbsolutePath(), "old");

            if(!Files.exists(tmpDir)){
                Files.createDirectories(tmpDir);
            }

            Files.move(binaryPath.toPath(), tmpDir.resolve(binaryPath.getName()), StandardCopyOption.REPLACE_EXISTING);
        }

        try{
            log.info("Starting download {}", getRepo());

            executablePath = doDownload(url, workDir);

            createLock(lock, lockTag);

            updated = true;

            log.info("Downloaded {}", executablePath);
        }catch(Exception e){
            log.error("Failed to update {} - {}", getRepo(), e.getCause());

            if(tmpDir != null){
                Files.move(tmpDir.resolve(binaryPath.getName()), binaryPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }else{
                tryFallback(workDir);
            }
        }finally{
            if(binaryPath.exists()){
                makeExecutable(binaryPath.getAbsolutePath());
            }
        }
    }

    protected File doDownload(String url, File workDir) throws Exception{
        String fileName = getFilenameFromUrl(url);

        File outputFile = new File(workDir, fileName);

        downloadFile(url, outputFile);

        return outputFile;
    }

    @Nullable
    protected File copyResource(String resource, File targetFile) throws IOException{
        try(InputStream initialStream = getClass().getResourceAsStream(resource)){
            if(initialStream == null){
                log.error("Resource not available: " + resource);
                return null;
            }

            byte[] buffer = new byte[initialStream.available()];
            initialStream.read(buffer);

            Files.write(targetFile.toPath(), buffer);

            makeExecutable(targetFile.getAbsolutePath());

            return targetFile;
        }
    }

    @Nullable
    protected Pair<String, String> getLatestReleaseTag() throws IOException, InterruptedException{
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", getUser(), getRepo());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36")
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        //log.info(response.body());
        JsonNode jsonNode = GDownloader.OBJECT_MAPPER.readTree(response.body());

        JsonNode tagName = jsonNode.get("tag_name");
        if(tagName == null){
            return null;
        }

        Iterator<JsonNode> assets = jsonNode.get("assets").elements();

        while(assets.hasNext()){
            JsonNode asset = assets.next();

            //If there is no key, there is no download url
            String downloadUrl = asset.get("browser_download_url").asText();

            if(downloadUrl.endsWith(getBinaryName())){
                return new Pair<>(tagName.asText(), downloadUrl);
            }
        }

        return null;
    }

    protected String getFilenameFromUrl(String url){
        return Paths.get(URI.create(url).getPath()).getFileName().toString();
    }

    protected void downloadFile(String urlIn, File outputFile) throws IOException, InterruptedException{
        log.info("Downloading {} -> {}", urlIn, outputFile);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(urlIn))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36")
            .build();

        HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());

        if(response.statusCode() == 200){
            int bufferSize = 8192;

            try(InputStream inputStream = response.body();
                BufferedOutputStream bos = new BufferedOutputStream(
                    Files.newOutputStream(outputFile.toPath()), bufferSize)){

                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                while((bytesRead = inputStream.read(buffer)) != -1){
                    bos.write(buffer, 0, bytesRead);
                }
            }
        }else{
            throw new IOException("Failed to download file: " + urlIn + ": " + response.statusCode());
        }
    }

    protected void makeExecutable(String filename) throws IOException{
        if(!GDownloader.isWindows()){
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(Paths.get(filename), permissions);
        }else{
            //No need
        }
    }

    protected String getLockTag(String releaseTag){
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

        return String.join("_", releaseTag, os, arch);
    }

    protected void createLock(File lockFile, String content) throws IOException{
        try(FileWriter writer = new FileWriter(lockFile)){
            writer.write(content);
        }
    }

    protected boolean checkLock(File lockFile, String tag) throws IOException{
        if(!lockFile.exists()){
            return false;
        }

        String content = new String(Files.readAllBytes(lockFile.toPath()));

        return content.equals(tag);
    }

    protected boolean lockExists(File lockFile) throws IOException{
        return lockFile.exists();
    }
}
