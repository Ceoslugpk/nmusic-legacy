package com.maxrave.simpmusic.legacy;

import org.junit.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class YoutubeRemoteDecoderTest {
    @Test
    public void resolvesCurrentPlayerAndDecodesThrottlingParameter() throws Exception {
        final Downloader previousDownloader = NewPipe.getDownloader();
        final AtomicInteger calls = new AtomicInteger();
        try {
            NewPipe.init(new Downloader() {
                @Override
                public Response execute(Request request) throws IOException, ReCaptchaException {
                    calls.incrementAndGet();
                    if (request.url().contains("latest-player")) {
                        return response(request, "{\"player\":\"player123\",\"signatureTimestamp\":12345}");
                    }
                    assertTrue(request.url().contains("player=player123"));
                    assertTrue(request.url().contains("n=opaque_n"));
                    assertEquals(Collections.singletonList("PipePipe/4.9.0"), request.headers().get("User-Agent"));
                    return response(request, "{\"type\":\"result\",\"responses\":[{\"type\":\"result\",\"data\":{\"opaque_n\":\"decoded_n\"}}]}");
                }
            });
            YoutubeJavaScriptPlayerManager.clearAllCaches();

            String url = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                    "video123", "https://r.googlevideo.com/videoplayback?n=opaque_n&itag=140");

            assertEquals("https://r.googlevideo.com/videoplayback?n=decoded_n&itag=140", url);
            assertEquals(2, calls.get());
            assertEquals(Integer.valueOf(12345), YoutubeJavaScriptPlayerManager.getSignatureTimestamp("video123"));
        } finally {
            YoutubeJavaScriptPlayerManager.clearAllCaches();
            NewPipe.init(previousDownloader);
        }
    }

    private static Response response(Request request, String body) {
        return new Response(200, "OK", Collections.<String, java.util.List<String>>emptyMap(), body, request.url());
    }
}
