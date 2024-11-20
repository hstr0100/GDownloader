package net.brlns.gdownloader;

import java.io.File;
import net.brlns.gdownloader.util.URLUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlToFilePathTest {

    @Test
    void testValidUrlWithTrailingSlash() {
        String url = "https://www.example.com/path/to/directory/";
        String expected = "example.com" + File.separator + "path" + File.separator + "to" + File.separator + "directory";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }

    @Test
    void testValidUrlWithoutTrailingSlash() {
        String url = "https://www.example.com/path/to/directory";
        String expected = "example.com" + File.separator + "path" + File.separator + "to" + File.separator + "directory";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }

    @Test
    void testValidHttpUrl() {
        String url = "http://www.example.com/path/to/directory";
        String expected = "example.com" + File.separator + "path" + File.separator + "to" + File.separator + "directory";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }

    @Test
    void testValidUrlWithoutProtocol() {
        String url = "www.example.com/path/to/directory";
        String result = URLUtils.getDirectoryPath(url);
        assertNull(result);
    }

    @Test
    void testValidUrlWithFileExtension() {
        String url = "https://www.example.com/path/to/file.txt";
        String expected = "example.com" + File.separator + "path" + File.separator + "to";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }

    @Test
    void testUrlWithNoPath() {
        String url = "https://www.example.com";
        String expected = "example.com";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }

    @Test
    void testUrlWithEmptyPath() {
        String url = "https://www.example.com/";
        String expected = "example.com";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }

    @Test
    void testFileUri() {
        String url = "file:///path/to/directory";
        String result = URLUtils.getDirectoryPath(url);
        assertNull(result);
    }

    @Test
    void testUrlWithSubdomain() {
        String url = "https://bananas.example.com/path/to/directory";
        String expected = "bananas.example.com" + File.separator + "path" + File.separator + "to" + File.separator + "directory";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }

    @Test
    void testUrlWithMultipleSlashes() {
        String url = "https://www.example.com///path///to///directory///";
        String expected = "example.com" + File.separator + "path" + File.separator + "to" + File.separator + "directory";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }

    @Test
    void testUrlWithNoProtocol() {
        String url = "www.example.com/path/to/directory";
        String result = URLUtils.getDirectoryPath(url);
        assertNull(result);
    }

    @Test
    void testEmptyUrl() {
        String url = "";
        String result = URLUtils.getDirectoryPath(url);
        assertNull(result);
    }

    @Test
    void testBestUrl() {
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        String expected = "youtube.com" + File.separator + "watch";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }

    @Test
    void testRedditUrl() {
        String url = "https://www.reddit.com/r/funny/";
        String expected = "reddit.com" + File.separator + "r" + File.separator + "funny";
        String result = URLUtils.getDirectoryPath(url);
        assertEquals(expected, result);
    }
}
