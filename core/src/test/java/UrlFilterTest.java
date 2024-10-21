
import net.brlns.gdownloader.settings.filters.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlFilterTest{

    @Test
    void testYoutube(){
        YoutubeFilter filter = new YoutubeFilter();
        assertTrue(filter.matches("youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(filter.matches("youtu.be/dQw4w9WgXcQ"));
        assertTrue(filter.matches("https://youtu.be/dQw4w9WgXcQ"));
        assertTrue(filter.matches("http://youtube.com/watch?v=dQw4w9WgXcQ"));
        assertTrue(filter.matches("http://youtube.com/shorts/dQw4w9WgXcQ"));
        assertFalse(filter.matches("https://www.youtube.com/live/dQw4w9WgXcQ"));
        assertFalse(filter.matches("https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"));
        assertFalse(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI&index=3"));
        assertFalse(filter.matches("https://www.example.com"));
    }

    @Test
    void testYoutubePlaylist(){
        YoutubePlaylistFilter filter = new YoutubePlaylistFilter();
        assertTrue(filter.matches("https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"));
        assertTrue(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"));
        assertTrue(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI&index=3"));
        assertFalse(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(filter.matches("youtu.be/dQw4w9WgXcQ"));
    }

    @Test
    void testTwitch(){
        TwitchFilter filter = new TwitchFilter();
        assertTrue(filter.matches("https://www.twitch.tv/somechannel"));
        assertTrue(filter.matches("http://twitch.tv/somechannel"));
        assertFalse(filter.matches("https://www.youtube.com/somechannel"));
    }

    @Test
    void testFacebook(){
        FacebookFilter filter = new FacebookFilter();
        assertTrue(filter.matches("https://www.facebook.com/somepage"));
        assertTrue(filter.matches("http://facebook.com/somepage"));
        assertFalse(filter.matches("https://www.instagram.com/somepage"));
    }

    @Test
    void testX(){
        XFilter filter = new XFilter();
        assertTrue(filter.matches("https://www.twitter.com/someuser"));
        assertTrue(filter.matches("https://twitter.com/someuser"));
        assertTrue(filter.matches("https://www.x.com/someuser"));
        assertTrue(filter.matches("https://x.com/someuser"));
        assertFalse(filter.matches("https://www.example.com"));
    }

    @Test
    void testCrunchyroll(){
        CrunchyrollFilter filter = new CrunchyrollFilter();
        assertTrue(filter.matches("https://www.crunchyroll.com/some-show"));
        assertTrue(filter.matches("http://crunchyroll.com/some-show"));
        assertFalse(filter.matches("https://www.example.com"));
    }

    @Test
    void testDropout(){
        DropoutFilter filter = new DropoutFilter();
        assertTrue(filter.matches("dropout.tv/some-show"));
        assertTrue(filter.matches("https://dropout.tv/some-show"));
        assertTrue(filter.matches("https://www.dropout.tv/some-show"));
        assertFalse(filter.matches("https://www.example.com"));
    }

    @Test
    void testReddit(){
        RedditFilter filter = new RedditFilter();
        assertTrue(filter.matches("reddit.com/r/somesubreddit"));
        assertTrue(filter.matches("www.reddit.com/r/somesubreddit"));
        assertTrue(filter.matches("https://www.reddit.com/r/somesubreddit"));
        assertTrue(filter.matches("https://old.reddit.com/r/somesubreddit"));
        assertTrue(filter.matches("https://new.reddit.com/r/somesubreddit"));
        assertFalse(filter.matches("https://www.facebook.com/somepage"));
    }

    @Test
    void testGeneric(){
        GenericFilter filter = new GenericFilter();
        assertFalse(filter.matches("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertFalse(filter.matches("https://www.example.com"));
    }

    @Test
    void testIsYoutubeChannel(){
        assertTrue(YoutubeFilter.isYoutubeChannel("https://www.youtube.com/@somechannel"));
        assertTrue(YoutubeFilter.isYoutubeChannel("https://www.youtube.com/channel/UC123456"));
        assertFalse(YoutubeFilter.isYoutubeChannel("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    void testEdgeCases(){
        YoutubeFilter filter = new YoutubeFilter();
        assertFalse(filter.matches(""));

        NullPointerException exception = assertThrows(NullPointerException.class, () -> filter.matches(null));
        assertNotNull(exception);
    }
}
