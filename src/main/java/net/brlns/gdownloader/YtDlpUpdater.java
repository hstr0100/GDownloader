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
package net.brlns.gdownloader;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class YtDlpUpdater{

    private static final String USER = "yt-dlp";
    private static final String REPO = "yt-dlp";

    private static final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .version(HttpClient.Version.HTTP_2)
        .build();

    private final GDownloader main;

    @Getter
    private File ytDlpExecutablePath;

    @Getter
    private File ffmpegExecutablePath;

    @Getter
    private ArchVersion archVersion;

    public YtDlpUpdater(GDownloader mainIn){
        main = mainIn;
    }

    protected void init() throws Exception{
        File workDir = main.getWorkDirectory();

        String tag = getLatestReleaseTag();
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

        switch(arch){
            case "x86":
            case "i386":
                if(os.contains("mac")){
                    archVersion = ArchVersion.MAC_X86;
                }else if(os.contains("win")){
                    archVersion = ArchVersion.WINDOWS_X86;
                }

                break;
            case "amd64":
            case "x86_64":
                if(os.contains("nux")){
                    archVersion = ArchVersion.LINUX_X64;
                }else if(os.contains("mac")){
                    archVersion = ArchVersion.MAC_X64;
                }else if(os.contains("win")){
                    archVersion = ArchVersion.WINDOWS_X64;
                }

                break;
            case "arm":
            case "aarch32":
                if(os.contains("nux")){
                    archVersion = ArchVersion.LINUX_ARM;
                }

                break;
            case "arm64":
            case "aarch64":
                if(os.contains("nux")){
                    archVersion = ArchVersion.LINUX_ARM64;
                }

                break;
            default:
                log.error("Unknown architecture: {}", arch);
                break;
        }

        if(archVersion == null){
            throw new UnsupportedOperationException("Unsupported operating system: " + os + " " + arch);
        }

        {//YT-DLP
            String url = getDownloadUrl(archVersion, tag);
            String fileName = getFilenameFromUrl(url);

            ytDlpExecutablePath = new File(workDir, fileName);

            String lockTag = String.join("_", tag, os, arch);
            File lock = new File(workDir, "version.txt");

            Path tmpDir = null;
            if(ytDlpExecutablePath.exists()){
                if(checkLock(lock, lockTag)){
                    log.info("yt-dlp is up to date");
                    return;
                }

                tmpDir = Paths.get(workDir.getAbsolutePath(), "old");

                if(!Files.exists(tmpDir)){
                    Files.createDirectories(tmpDir);
                }

                Files.move(ytDlpExecutablePath.toPath(), tmpDir.resolve(ytDlpExecutablePath.getName()), StandardCopyOption.REPLACE_EXISTING);
            }

            try{
                downloadFile(url, workDir, fileName);

                log.info("Downloaded {}", ytDlpExecutablePath);
            }catch(Exception e){
                if(tmpDir != null){
                    Files.move(tmpDir.resolve(ytDlpExecutablePath.getName()), ytDlpExecutablePath.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                throw e;
            }finally{
                if(ytDlpExecutablePath.exists()){
                    makeExecutable(ytDlpExecutablePath.getAbsolutePath());
                }
            }

            createLock(lock, lockTag);
        }

        {//FFMPEG
            String fileName = archVersion.getFfmpegBinary();
            if(fileName != null){
                ffmpegExecutablePath = copyResource(
                    "bin/ffmpeg/" + fileName,
                    new File(workDir, fileName));
            }
        }
    }

    @Nullable
    public File copyResource(String resource, File targetFile) throws IOException{
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

    private static String getLatestReleaseTag() throws IOException, InterruptedException{
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", USER, REPO);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode jsonNode = GDownloader.OBJECT_MAPPER.readTree(response.body());
        return jsonNode.get("tag_name").asText();
    }

    @Nullable
    private static String getDownloadUrl(ArchVersion version, String tag) throws IOException, InterruptedException{
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/tags/%s", USER, REPO, tag);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode jsonNode = GDownloader.OBJECT_MAPPER.readTree(response.body());
        Iterator<JsonNode> assets = jsonNode.get("assets").elements();

        while(assets.hasNext()){
            JsonNode asset = assets.next();

            String downloadUrl = asset.get("browser_download_url").asText();

            if(downloadUrl.endsWith(version.getYtDlpBinary())){
                return downloadUrl;
            }
        }

        return null;
    }

    private static String getFilenameFromUrl(String url){
        return Paths.get(URI.create(url).getPath()).getFileName().toString();
    }

    private static void downloadFile(String urlIn, File workDir, String fileName) throws IOException, InterruptedException{
        log.info("Downloading {} -> {}", urlIn, workDir);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(urlIn))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36")
            .build();

        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(Paths.get(workDir.getAbsolutePath(), fileName)));

        if(response.statusCode() != 200){//TODO: retry
            throw new RuntimeException("Failed to download file: " + response.statusCode());
        }
    }

    private static void makeExecutable(String filename) throws IOException{
        if(!GDownloader.isWindows()){
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(Paths.get(filename), permissions);
        }else{
            //No need
        }
    }

    public static void createLock(File lockFile, String content) throws IOException{
        try(FileWriter writer = new FileWriter(lockFile)){
            writer.write(content);
        }
    }

    public static boolean checkLock(File lockFile, String tag) throws IOException{
        if(!lockFile.exists()){
            return false;
        }

        String content = new String(Files.readAllBytes(lockFile.toPath()));

        return content.equals(tag);
    }

    private static enum OS{
        WINDOWS,
        LINUX,
        MAC;
    }

    @Getter
    private static enum ArchVersion{
        MAC_X86("yt-dlp_macos_legacy", null, OS.MAC),
        MAC_X64("yt-dlp_macos", null, OS.MAC),
        WINDOWS_X86("yt-dlp_x86.exe", null, OS.WINDOWS),
        WINDOWS_X64("yt-dlp.exe", "ffmpeg.exe", OS.WINDOWS),
        LINUX_X64("yt-dlp_linux", null, OS.LINUX),//You're on your own for ffmpeg buddy @TODO: add binary
        LINUX_ARM("yt-dlp_linux_armv7l", null, OS.LINUX),
        LINUX_ARM64("yt-dlp_linux_aarch64", null, OS.LINUX);

        private final String ytDlpBinary;
        private final String ffmpegBinary;
        private final OS os;

        private ArchVersion(String ytDlpBinaryIn, String ffmpegBinaryIn, OS osIn){
            ytDlpBinary = ytDlpBinaryIn;
            ffmpegBinary = ffmpegBinaryIn;
            os = osIn;
        }
    }
}
