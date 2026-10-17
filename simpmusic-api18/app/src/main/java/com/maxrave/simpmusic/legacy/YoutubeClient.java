package com.maxrave.simpmusic.legacy;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** YouTube search and stream resolution through the open-source NewPipe Extractor. */
final class YoutubeClient {
    private static volatile boolean initialized;
    private static final String MUSIC_CLIENT_VERSION = "1.20260304.03.00";
    private static final String MUSIC_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    private YoutubeClient() { }

    static synchronized void initialize() {
        if (initialized) return;
        NewPipe.init(new AndroidDownloader());
        initialized = true;
    }

    static ArrayList<Song> search(String query) throws Exception {
        Exception musicFailure;
        try {
            ArrayList<Song> songs = searchMusic(query);
            if (!songs.isEmpty()) return songs;
            musicFailure = new IOException("YouTube Music returned no song results.");
        } catch (Exception error) {
            musicFailure = error;
        }
        try {
            initialize();
            SearchInfo info = SearchInfo.getInfo(ServiceList.YouTube,
                    YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(query));
            ArrayList<Song> videos = new ArrayList<Song>();
            for (InfoItem item : info.getRelatedItems()) {
                if (item instanceof StreamInfoItem) {
                    StreamInfoItem stream = (StreamInfoItem) item;
                    videos.add(new Song(stream.getUrl(), stream.getName(), stream.getUploaderName()));
                }
            }
            if (!videos.isEmpty()) return videos;
        } catch (Exception fallbackError) {
            throw new IOException("YouTube Music: " + message(musicFailure)
                    + "; YouTube fallback: " + message(fallbackError), fallbackError);
        }
        if (!(musicFailure instanceof IOException)) throw new IOException("YouTube search failed: " + message(musicFailure), musicFailure);
        throw (IOException) musicFailure;
    }

    private static ArrayList<Song> searchMusic(String query) throws Exception {
        JsonObject client = new JsonObject();
        client.put("clientName", "WEB_REMIX");
        client.put("clientVersion", MUSIC_CLIENT_VERSION);
        client.put("hl", "en");
        client.put("gl", "US");
        client.put("userAgent", MUSIC_USER_AGENT);
        JsonObject context = new JsonObject();
        context.put("client", client);
        JsonObject body = new JsonObject();
        body.put("context", context);
        body.put("query", query);
        body.put("params", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D");

        URL endpoint = new URL("https://music.youtube.com/youtubei/v1/search?prettyPrint=false");
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Origin", "https://music.youtube.com");
        connection.setRequestProperty("Referer", "https://music.youtube.com/");
        connection.setRequestProperty("X-Goog-Api-Format-Version", "1");
        connection.setRequestProperty("X-Goog-Authuser", "0");
        connection.setRequestProperty("x-origin", "https://music.youtube.com");
        connection.setRequestProperty("X-YouTube-Client-Name", "1");
        connection.setRequestProperty("X-YouTube-Client-Version", MUSIC_CLIENT_VERSION);
        connection.setRequestProperty("User-Agent", MUSIC_USER_AGENT);
        if (connection instanceof HttpsURLConnection && AndroidDownloader.TLS12 != null) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(AndroidDownloader.TLS12);
        }
        byte[] payload = JsonWriter.string(body).getBytes("UTF-8");
        connection.setFixedLengthStreamingMode(payload.length);
        java.io.OutputStream requestBody = connection.getOutputStream();
        requestBody.write(payload);
        requestBody.close();
        int status = connection.getResponseCode();
        InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = input == null ? "" : readUtf8(input);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String detail = response.length() > 240 ? response.substring(0, 240) : response;
            throw new IOException("YouTube Music HTTP " + status + (detail.length() == 0 ? "" : ": " + detail));
        }
        return parseSearchResults(response);
    }

    static ArrayList<Song> parseSearchResults(String response) throws Exception {
        JsonObject root = JsonParser.object().from(response);
        ArrayList<Song> results = new ArrayList<Song>();
        Set<String> seen = new HashSet<String>();
        collectSongs(root, results, seen);
        return results;
    }

    private static void collectSongs(Object node, ArrayList<Song> results, Set<String> seen) {
        if (node instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) node;
            Object renderer = map.get("musicResponsiveListItemRenderer");
            if (renderer instanceof Map) {
                Song song = parseSong((Map<?, ?>) renderer);
                if (song != null && seen.add(song.youtubeUrl) && results.size() < 30) results.add(song);
            }
            for (Object value : map.values()) collectSongs(value, results, seen);
        } else if (node instanceof Collection) {
            for (Object value : (Collection<?>) node) collectSongs(value, results, seen);
        }
    }

    private static Song parseSong(Map<?, ?> renderer) {
        String videoId = stringAt(renderer, "playlistItemData", "videoId");
        if (videoId == null) videoId = stringAt(renderer, "overlay", "musicItemThumbnailOverlayRenderer", "content",
                "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId");
        if (videoId == null) videoId = stringAt(renderer, "flexColumns", "0", "musicResponsiveListItemFlexColumnRenderer",
                "text", "runs", "0", "navigationEndpoint", "watchEndpoint", "videoId");
        if (videoId == null || videoId.length() == 0) return null;
        String title = stringAt(renderer, "flexColumns", "0", "musicResponsiveListItemFlexColumnRenderer", "text", "runs", "0", "text");
        if (title == null) title = stringAt(renderer, "flexColumns", "0", "musicResponsiveListItemFlexColumnRenderer", "text", "simpleText");
        String artist = firstArtist(renderer);
        String url = "https://www.youtube.com/watch?v=" + videoId;
        return new Song(url, title, artist);
    }

    private static String firstArtist(Map<?, ?> renderer) {
        Object columnsValue = renderer.get("flexColumns");
        if (!(columnsValue instanceof List) || ((List<?>) columnsValue).size() < 2) return "YouTube Music";
        Object column = ((List<?>) columnsValue).get(1);
        Object runsValue = valueAt(column, "musicResponsiveListItemFlexColumnRenderer", "text", "runs");
        if (!(runsValue instanceof List)) return "YouTube Music";
        String fallback = null;
        for (Object run : (List<?>) runsValue) {
            String text = stringAt(run, "text");
            if (text == null || text.trim().length() == 0 || "•".equals(text.trim())) continue;
            if (fallback == null) fallback = text.trim();
            if (valueAt(run, "navigationEndpoint", "browseEndpoint") instanceof Map) return text.trim();
        }
        return fallback == null ? "YouTube Music" : fallback;
    }

    private static String stringAt(Object node, String... path) {
        Object value = valueAt(node, path);
        return value instanceof CharSequence ? value.toString() : null;
    }

    private static Object valueAt(Object node, String... path) {
        Object value = node;
        for (String key : path) {
            if (value instanceof Map) value = ((Map<?, ?>) value).get(key);
            else if (value instanceof List) {
                try { value = ((List<?>) value).get(Integer.parseInt(key)); }
                catch (RuntimeException error) { return null; }
            } else return null;
            if (value == null) return null;
        }
        return value;
    }

    private static String readUtf8(InputStream input) throws IOException {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), "UTF-8");
        } finally { input.close(); }
    }

    private static String message(Exception error) {
        return error == null || error.getMessage() == null ? "unknown error" : error.getMessage();
    }

    static String resolveAudioUrl(String youtubeUrl) throws Exception {
        initialize();
        StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, youtubeUrl);
        List<AudioStream> streams = info.getAudioStreams();
        if (streams == null || streams.isEmpty()) throw new IOException("No playable audio stream was found.");
        // Android 4.3 MediaPlayer supports AAC/M4A; newer Opus/WebM streams do not work there.
        for (AudioStream stream : streams) {
            if (stream.getFormat() == MediaFormat.M4A) return stream.getContent();
        }
        throw new IOException("This video has no AAC/M4A stream supported by Android 4.3.");
    }

    static void downloadAudio(String youtubeUrl, File destination) throws Exception {
        String audioUrl = resolveAudioUrl(youtubeUrl);
        HttpURLConnection connection = (HttpURLConnection) new URL(audioUrl).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        SSLSocketFactory tls = AndroidDownloader.TLS12;
        if (connection instanceof HttpsURLConnection && tls != null) ((HttpsURLConnection) connection).setSSLSocketFactory(tls);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IOException("Audio download failed (HTTP " + status + ").");
        InputStream input = connection.getInputStream();
        FileOutputStream output = new FileOutputStream(destination);
        try {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.getFD().sync();
        } finally {
            try { input.close(); } finally { output.close(); connection.disconnect(); }
        }
    }

    private static final class AndroidDownloader extends Downloader {
        static final SSLSocketFactory TLS12 = createTls12Factory();

        @Override public Response execute(Request request) throws IOException, ReCaptchaException {
            HttpURLConnection connection = (HttpURLConnection) new URL(request.url()).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod(request.httpMethod());
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
            for (Map.Entry<String, List<String>> header : request.headers().entrySet()) {
                if (header.getValue() == null) continue;
                for (String value : header.getValue()) connection.addRequestProperty(header.getKey(), value);
            }
            if (connection instanceof HttpsURLConnection && TLS12 != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(TLS12);
            }
            byte[] body = request.dataToSend();
            if (body != null && body.length > 0) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(body);
                connection.getOutputStream().close();
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseBody = "";
            if (input != null && !"HEAD".equals(request.httpMethod())) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                input.close();
                responseBody = new String(output.toByteArray(), Charset.forName("UTF-8"));
            }
            Response response = new Response(status, connection.getResponseMessage(), connection.getHeaderFields(),
                    responseBody, connection.getURL().toString());
            connection.disconnect();
            return response;
        }

        private static SSLSocketFactory createTls12Factory() {
            try {
                SSLContext context = SSLContext.getInstance("TLSv1.2");
                context.init(null, null, null);
                return new Tls12SocketFactory(context.getSocketFactory());
            } catch (Exception ignored) { return null; }
        }
    }

    private static final class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        Tls12SocketFactory(SSLSocketFactory delegate) { this.delegate = delegate; }
        private java.net.Socket enable(java.net.Socket socket) {
            if (socket instanceof SSLSocket) ((SSLSocket) socket).setEnabledProtocols(new String[] { "TLSv1.2" });
            return socket;
        }
        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public java.net.Socket createSocket(java.net.Socket s, String h, int p, boolean a) throws IOException { return enable(delegate.createSocket(s, h, p, a)); }
        @Override public java.net.Socket createSocket(String h, int p) throws IOException { return enable(delegate.createSocket(h, p)); }
        @Override public java.net.Socket createSocket(String h, int p, java.net.InetAddress l, int lp) throws IOException { return enable(delegate.createSocket(h, p, l, lp)); }
        @Override public java.net.Socket createSocket(java.net.InetAddress h, int p) throws IOException { return enable(delegate.createSocket(h, p)); }
        @Override public java.net.Socket createSocket(java.net.InetAddress h, int p, java.net.InetAddress l, int lp) throws IOException { return enable(delegate.createSocket(h, p, l, lp)); }
    }
}
