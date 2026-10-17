package com.maxrave.simpmusic.legacy;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** YouTube search and stream resolution through the open-source NewPipe Extractor. */
final class YoutubeClient {
    private static volatile boolean initialized;
    private YoutubeClient() { }

    static synchronized void initialize() {
        if (initialized) return;
        NewPipe.init(new AndroidDownloader());
        initialized = true;
    }

    static ArrayList<Song> search(String query) throws Exception {
        initialize();
        SearchInfo info = SearchInfo.getInfo(ServiceList.YouTube,
                YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(query));
        ArrayList<Song> songs = new ArrayList<Song>();
        for (InfoItem item : info.getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                StreamInfoItem stream = (StreamInfoItem) item;
                songs.add(new Song(stream.getUrl(), stream.getName(), stream.getUploaderName()));
            }
        }
        return songs;
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
