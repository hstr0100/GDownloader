package net.brlns.gdownloader.settings.enums;

import lombok.Getter;
import static net.brlns.gdownloader.Language.*;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Getter
public enum BrowserEnum implements SettingsEnum{
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
