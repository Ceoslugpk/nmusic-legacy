package com.maxrave.simpmusic.legacy;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
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
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_AUDIO_PERMISSION = 18;
    private static final int REQUEST_DOWNLOAD_PERMISSION = 19;
    private final ArrayList<Song> songs = new ArrayList<Song>();
    private final ArrayList<Song> remoteSongs = new ArrayList<Song>();
    private final ArrayList<Song> visibleSongs = new ArrayList<Song>();
    private EditText search;
    private TextView nowPlaying;
    private TextView time;
    private TextView message;
    private SeekBar seekBar;
    private Button playButton;
    private Button modeButton;
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
            currentUri = intent.getStringExtra(LocalPlaybackService.EXTRA_URI);
            boolean playing = intent.getBooleanExtra(LocalPlaybackService.EXTRA_PLAYING, false);
            int position = intent.getIntExtra(LocalPlaybackService.EXTRA_POSITION, 0);
            int duration = intent.getIntExtra(LocalPlaybackService.EXTRA_DURATION, 0);
            nowPlaying.setText(title == null ? "Nothing playing" : title + "\n" + safeArtist(artist));
            playButton.setText(playing ? "Pause" : "Play");
            seekBar.setMax(Math.max(1, duration));
            if (!draggingSeekBar) seekBar.setProgress(Math.min(position, seekBar.getMax()));
            time.setText(formatTime(position) + " / " + formatTime(duration));
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
        root.setPadding(dp(14), dp(10), dp(14), dp(8));
        root.setBackgroundColor(Color.rgb(16, 20, 17));
        TextView heading = new TextView(this);
        heading.setText("SimpMusic  /  Legacy"); heading.setTextColor(Color.WHITE); heading.setTextSize(22);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout searchRow = new LinearLayout(this);
        search = new EditText(this); search.setSingleLine(true); search.setHint(R.string.search_hint);
        search.setTextColor(Color.WHITE); search.setHintTextColor(Color.LTGRAY); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchRow.addView(search, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button searchButton = button("YouTube"); searchRow.addView(searchButton, new LinearLayout.LayoutParams(dp(92), dp(48)));
        root.addView(searchRow);
        modeButton = button("Device music"); root.addView(modeButton, new LinearLayout.LayoutParams(-1, dp(40)));

        nowPlaying = new TextView(this); nowPlaying.setText("Nothing playing"); nowPlaying.setTextColor(Color.WHITE);
        nowPlaying.setTextSize(16); nowPlaying.setPadding(0, dp(8), 0, 0);
        root.addView(nowPlaying, new LinearLayout.LayoutParams(-1, dp(52)));
        seekBar = new SeekBar(this); seekBar.setContentDescription("Playback position");
        root.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(32)));
        time = new TextView(this); time.setText("00:00 / 00:00"); time.setTextColor(Color.LTGRAY); time.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(time, new LinearLayout.LayoutParams(-1, dp(22)));

        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        Button previous = button("Previous"); playButton = button("Play"); Button next = button("Next");
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(playButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(44), 1));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(46)));
        message = new TextView(this); message.setText(R.string.empty_library); message.setTextColor(Color.LTGRAY);
        message.setGravity(Gravity.CENTER_VERTICAL); root.addView(message, new LinearLayout.LayoutParams(-1, dp(34)));
        ListView list = new ListView(this); adapter = new SongAdapter(); list.setAdapter(adapter); list.setDividerHeight(dp(1));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) { play(visibleSongs.get(position)); }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);

        searchButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { searchYouTube(); } });
        search.setOnEditorActionListener((v, actionId, event) -> { searchYouTube(); return true; });
        modeButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setYoutubeMode(!youtubeMode); } });
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
        modeButton.setText(enabled ? "Show device music" : "Show YouTube results");
        visibleSongs.clear(); visibleSongs.addAll(enabled ? remoteSongs : songs); adapter.notifyDataSetChanged();
        if (enabled) message.setText(remoteSongs.isEmpty() ? "Search YouTube for songs and videos." : remoteSongs.size() + " YouTube results");
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
        message.setText("Resolving YouTube audio…");
        new AsyncTask<Void, Void, String>() {
            private Exception failure;
            @Override protected String doInBackground(Void... ignored) {
                try { return YoutubeClient.resolveAudioUrl(song.youtubeUrl); } catch (Exception error) { failure = error; return null; }
            }
            @Override protected void onPostExecute(String url) {
                if (url == null) message.setText("Could not play this result: " + safeMessage(failure));
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
        final File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SimpMusic");
        if (!directory.exists() && !directory.mkdirs()) { message.setText("Could not create the Music/SimpMusic folder."); return; }
        String safeName = (song.title + " - " + song.artist).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (safeName.length() > 120) safeName = safeName.substring(0, 120);
        final File destination = new File(directory, safeName + ".m4a");
        message.setText("Downloading “" + song.title + "”…");
        new AsyncTask<Void, Void, Boolean>() {
            private Exception failure;
            @Override protected Boolean doInBackground(Void... ignored) {
                File partial = new File(destination.getAbsolutePath() + ".part");
                try { YoutubeClient.downloadAudio(song.youtubeUrl, partial); if (!partial.renameTo(destination)) throw new java.io.IOException("Could not finish saving the file."); return true; }
                catch (Exception error) { failure = error; partial.delete(); return false; }
            }
            @Override protected void onPostExecute(Boolean okay) {
                if (okay) {
                    android.media.MediaScannerConnection.scanFile(MainActivity.this, new String[] { destination.getAbsolutePath() }, null, null);
                    message.setText("Saved to Music/SimpMusic/" + destination.getName());
                } else message.setText("Download failed: " + safeMessage(failure));
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
    private Button button(String label) { Button result = new Button(this); result.setText(label); result.setTextColor(Color.WHITE); result.setContentDescription(label); return result; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private static String safeArtist(String artist) { return artist == null || artist.length() == 0 ? "Unknown artist" : artist; }
    private static String safeMessage(Exception error) { return error == null || error.getMessage() == null ? "unknown error" : error.getMessage(); }
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
                TextView title = new TextView(MainActivity.this); title.setTag("song-title"); title.setTextColor(Color.WHITE); title.setTextSize(15); title.setPadding(dp(8), dp(6), dp(4), dp(6));
                row.addView(title, new LinearLayout.LayoutParams(0, dp(58), 1));
                Button download = button("Save"); download.setTag("song-download"); row.addView(download, new LinearLayout.LayoutParams(dp(76), dp(48)));
            }
            final Song song = visibleSongs.get(position);
            TextView title = (TextView) row.getChildAt(0); title.setText(song.title + "\n" + safeArtist(song.artist));
            Button download = (Button) row.getChildAt(1); download.setVisibility(song.online ? View.VISIBLE : View.GONE);
            download.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { download(song); } });
            row.setContentDescription(song.title + ", " + safeArtist(song.artist) + (song.online ? ", tap to play, Save to download" : ", tap to play"));
            return row;
        }
    }
}
