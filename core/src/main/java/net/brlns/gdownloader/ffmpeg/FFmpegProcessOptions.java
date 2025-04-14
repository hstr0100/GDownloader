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
package net.brlns.gdownloader.ffmpeg;

import jakarta.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.brlns.gdownloader.util.CancelHook;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FFmpegProcessOptions {

    @Nullable
    private FFmpegProgressCalculator calculator;

    @Nullable
    private FFmpegProgressListener listener;

    @Nullable
    private TimeUnit timeoutUnit;

    @Nullable
    private Long timeoutValue;

    private boolean discardOutput;

    private boolean logOutput;

    private boolean lazyReader;

    private boolean cancellable;

    @Builder.Default
    private final String logPrefix = "";

    @Builder.Default
    private final CancelHook cancelHook = new CancelHook();

    @Builder.Default
    private final AtomicBoolean taskStarted = new AtomicBoolean();

}
