package io.github.junsik.mdshare.confluence;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    public void rendersGfmTableWithConfluenceClasses() {
        String html = renderer.render("| a | b |\n| --- | --- |\n| 1 | 2 |\n");
        assertTrue(html.contains("<table class=\"confluenceTable\">"));
        // PDF/Word export styles tables only via these cell classes.
        assertTrue(html.contains("<th class=\"confluenceTh\">a</th>"));
        assertTrue(html.contains("<td class=\"confluenceTd\">1</td>"));
    }

    @Test
    public void escapesRawHtml() {
        String html = renderer.render("hello <script>alert(1)</script>");
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    public void rendersTaskList() {
        String html = renderer.render("- [x] done\n- [ ] todo\n");
        assertTrue(html.contains("checkbox"));
    }

    @Test
    public void nullMarkdownRendersEmpty() {
        renderer.render(null);
    }
}
