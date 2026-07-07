package io.github.junsik.mdshare.confluence;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class UrlMarkdownFetcherTest {

    @After
    public void clearAllowlist() {
        System.clearProperty(UrlMarkdownFetcher.ALLOWLIST_PROPERTY);
    }

    @Test
    public void normalizesShareLinkToRawEndpoint() {
        assertEquals(
                "https://md-share.example.com/api/documents/3gzt6TgARLou/raw",
                UrlMarkdownFetcher.normalize("https://md-share.example.com/d/3gzt6TgARLou"));
    }

    @Test
    public void keepsRawUrlUntouched() {
        String raw = "https://md-share.example.com/api/documents/abc/raw";
        assertEquals(raw, UrlMarkdownFetcher.normalize(raw));
    }

    @Test
    public void failsClosedWithoutAllowlist() {
        UrlMarkdownFetcher.FetchResult result =
                new UrlMarkdownFetcher().fetch("https://md-share.example.com/d/abc123");
        assertNotNull(result.error);
        assertTrue(result.error.contains(UrlMarkdownFetcher.ALLOWLIST_PROPERTY));
    }

    @Test
    public void rejectsUrlOutsideAllowlist() {
        System.setProperty(UrlMarkdownFetcher.ALLOWLIST_PROPERTY, "https://md-share.example.com/");
        UrlMarkdownFetcher.FetchResult result =
                new UrlMarkdownFetcher().fetch("https://evil.example.com/steal");
        assertNotNull(result.error);
        assertTrue(result.error.contains("allowlist"));
    }
}
