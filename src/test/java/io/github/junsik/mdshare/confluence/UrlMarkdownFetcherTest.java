package io.github.junsik.mdshare.confluence;

import com.atlassian.plugins.whitelist.OutboundWhitelist;
import com.atlassian.plugins.whitelist.WhitelistService;
import org.junit.After;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    public void usesConfluenceWhitelistWhenEnabled() {
        WhitelistService service = mock(WhitelistService.class);
        OutboundWhitelist outbound = mock(OutboundWhitelist.class);
        when(service.isWhitelistEnabled()).thenReturn(true);
        when(outbound.isAllowed(URI.create("https://md-share.example.com/api/documents/abc/raw")))
                .thenReturn(true);

        UrlMarkdownFetcher fetcher = new UrlMarkdownFetcher(service, outbound);
        assertNull(fetcher.denialReason("https://md-share.example.com/api/documents/abc/raw"));
        assertNotNull(fetcher.denialReason("https://evil.example.com/steal"));
    }

    @Test
    public void disabledWhitelistFallsBackToPropertyFailClosed() {
        WhitelistService service = mock(WhitelistService.class);
        OutboundWhitelist outbound = mock(OutboundWhitelist.class);
        when(service.isWhitelistEnabled()).thenReturn(false);
        // A disabled Confluence whitelist means "allow everything" — the macro
        // must not inherit that and instead requires the explicit property.
        when(outbound.isAllowed(any(URI.class))).thenReturn(true);

        UrlMarkdownFetcher fetcher = new UrlMarkdownFetcher(service, outbound);
        assertNotNull(fetcher.denialReason("https://md-share.example.com/api/documents/abc/raw"));

        System.setProperty(UrlMarkdownFetcher.ALLOWLIST_PROPERTY, "https://md-share.example.com/");
        assertNull(fetcher.denialReason("https://md-share.example.com/api/documents/abc/raw"));
        assertNotNull(fetcher.denialReason("https://evil.example.com/steal"));
    }

    @Test
    public void failsClosedWithoutWhitelistAndProperty() {
        UrlMarkdownFetcher.FetchResult result =
                new UrlMarkdownFetcher(null, null).fetch("https://md-share.example.com/d/abc123");
        assertNotNull(result.error);
        assertTrue(result.error.contains(UrlMarkdownFetcher.ALLOWLIST_PROPERTY));
    }
}
