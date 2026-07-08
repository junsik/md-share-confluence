package io.github.junsik.mdshare.confluence;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class MermaidImageServletTest {

    @Test
    public void extractsPayloadFromFullDispatcherPath() {
        assertArrayEquals(new String[]{"eNpLy8kv-abc_123", "png"},
                MermaidImageServlet.extractPayload("/md-share/mermaid/eNpLy8kv-abc_123.png"));
    }

    @Test
    public void extractsPayloadAndFormatFromFullRequestUri() {
        assertArrayEquals(new String[]{"eNpLy8kv", "png"},
                MermaidImageServlet.extractPayload("/plugins/servlet/md-share/mermaid/eNpLy8kv.png"));
        assertArrayEquals(new String[]{"eNpLy8kv", "svg"},
                MermaidImageServlet.extractPayload("/confluence/plugins/servlet/md-share/mermaid/eNpLy8kv.svg"));
    }

    @Test
    public void extractsFromPatternStrippedPath() {
        // 6.1.2 실측: 디스패처가 url-pattern 매칭 부분을 잘라 pathInfo 에 남기지 않는다.
        assertNull(MermaidImageServlet.extractPayload("/abc.png"));
    }

    @Test
    public void rejectsUnexpectedPaths() {
        assertNull(MermaidImageServlet.extractPayload(null));
        assertNull(MermaidImageServlet.extractPayload("/md-share/mermaid/"));
        assertNull(MermaidImageServlet.extractPayload("/md-share/mermaid/a.gif"));
        assertNull(MermaidImageServlet.extractPayload("/md-share/mermaid/../etc.png"));
    }
}
