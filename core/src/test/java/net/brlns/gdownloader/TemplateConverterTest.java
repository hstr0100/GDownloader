package net.brlns.gdownloader;

import java.util.stream.Stream;
import net.brlns.gdownloader.util.TemplateConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TemplateConverterTest {

    @Test
    public void testNullInput() {
        assertNull(TemplateConverter.convertTemplateForSpotDL(null));
    }

    @Test
    public void testEmptyInput() {
        assertEquals("", TemplateConverter.convertTemplateForSpotDL(""));
    }

    @Test
    public void testInputWithoutTemplates() {
        String input = "Artist - Song";
        assertEquals(input, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @ParameterizedTest
    @MethodSource("provideBasicTemplates")
    public void testBasicTemplates(String input, String expected) {
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    private static Stream<Arguments> provideBasicTemplates() {
        return Stream.of(
            Arguments.of("%(title)s", "{title}"),
            Arguments.of("%(uploader)s", "{artists}"),
            Arguments.of("%(creator)s", "{artists}"),
            Arguments.of("%(artist)s", "{artist}"),
            Arguments.of("%(album)s", "{album}"),
            Arguments.of("%(channel)s", "{album-artist}"),
            Arguments.of("%(track)s", "{title}"),
            Arguments.of("%(genre)s", "{genre}"),
            Arguments.of("%(duration)s", "{duration}"),
            Arguments.of("%(duration_string)s", "{duration}"),
            Arguments.of("%(release_year)s", "{year}"),
            Arguments.of("%(release_date)s", "{original-date}"),
            Arguments.of("%(upload_date)s", "{original-date}"),
            Arguments.of("%(track_number)s", "{track-number}"),
            Arguments.of("%(n_entries)s", "{tracks-count}"),
            Arguments.of("%(playlist_index)s", "{list-position}"),
            Arguments.of("%(playlist_title)s", "{list-name}"),
            Arguments.of("%(playlist_count)s", "{list-length}"),
            Arguments.of("%(ext)s", "{output-ext}"),
            Arguments.of("%(disc_number)s", "{disc-number}"),
            Arguments.of("%(id)s", "{track-id}")
        );
    }

    @ParameterizedTest
    @MethodSource("provideComplexTemplates")
    public void testComplexTemplates(String input, String expected) {
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    private static Stream<Arguments> provideComplexTemplates() {
        return Stream.of(
            // Object traversal
            Arguments.of("%(tags.0)s", "{tags}"),
            Arguments.of("%(subtitles.en.-1.ext)s", "{subtitles}"),
            Arguments.of("%(id.3:7)s", "{track-id}"),
            Arguments.of("%(formats.:.format_id)s", "{formats}"),
            // Arithmetic
            Arguments.of("%(playlist_index+10)03d", "{list-position}"),
            Arguments.of("%(n_entries+1-playlist_index)d", "{tracks-count}"),
            Arguments.of("%(track_number+1)s", "{track-number}"),
            // Date formatting
            Arguments.of("%(duration>%H-%M-%S)s", "{duration}"),
            Arguments.of("%(upload_date>%Y-%m-%d)s", "{original-date}"),
            Arguments.of("%(epoch-3600>%H-%M-%S)s", "{epoch}"),
            // Alternatives
            Arguments.of("%(release_date>%Y,upload_date>%Y|Unknown)s", "{original-date}"),
            Arguments.of("%(artist,creator|Unknown)s", "{artist}"),
            // Replacement
            Arguments.of("%(chapters&has chapters|no chapters)s", "{chapters}"),
            Arguments.of("%(title&TITLE={:>20}|NO TITLE)s", "{title}"),
            // Default values
            Arguments.of("%(uploader|Unknown)s", "{artists}"),
            Arguments.of("%(album|Single)s", "{album}"),
            // Format types
            Arguments.of("%(title)s", "{title}"),
            Arguments.of("%(duration)d", "{duration}"),
            Arguments.of("%(track_number)03d", "{track-number}"),
            Arguments.of("%(playlist_count)#B", "{list-length}"),
            Arguments.of("%(formats)#j", "{formats}"),
            Arguments.of("%(title)#h", "{title}"),
            Arguments.of("%(tags)#l", "{tags}")
        );
    }

    @ParameterizedTest
    @MethodSource("provideMultipleTemplatesInOneString")
    public void testMultipleTemplatesInOneString(String input, String expected) {
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    private static Stream<Arguments> provideMultipleTemplatesInOneString() {
        return Stream.of(
            Arguments.of(
                "%(artist)s - %(title)s",
                "{artist} - {title}"
            ),
            Arguments.of(
                "%(artist)s - %(title)s.%(ext)s",
                "{artist} - {title}.{output-ext}"
            ),
            Arguments.of(
                "%(playlist_title)s/%(playlist_index)03d - %(artist)s - %(title)s",
                "{list-name}/{list-position} - {artist} - {title}"
            ),
            Arguments.of(
                "Music/%(artist)s/%(album)s/%(track_number)02d - %(title)s",
                "Music/{artist}/{album}/{track-number} - {title}"
            ),
            Arguments.of(
                "%(artist)s - %(release_year)s - %(album)s/%(track_number)02d - %(title)s",
                "{artist} - {year} - {album}/{track-number} - {title}"
            ),
            Arguments.of(
                "%(artist|Unknown)s/%(album|Unknown)s/%(track_number|00)02d - %(title|Unknown)s.%(ext)s",
                "{artist}/{album}/{track-number} - {title}.{output-ext}"
            ),
            Arguments.of(
                "%(artist)s/%(album)s/%(disc_number)s-%(track_number)s %(title)s",
                "{artist}/{album}/{disc-number}-{track-number} {title}"
            ),
            Arguments.of(
                "Playlists/%(playlist_title)s/%(playlist_index)03d - %(title)s by %(artist)s",
                "Playlists/{list-name}/{list-position} - {title} by {artist}"
            )
        );
    }

    @Test
    public void testWithMultipleFormattingOptions() {
        String input = "Music/%(artist,uploader|Unknown)s/%(album>%Y|Unknown)s/%(track_number+0)02d - %(title)s.%(ext)s";
        String expected = "Music/{artist}/{album}/{track-number} - {title}.{output-ext}";
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @Test
    public void testMixOfTextAndTemplates() {
        String input = "Downloaded on %(upload_date>%Y-%m-%d)s: '%(title)s' by %(artist)s";
        String expected = "Downloaded on {original-date}: '{title}' by {artist}";
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @Test
    public void testWithEscapedPercentages() {
        String input = "100%% quality - %(title)s by %(artist)s";
        String expected = "100%% quality - {title} by {artist}";
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @Test
    public void testWithUnrecognizedFields() {
        String input = "%(unrecognized_field)s - %(title)s";
        String expected = "{unrecognized_field} - {title}";
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @Test
    public void testWithNestedBraces() {
        String input = "%(title&TITLE={:>20})s - %(artist)s";
        String expected = "{title} - {artist}";
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @Test
    public void testWithUnmappedFields() {
        // Fields that exist in yt-dlp but have no direct mapping
        String input = "%(fulltitle)s - %(channel_id)s - %(like_count)s";
        String expected = "{fulltitle} - {channel_id} - {like_count}";
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @Test
    public void testRealWorldUseCases() {
        // Common file naming patterns
        assertEquals(
            "Music/{artist}/{album} ({year})/{track-number} - {title}",
            TemplateConverter.convertTemplateForSpotDL("Music/%(artist)s/%(album)s (%(release_year)s)/%(track_number)02d - %(title)s")
        );

        assertEquals(
            "{artists} - {title} [{track-id}]",
            TemplateConverter.convertTemplateForSpotDL("%(uploader)s - %(title)s [%(id)s]")
        );

        assertEquals(
            "{list-name}/[{list-position}] {artist} - {title}",
            TemplateConverter.convertTemplateForSpotDL("%(playlist)s/[%(playlist_index)s] %(artist)s - %(title)s")
        );

        assertEquals(
            "{artist} - {album} - {track-number} - {title}",
            TemplateConverter.convertTemplateForSpotDL("%(artist)s - %(album)s - %(track_number)s - %(title)s")
        );
    }

    @Test
    public void testWithComprehensiveFormattingOptions() {
        String input = "%(artist,uploader>%s,creator>%s|Unknown Artist)#s - %(title&T={:>20}|Unknown Title)s.%(ext)s";
        String expected = "{artist} - {title}.{output-ext}";
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @Test
    public void testMalformedTemplates() {
        // Intentionally malformed templates should be left as-is
        String input = "%(broken template - %(title)s";
        assertEquals(input, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @Test
    public void testEmptyFieldName() {
        // The empty field case %()s
        String input = "%()s - %(title)s";
        String expected = "{} - {title}";
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }

    @Test
    public void testWithCurlyBraces() {
        // Test with JSON-like structures
        String input = "%(formats.:.{format_id,height})#j";
        String expected = "{formats}";
        assertEquals(expected, TemplateConverter.convertTemplateForSpotDL(input));
    }
}
