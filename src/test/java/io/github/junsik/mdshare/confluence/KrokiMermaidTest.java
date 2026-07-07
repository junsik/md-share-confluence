package io.github.junsik.mdshare.confluence;

import org.junit.After;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Inflater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KrokiMermaidTest {

    private static final String DIAGRAM = "flowchart LR\n  A[리포트] --> B[md-share]\n";

    @After
    public void clearProperty() {
        System.clearProperty(KrokiMermaid.KROKI_URL_PROPERTY);
    }

    @Test
    public void encodeIsKrokiCompatibleDeflateBase64Url() throws Exception {
        String payload = KrokiMermaid.encode(DIAGRAM);
        assertTrue(payload.matches("[A-Za-z0-9_-]+"));

        byte[] compressed = Base64.getUrlDecoder().decode(payload);
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] out = new byte[4096];
        int length = inflater.inflate(out);
        inflater.end();
        assertEquals(DIAGRAM, new String(out, 0, length, StandardCharsets.UTF_8));
    }

    @Test
    public void disabledWithoutProperty() {
        assertFalse(KrokiMermaid.enabled());
    }

    @Test
    public void mermaidFenceBecomesServletImageWhenKrokiConfigured() {
        System.setProperty(KrokiMermaid.KROKI_URL_PROPERTY, "https://kroki.example.com");
        String html = new MarkdownRenderer("https://wiki.example.com")
                .render("```mermaid\n" + DIAGRAM + "```\n");
        assertTrue(html.contains("<img src=\"https://wiki.example.com/plugins/servlet/md-share/mermaid/"));
        assertTrue(html.contains("class=\"md-share-mermaid\""));
        assertFalse(html.contains("language-mermaid"));
    }

    @Test
    public void mermaidFenceStaysCodeBlockWithoutKroki() {
        String html = new MarkdownRenderer("https://wiki.example.com")
                .render("```mermaid\n" + DIAGRAM + "```\n");
        assertTrue(html.contains("language-mermaid"));
        assertFalse(html.contains("<img"));
    }

    @Test
    public void nonMermaidFencesStillRenderAsCode() {
        System.setProperty(KrokiMermaid.KROKI_URL_PROPERTY, "https://kroki.example.com");
        String html = new MarkdownRenderer("").render("```python\nprint(1)\n```\n");
        assertTrue(html.contains("language-python"));
        assertFalse(html.contains("<img"));
    }
}
