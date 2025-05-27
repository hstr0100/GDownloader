# GDownloader

A user-friendly graphical user interface (GUI) for [yt-dlp](https://github.com/yt-dlp/yt-dlp), [gallery-dl](https://github.com/mikf/gallery-dl) and [spotDL](https://github.com/spotDL/spotify-downloader) written in Java.

## Overview

GDownloader enables you to batch download YouTube videos and playlists with a simple CTRL+C.\
It supports various platforms such as Crunchyroll, Twitch, X/Twitter, Spotify, and all other platforms supported by yt-dlp and gallery-dl.

## Features

- Batch download videos, image galleries and playlists
- Supports multiple sites and content types
- Seamlessly switch between the supported downloaders
- Embeds thumbnails and subtitles in the resulting media files, when available
- Automatic FFMPEG setup for Windows upon first boot
- Hardware-accelerated automatic video transcoding
- Keeps yt-dlp, spotDL and gallery-dl always updated and ready go
- Multiple customizable settings to best suit your usage style
- Available in three languages: en-US, pt-BR and es-MX
- Easy toggles for downloading audio, video, or both

## Motivation

The motivation for this project can be found in [this Reddit thread](https://www.reddit.com/r/DataHoarder/comments/1g34i9g/gdownloader_yet_another_user_friendly_ytdlp_gui/)

## Screenshots

<img src="screenshot1.png" alt="Screenshot1" width="500"/>
<img src="screenshot2.png" alt="Screenshot2" width="500"/>

## Requirements

For platforms other than Windows, you need to download and install FFMPEG separately, instructions vary per platform.

- [FFMPEG](https://ffmpeg.org/download.html)

## Installation

### Download

Download the latest version for your platform from the [releases page](https://github.com/hstr0100/GDownloader/releases/latest).

#### === OR ===

### Build from Source

0. Ensure you have [JDK 21](https://adoptium.net/temurin/releases/) or a newer version installed.

   **Windows Prerequisite:** On Windows, building requires the [WiX Toolset](https://github.com/wixtoolset/wix3/releases/latest) to be installed.

1. Clone this repository:
   ```bash
   git clone https://github.com/hstr0100/GDownloader.git
   ```

2. Navigate to the project directory:
   ```bash
   cd GDownloader
   ```

3. Build the project using Gradle:
   ```bash
   ./gradlew clean build jpackage
   ```

4. Create AppImage (Linux Only):

   Requires [appimagetool](https://github.com/AppImage/appimagetool/releases/latest) to be in your PATH variable.
   ```bash
   ./gradlew createAppImage
   ```

## Configurations

### Platform-Specific Configuration File Locations

The configuration files for GDownloader are stored in the following directories:

#### Windows

    %USERPROFILE%\AppData\Roaming\GDownloader\

#### MacOS

    ~/Library/Application Support/GDownloader/

#### Linux

    ~/.gdownloader/

#### Portable Mode (All Platforms)

    <Portable Installation Directory>/Internal/

In these directories, you will find the following configuration files:
- `config.json`: Main GDownloader configuration file in json format.
- `yt-dlp.conf`: Custom user configuration for the yt-dlp downloader. For instructions see [yt-dlp config](https://github.com/yt-dlp/yt-dlp?tab=readme-ov-file#configuration)
- `gallery-dl.conf`: Custom user configuration for the gallery-dl downloader. For instructions see [gallery-dl config](https://github.com/mikf/gallery-dl?tab=readme-ov-file#configuration)
- `spotdl.json`: Custom user configuration for the spotDL downloader. For instructions see [spotDL config](https://github.com/spotDL/spotify-downloader/blob/master/docs/usage.md#default-config)


For advanced users, you have the option to manually edit these configuration files to add custom parameters. e.g proxy settings.

Please be aware that some configuration parameters may conflict with GDownloader's internal settings passed to yt-dlp or gallery-dl. It is recommended to edit these files with caution.

## FAQ

### gallery-dl Support

To activate gallery-dl support, navigate to `Settings` > `Download Settings`, scroll down to the bottom and check the option `Enable gallery-dl downloader.` then, restart the program.

### spotDL Support

To activate spotDL support, navigate to `Settings` > `Download Settings`, scroll down to the bottom and check the option `Enable spotDL downloader.` then, restart the program.

### My Downloads Are Stuck Transcoding

If you are using a supported media player such as VLC, disabling the option `Convert audio to a widely supported codec (Slow)` under `Download Settings` will result in significant improvements in speed during the final transcoding step.

### Why Can't I Download From A Particular Site?

By default, GDownloader is configured to automatically capture links from a select number of popular websites. This is designed to minimize the capture of irrelevant unsupported links.\
To download content from a website not included in the default filter list, you can manually add the link by dragging it into the program window, selecting Right-click > `Paste URLs`, or pressing `Ctrl+V`.\
If you prefer GDownloader to capture all links without restriction, you can enable the `Capture Any Links` option in the `Download Settings` menu.

## Feedback

We welcome any feedback you may have to improve the user experience.

## Atributions

- Icons by [IconsDB.com](https://www.iconsdb.com)
- FFMpeg builds by [GyanD/codexffmpeg](https://github.com/GyanD/codexffmpeg)
- yt-dlp builds by [yt-dlp/yt-dlp](https://github.com/yt-dlp/yt-dlp)
- gallery-dl builds by [mikf/gallery-dl](https://github.com/mikf/gallery-dl)
- spotDL builds by [spotDL/spotify-downloader](https://github.com/spotDL/spotify-downloader)

