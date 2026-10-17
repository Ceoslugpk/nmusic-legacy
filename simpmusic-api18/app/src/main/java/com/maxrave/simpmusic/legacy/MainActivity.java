package com.maxrave.simpmusic.legacy;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class MainActivity extends Activity {
    private static final int REQUEST_AUDIO_PERMISSION = 18;
    private static final int REQUEST_DOWNLOAD_PERMISSION = 19;
    private final ArrayList<Song> songs = new ArrayList<Song>();
    private final ArrayList<Song> remoteSongs = new ArrayList<Song>();
    private final ArrayList<Song> visibleSongs = new ArrayList<Song>();
    private EditText search;
    private TextView nowPlaying;
    private TextView nowArtist;
    private TextView time;
    private TextView message;
    private String lastDiagnostic;
    private SeekBar seekBar;
    private Button playButton;
    private Button youtubeTab;
    private Button deviceTab;
    private SongAdapter adapter;
    private String currentUri;
    private String currentQueueKey;
    private boolean draggingSeekBar;
    private boolean receiverRegistered;
    private boolean youtubeMode;
    private Song pendingDownload;
    private AsyncTask<Void, Void, ArrayList<Song>> loadTask;

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!LocalPlaybackService.ACTION_UPDATE.equals(intent.getAction())) return;
            String title = intent.getStringExtra(LocalPlaybackService.EXTRA_TITLE);
            String artist = intent.getStringExtra(LocalPlaybackService.EXTRA_ARTIST);
            String playbackError = intent.getStringExtra(LocalPlaybackService.EXTRA_ERROR);
            currentUri = intent.getStringExtra(LocalPlaybackService.EXTRA_URI);
            boolean playing = intent.getBooleanExtra(LocalPlaybackService.EXTRA_PLAYING, false);
            int position = intent.getIntExtra(LocalPlaybackService.EXTRA_POSITION, 0);
            int duration = intent.getIntExtra(LocalPlaybackService.EXTRA_DURATION, 0);
            nowPlaying.setText(title == null ? "Nothing playing yet" : title);
            nowArtist.setText(title == null ? "Choose a song to start listening" : safeArtist(artist));
            playButton.setText(playing ? "Pause" : "Play");
            seekBar.setMax(Math.max(1, duration));
            if (!draggingSeekBar) seekBar.setProgress(Math.min(position, seekBar.getMax()));
            time.setText(formatTime(position) + " / " + formatTime(duration));
            if (playbackError != null) message.setText("Playback failed: " + playbackError);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildInterface();
        if (hasAudioPermission()) loadSongs(); else requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_AUDIO_PERMISSION);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!receiverRegistered) {
            registerReceiver(playbackReceiver, new IntentFilter(LocalPlaybackService.ACTION_UPDATE));
            receiverRegistered = true;
        }
        if (!youtubeMode && hasAudioPermission() && songs.isEmpty()) loadSongs();
        sendServiceAction(LocalPlaybackService.ACTION_GET_STATE);
    }

    @Override protected void onPause() {
        if (receiverRegistered) { unregisterReceiver(playbackReceiver); receiverRegistered = false; }
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (loadTask != null) loadTask.cancel(true);
        super.onDestroy();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            message.setText("Storage permission is needed for this action.");
            return;
        }
        if (requestCode == REQUEST_AUDIO_PERMISSION) loadSongs();
        else if (requestCode == REQUEST_DOWNLOAD_PERMISSION && pendingDownload != null) {
            Song toDownload = pendingDownload;
            pendingDownload = null;
            download(toDownload);
        }
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(8));
        root.setBackgroundColor(Color.rgb(10, 14, 13));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = new TextView(this);
        mark.setText("♪"); mark.setTextColor(Color.rgb(92, 231, 179)); mark.setTextSize(28);
        mark.setGravity(Gravity.CENTER); mark.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(44)));
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this); heading.setText("simp music"); heading.setTextColor(Color.WHITE);
        heading.setTextSize(19); heading.setTypeface(Typeface.DEFAULT_BOLD);
        TextView subheading = new TextView(this); subheading.setText("MUSIC FOR EVERYWHERE");
        subheading.setTextColor(Color.rgb(130, 151, 143)); subheading.setTextSize(9);
        brand.addView(heading); brand.addView(subheading); header.addView(brand);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(6), 0, dp(5), 0);
        searchRow.setBackground(rounded(Color.rgb(22, 30, 27), 14, Color.rgb(43, 58, 51)));
        search = new EditText(this); search.setSingleLine(true); search.setHint("Search songs, artists, albums");
        search.setTextColor(Color.WHITE); search.setHintTextColor(Color.rgb(139, 157, 149));
        search.setTextSize(15); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackgroundColor(Color.TRANSPARENT); search.setPadding(dp(11), 0, dp(6), 0);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button searchButton = button("SEARCH"); searchButton.setTextSize(11); searchButton.setTypeface(Typeface.DEFAULT_BOLD);
        searchButton.setTextColor(Color.rgb(7, 25, 18)); searchButton.setBackground(rounded(Color.rgb(92, 231, 179), 11, Color.TRANSPARENT));
        searchRow.addView(searchButton, new LinearLayout.LayoutParams(dp(86), dp(40)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(54));
        searchParams.topMargin = dp(7); root.addView(searchRow, searchParams);

        LinearLayout tabs = new LinearLayout(this); tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        tabs.setBackground(rounded(Color.rgb(18, 24, 22), 13, Color.TRANSPARENT));
        youtubeTab = button("YOUTUBE"); deviceTab = button("ON DEVICE");
        youtubeTab.setTextSize(11); deviceTab.setTextSize(11);
        youtubeTab.setTypeface(Typeface.DEFAULT_BOLD); deviceTab.setTypeface(Typeface.DEFAULT_BOLD);
        tabs.addView(youtubeTab, new LinearLayout.LayoutParams(0, dp(38), 1));
        tabs.addView(deviceTab, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, dp(48));
        tabParams.topMargin = dp(9); root.addView(tabs, tabParams);

        message = new TextView(this); message.setText("Search YouTube Music or browse songs on this device.");
        message.setTextColor(Color.rgb(151, 169, 160)); message.setTextSize(12);
        message.setGravity(Gravity.CENTER_VERTICAL); message.setPadding(dp(4), 0, dp(4), 0);
        message.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showDiagnostic(); }
        });
        root.addView(message, new LinearLayout.LayoutParams(-1, dp(34)));

        ListView list = new ListView(this); adapter = new SongAdapter(); list.setAdapter(adapter); list.setDividerHeight(dp(7));
        list.setSelector(android.R.color.transparent); list.setCacheColorHint(Color.TRANSPARENT);
        list.setPadding(0, dp(3), 0, dp(7)); list.setClipToPadding(false);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) { play(visibleSongs.get(position)); }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout playerCard = new LinearLayout(this); playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setPadding(dp(13), dp(8), dp(13), dp(7));
        playerCard.setBackground(rounded(Color.rgb(20, 28, 25), 15, Color.rgb(39, 52, 46)));
        TextView nowLabel = new TextView(this); nowLabel.setText("NOW PLAYING"); nowLabel.setTextColor(Color.rgb(92, 231, 179));
        nowLabel.setTextSize(9); nowLabel.setTypeface(Typeface.DEFAULT_BOLD);
        playerCard.addView(nowLabel, new LinearLayout.LayoutParams(-1, dp(16)));
        nowPlaying = new TextView(this); nowPlaying.setText("Nothing playing yet"); nowPlaying.setTextColor(Color.WHITE);
        nowPlaying.setTextSize(14); nowPlaying.setSingleLine(true); nowPlaying.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nowPlaying.setTypeface(Typeface.DEFAULT_BOLD);
        playerCard.addView(nowPlaying, new LinearLayout.LayoutParams(-1, dp(26)));
        nowArtist = new TextView(this); nowArtist.setText("Choose a song to start listening");
        nowArtist.setTextColor(Color.rgb(151, 169, 160)); nowArtist.setTextSize(10);
        nowArtist.setSingleLine(true); nowArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        playerCard.addView(nowArtist, new LinearLayout.LayoutParams(-1, dp(15)));
        seekBar = new SeekBar(this); seekBar.setContentDescription("Playback position");
        playerCard.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(25)));
        LinearLayout playerBottom = new LinearLayout(this); playerBottom.setGravity(Gravity.CENTER_VERTICAL);
        time = new TextView(this); time.setText("00:00 / 00:00"); time.setTextColor(Color.rgb(151, 169, 160)); time.setTextSize(10);
        playerBottom.addView(time, new LinearLayout.LayoutParams(0, dp(32), 1));
        Button previous = button("PREV"); playButton = button("PLAY"); Button next = button("NEXT");
        previous.setTextSize(10); playButton.setTextSize(11); next.setTextSize(10);
        previous.setTextColor(Color.WHITE); next.setTextColor(Color.WHITE); playButton.setTextColor(Color.rgb(7, 25, 18));
        previous.setBackground(rounded(Color.rgb(38, 50, 45), 12, Color.TRANSPARENT));
        next.setBackground(rounded(Color.rgb(38, 50, 45), 12, Color.TRANSPARENT));
        playButton.setTypeface(Typeface.DEFAULT_BOLD); playButton.setBackground(rounded(Color.rgb(92, 231, 179), 12, Color.TRANSPARENT));
        playerBottom.addView(previous, new LinearLayout.LayoutParams(dp(62), dp(34)));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(70), dp(34));
        playParams.leftMargin = dp(6); playParams.rightMargin = dp(6); playerBottom.addView(playButton, playParams);
        playerBottom.addView(next, new LinearLayout.LayoutParams(dp(62), dp(34)));
        playerCard.addView(playerBottom);
        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(-1, dp(127));
        playerParams.topMargin = dp(6); playerParams.bottomMargin = dp(2); root.addView(playerCard, playerParams);
        setContentView(root);
        setYoutubeMode(true);

        searchButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { searchYouTube(); } });
        search.setOnEditorActionListener((v, actionId, event) -> { searchYouTube(); return true; });
        youtubeTab.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setYoutubeMode(true); } });
        deviceTab.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setYoutubeMode(false); } });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { if (!youtubeMode) filterSongs(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        previous.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playAdjacent(-1); } });
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { playAdjacent(1); } });
        playButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sendServiceAction(LocalPlaybackService.ACTION_TOGGLE); } });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { if (fromUser) time.setText(formatTime(progress) + " / " + formatTime(bar.getMax())); }
            @Override public void onStartTrackingTouch(SeekBar bar) { draggingSeekBar = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                draggingSeekBar = false; Intent intent = new Intent(MainActivity.this, LocalPlaybackService.class);
                intent.setAction(LocalPlaybackService.ACTION_SEEK); intent.putExtra(LocalPlaybackService.EXTRA_POSITION, bar.getProgress()); startService(intent);
            }
        });
    }

    private void searchYouTube() {
        final String query = search.getText().toString().trim();
        if (query.length() == 0) { message.setText("Enter a song, artist, or video to search YouTube."); return; }
        setYoutubeMode(true);
        message.setText("Searching YouTube…"); remoteSongs.clear(); visibleSongs.clear(); adapter.notifyDataSetChanged();
        new AsyncTask<Void, Void, ArrayList<Song>>() {
            private Exception failure;
            @Override protected ArrayList<Song> doInBackground(Void... ignored) {
                try { return YoutubeClient.search(query); } catch (Exception error) { failure = error; return new ArrayList<Song>(); }
            }
            @Override protected void onPostExecute(ArrayList<Song> result) {
                remoteSongs.clear(); remoteSongs.addAll(result); visibleSongs.clear(); visibleSongs.addAll(result); adapter.notifyDataSetChanged();
                message.setText(failure == null ? result.size() + " YouTube results" : "Search failed: " + safeMessage(failure));
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void setYoutubeMode(boolean enabled) {
        youtubeMode = enabled;
        styleTab(youtubeTab, enabled); styleTab(deviceTab, !enabled);
        visibleSongs.clear(); visibleSongs.addAll(enabled ? remoteSongs : songs); adapter.notifyDataSetChanged();
        if (enabled) message.setText(remoteSongs.isEmpty() ? "Search YouTube Music for songs and artists." : remoteSongs.size() + " YouTube Music results");
        else filterSongs(search.getText().toString());
    }

    private void loadSongs() {
        if (loadTask != null) loadTask.cancel(true);
        message.setText("Loading device music…");
        loadTask = new AsyncTask<Void, Void, ArrayList<Song>>() {
            private boolean failed;
            @Override protected ArrayList<Song> doInBackground(Void... ignored) {
                ArrayList<Song> result = new ArrayList<Song>(); Cursor cursor = null;
                try {
                    String[] columns = { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST };
                    cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
                            MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.DURATION + ">0", null,
                            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
                    if (cursor != null) {
                        int id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                        int title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                        int artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                        while (!isCancelled() && cursor.moveToNext()) result.add(new Song(cursor.getLong(id), cursor.getString(title), cursor.getString(artist)));
                    }
                } catch (RuntimeException error) { failed = true; }
                finally { if (cursor != null) cursor.close(); }
                return result;
            }
            @Override protected void onPostExecute(ArrayList<Song> loaded) {
                songs.clear(); songs.addAll(loaded); loadTask = null;
                if (!youtubeMode) filterSongs(search.getText().toString());
                if (failed) message.setText("Could not read this device's music library.");
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void filterSongs(String query) {
        if (youtubeMode) return;
        visibleSongs.clear(); for (Song song : songs) if (song.matches(query)) visibleSongs.add(song);
        adapter.notifyDataSetChanged();
        message.setText(visibleSongs.isEmpty() ? (songs.isEmpty() ? getString(R.string.empty_library) : "No matching device songs.")
                : visibleSongs.size() + (visibleSongs.size() == 1 ? " device song" : " device songs"));
    }

    private void play(final Song song) {
        currentQueueKey = song.online ? song.youtubeUrl : song.uri.toString();
        if (!song.online) { startPlayback(song.uri.toString(), song.title, song.artist); return; }
        message.setText("Preparing YouTube audio for this device…");
        new AsyncTask<Void, Void, String>() {
            private Exception failure;
            @Override protected String doInBackground(Void... ignored) {
                File directory = new File(getCacheDir(), "youtube-audio");
                if (!directory.exists() && !directory.mkdirs()) {
                    failure = new java.io.IOException("Could not create the playback cache.");
                    return null;
                }
                final File cached = new File(directory, Long.toHexString(song.id) + ".m4a");
                if (cached.isFile() && cached.length() > 0) return cached.getAbsolutePath();
                File partial = new File(directory, Long.toHexString(song.id) + ".part");
                try {
                    YoutubeClient.downloadAudio(song.youtubeUrl, partial);
                    if (partial.length() == 0) throw new java.io.IOException("YouTube returned an empty audio file.");
                    if (cached.exists() && !cached.delete()) throw new java.io.IOException("Could not replace the old cache file.");
                    if (!partial.renameTo(cached)) throw new java.io.IOException("Could not finish preparing the audio file.");
                    return cached.getAbsolutePath();
                } catch (Exception error) {
                    failure = error;
                    partial.delete();
                    return null;
                }
            }
            @Override protected void onPostExecute(String url) {
                if (url == null) message.setText("Could not prepare this result: " + safeMessage(failure));
                else startPlayback(url, song.title, song.artist);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void startPlayback(String uri, String title, String artist) {
        Intent intent = new Intent(this, LocalPlaybackService.class); intent.setAction(LocalPlaybackService.ACTION_PLAY);
        intent.putExtra(LocalPlaybackService.EXTRA_URI, uri); intent.putExtra(LocalPlaybackService.EXTRA_TITLE, title);
        intent.putExtra(LocalPlaybackService.EXTRA_ARTIST, artist);
        if (Build.VERSION.SDK_INT >= 26) {
            try { Context.class.getMethod("startForegroundService", Intent.class).invoke(this, intent); return; }
            catch (Exception ignored) { }
        }
        startService(intent);
    }

    private void download(final Song song) {
        if (!song.online) { message.setText("This song is already stored on the device."); return; }
        if (Build.VERSION.SDK_INT >= 23 && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            pendingDownload = song; requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_DOWNLOAD_PERMISSION); return;
        }
        final File destination;
        try {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SimpMusic");
            if (!directory.exists() && !directory.mkdirs()) { message.setText("Could not create the Music/SimpMusic folder."); return; }
            if (!directory.isDirectory()) { message.setText("Music/SimpMusic is not a folder on this device."); return; }
            String safeName = (song.title + " - " + song.artist).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            if (safeName.length() > 120) safeName = safeName.substring(0, 120);
            if (safeName.length() == 0) safeName = "YouTube audio";
            destination = new File(directory, safeName + ".m4a");
        } catch (RuntimeException error) {
            message.setText("Could not access Music storage: " + safeMessage(error));
            return;
        }
        message.setText("Downloading “" + song.title + "”…");
        new AsyncTask<Void, Void, Boolean>() {
            private Throwable failure;
            @Override protected Boolean doInBackground(Void... ignored) {
                File partial = new File(destination.getAbsolutePath() + ".part");
                try {
                    YoutubeClient.downloadAudio(song.youtubeUrl, partial);
                    if (!partial.isFile() || partial.length() == 0) throw new java.io.IOException("YouTube returned an empty audio file.");
                    if (destination.exists() && !destination.delete()) throw new java.io.IOException("Could not replace the existing download.");
                    if (!partial.renameTo(destination)) throw new java.io.IOException("Could not finish saving the file.");
                    return true;
                } catch (Exception error) {
                    failure = error;
                    partial.delete();
                    return false;
                } catch (LinkageError error) {
                    failure = error;
                    partial.delete();
                    return false;
                }
            }
            @Override protected void onPostExecute(Boolean okay) {
                if (okay) {
                    try {
                        android.media.MediaScannerConnection.scanFile(MainActivity.this, new String[] { destination.getAbsolutePath() }, null, null);
                        message.setText("Saved to Music/SimpMusic/" + destination.getName());
                    } catch (RuntimeException error) {
                        message.setText("Saved the file, but Android could not refresh its music library.");
                    }
                } else {
                    lastDiagnostic = fullDiagnostic(failure);
                    message.setText("Download failed: " + safeMessage(failure));
                    showDiagnostic();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void playAdjacent(int direction) {
        ArrayList<Song> queue = youtubeMode ? remoteSongs : songs;
        if (queue.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < queue.size(); i++) {
            String uri = queue.get(i).online ? queue.get(i).youtubeUrl : queue.get(i).uri.toString();
            if (uri.equals(currentQueueKey == null ? currentUri : currentQueueKey)) { index = i; break; }
        }
        index = index < 0 ? (direction > 0 ? 0 : queue.size() - 1) : (index + direction + queue.size()) % queue.size();
        play(queue.get(index));
    }

    private boolean hasAudioPermission() { return Build.VERSION.SDK_INT < 23 || hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE); }
    private boolean hasPermission(String permission) {
        try { return ((Integer) Activity.class.getMethod("checkSelfPermission", String.class).invoke(this, permission)).intValue() == PackageManager.PERMISSION_GRANTED; }
        catch (Exception ignored) { return false; }
    }
    private void requestPermission(String permission, int requestCode) {
        if (Build.VERSION.SDK_INT < 23) return;
        try { Activity.class.getMethod("requestPermissions", String[].class, Integer.TYPE).invoke(this, new Object[] { new String[] { permission }, Integer.valueOf(requestCode) }); }
        catch (Exception error) { message.setText("Permission request could not be opened."); }
    }
    private void sendServiceAction(String action) { Intent intent = new Intent(this, LocalPlaybackService.class); intent.setAction(action); startService(intent); }
    private Button button(String label) {
        Button result = new Button(this); result.setText(label); result.setTextColor(Color.WHITE);
        result.setContentDescription(label); result.setAllCaps(false); result.setMinHeight(0); result.setMinimumHeight(0);
        result.setPadding(dp(4), 0, dp(4), 0); return result;
    }
    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) shape.setStroke(dp(1), stroke);
        return shape;
    }
    private void styleTab(Button tab, boolean selected) {
        tab.setTextColor(selected ? Color.rgb(7, 25, 18) : Color.rgb(158, 176, 167));
        tab.setBackground(rounded(selected ? Color.rgb(92, 231, 179) : Color.TRANSPARENT, 10, Color.TRANSPARENT));
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private static String safeArtist(String artist) { return artist == null || artist.length() == 0 ? "Unknown artist" : artist; }
    private static String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
    private static String fullDiagnostic(Throwable error) {
        if (error == null) return "No error details were captured.";
        StringWriter output = new StringWriter();
        error.printStackTrace(new PrintWriter(output));
        return output.toString();
    }
    private void showDiagnostic() {
        if (lastDiagnostic == null) return;
        TextView details = new TextView(this);
        details.setText(lastDiagnostic);
        details.setTextIsSelectable(true);
        details.setTextSize(12);
        details.setPadding(dp(16), dp(12), dp(16), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(details);
        new AlertDialog.Builder(this).setTitle("Download error details")
                .setView(scroll).setPositiveButton("CLOSE", null).show();
    }
    private static String formatTime(long millis) { long seconds = Math.max(0, millis / 1000); return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60); }

    private final class SongAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleSongs.size(); }
        @Override public Object getItem(int position) { return visibleSongs.get(position); }
        @Override public long getItemId(int position) { return visibleSongs.get(position).id; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout && "song-row".equals(convertView.getTag())) row = (LinearLayout) convertView;
            else {
                row = new LinearLayout(MainActivity.this); row.setTag("song-row"); row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(9), dp(7), dp(8), dp(7));
                row.setBackground(rounded(Color.rgb(19, 26, 23), 13, Color.rgb(34, 45, 40)));
                TextView art = new TextView(MainActivity.this); art.setTag("song-art"); art.setTextColor(Color.rgb(7, 25, 18));
                art.setTextSize(18); art.setTypeface(Typeface.DEFAULT_BOLD); art.setGravity(Gravity.CENTER);
                art.setBackground(rounded(Color.rgb(92, 231, 179), 11, Color.TRANSPARENT));
                row.addView(art, new LinearLayout.LayoutParams(dp(48), dp(48)));
                LinearLayout textBox = new LinearLayout(MainActivity.this); textBox.setOrientation(LinearLayout.VERTICAL);
                textBox.setPadding(dp(10), 0, dp(4), 0);
                TextView title = new TextView(MainActivity.this); title.setTag("song-title"); title.setTextColor(Color.WHITE);
                title.setTextSize(14); title.setTypeface(Typeface.DEFAULT_BOLD); title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView artist = new TextView(MainActivity.this); artist.setTag("song-artist"); artist.setTextColor(Color.rgb(151, 169, 160));
                artist.setTextSize(11); artist.setSingleLine(true); artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
                textBox.addView(title); textBox.addView(artist);
                row.addView(textBox, new LinearLayout.LayoutParams(0, dp(48), 1));
                Button download = button("↓"); download.setTag("song-download"); download.setTextSize(21);
                download.setTextColor(Color.rgb(92, 231, 179)); download.setBackground(rounded(Color.rgb(32, 44, 39), 11, Color.TRANSPARENT));
                row.addView(download, new LinearLayout.LayoutParams(dp(42), dp(42)));
            }
            final Song song = visibleSongs.get(position);
            TextView art = (TextView) row.getChildAt(0); art.setText(song.title.substring(0, 1).toUpperCase(Locale.US));
            LinearLayout textBox = (LinearLayout) row.getChildAt(1);
            TextView title = (TextView) textBox.getChildAt(0); title.setText(song.title);
            TextView artist = (TextView) textBox.getChildAt(1); artist.setText(safeArtist(song.artist));
            Button download = (Button) row.getChildAt(2); download.setVisibility(song.online ? View.VISIBLE : View.GONE);
            download.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { download(song); } });
            row.setContentDescription(song.title + ", " + safeArtist(song.artist) + (song.online ? ", tap to play, Save to download" : ", tap to play"));
            return row;
        }
    }
}
