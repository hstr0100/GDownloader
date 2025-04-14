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
package net.brlns.gdownloader.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class CancelHook {

    private final AtomicBoolean value = new AtomicBoolean(false);

    private final List<ConditionChecker> conditions = new ArrayList<>();

    public CancelHook set(boolean newValue) {
        value.set(newValue);
        return this;
    }

    public boolean get() {
        if (value.get()) {
            return true;
        }

        for (ConditionChecker condition : conditions) {
            if (condition.shouldCancel()) {
                return true;
            }
        }

        return false;
    }

    public CancelHook addCondition(Supplier<Boolean> getter, boolean expectedValue) {
        conditions.add(new ConditionChecker(getter, expectedValue));
        return this;
    }

    public CancelHook derive(Supplier<Boolean> getter, boolean expectedValue) {
        return new CancelHook()
            .addCondition(this::get, false)
            .addCondition(getter, expectedValue);
    }

    private static class ConditionChecker {

        private final Supplier<Boolean> valueGetter;
        private final boolean expectedValue;

        public ConditionChecker(Supplier<Boolean> valueGetterIn, boolean expectedValueIn) {
            valueGetter = valueGetterIn;
            expectedValue = expectedValueIn;
        }

        public boolean shouldCancel() {
            Boolean actual = valueGetter.get();
            return actual == null || actual != expectedValue;
        }
    }
}
