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
package net.brlns.gdownloader.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.settings.filters.AbstractUrlFilter;
import net.brlns.gdownloader.settings.filters.GenericFilter;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Converter
@Slf4j
public class UrlFilterConverter implements AttributeConverter<AbstractUrlFilter, String> {

    @Override
    public String convertToDatabaseColumn(AbstractUrlFilter filter) {
        return filter != null ? filter.getId() : null;
    }

    @Override
    public AbstractUrlFilter convertToEntityAttribute(String dbData) {
        AbstractUrlFilter filter = null;
        AbstractUrlFilter generic = null;

        for (AbstractUrlFilter filterNeedle : GDownloader.getInstance().getConfig().getUrlFilters()) {
            if (filterNeedle.getId().equals(GenericFilter.ID)) {
                generic = filterNeedle;
            }

            if (filterNeedle.getId().equals(dbData)) {
                filter = filterNeedle;
                break;
            }
        }

        if (filter == null) {
            log.error("Url filter id: {} not found, falling back to generic filter.", dbData);
            filter = generic;
        }

        if (filter == null) {
            throw new IllegalStateException("Required generic url filter was not found.");
        }

        return filter;
    }
}
