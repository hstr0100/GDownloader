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
package net.brlns.gdownloader.downloader.hosts;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.downloader.hosts.impl.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public class HostResolverRegistry {

    private final List<IHostResolver> resolvers;

    public HostResolverRegistry(List<IHostResolver> resolversIn) {
        resolvers = new ArrayList<>(resolversIn);
    }

    public static HostResolverRegistry createDefault() {
        List<IHostResolver> defaults = new ArrayList<>();

        defaults.add(new SunoResolver());

        return new HostResolverRegistry(defaults);
    }

    public Optional<IHostResolver> findResolver(String url, HostResolverContext context) {
        if (context.getSettings() != null && !context.getSettings().isEnabled()) {
            return Optional.empty();
        }

        for (IHostResolver resolver : resolvers) {
            try {
                if (resolver.canHandle(url)) {
                    if (!resolver.isEnabled(context)) {
                        log.debug("Resolver {} matches {} but is disabled/unconfigured, skipping",
                            resolver.getId(), url);

                        continue;
                    }

                    return Optional.of(resolver);
                }
            } catch (Exception e) {
                log.warn("Resolver {} threw while matching {}: {}", resolver.getId(), url, e.getMessage());
            }
        }

        return Optional.empty();
    }

    public List<IHostResolver> getResolvers() {
        return Collections.unmodifiableList(resolvers);
    }

    @Nullable
    public IHostResolver getById(String id) {
        for (IHostResolver resolver : resolvers) {
            if (resolver.getId().equalsIgnoreCase(id)) {
                return resolver;
            }
        }

        return null;
    }
}
