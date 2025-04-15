package net.brlns.gdownloader;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import net.brlns.gdownloader.settings.enums.LanguageEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LanguageTest {

    @Test
    void testAllLanguageKeysConsistent() {
        Set<Locale> allLocales = Arrays.stream(LanguageEnum.values())
            .map(LanguageEnum::getLocale)
            .collect(Collectors.toSet());

        Set<ResourceBundle> bundles = allLocales.stream()
            .map(locale -> ResourceBundle.getBundle("lang/language", locale))
            .collect(Collectors.toSet());

        Set<String> allKeys = new HashSet<>();
        bundles.forEach(bundle -> allKeys.addAll(bundle.keySet()));

        for (ResourceBundle bundle : bundles) {
            Set<String> bundleKeys = bundle.keySet();

            Set<String> missingKeys = new HashSet<>(allKeys);
            missingKeys.removeAll(bundleKeys);
            assertTrue(missingKeys.isEmpty(),
                String.format("Language %s is missing keys: %s",
                    bundle.getLocale(), missingKeys));

            Set<String> extraKeys = new HashSet<>(bundleKeys);
            extraKeys.removeAll(allKeys);
            assertTrue(extraKeys.isEmpty(),
                String.format("Language %s has extra keys: %s",
                    bundle.getLocale(), extraKeys));
        }
    }
}
