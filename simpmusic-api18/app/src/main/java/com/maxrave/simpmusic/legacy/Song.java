package com.maxrave.simpmusic.legacy;

import android.net.Uri;
import java.util.Locale;

final class Song {
    final long id;
    final String title;
    final String artist;
    final Uri uri;

    Song(long id, String title, String artist) {
        this.id = id;
        this.title = title == null || title.length() == 0 ? "Unknown title" : title;
        this.artist = artist == null || artist.length() == 0 ? "Unknown artist" : artist;
        this.uri = Uri.withAppendedPath(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                Long.toString(id));
    }

    boolean matches(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return needle.length() == 0
                || title.toLowerCase(Locale.ROOT).contains(needle)
                || artist.toLowerCase(Locale.ROOT).contains(needle);
    }
}
