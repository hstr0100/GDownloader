package net.brlns.gdownloader;

import java.util.concurrent.atomic.AtomicReference;
import net.brlns.gdownloader.util.FlagUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class FlagUtilTest {

    private AtomicReference<Integer> reference;

    @BeforeEach
    void setUp() {
        reference = new AtomicReference<>(0);
    }

    // set Tests
    @Test
    void set_ShouldSetSpecificBit() {
        // Set flag at position 3
        int result = FlagUtil.set(reference, 3);

        // Check return value
        assertEquals(8, result, "Return value should be 8 (binary: 1000)");

        // Check reference value was updated
        assertEquals(8, reference.get().intValue(), "Reference should be updated to 8");

        // Verify bit was actually set
        assertTrue((reference.get() & (1 << 3)) != 0, "Bit at position 3 should be set");
    }

    @Test
    void set_ShouldNotAffectOtherBits() {
        // Set some initial bits
        reference.set(0b10101010);

        // Set a new flag
        FlagUtil.set(reference, 4);

        // Expect: original value with bit 4 set
        int expected = 0b10111010; // 0b10101010 | (1 << 4)

        assertEquals(expected, reference.get().intValue(), "Only bit 4 should be changed");
    }

    @Test
    void set_WhenBitAlreadySet_ShouldNotChange() {
        // Set bit 5
        reference.set(1 << 5);

        // Try to set the same bit again
        int result = FlagUtil.set(reference, 5);

        // Should still just have bit 5 set
        assertEquals(1 << 5, result, "Value should remain unchanged");
        assertEquals(1 << 5, reference.get().intValue(), "Value should remain unchanged");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 15, 30, 31})
    void set_ValidBoundaryPositions_ShouldWork(int position) {
        // Test boundary values
        int result = FlagUtil.set(reference, position);

        // Check the specific bit was set
        assertEquals(1 << position, result, "Should set the bit at position " + position);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 32, 100})
    void set_InvalidPositions_ShouldThrowException(int position) {
        assertThrows(IllegalArgumentException.class, () -> {
            FlagUtil.set(reference, position);
        }, "Should throw exception for position " + position);
    }

    // clear Tests
    @Test
    void clear_ShouldClearSpecificBit() {
        // Set all bits to 1
        reference.set(0xFFFFFFFF);

        // Clear flag at position 3
        int result = FlagUtil.clear(reference, 3);

        assertEquals(0xFFFFFFF7, result, "Bit 3 should be cleared");
        assertEquals(0xFFFFFFF7, reference.get().intValue(), "Bit 3 should be cleared");

        // Verify bit was actually cleared
        assertFalse((reference.get() & (1 << 3)) != 0, "Bit at position 3 should be cleared");
    }

    @Test
    void clear_WhenBitAlreadyClear_ShouldNotChange() {
        // Set some bits, but not bit 7
        reference.set(0b00101010);

        // Try to clear bit 7
        int result = FlagUtil.clear(reference, 7);

        // Value should remain unchanged
        assertEquals(0b00101010, result, "Value should remain unchanged");
        assertEquals(0b00101010, reference.get().intValue(), "Value should remain unchanged");
    }

    // toggle Tests
    @Test
    void toggle_ShouldToggleBit_0to1() {
        reference.set(0);

        // Toggle bit 6
        int result = FlagUtil.toggle(reference, 6);

        // Bit 6 should now be set
        assertEquals(1 << 6, result, "Bit 6 should be toggled to 1");
        assertEquals(1 << 6, reference.get().intValue(), "Bit 6 should be toggled to 1");
    }

    @Test
    void toggle_ShouldToggleBit_1to0() {
        // Start with bit 9 set
        reference.set(1 << 9);

        // Toggle bit 9
        int result = FlagUtil.toggle(reference, 9);

        // Bit 9 should now be clear
        assertEquals(0, result, "Bit 9 should be toggled to 0");
        assertEquals(0, reference.get().intValue(), "Bit 9 should be toggled to 0");
    }

    @Test
    void toggle_ShouldOnlyAffectSpecifiedBit() {
        // Set some bits
        reference.set(0b10101010);

        // Toggle bit 0
        int result = FlagUtil.toggle(reference, 0);

        // Only bit 0 should be changed
        assertEquals(0b10101011, result, "Only bit 0 should be toggled");
        assertEquals(0b10101011, reference.get().intValue(), "Only bit 0 should be toggled");
    }

    // isSet Tests
    @Test
    void isSet_WhenFlagIsSet_ShouldReturnTrue() {
        // Set bit 12
        reference.set(1 << 12);

        // Check if bit 12 is set
        boolean isSet = FlagUtil.isSet(reference, 12);

        assertTrue(isSet, "Should return true for set bit");
    }

    @Test
    void isSet_WhenFlagIsNotSet_ShouldReturnFalse() {
        // Set bit 12 only
        reference.set(1 << 12);

        // Check if bit 11 is set (shouldn't)
        boolean isSet = FlagUtil.isSet(reference, 11);

        assertFalse(isSet, "Should return false for cleared bit");
    }

    // setFlags Tests
    @Test
    void setFlags_ShouldSetMultipleBits() {
        reference.set(0);

        // Set bits 1, 3, and 5
        int mask = (1 << 1) | (1 << 3) | (1 << 5);
        int result = FlagUtil.setFlags(reference, mask);

        assertEquals(mask, result, "Bits 1, 3, and 5 should be set");
        assertEquals(mask, reference.get().intValue(), "Bits 1, 3, and 5 should be set");
    }

    @Test
    void setFlags_WithExistingFlags_ShouldMergeCorrectly() {
        // Start with bits 2 and 4 set
        reference.set((1 << 2) | (1 << 4));

        // Set bits 1, 3, and 5
        int mask = (1 << 1) | (1 << 3) | (1 << 5);
        int result = FlagUtil.setFlags(reference, mask);

        // Expected: bits 1, 2, 3, 4, and 5 all set
        int expected = (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5);

        assertEquals(expected, result, "Bits 1, 2, 3, 4, and 5 should all be set");
        assertEquals(expected, reference.get().intValue(), "Bits 1, 2, 3, 4, and 5 should all be set");
    }

    // clearFlags Tests
    @Test
    void clearFlags_ShouldClearMultipleBits() {
        // Start with all bits set
        reference.set(0xFFFFFFFF);

        // Clear bits 2, 4, and 6
        int mask = (1 << 2) | (1 << 4) | (1 << 6);
        int result = FlagUtil.clearFlags(reference, mask);

        // Expect: all bits set except 2, 4, and 6
        int expected = 0xFFFFFFFF & ~mask;

        assertEquals(expected, result, "Bits 2, 4, and 6 should be cleared");
        assertEquals(expected, reference.get().intValue(), "Bits 2, 4, and 6 should be cleared");
    }

    @Test
    void clearFlags_WithSomeBitsAlreadyClear_ShouldWorkCorrectly() {
        // Start with some bits set
        reference.set(0b10101010);

        // Clear bits 1, 3, and 5 (bit 3 is already clear)
        int mask = (1 << 1) | (1 << 3) | (1 << 5);
        int result = FlagUtil.clearFlags(reference, mask);

        // Expect: 0b10101010 with bits 1, 3, and 5 cleared
        int expected = 0b10000000;

        assertEquals(expected, result, "Bits 1, 3, and 5 should be cleared");
        assertEquals(expected, reference.get().intValue(), "Bits 1, 3, and 5 should be cleared");
    }
}
