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

import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.util.Nullable;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class YtDlpUpdater extends AbstractGitUpdater{

    private static final String USER = "yt-dlp";
    private static final String REPO = "yt-dlp";

    public YtDlpUpdater(GDownloader mainIn){
        super(mainIn);
    }

    @Override
    protected String getUser(){
        return USER;
    }

    @Override
    protected String getRepo(){
        return REPO;
    }

    @Override
    @Nullable
    public String getBinaryName(){
        ArchVersionEnum archVersion = main.getArchVersion();

        return archVersion.getYtDlpBinary();
    }

    @Override
    @Nullable
    public String getBinaryFallback(){
        ArchVersionEnum archVersion = main.getArchVersion();

        return archVersion.getYtDlpFallback();
    }

    @Nullable
    @Override
    protected String getRuntimeBinaryName(){
        return getBinaryName();
    }

    @Nullable
    @Override
    protected String getLockFileName(){
        return "ytdlp_lock.txt";
    }

}
