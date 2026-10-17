package com.maxrave.simpmusic.legacy;

import android.Manifest;
import android.app.Activity;
import android.os.AsyncTask;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_AUDIO_PERMISSION = 18;
    private final ArrayList<Song> songs = new ArrayList<Song>();
    private final ArrayList<Song> visibleSongs = new ArrayList<Song>();
    private EditText search;
    private TextView nowPlaying;
    private TextView time;
    private TextView message;
    private SeekBar seekBar;
    private Button playButton;
    private SongAdapter adapter;
    private String currentUri;
    private boolean draggingSeekBar;
    private boolean receiverRegistered;
    private AsyncTask<Void, Void, ArrayList<Song>> loadTask;
    private boolean loadFailed;

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
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

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        buildInterface();
        if (hasAudioPermission()) loadSongs();
        else requestAudioPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(LocalPlaybackService.ACTION_UPDATE);
            registerReceiver(playbackReceiver, filter);
            receiverRegistered = true;
        }
        if (hasAudioPermission() && songs.isEmpty()) loadSongs();
        sendServiceAction(LocalPlaybackService.ACTION_GET_STATE);
    }

    @Override
    protected void onPause() {
        if (receiverRegistered) {
            unregisterReceiver(playbackReceiver);
            receiverRegistered = false;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (loadTask != null) loadTask.cancel(true);
        super.onDestroy();
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadSongs();
            else message.setText(R.string.permission_message);
        }
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(10));
        root.setBackgroundColor(Color.rgb(16, 20, 17));

        TextView heading = new TextView(this);
        heading.setText("SimpMusic  /  Legacy");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(22);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(44)));

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(R.string.search_hint);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.LTGRAY);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(48)));

        nowPlaying = new TextView(this);
        nowPlaying.setText("Nothing playing");
        nowPlaying.setTextColor(Color.WHITE);
        nowPlaying.setTextSize(17);
        nowPlaying.setPadding(0, dp(12), 0, 0);
        root.addView(nowPlaying, new LinearLayout.LayoutParams(-1, dp(58)));

        seekBar = new SeekBar(this);
        seekBar.setContentDescription("Playback position");
        root.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(36)));

        time = new TextView(this);
        time.setText("00:00 / 00:00");
        time.setTextColor(Color.LTGRAY);
        time.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(time, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        Button previous = button("Previous");
        playButton = button("Play");
        Button next = button("Next");
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(playButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(controls, new LinearLayout.LayoutParams(-1, dp(52)));

        message = new TextView(this);
        message.setText(R.string.empty_library);
        message.setTextColor(Color.LTGRAY);
        message.setGravity(Gravity.CENTER_VERTICAL);
        message.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (hasAudioPermission()) loadSongs();
                else requestAudioPermission();
            }
        });
        root.addView(message, new LinearLayout.LayoutParams(-1, dp(40)));

        ListView list = new ListView(this);
        adapter = new SongAdapter();
        list.setAdapter(adapter);
        list.setDividerHeight(dp(1));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                play(visibleSongs.get(position));
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterSongs(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        previous.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playAdjacent(-1); }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playAdjacent(1); }
        });
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendServiceAction(LocalPlaybackService.ACTION_TOGGLE); }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) time.setText(formatTime(progress) + " / " + formatTime(bar.getMax()));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { draggingSeekBar = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                draggingSeekBar = false;
                Intent intent = new Intent(MainActivity.this, LocalPlaybackService.class);
                intent.setAction(LocalPlaybackService.ACTION_SEEK);
                intent.putExtra(LocalPlaybackService.EXTRA_POSITION, bar.getProgress());
                startService(intent);
            }
        });
    }

    private Button button(String label) {
        Button result = new Button(this);
        result.setText(label);
        result.setTextColor(Color.WHITE);
        result.setContentDescription(label);
        return result;
    }

    private void loadSongs() {
        if (loadTask != null) loadTask.cancel(true);
        message.setText("Loading music…");
        loadTask = new AsyncTask<Void, Void, ArrayList<Song>>() {
            @Override
            protected ArrayList<Song> doInBackground(Void... ignored) {
                ArrayList<Song> result = new ArrayList<Song>();
                Cursor cursor = null;
                loadFailed = false;
                try {
                    String[] columns = {
                            MediaStore.Audio.Media._ID,
                            MediaStore.Audio.Media.TITLE,
                            MediaStore.Audio.Media.ARTIST,
                            MediaStore.Audio.Media.DURATION
                    };
                    cursor = getContentResolver().query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            columns,
                            MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.DURATION + ">0",
                            null,
                            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
                    if (cursor != null) {
                        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                        int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                        int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                        int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                        while (!isCancelled() && cursor.moveToNext()) {
                            result.add(new Song(cursor.getLong(idColumn),
                                    cursor.getString(titleColumn),
                                    cursor.getString(artistColumn)));
                        }
                    }
                } catch (RuntimeException error) {
                    loadFailed = true;
                } finally {
                    if (cursor != null) cursor.close();
                }
                return result;
            }

            @Override
            protected void onPostExecute(ArrayList<Song> loaded) {
                songs.clear();
                songs.addAll(loaded);
                filterSongs(search.getText().toString());
                if (loadFailed) message.setText("Could not read this device's music library.");
                loadTask = null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void filterSongs(String query) {
        visibleSongs.clear();
        for (Song song : songs) if (song.matches(query)) visibleSongs.add(song);
        adapter.notifyDataSetChanged();
        if (visibleSongs.isEmpty()) {
            if (songs.isEmpty()) message.setText(R.string.empty_library);
            else message.setText("No matching songs.");
        } else {
            message.setText(visibleSongs.size() + (visibleSongs.size() == 1 ? " song" : " songs"));
        }
    }

    private void play(Song song) {
        Intent intent = new Intent(this, LocalPlaybackService.class);
        intent.setAction(LocalPlaybackService.ACTION_PLAY);
        intent.putExtra(LocalPlaybackService.EXTRA_URI, song.uri.toString());
        intent.putExtra(LocalPlaybackService.EXTRA_TITLE, song.title);
        intent.putExtra(LocalPlaybackService.EXTRA_ARTIST, song.artist);
        startPlaybackService(intent);
    }

    private void playAdjacent(int direction) {
        if (songs.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).uri.toString().equals(currentUri)) { index = i; break; }
        }
        index = index < 0 ? (direction > 0 ? 0 : songs.size() - 1) : (index + direction + songs.size()) % songs.size();
        play(songs.get(index));
    }

    private boolean hasAudioPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        try {
            Object result = Activity.class.getMethod("checkSelfPermission", String.class).invoke(this, permission);
            return ((Integer) result).intValue() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
            try {
                Activity.class.getMethod("requestPermissions", String[].class, Integer.TYPE)
                        .invoke(this, new Object[] { new String[] { permission }, Integer.valueOf(REQUEST_AUDIO_PERMISSION) });
            } catch (Exception error) {
                message.setText(R.string.permission_message);
            }
        }
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, LocalPlaybackService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void startPlaybackService(Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Context.class.getMethod("startForegroundService", Intent.class).invoke(this, intent);
                return;
            } catch (Exception ignored) { }
        }
        startService(intent);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String safeArtist(String artist) {
        return artist == null || artist.length() == 0 ? "Unknown artist" : artist;
    }

    private static String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private final class SongAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleSongs.size(); }
        @Override public Object getItem(int position) { return visibleSongs.get(position); }
        @Override public long getItemId(int position) { return visibleSongs.get(position).id; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView row = convertView instanceof TextView ? (TextView) convertView : new TextView(MainActivity.this);
            Song song = visibleSongs.get(position);
            row.setText(song.title + "\n" + safeArtist(song.artist));
            row.setTextColor(Color.WHITE);
            row.setTextSize(16);
            row.setPadding(dp(8), dp(10), dp(8), dp(10));
            row.setMinHeight(dp(58));
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setContentDescription(song.title + ", " + safeArtist(song.artist));
            return row;
        }
    }
}
