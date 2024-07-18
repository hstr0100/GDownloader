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
package net.brlns.gdownloader.settings.enums;

import lombok.Getter;
import static net.brlns.gdownloader.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum BrowserEnum implements ISettingsEnum{
    UNSET("", ""),
    CHROME("chrome", "Google Chrome", "chromium"),
    FIREFOX("firefox", "Firefox"),
    EDGE("edge", "Microsoft Edge"),
    OPERA("opera", "Opera"),
    SAFARI("safari", "Safari"),
    BRAVE("brave", "Brave");

    private final String name;
    private final String[] aliases;

    private final String displayName;

    private BrowserEnum(String nameIn, String displayNameIn, String... aliasesIn){
        name = nameIn;
        displayName = displayNameIn;
        aliases = aliasesIn;
    }

    @Override
    public String getDisplayName(){
        return this == UNSET ? get("enums.browser.default_browser") : displayName;
    }

    @Override
    public String getTranslationKey(){
        return "";
    }

    public static BrowserEnum getBrowserForName(String name){
        for(BrowserEnum browser : BrowserEnum.values()){
            if(name.contains(browser.getName())){
                return browser;
            }

            for(String alias : browser.getAliases()){
                if(name.contains(alias)){
                    return browser;
                }
            }
        }

        return BrowserEnum.UNSET;
    }
}
