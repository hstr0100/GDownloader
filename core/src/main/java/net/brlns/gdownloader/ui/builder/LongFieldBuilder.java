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
package net.brlns.gdownloader.ui.builder;

import jakarta.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import net.brlns.gdownloader.ui.themes.UIColors;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
// TODO: can't make this a generic NumericFieldBuilder<T> because my IDE just absolutely cannot stand it. Javac bug.
@Data
@Builder
public class LongFieldBuilder {

    @NonNull
    private String labelKey;
    private String placeholderText;
    @Builder.Default
    private int columns = 20;
    private boolean requiresRestart;
    @Builder.Default
    private boolean enabled = true;

    @NonNull
    private Supplier<Long> getter;
    @NonNull
    private Consumer<Long> setter;
    @Nullable
    private Consumer<Long> onSet;
    @NonNull
    private UIColors background;

    private boolean zeroIsEmpty;
    @Nullable
    private Long clampMin;
    @Nullable
    private Long clampMax;

}
