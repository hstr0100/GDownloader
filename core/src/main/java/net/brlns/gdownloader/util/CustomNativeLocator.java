package net.brlns.gdownloader.util;

import com.github.kwhat.jnativehook.NativeLibraryLocator;
import com.github.kwhat.jnativehook.NativeSystem;
import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
 * Sourced from
 * https://github.com/beryx-gist/jnativehook-modular-demo/blob/main/src/main/java/org/jnativehook/example/CustomLocator.java
 */
public class CustomNativeLocator implements NativeLibraryLocator{

    @Override
    public Iterator<File> getLibraries(){
        String libName = System.getProperty("jnativehook.lib.name", "JNativeHook");

        String libNativeArch = NativeSystem.getArchitecture().toString().toLowerCase();
        String libNativeName = System
            .mapLibraryName(libName) // Get what the system "thinks" the library name should be.
            .replaceAll("\\.jnilib$", "\\.dylib"); // Hack for OS X JRE 1.6 and earlier.

        String baseDir = System.getProperty("java.home", ".") + "/bin/native-libs";
        String libFilePath = baseDir + "/" + NativeSystem.getFamily().toString().toLowerCase() + '/' + libNativeArch + '/' + libNativeName;

        File libFile = new File(libFilePath);
        if(!libFile.exists()){
            throw new RuntimeException("Unable to locate JNI library at " + libFile.getPath() + "!\n");
        }

        return List.of(libFile).iterator();
    }
}
