package io.github.junsik.mdshare.confluence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches Markdown from an allowlisted URL.
 *
 * The allowlist is fail-closed: with no configured prefixes the url parameter
 * is disabled entirely, because a URL-fetching macro is otherwise an SSRF
 * proxy running inside the Confluence server.
 */
public class UrlMarkdownFetcher {

    static final String ALLOWLIST_PROPERTY = "mdshare.confluence.allowed-url-prefixes";

    private static final Pattern SHARE_LINK = Pattern.compile("^(https?://[^/]+)/d/([A-Za-z0-9_-]+)/?$");
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int TIMEOUT_MILLIS = 5_000;

    private final TtlCache cache = new TtlCache(5 * 60 * 1000L);

    public static final class FetchResult {
        public final String markdown;
        public final String error;

        private FetchResult(String markdown, String error) {
            this.markdown = markdown;
            this.error = error;
        }

        static FetchResult ok(String markdown) {
            return new FetchResult(markdown, null);
        }

        static FetchResult failed(String error) {
            return new FetchResult(null, error);
        }
    }

    /** md-share share links (/d/{id}) are normalised to their raw endpoint. */
    static String normalize(String url) {
        Matcher share = SHARE_LINK.matcher(url.trim());
        if (share.matches()) {
            return share.group(1) + "/api/documents/" + share.group(2) + "/raw";
        }
        return url.trim();
    }

    static List<String> allowedPrefixes() {
        List<String> prefixes = new ArrayList<>();
        String raw = System.getProperty(ALLOWLIST_PROPERTY, "");
        for (String part : raw.split(",")) {
            String prefix = part.trim();
            if (!prefix.isEmpty()) {
                prefixes.add(prefix);
            }
        }
        return prefixes;
    }

    public FetchResult fetch(String rawUrl) {
        List<String> prefixes = allowedPrefixes();
        if (prefixes.isEmpty()) {
            return FetchResult.failed("URL rendering is disabled. Set the system property "
                    + ALLOWLIST_PROPERTY + " to a comma-separated list of allowed URL prefixes.");
        }
        String url = normalize(rawUrl);
        boolean allowed = false;
        for (String prefix : prefixes) {
            if (url.startsWith(prefix)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            return FetchResult.failed("URL is not on the allowlist: " + url);
        }

        String cached = cache.get(url);
        if (cached != null) {
            return FetchResult.ok(cached);
        }
        try {
            String markdown = download(url);
            cache.put(url, markdown);
            return FetchResult.ok(markdown);
        } catch (NotFoundException e) {
            return FetchResult.failed("The shared document was not found. "
                    + "It may have expired (md-share TTL); attach the .md file to this page instead.");
        } catch (IOException e) {
            return FetchResult.failed("Failed to fetch the document: " + e.getMessage());
        }
    }

    private static final class NotFoundException extends IOException {
        NotFoundException(String message) {
            super(message);
        }
    }

    private static String download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        // Redirects are not followed: a redirect could escape the allowlist.
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "text/markdown, text/plain, */*");
        int status = connection.getResponseCode();
        if (status == 404) {
            throw new NotFoundException("404");
        }
        if (status != 200) {
            throw new IOException("HTTP " + status);
        }
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new IOException("document exceeds " + MAX_BYTES + " bytes");
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }
}
