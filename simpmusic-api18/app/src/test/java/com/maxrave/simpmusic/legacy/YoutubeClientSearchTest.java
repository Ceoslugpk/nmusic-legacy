package com.maxrave.simpmusic.legacy;

import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class YoutubeClientSearchTest {
    @Test public void readsCurrentMusicRowsAndDeduplicatesVideos() throws Exception {
        String response = "{\"contents\":{\"musicShelfRenderer\":{\"contents\":["
                + row("song-1", "First Song", "First Artist") + ","
                + row("song-1", "First Song", "First Artist") + "]}}}";
        ArrayList<Song> songs = YoutubeClient.parseSearchResults(response);
        assertEquals(1, songs.size());
        assertEquals("First Song", songs.get(0).title);
        assertEquals("First Artist", songs.get(0).artist);
        assertEquals("https://www.youtube.com/watch?v=song-1", songs.get(0).youtubeUrl);
        assertTrue(songs.get(0).online);
    }

    @Test public void readsVideoIdFromCurrentPlayOverlay() throws Exception {
        String response = "{\"musicResponsiveListItemRenderer\":{"
                + "\"overlay\":{\"musicItemThumbnailOverlayRenderer\":{\"content\":{"
                + "\"musicPlayButtonRenderer\":{\"playNavigationEndpoint\":{"
                + "\"watchEndpoint\":{\"videoId\":\"overlay-id\"}}}}}},"
                + "\"flexColumns\":[{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{"
                + "\"runs\":[{\"text\":\"Overlay Track\"}]}}}]}}";
        ArrayList<Song> songs = YoutubeClient.parseSearchResults(response);
        assertEquals(1, songs.size());
        assertEquals("Overlay Track", songs.get(0).title);
        assertEquals("https://www.youtube.com/watch?v=overlay-id", songs.get(0).youtubeUrl);
    }

    @Test public void usesPlayerClientUserAgentForAudioUrls() throws Exception {
        assertTrue(YoutubeClient.mediaUserAgent("https://audio.example/test?c=ANDROID")
                .startsWith("com.google.android.youtube/"));
        assertTrue(YoutubeClient.mediaUserAgent("https://audio.example/test?c=IOS")
                .startsWith("com.google.ios.youtube/"));
    }

    private static String row(String id, String title, String artist) {
        return "{\"musicResponsiveListItemRenderer\":{"
                + "\"playlistItemData\":{\"videoId\":\"" + id + "\"},"
                + "\"flexColumns\":["
                + "{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{"
                + "\"runs\":[{\"text\":\"" + title + "\"}] } }},"
                + "{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{"
                + "\"runs\":[{\"text\":\"" + artist + "\",\"navigationEndpoint\":{"
                + "\"browseEndpoint\":{\"browseId\":\"artist-id\"}}}]}}}]}}";
    }
}
