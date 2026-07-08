package io.github.junsik.mdshare.confluence;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MermaidImageServletTest {

    @Test
    public void extractsPayloadFromFullDispatcherPath() {
        // Confluence 는 pathInfo 로 /plugins/servlet 뒤 전체 경로를 넘긴다.
        assertEquals("eNpLy8kv-abc_123",
                MermaidImageServlet.extractPayload("/md-share/mermaid/eNpLy8kv-abc_123.png"));
    }

    @Test
    public void extractsPayloadFromServletRelativePath() {
        assertEquals("abc", MermaidImageServlet.extractPayload("/mermaid/abc.png"));
    }

    @Test
    public void rejectsUnexpectedPaths() {
        assertNull(MermaidImageServlet.extractPayload(null));
        assertNull(MermaidImageServlet.extractPayload("/md-share/mermaid/"));
        assertNull(MermaidImageServlet.extractPayload("/md-share/mermaid/a.svg"));
        assertNull(MermaidImageServlet.extractPayload("/md-share/mermaid/../etc.png"));
    }
}
