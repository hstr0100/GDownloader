
import net.brlns.gdownloader.updater.UpdaterBootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VersionComparatorTest{

    @Test
    public void testVersionIsNewer(){
        assertTrue(UpdaterBootstrap.isVersionNewer("1.0.10", "1.0.11"));
        assertTrue(UpdaterBootstrap.isVersionNewer("1.0.12", "1.0.13"));
        assertTrue(UpdaterBootstrap.isVersionNewer("1.2.1", "1.2.222"));
        assertTrue(UpdaterBootstrap.isVersionNewer("10.0.2", "10.1.0"));
        assertTrue(UpdaterBootstrap.isVersionNewer("1.0.11", "1.1.0"));
        assertTrue(UpdaterBootstrap.isVersionNewer("10.0.2", "10.1.2-dev"));

        for(int i = 11; i < 30; i++){
            assertTrue(UpdaterBootstrap.isVersionNewer("1.0.10", "1.0." + i));
            assertTrue(UpdaterBootstrap.isVersionNewer("1.0.10", i + ".0.0"));
        }
    }

    @Test
    public void testVersionIsNotNewer(){
        assertFalse(UpdaterBootstrap.isVersionNewer("1.0.11", "1.0.10"));
        assertFalse(UpdaterBootstrap.isVersionNewer("1.0.13", "1.0.12"));
        assertFalse(UpdaterBootstrap.isVersionNewer("1.2.222", "1.2.222"));
        assertFalse(UpdaterBootstrap.isVersionNewer("10.1.0", "10.1.0"));
    }

    @Test
    public void testVersionIsOlder(){
        assertFalse(UpdaterBootstrap.isVersionNewer("1.2.1", "1.2.0"));
        assertFalse(UpdaterBootstrap.isVersionNewer("1.0.10", "1.0.9"));
        assertFalse(UpdaterBootstrap.isVersionNewer("1.344.10", "1.12.10"));
    }

    @Test
    public void testDifferentLengthVersions(){
        assertTrue(UpdaterBootstrap.isVersionNewer("1.2", "1.2.1"));
        assertTrue(UpdaterBootstrap.isVersionNewer("1.0", "1.0.0.1"));
        assertFalse(UpdaterBootstrap.isVersionNewer("1.0.0.1", "1.0"));
    }
}
