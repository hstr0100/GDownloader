package net.brlns.gdownloader;

import java.util.concurrent.TimeUnit;
import net.brlns.gdownloader.util.collection.ExpiringSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

// Mac JVM appears to have significant issues keeping up with precise timings.
@DisabledOnOs(OS.MAC)
class ExpiringSetTest {

    private ExpiringSet<String> expiringSet;

    @BeforeEach
    void setUp() {
        expiringSet = new ExpiringSet<>(TimeUnit.MILLISECONDS, 100);
    }

    @Test
    void testAddAndContains() {
        expiringSet.add("item1");
        assertTrue(expiringSet.contains("item1"), "Item should be present");
    }

    @Test
    void testExpiration() throws InterruptedException {
        expiringSet.add("item1");

        assertTrue(expiringSet.contains("item1"), "Item should be present");

        Thread.sleep(200);

        assertFalse(expiringSet.contains("item1"), "Item should expire");
    }

    @Test
    void testRemove() {
        expiringSet.add("item1");
        assertTrue(expiringSet.contains("item1"), "Item should be present");

        boolean removed = expiringSet.remove("item1");
        assertTrue(removed, "Item should be removed");
        assertFalse(expiringSet.contains("item1"), "Item should no longer be present");
    }

    @Test
    void testSize() {
        expiringSet.add("item1");
        expiringSet.add("item2");
        assertEquals(2, expiringSet.size(), "Size should be 2");

        expiringSet.remove("item1");
        assertEquals(1, expiringSet.size(), "Size should be 1");

        expiringSet.remove("item2");
        assertEquals(0, expiringSet.size(), "Size should be 0");
    }

    @Test
    void testExpirationDoesNotAffectUnexpiredItems() throws InterruptedException {
        expiringSet.add("item1");
        Thread.sleep(40);
        expiringSet.add("item2");

        assertTrue(expiringSet.contains("item1"), "Item1 should still be present");
        assertTrue(expiringSet.contains("item2"), "Item2 should still be present");

        Thread.sleep(80);

        assertFalse(expiringSet.contains("item1"), "Item1 should have expired");
        assertTrue(expiringSet.contains("item2"), "Item2 should still be present");
    }

    @Test
    void testRemoveExpiredEntries() throws InterruptedException {
        expiringSet.add("item1");
        Thread.sleep(200);

        assertEquals(0, expiringSet.size(), "Set size should be 0 after expiration");
    }

    @Test
    void testNoDuplicateEntries() {
        expiringSet.add("item1");
        expiringSet.add("item1");
        assertEquals(1, expiringSet.size(), "Duplicate entries should not increase size");
    }

}
