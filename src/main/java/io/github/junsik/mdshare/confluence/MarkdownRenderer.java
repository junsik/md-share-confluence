package io.github.junsik.mdshare.confluence;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.html.MutableAttributes;

import java.util.Arrays;

/**
 * GFM-flavoured Markdown to HTML. Raw HTML inside the Markdown is escaped so
 * shared documents can never inject markup into the Confluence page.
 */
public final class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create()));
        options.set(HtmlRenderer.ESCAPE_HTML, true);
        options.set(TablesExtension.CLASS_NAME, "confluenceTable");
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options)
                .attributeProviderFactory(new IndependentAttributeProviderFactory() {
                    @Override
                    public AttributeProvider apply(LinkResolverContext context) {
                        return MarkdownRenderer::setConfluenceTableAttributes;
                    }
                })
                .build();
    }

    /**
     * Confluence styles tables through the confluenceTh/confluenceTd cell
     * classes — PDF/Word export applies only those class-based rules (plugin
     * web-resource CSS is not loaded there), so the cells must carry them.
     */
    private static void setConfluenceTableAttributes(Node node, AttributablePart part, MutableAttributes attributes) {
        if (part != AttributablePart.NODE || !(node instanceof TableCell)) {
            return;
        }
        boolean header = node.getAncestorOfType(TableHead.class) != null;
        attributes.replaceValue("class", header ? "confluenceTh" : "confluenceTd");
    }

    public String render(String markdown) {
        return renderer.render(parser.parse(markdown == null ? "" : markdown));
    }
}
