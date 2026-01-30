package net.brlns.gdownloader;

import net.brlns.gdownloader.filters.CrunchyrollFilter;
import net.brlns.gdownloader.filters.DropoutFilter;
import net.brlns.gdownloader.filters.FacebookFilter;
import net.brlns.gdownloader.filters.GenericFilter;
import net.brlns.gdownloader.filters.RedditFilter;
import net.brlns.gdownloader.filters.TwitchFilter;
import net.brlns.gdownloader.filters.XFilter;
import net.brlns.gdownloader.filters.YoutubeFilter;
import net.brlns.gdownloader.filters.YoutubePlaylistFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlFilterTest {

    @Test
    void testYoutube() {
        YoutubeFilter filter = new YoutubeFilter();
        // Regular video URLs
        assertTrue(filter.matches("https://www.youtube.com/watch?v=LV2-SY36Ss8"));
        assertTrue(filter.matches("https://youtube.com/watch?v=LV2-SY36Ss8"));
        assertTrue(filter.matches("https://youtu.be/dQw4w9WgXcQ"));
        assertTrue(filter.matches("https://www.youtube.com/embed/dQw4w9WgXcQ"));
        assertTrue(filter.matches("https://www.youtube.com/v/dQw4w9WgXcQ"));

        // Playlist URLs
        assertFalse(filter.matches("https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"));
        assertFalse(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"));
        assertFalse(filter.matches("https://www.youtube.com/watch?v=M494Ty2GlOA&list=PLDOjCqYj3ys3TEe8HCR7_cYH7X7dU28_B&index=21"));

        // Channel URLs
        assertFalse(filter.matches("https://www.youtube.com/channel/UCY8iijN1AkyDCh1Z9akcqUA"));
        assertFalse(filter.matches("https://youtube.com/channel/UCY8iijN1AkyDCh1Z9akcqUA"));

        // Custom channel URLs
        assertFalse(filter.matches("https://www.youtube.com/c/@funkyblackcat"));
        assertFalse(filter.matches("https://youtube.com/c/@funkyblackcat"));

        // User URLs
        assertFalse(filter.matches("https://www.youtube.com/user/funkyblackcat"));
        assertFalse(filter.matches("https://youtube.com/user/funkyblackca"));

        // Handle URLs
        assertFalse(filter.matches("https://www.youtube.com/@funkyblackcat"));
        assertFalse(filter.matches("https://youtube.com/@funkyblackcat"));

        // Live URLs (Unsupported)
        assertFalse(filter.matches("https://www.youtube.com/live/dQw4w9WgXcQ"));
        assertFalse(filter.matches("https://youtube.com/live/dQw4w9WgXcQ"));

        // Non-YouTube URLs
        assertFalse(filter.matches("https://www.example.com"));
        assertFalse(filter.matches("https://vimeo.com/12345"));
    }

    @Test
    void testYoutubePlaylist() {
        YoutubePlaylistFilter filter = new YoutubePlaylistFilter();

        // Regular video URLs
        assertFalse(filter.matches("https://www.youtube.com/watch?v=LV2-SY36Ss8"));
        assertFalse(filter.matches("https://youtube.com/watch?v=LV2-SY36Ss8"));
        assertFalse(filter.matches("https://youtu.be/dQw4w9WgXcQ"));
        assertFalse(filter.matches("https://www.youtube.com/embed/dQw4w9WgXcQ"));
        assertFalse(filter.matches("https://www.youtube.com/v/dQw4w9WgXcQ"));
        assertFalse(filter.matches("youtu.be/dQw4w9WgXcQ"));

        // Playlist URLs
        assertTrue(filter.matches("https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"));
        assertTrue(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"));
        assertTrue(filter.matches("https://www.youtube.com/watch?v=M494Ty2GlOA&list=PLDOjCqYj3ys3TEe8HCR7_cYH7X7dU28_B&index=21"));
        assertTrue(filter.matches("http://youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"));

        // Channel URLs
        assertTrue(filter.matches("https://www.youtube.com/channel/UCY8iijN1AkyDCh1Z9akcqUA"));
        assertTrue(filter.matches("https://youtube.com/channel/UCY8iijN1AkyDCh1Z9akcqUA"));
        assertTrue(filter.matches("http://www.youtube.com/channel/UCY8iijN1AkyDCh1Z9akcqUA"));

        // Custom channel URLs
        assertTrue(filter.matches("https://www.youtube.com/c/funkyblackcat"));
        assertTrue(filter.matches("https://youtube.com/c/funkyblackcat"));

        // User URLs
        assertTrue(filter.matches("https://www.youtube.com/user/funkyblackcat"));
        assertTrue(filter.matches("https://youtube.com/user/funkyblackcat"));

        // Handle URLs
        assertTrue(filter.matches("https://www.youtube.com/@funkyblackcat"));
        assertTrue(filter.matches("https://youtube.com/@funkyblackcat"));
        assertTrue(filter.matches("https://www.youtube.com/@funkyblackcat/videos"));

        // Regular video URLs
        assertFalse(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(filter.matches("youtu.be/dQw4w9WgXcQ"));
        assertFalse(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=youtu.be"));

        // Live URLs (Unsupported)
        assertFalse(filter.matches("https://www.youtube.com/live/dQw4w9WgXcQ"));
        assertFalse(filter.matches("https://youtube.com/live/dQw4w9WgXcQ"));

        // Non-YouTube URLs
        assertFalse(filter.matches("https://www.example.com"));
        assertFalse(filter.matches("https://vimeo.com/12345"));
    }

    @Test
    void testTwitch() {
        TwitchFilter filter = new TwitchFilter();
        assertTrue(filter.matches("https://www.twitch.tv/somechannel"));
        assertTrue(filter.matches("http://twitch.tv/somechannel"));
        assertFalse(filter.matches("https://www.youtube.com/somechannel"));
    }

    @Test
    void testFacebook() {
        FacebookFilter filter = new FacebookFilter();
        assertTrue(filter.matches("https://www.facebook.com/somepage"));
        assertTrue(filter.matches("http://facebook.com/somepage"));
        assertFalse(filter.matches("https://www.instagram.com/somepage"));
    }

    @Test
    void testX() {
        XFilter filter = new XFilter();
        assertTrue(filter.matches("https://www.twitter.com/someuser"));
        assertTrue(filter.matches("https://twitter.com/someuser"));
        assertTrue(filter.matches("https://www.x.com/someuser"));
        assertTrue(filter.matches("https://x.com/someuser"));
        assertFalse(filter.matches("https://www.example.com"));
    }

    @Test
    void testCrunchyroll() {
        CrunchyrollFilter filter = new CrunchyrollFilter();
        assertTrue(filter.matches("https://www.crunchyroll.com/some-show"));
        assertTrue(filter.matches("http://crunchyroll.com/some-show"));
        assertFalse(filter.matches("https://www.example.com"));
    }

    @Test
    void testDropout() {
        DropoutFilter filter = new DropoutFilter();
        assertTrue(filter.matches("dropout.tv/some-show"));
        assertTrue(filter.matches("https://dropout.tv/some-show"));
        assertTrue(filter.matches("https://www.dropout.tv/some-show"));
        assertFalse(filter.matches("https://www.example.com"));
    }

    @Test
    void testReddit() {
        RedditFilter filter = new RedditFilter();
        assertTrue(filter.matches("reddit.com/r/somesubreddit"));
        assertTrue(filter.matches("www.reddit.com/r/somesubreddit"));
        assertTrue(filter.matches("https://www.reddit.com/r/somesubreddit"));
        assertTrue(filter.matches("https://old.reddit.com/r/somesubreddit"));
        assertTrue(filter.matches("https://new.reddit.com/r/somesubreddit"));
        assertFalse(filter.matches("https://www.facebook.com/somepage"));
    }

    @Test
    void testGeneric() {
        GenericFilter filter = new GenericFilter();
        assertFalse(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(filter.matches("https://www.example.com"));
    }

    @Test
    void testIsYoutubeChannel() {
        assertTrue(YoutubeFilter.isYoutubeChannel("https://www.youtube.com/@somechannel"));
        assertTrue(YoutubeFilter.isYoutubeChannel("https://www.youtube.com/channel/UC123456"));
        assertFalse(YoutubeFilter.isYoutubeChannel("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    void testEdgeCases() {
        YoutubeFilter filter = new YoutubeFilter();
        assertFalse(filter.matches(""));

        NullPointerException exception = assertThrows(NullPointerException.class, () -> filter.matches(null));
        assertNotNull(exception);
    }
}
