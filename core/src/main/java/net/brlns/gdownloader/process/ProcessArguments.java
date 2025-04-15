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
package net.brlns.gdownloader.process;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import net.brlns.gdownloader.util.StringUtils;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class ProcessArguments extends ArrayList<String> {

    public ProcessArguments(String... args) {
        addAll(args);
    }

    public ProcessArguments(Object... args) {
        add(args);
    }

    public ProcessArguments addAll(String[] arguments) {
        addAll(Arrays.asList(arguments));
        return this;
    }

    public ProcessArguments addAll(Object[] arguments) {
        add(Arrays.asList(arguments));
        return this;
    }

    public ProcessArguments add(Object... arguments) {
        for (Object argument : arguments) {
            if (argument instanceof String string) {
                add(string);
            } else if (argument instanceof Number) {
                add(String.valueOf(argument));
            } else if (argument instanceof Collection<?> objCollection) {
                for (Object item : objCollection) {
                    if (!(item instanceof String)) {
                        throw new IllegalArgumentException("Collection must contain only Strings");
                    }
                }

                @SuppressWarnings("unchecked")
                Collection<String> collection = (Collection<String>)objCollection;
                addAll(collection);
            } else if (argument instanceof String[] array) {
                addAll(array);
            } else {
                throw new IllegalArgumentException("Argument must be of type String, Number, Collection<String> or String[]");
            }
        }

        return this;
    }

    @Override
    public String toString() {
        return StringUtils.escapeAndBuildCommandLine(this);
    }

}
