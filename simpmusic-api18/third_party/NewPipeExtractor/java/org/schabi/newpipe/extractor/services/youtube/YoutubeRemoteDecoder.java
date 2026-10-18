package org.schabi.newpipe.extractor.services.youtube;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.Localization;

import javax.annotation.Nonnull;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** API 18-compatible port of PipePipe's current-player and signature/n decoder fallback. */
final class YoutubeRemoteDecoder {
    private static final String API = "https://api.pipepipe.dev/decoder/";
    private static final String USER_AGENT = "PipePipe/4.9.0";
    private static final long METADATA_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final Map<String, String> CACHE = new HashMap<String, String>();
    private static PlayerMetadata metadata;

    private YoutubeRemoteDecoder() { }

    static synchronized int getSignatureTimestamp() throws ParsingException {
        return getMetadata().signatureTimestamp;
    }

    static synchronized String decode(@Nonnull String videoId,
                                      @Nonnull String type,
                                      @Nonnull String value) throws ParsingException {
        if (!("sig".equals(type) || "n".equals(type))) {
            throw new ParsingException("Unsupported YouTube decoder parameter: " + type);
        }
        final String key = metadata().playerId + ":" + type + ":" + value;
        final String cached = CACHE.get(key);
        if (cached != null) return cached;

        try {
            final String url = API + "decode?player=" + encode(metadata().playerId)
                    + "&" + type + "=" + encode(value);
            final JsonObject response = getJson(url);
            if (!"result".equals(response.getString("type"))) {
                throw new ParsingException("YouTube decoder returned an unexpected response");
            }
            final JsonArray responses = response.getArray("responses");
            if (responses == null || responses.size() == 0) {
                throw new ParsingException("YouTube decoder returned no result");
            }
            final JsonObject first = responses.getObject(0);
            final JsonObject data = first == null ? null : first.getObject("data");
            final String decoded = data == null ? null : data.getString(value);
            if (decoded == null || decoded.length() == 0) {
                throw new ParsingException("YouTube decoder returned an empty value");
            }
            CACHE.put(key, decoded);
            return decoded;
        } catch (ParsingException error) {
            throw error;
        } catch (Exception error) {
            throw new ParsingException("Could not decode YouTube stream parameters", error);
        }
    }

    private static PlayerMetadata metadata() throws ParsingException {
        return getMetadata();
    }

    private static PlayerMetadata getMetadata() throws ParsingException {
        if (metadata != null && System.currentTimeMillis() < metadata.expiresAt) return metadata;
        try {
            final JsonObject response = getJson(API + "latest-player");
            final String playerId = response.getString("player", "");
            if (playerId.length() == 0) {
                throw new ParsingException("YouTube decoder omitted its current player id");
            }
            if (!response.has("signatureTimestamp")) {
                throw new ParsingException("YouTube decoder omitted its signature timestamp");
            }
            metadata = new PlayerMetadata(playerId, response.getInt("signatureTimestamp"),
                    System.currentTimeMillis() + METADATA_TTL_MS);
            return metadata;
        } catch (ParsingException error) {
            throw error;
        } catch (Exception error) {
            throw new ParsingException("Could not load the current YouTube player", error);
        }
    }

    private static JsonObject getJson(String url) throws Exception {
        final Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put("User-Agent", Collections.singletonList(USER_AGENT));
        final Response response = NewPipe.getDownloader().get(url, headers, Localization.DEFAULT);
        if (response.responseCode() < 200 || response.responseCode() >= 300) {
            throw new ParsingException("YouTube decoder returned HTTP " + response.responseCode());
        }
        return JsonParser.object().from(response.responseBody());
    }

    private static String encode(String value) throws ParsingException {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new ParsingException("UTF-8 encoding is unavailable", impossible);
        }
    }

    static synchronized int getCacheSize() {
        return CACHE.size();
    }

    static synchronized void clearDecodeCache() {
        CACHE.clear();
    }

    static synchronized void clearCaches() {
        CACHE.clear();
        metadata = null;
    }

    private static final class PlayerMetadata {
        final String playerId;
        final int signatureTimestamp;
        final long expiresAt;

        PlayerMetadata(String playerId, int signatureTimestamp, long expiresAt) {
            this.playerId = playerId;
            this.signatureTimestamp = signatureTimestamp;
            this.expiresAt = expiresAt;
        }
    }
}
