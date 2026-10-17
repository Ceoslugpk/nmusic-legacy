package com.maxrave.simpmusic.legacy;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import java.io.IOException;

public final class LocalPlaybackService extends Service implements AudioManager.OnAudioFocusChangeListener {
    static final String ACTION_PLAY = "com.maxrave.simpmusic.legacy.PLAY";
    static final String ACTION_TOGGLE = "com.maxrave.simpmusic.legacy.TOGGLE";
    static final String ACTION_SEEK = "com.maxrave.simpmusic.legacy.SEEK";
    static final String ACTION_GET_STATE = "com.maxrave.simpmusic.legacy.GET_STATE";
    static final String ACTION_UPDATE = "com.maxrave.simpmusic.legacy.UPDATE";
    static final String ACTION_STOP = "com.maxrave.simpmusic.legacy.STOP";
    static final String EXTRA_URI = "uri";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_ARTIST = "artist";
    static final String EXTRA_PLAYING = "playing";
    static final String EXTRA_POSITION = "position";
    static final String EXTRA_DURATION = "duration";
    static final String EXTRA_ERROR = "error";
    private static final int NOTIFICATION_ID = 1804;
    // PendingIntent.FLAG_IMMUTABLE was added in API 23; keep this source buildable against API 18.
    private static final int FLAG_IMMUTABLE_COMPAT = 0x04000000;

    private final Handler handler = new Handler();
    private MediaPlayer player;
    private AudioManager audioManager;
    private String currentUri;
    private String title;
    private String artist;
    private boolean prepared;
    private boolean playing;
    private boolean resumeOnAudioFocusGain;
    private String playbackError;

    private final Runnable progressUpdate = new Runnable() {
        @Override public void run() {
            publishState();
            if (playing) handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) play(intent);
        else if (ACTION_TOGGLE.equals(action)) {
            toggle();
            if (player == null) stopSelf(startId);
        } else if (ACTION_SEEK.equals(action)) {
            seek(intent.getIntExtra(EXTRA_POSITION, 0));
            if (player == null) stopSelf(startId);
        } else if (ACTION_GET_STATE.equals(action)) {
            publishState();
            if (player == null) stopSelf(startId);
        }
        else if (ACTION_STOP.equals(action)) stopPlayback();
        return START_NOT_STICKY;
    }

    private void play(Intent intent) {
        String uriText = intent.getStringExtra(EXTRA_URI);
        if (uriText == null) return;
        currentUri = uriText;
        playbackError = null;
        title = intent.getStringExtra(EXTRA_TITLE);
        artist = intent.getStringExtra(EXTRA_ARTIST);
        releasePlayer();
        startForeground(NOTIFICATION_ID, buildNotification());

        int focus = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            publishState();
            stopForeground(true);
            stopSelf();
            return;
        }
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setWakeMode(getApplicationContext(), android.os.PowerManager.PARTIAL_WAKE_LOCK);
            Uri source = Uri.parse(currentUri);
            if ("http".equalsIgnoreCase(source.getScheme()) || "https".equalsIgnoreCase(source.getScheme())) {
                java.util.HashMap<String, String> headers = new java.util.HashMap<String, String>();
                headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 4.3) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
                headers.put("Referer", "https://www.youtube.com/");
                player.setDataSource(this, source, headers);
            } else if (source.getScheme() == null || "file".equalsIgnoreCase(source.getScheme())) {
                player.setDataSource("file".equalsIgnoreCase(source.getScheme()) ? source.getPath() : currentUri);
            } else {
                player.setDataSource(this, source);
            }
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mediaPlayer) {
                    if (mediaPlayer != player) return;
                    prepared = true;
                    resumeOnAudioFocusGain = false;
                    mediaPlayer.start();
                    playing = true;
                    refreshNotification();
                    publishState();
                    handler.removeCallbacks(progressUpdate);
                    handler.post(progressUpdate);
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mediaPlayer) {
                    if (mediaPlayer != player) return;
                    playing = false;
                    handler.removeCallbacks(progressUpdate);
                    refreshNotification();
                    publishState();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                    if (mediaPlayer != player) return true;
                    playing = false;
                    prepared = false;
                    playbackError = "Android audio decoder/network error (" + what + ", " + extra + ").";
                    handler.removeCallbacks(progressUpdate);
                    refreshNotification();
                    publishState();
                    return true;
                }
            });
            player.prepareAsync();
            publishState();
        } catch (IOException error) {
            playbackError = error.getMessage();
            releasePlayer();
            refreshNotification();
            publishState();
        } catch (IllegalArgumentException error) {
            playbackError = error.getMessage();
            releasePlayer();
            refreshNotification();
            publishState();
        } catch (SecurityException error) {
            playbackError = error.getMessage();
            releasePlayer();
            refreshNotification();
            publishState();
        }
    }

    private void toggle() {
        if (player == null || !prepared) return;
        try {
            if (playing) {
                player.pause();
                playing = false;
                resumeOnAudioFocusGain = false;
                handler.removeCallbacks(progressUpdate);
            } else {
                int focus = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (focus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    player.start();
                    playing = true;
                    resumeOnAudioFocusGain = false;
                    handler.post(progressUpdate);
                }
            }
        } catch (IllegalStateException ignored) {
            playing = false;
        }
        refreshNotification();
        publishState();
    }

    private void seek(int position) {
        if (player == null || !prepared) return;
        try {
            player.seekTo(Math.max(0, Math.min(position, player.getDuration())));
        } catch (IllegalStateException ignored) { }
        publishState();
    }

    private void stopPlayback() {
        releasePlayer();
        currentUri = null;
        title = null;
        artist = null;
        publishState();
        stopForeground(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        stopSelf();
    }

    private void releasePlayer() {
        handler.removeCallbacks(progressUpdate);
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException ignored) { }
            player.release();
            player = null;
        }
        prepared = false;
        playing = false;
        resumeOnAudioFocusGain = false;
        if (audioManager != null) audioManager.abandonAudioFocus(this);
    }

    private void publishState() {
        Intent update = new Intent(ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra(EXTRA_URI, currentUri);
        update.putExtra(EXTRA_TITLE, title);
        update.putExtra(EXTRA_ARTIST, artist);
        update.putExtra(EXTRA_PLAYING, playing);
        int position = 0;
        int duration = 0;
        if (player != null && prepared) {
            try { position = player.getCurrentPosition(); duration = player.getDuration(); }
            catch (IllegalStateException ignored) { }
        }
        update.putExtra(EXTRA_POSITION, position);
        update.putExtra(EXTRA_DURATION, duration);
        update.putExtra(EXTRA_ERROR, playbackError);
        sendBroadcast(update);
    }

    private void refreshNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int immutable = Build.VERSION.SDK_INT >= 23 ? FLAG_IMMUTABLE_COMPAT : 0;
        PendingIntent content = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | immutable);

        Intent toggleIntent = new Intent(this, LocalPlaybackService.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent togglePending = PendingIntent.getService(this, 2, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT | immutable);
        Intent stopIntent = new Intent(this, LocalPlaybackService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | immutable);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title == null ? "SimpMusic Legacy" : title)
                .setContentText(artist == null ? "Local music" : artist)
                .setContentIntent(content)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play", togglePending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending);
        if (Build.VERSION.SDK_INT >= 16) builder.setPriority(Notification.PRIORITY_LOW);
        return builder.build();
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (playing && player != null && prepared) {
                try { player.pause(); } catch (IllegalStateException ignored) { }
                playing = false;
                resumeOnAudioFocusGain = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                handler.removeCallbacks(progressUpdate);
                refreshNotification();
                publishState();
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN && resumeOnAudioFocusGain && !playing && player != null && prepared) {
            try { player.start(); playing = true; resumeOnAudioFocusGain = false; handler.post(progressUpdate); }
            catch (IllegalStateException ignored) { }
            refreshNotification();
            publishState();
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
