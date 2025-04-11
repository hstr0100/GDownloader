package net.brlns.gdownloader;

import java.io.File;
import net.brlns.gdownloader.util.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FileDerivationTest {

    @Test
    public void testBasicFileWithExtension() {
        File input = new File("/a/b/c.txt");
        File output = FileUtils.deriveFile(input, ".suffix", null);
        assertEquals(new File("/a/b/c.suffix.txt"), output);
    }

    @Test
    public void testFileWithoutExtension() {
        File input = new File("/a/b/c");
        File output = FileUtils.deriveFile(input, ".suffix", null);
        assertEquals(new File("/a/b/c.suffix"), output);
    }

    @Test
    public void testFileEndingWithDot() {
        File input = new File("/a/b/c.");
        File output = FileUtils.deriveFile(input, ".suffix", null);
        assertEquals(new File("/a/b/c.suffix"), output);
    }

    @Test
    public void testFileWithCustomExtension() {
        File input = new File("/a/b/c.txt");
        File output = FileUtils.deriveFile(input, ".suffix", "mp4");
        assertEquals(new File("/a/b/c.suffix.mp4"), output);
    }

    @Test
    public void testFileWithMichaelsoftBinbowsPath() {
        File input = new File("C:\\a\\b\\c.txt");
        File output = FileUtils.deriveFile(input, ".suffix", "mp4");
        assertEquals(new File("C:/a/b/c.suffix.mp4"), output);
    }

    @Test
    public void testFileWithCustomExtensionAndNoSuffix() {
        File input = new File("/a/b/c.txt");
        File output = FileUtils.deriveFile(input, "", "mp4");
        assertEquals(new File("/a/b/c.mp4"), output);
    }

    @Test
    public void testFileWithCustomExtensionAndDifferentSuffix() {
        File input = new File("/a/b.txt");
        File output = FileUtils.deriveFile(input, "ananas", "mp4");
        assertEquals(new File("/a/bananas.mp4"), output);
    }

    @Test
    public void testFileWithNoName() {
        File input = new File("/a/b/.txt");
        File output = FileUtils.deriveFile(input, "tomatoes", "mp4");
        assertEquals(new File("/a/b/tomatoes.mp4"), output);
    }

    @Test
    public void testFileWithDoubleDots() {
        File input = new File("/a/b/c..txt");
        File output = FileUtils.deriveFile(input, ".suffix", null);
        assertEquals(new File("/a/b/c..suffix.txt"), output);
    }

    @Test
    public void testFileWithEmptyExtension() {
        File input = new File("/a/b/c.");
        File output = FileUtils.deriveFile(input, ".suffix", "");
        assertEquals(new File("/a/b/c.suffix"), output);
    }

    @Test
    public void testFileInRootDirectory() {
        File input = new File("example.txt");
        File output = FileUtils.deriveFile(input, ".suffix", null);
        assertEquals(new File("./example.suffix.txt"), output);
    }

    @Test
    public void testNullExtensionWithTrailingDot() {
        File input = new File("/a/b/c.");
        File output = FileUtils.deriveFile(input, ".suffix", null);
        assertFalse(output.getName().contains(".."));
    }
}
