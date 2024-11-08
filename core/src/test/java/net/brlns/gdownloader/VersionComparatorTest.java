package net.brlns.gdownloader;

import net.brlns.gdownloader.util.LockUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionComparatorTest {

    @Test
    void testVersionIsNewer() {
        assertTrue(LockUtils.isVersionNewer("1.0.12", "1.0.13"));
        assertTrue(LockUtils.isVersionNewer("1.2.1", "1.2.222"));
        assertTrue(LockUtils.isVersionNewer("10.0.2", "10.1.0"));
        assertTrue(LockUtils.isVersionNewer("1.0.11", "1.1.0"));
        assertTrue(LockUtils.isVersionNewer("10.0.2", "10.1.2-dev"));
    }

    @Test
    void testVersionIsNotNewer() {
        assertFalse(LockUtils.isVersionNewer("1.2.222", "1.2.222"));
        assertFalse(LockUtils.isVersionNewer("10.1.0", "10.1.0"));
    }

    @Test
    void testVersionIsOlder() {
        assertFalse(LockUtils.isVersionNewer("1.0.13", "1.0.12"));
        assertFalse(LockUtils.isVersionNewer("1.2.1", "1.2.0"));
        assertFalse(LockUtils.isVersionNewer("1.0.10", "1.0.9"));
        assertFalse(LockUtils.isVersionNewer("1.344.10", "1.12.10"));
    }

    @Test
    void testDifferentLengthVersions() {
        assertTrue(LockUtils.isVersionNewer("1.2", "1.2.1"));
        assertTrue(LockUtils.isVersionNewer("1.0", "1.0.0.1"));
        assertFalse(LockUtils.isVersionNewer("1.0.0.1", "1.0"));
    }
}
