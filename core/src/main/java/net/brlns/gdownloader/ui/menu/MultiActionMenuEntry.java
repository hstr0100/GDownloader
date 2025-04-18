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
package net.brlns.gdownloader.ui.menu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class MultiActionMenuEntry<T> implements IMenuEntry {

    private final Supplier<T> source;

    private final Consumer<Collection<T>> action;

    private Supplier<String> iconAsset;

    @SuppressWarnings("unchecked")
    public void processActions(List<IMenuEntry> entries) {
        List<T> list = new ArrayList<>();

        for (IMenuEntry entry : entries) {
            MultiActionMenuEntry<T> dependentAction = (MultiActionMenuEntry<T>)entry;

            list.add(dependentAction.getSource().get());
        }

        action.accept(list);
    }
}
