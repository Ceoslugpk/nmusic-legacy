package com.maxrave.simpmusic.legacy;

import android.net.Uri;
import java.util.Locale;

final class Song {
    final long id;
    final String title;
    final String artist;
    final Uri uri;
    final String youtubeUrl;
    final boolean online;

    Song(long id, String title, String artist) {
        this.id = id;
        this.title = clean(title, "Unknown title");
        this.artist = clean(artist, "Unknown artist");
        this.uri = Uri.withAppendedPath(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                Long.toString(id));
        this.youtubeUrl = null;
        this.online = false;
    }

    Song(String youtubeUrl, String title, String artist) {
        this.id = youtubeUrl == null ? 0 : youtubeUrl.hashCode();
        this.title = clean(title, "Unknown title");
        this.artist = clean(artist, "Unknown artist");
        this.uri = null;
        this.youtubeUrl = youtubeUrl;
        this.online = true;
    }

    private static String clean(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    boolean matches(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return needle.length() == 0
                || title.toLowerCase(Locale.ROOT).contains(needle)
                || artist.toLowerCase(Locale.ROOT).contains(needle);
    }
}
