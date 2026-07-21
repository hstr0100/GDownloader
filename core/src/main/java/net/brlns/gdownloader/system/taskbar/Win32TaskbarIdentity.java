/*
 * Copyright (C) 2026 @hstr0100
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
package net.brlns.gdownloader.system.taskbar;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.awt.Window;
import java.io.File;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.brlns.gdownloader.GDownloader;
import net.brlns.gdownloader.updater.UpdaterBootstrap;

/**
 * Gives us a stable AppUserModelID so Windows stops deriving
 * one from the OTA's ever-changing path for taskbar pinning.
 *
 * Java does not expose the necessary interfaces for this, so
 * we're using JNA to set IPropertyStore COM properties on the
 * window handle.
 *
 * @author Gabriel / hstr0100 / vertx010
 */
@Slf4j
public final class Win32TaskbarIdentity {

    private Win32TaskbarIdentity() {

    }

    public static void apply(Window window, String relaunchCommand, String executablePath) {
        if (!GDownloader.isWindows() || relaunchCommand == null) {
            return;
        }

        try {
            if (!window.isDisplayable()) {
                window.addNotify();
            }

            HWND hwnd = new HWND(Native.getComponentPointer(window));
            if (Pointer.nativeValue(hwnd.getPointer()) == 0) {
                log.warn("Could not resolve a native window handle, skipping taskbar identity change.");
                return;
            }

            boolean weInitializedCom = initializeCom();
            try {
                applyToWindow(hwnd, relaunchCommand, executablePath);
            } finally {
                if (weInitializedCom) {
                    Ole32.INSTANCE.CoUninitialize();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to apply Win32 taskbar identity", e);
        }
    }

    private static boolean initializeCom() {
        HRESULT hr = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED);
        int code = hr.intValue();

        if (code == WinError.RPC_E_CHANGED_MODE) {
            log.debug("COM already initialized on this thread with a different concurrency model");
            return false;
        }

        if (!COMUtils.SUCCEEDED(hr)) {
            log.warn("CoInitializeEx failed with 0x{}", Integer.toHexString(code));
            return false;
        }

        // S_FALSE = COM already initialized.
        return code != COMUtils.S_FALSE;
    }

    private static void applyToWindow(HWND hwnd, String relaunchCommand, String executablePath) {
        PointerByReference ppv = new PointerByReference();
        REFIID refiid = new REFIID(IPropertyStore.IID_IPROPERTYSTORE);

        HRESULT hr = Shell32Ext.INSTANCE.SHGetPropertyStoreForWindow(hwnd, refiid, ppv);
        if (!COMUtils.SUCCEEDED(hr)) {
            log.warn("SHGetPropertyStoreForWindow failed with 0x{}", Integer.toHexString(hr.intValue()));
            return;
        }

        IPropertyStore propertyStore = new IPropertyStore(ppv.getValue());
        try {
            String appUserModelId;

            String launcher = GDownloader.getLauncher();
            boolean isJarLaunch = launcher != null
                ? launcher.endsWith(".jar")
                : UpdaterBootstrap.getJarLocation() != null;

            if (GDownloader.isPortable()) {
                appUserModelId = "net.brlns.GDownloader.PortableBuild";
            } else if (isJarLaunch) {
                appUserModelId = "net.brlns.GDownloader.Jar";
            } else {
                appUserModelId = launcher != null ? launcher : System.getProperty("jpackage.app-path");

                if (appUserModelId == null) {
                    log.warn("Not running from jpackage. Aborted.");

                    return;
                }
            }

            // Relaunch properties must be set before AppUserModel.ID, per MSDN.
            setStringValue(propertyStore, AppUserModelKeys.PKEY_AppUserModel_RelaunchCommand, relaunchCommand);

            String iconResource = buildIconResource(executablePath);
            if (iconResource != null) {
                setStringValue(propertyStore, AppUserModelKeys.PKEY_AppUserModel_RelaunchIconResource, iconResource);
            } else {
                log.debug("No suitable executable found to source RelaunchIconResource from.");
            }

            setStringValue(propertyStore, AppUserModelKeys.PKEY_AppUserModel_ID, appUserModelId);

            HRESULT commitResult = propertyStore.commit();
            if (!COMUtils.SUCCEEDED(commitResult)) {
                log.warn("IPropertyStore::Commit failed with 0x{}", Integer.toHexString(commitResult.intValue()));
            } else {
                log.info("Applied taskbar identity, relaunch command: {}", relaunchCommand);
            }
        } finally {
            propertyStore.release();
        }
    }

    private static String buildIconResource(String executablePath) {
        if (executablePath == null || !executablePath.toLowerCase().endsWith(".exe")) {
            log.warn("RelaunchIconResource skipped: executablePath is null or not .exe: {}", executablePath);

            return null;
        }

        File exeFile = new File(executablePath).getAbsoluteFile();
        if (!exeFile.isFile()) {
            log.warn("RelaunchIconResource skipped: file does not exist at {}", exeFile);

            return null;
        }

        return exeFile.getAbsolutePath() + ",0";
    }

    private static void setStringValue(IPropertyStore propertyStore, PROPERTYKEY.ByReference key, String value) {
        PROPVARIANT.ByReference propVariant = PROPVARIANT.forString(value);
        try {
            HRESULT hr = propertyStore.setValue(key, propVariant);

            if (!COMUtils.SUCCEEDED(hr)) {
                log.warn("IPropertyStore::SetValue failed with 0x{}", Integer.toHexString(hr.intValue()));
            }
        } finally {
            Ole32Ext.INSTANCE.PropVariantClear(propVariant);
        }
    }

    /**
     * Property keys for System.AppUserModel.
     */
    public static final class AppUserModelKeys {

        private static final String FMTID_APPUSERMODEL = "{9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3}";

        public static final PROPERTYKEY.ByReference PKEY_AppUserModel_ID
            = new PROPERTYKEY.ByReference(FMTID_APPUSERMODEL, 5);

        public static final PROPERTYKEY.ByReference PKEY_AppUserModel_RelaunchCommand
            = new PROPERTYKEY.ByReference(FMTID_APPUSERMODEL, 2);

        public static final PROPERTYKEY.ByReference PKEY_AppUserModel_RelaunchDisplayNameResource
            = new PROPERTYKEY.ByReference(FMTID_APPUSERMODEL, 4);

        public static final PROPERTYKEY.ByReference PKEY_AppUserModel_RelaunchIconResource
            = new PROPERTYKEY.ByReference(FMTID_APPUSERMODEL, 3);

    }

    /**
     * JNA proxy for the COM IPropertyStore interface.
     *
     * Vtable layout:
     * 0 QueryInterface
     * 1 AddRef
     * 2 Release
     * 3 GetCount
     * 4 GetAt
     * 5 GetValue
     * 6 SetValue
     * 7 Commit
     */
    public static final class IPropertyStore {

        public static final Guid.IID IID_IPROPERTYSTORE = new Guid.IID("{886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99}");

        private static final int VTBL_RELEASE = 2;
        private static final int VTBL_SET_VALUE = 6;
        private static final int VTBL_COMMIT = 7;

        private final Pointer self;
        private final Pointer vtbl;

        public IPropertyStore(Pointer selfIn) {
            self = selfIn;
            vtbl = self.getPointer(0);
        }

        public HRESULT setValue(PROPERTYKEY.ByReference key, PROPVARIANT.ByReference value) {
            return invoke(VTBL_SET_VALUE, self, key, value);
        }

        public HRESULT commit() {
            return invoke(VTBL_COMMIT, self);
        }

        public int release() {
            return vtblFunction(VTBL_RELEASE).invokeInt(new Object[] {self});
        }

        private HRESULT invoke(int vtblIndex, Object... args) {
            int hr = vtblFunction(vtblIndex).invokeInt(args);

            return new HRESULT(hr);
        }

        private Function vtblFunction(int index) {
            Pointer entry = vtbl.getPointer((long)index * Native.POINTER_SIZE);

            return Function.getFunction(entry, StdCallLibrary.STDCALL_CONVENTION);
        }
    }

    /**
     * Extended JNA mappings for ole32.
     */
    public static interface Ole32Ext extends StdCallLibrary {

        Ole32Ext INSTANCE = Native.load("ole32", Ole32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        HRESULT PropVariantClear(PROPVARIANT.ByReference pvar);
    }

    /**
     * JNA mapping for the Win32 PROPERTYKEY structure.
     */
    public static class PROPERTYKEY extends Structure {

        public Guid.GUID fmtid;
        public int pid;

        public PROPERTYKEY(String fmtidString, int pidIn) {
            super();

            fmtid = new Guid.GUID(fmtidString);
            pid = pidIn;

            write();
        }

        @Override
        protected List<String> getFieldOrder() {
            return List.of("fmtid", "pid");
        }

        public static class ByReference extends PROPERTYKEY implements Structure.ByReference {

            public ByReference(String fmtidString, int pid) {
                super(fmtidString, pid);
            }
        }
    }

    /**
     * JNA mapping for the Win32 PROPVARIANT structure.
     */
    public static class PROPVARIANT extends Structure {

        public static final short VT_LPWSTR = 31;

        public short vt;
        public short wReserved1;
        public short wReserved2;
        public short wReserved3;
        public Pointer pwszVal;

        @Override
        protected List<String> getFieldOrder() {
            return List.of("vt", "wReserved1", "wReserved2", "wReserved3", "pwszVal");
        }

        public static ByReference forString(String value) {
            ByReference propVariant = new ByReference();
            propVariant.vt = VT_LPWSTR;

            int byteLength = (value.length() + 1) * Native.WCHAR_SIZE;
            Pointer nativeString = Ole32.INSTANCE.CoTaskMemAlloc(byteLength);
            nativeString.setWideString(0, value);

            propVariant.pwszVal = nativeString;
            propVariant.write();

            return propVariant;
        }

        public static class ByReference extends PROPVARIANT implements Structure.ByReference {

        }
    }

    /**
     * Extended JNA mappings for SHGetPropertyStoreForWindow.
     */
    public interface Shell32Ext extends StdCallLibrary {

        Shell32Ext INSTANCE = Native.load("shell32", Shell32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        HRESULT SHGetPropertyStoreForWindow(HWND hwnd, REFIID riid, PointerByReference ppv);
    }

}
