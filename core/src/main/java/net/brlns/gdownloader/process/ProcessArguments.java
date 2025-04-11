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
import java.util.List;
import net.brlns.gdownloader.util.StringUtils;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public final class ProcessArguments extends ArrayList<String> {

    public ProcessArguments(String... args) {
        addAll(Arrays.asList(args));
    }

    public ProcessArguments(Object... args) {
        add(args);
    }

    public ProcessArguments add(Object... arguments) {
        for (Object argument : arguments) {
            if (argument instanceof String string) {
                add(string);
            } else if (argument instanceof Number) {
                add(String.valueOf(argument));
            } else {
                throw new IllegalArgumentException("Argument must be of type String or Number");
            }
        }

        return this;
    }

    @Override
    public String toString() {
        return StringUtils.escapeAndBuildCommandLine(this);
    }

}
