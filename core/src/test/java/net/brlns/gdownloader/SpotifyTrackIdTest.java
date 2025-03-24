package net.brlns.gdownloader;

import net.brlns.gdownloader.util.URLUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpotifyTrackIdTest {

    @Test
    void testValidTrackUrl() {
        String url = "https://open.spotify.com/track/0heJlRkloNhkrBU9ROnM9Y";
        String expectedId = "0heJlRkloNhkrBU9ROnM9Y";
        assertEquals(expectedId, URLUtils.getSpotifyTrackId(url));
    }

    @Test
    void testValidArtistUrlAndTrailingSlash() {
        String url = "https://open.spotify.com/artist/6NWtt9pNOL2Gx7kBykdE5x/";
        String expectedId = "6NWtt9pNOL2Gx7kBykdE5x";
        assertEquals(expectedId, URLUtils.getSpotifyTrackId(url));
    }

    @Test
    void testValidAlbumUrl() {
        String url = "https://open.spotify.com/album/6qpMWeSSJDQci7tKsvvP4L/<>?BDWYDGHNbhnbKeyboardsmash12345";
        String expectedId = "6qpMWeSSJDQci7tKsvvP4L";
        assertEquals(expectedId, URLUtils.getSpotifyTrackId(url));
    }

    @Test
    void testUrlWithQueryParameters() {
        String url = "https://open.spotify.com/track/0heJlRkloNhkrBU9ROnM9Y?si=bananas";
        String expectedId = "0heJlRkloNhkrBU9ROnM9Y";
        assertEquals(expectedId, URLUtils.getSpotifyTrackId(url));
    }

    @Test
    void testUrlWithTrailingSlash() {
        String url = "https://open.spotify.com/track/0heJlRkloNhkrBU9ROnM9Y/";
        String expectedId = "0heJlRkloNhkrBU9ROnM9Y";
        assertEquals(expectedId, URLUtils.getSpotifyTrackId(url));
    }

    @Test
    void testUrlWithQueryParametersAndTrailingSlash() {
        String url = "https://open.spotify.com/track/0heJlRkloNhkrBU9ROnM9Y/?si=tomatoes";
        String expectedId = "0heJlRkloNhkrBU9ROnM9Y";
        assertEquals(expectedId, URLUtils.getSpotifyTrackId(url));
    }

    @Test
    void testInvalidUrl() {
        String url = "https://open.spotify.com/user/hstr0100";
        assertNull(URLUtils.getSpotifyTrackId(url));
    }

    @Test
    void testMalformedUrl() {
        String url = "https://open.spotify.com/";
        assertNull(URLUtils.getSpotifyTrackId(url));
    }

    @Test
    void testEmptyUrl() {
        String url = "";
        assertNull(URLUtils.getSpotifyTrackId(url));
    }
}
