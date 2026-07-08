package io.github.junsik.mdshare.confluence;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Inflater;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KrokiMermaidTest {

    private static final String DIAGRAM = "flowchart LR\n  A[리포트] --> B[md-share]\n";

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
    public void mermaidFenceBecomesSvgImageForWebView() {
        String html = new MarkdownRenderer("https://wiki.example.com", () -> "https://kroki.example.com")
                .render("```mermaid\n" + DIAGRAM + "```\n");
        assertTrue(html.contains("<img src=\"https://wiki.example.com/plugins/servlet/md-share/mermaid/"));
        assertTrue(html.contains(".svg\""));
        assertTrue(html.contains("class=\"md-share-mermaid\""));
        assertFalse(html.contains("language-mermaid"));
    }

    @Test
    public void mermaidFenceBecomesPngImageForExport() {
        String html = new MarkdownRenderer("https://wiki.example.com", () -> "https://kroki.example.com")
                .render("```mermaid\n" + DIAGRAM + "```\n", "png");
        assertTrue(html.contains(".png\""));
        assertFalse(html.contains(".svg\""));
    }

    @Test
    public void mermaidFenceStaysCodeBlockWithoutKroki() {
        String html = new MarkdownRenderer("https://wiki.example.com", () -> "")
                .render("```mermaid\n" + DIAGRAM + "```\n");
        assertTrue(html.contains("language-mermaid"));
        assertFalse(html.contains("<img"));
    }

    @Test
    public void nonMermaidFencesStillRenderAsCode() {
        String html = new MarkdownRenderer("", () -> "https://kroki.example.com")
                .render("```python\nprint(1)\n```\n");
        assertTrue(html.contains("language-python"));
        assertFalse(html.contains("<img"));
    }

    @Test
    public void krokiUrlIsReadPerRender() {
        String[] url = {""};
        MarkdownRenderer renderer = new MarkdownRenderer("", () -> url[0]);
        assertTrue(renderer.render("```mermaid\nA-->B\n```\n").contains("language-mermaid"));
        url[0] = "https://kroki.example.com";  // 관리자 설정 변경 시나리오 — 재시작 없이 반영
        assertTrue(renderer.render("```mermaid\nA-->B\n```\n").contains("<img"));
    }
}
