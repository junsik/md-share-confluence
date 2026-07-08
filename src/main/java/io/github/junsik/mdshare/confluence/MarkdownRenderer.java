package io.github.junsik.mdshare.confluence;

import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableHead;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.DelegatingNodeRendererFactory;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.html.MutableAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * GFM-flavoured Markdown to HTML. Raw HTML inside the Markdown is escaped so
 * shared documents can never inject markup into the Confluence page.
 */
public final class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;
    // 웹 뷰는 SVG(벡터, 축소돼도 선명), PDF/Word export 는 PNG(구형 export 렌더러가
    // SVG 를 못 그림) — 호출별로 다르므로 render 호출 동안만 유효한 값으로 전달한다.
    private final ThreadLocal<String> mermaidImageFormat = ThreadLocal.withInitial(() -> "svg");

    public MarkdownRenderer() {
        this("", () -> "");
    }

    /**
     * baseUrl prefixes diagram-image servlet links (absolute URLs survive PDF
     * export); krokiUrlSupplier is read per render so admin config changes
     * apply without a restart.
     */
    public MarkdownRenderer(String baseUrl, java.util.function.Supplier<String> krokiUrlSupplier) {
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
                .nodeRendererFactory(new MermaidNodeRenderer.Factory(baseUrl, krokiUrlSupplier, mermaidImageFormat::get))
                .build();
    }

    /**
     * Kroki 가 설정돼 있으면 mermaid fence 를 same-origin 이미지 서블릿 URL 의 img 로
     * 렌더링한다 — PDF/Word export 는 JS 를 실행하지 않으므로 이미지여야 다이어그램이
     * 살아남는다. Kroki 미설정 시 기본 코드 블록으로 두어 클라이언트 JS 폴백이 받는다.
     */
    private static final class MermaidNodeRenderer implements NodeRenderer {

        private final String baseUrl;
        private final java.util.function.Supplier<String> krokiUrlSupplier;
        private final java.util.function.Supplier<String> formatSupplier;

        private MermaidNodeRenderer(String baseUrl,
                                    java.util.function.Supplier<String> krokiUrlSupplier,
                                    java.util.function.Supplier<String> formatSupplier) {
            this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
            this.krokiUrlSupplier = krokiUrlSupplier;
            this.formatSupplier = formatSupplier;
        }

        @Override
        public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
            return new HashSet<>(Collections.singletonList(
                    new NodeRenderingHandler<>(FencedCodeBlock.class, this::render)));
        }

        private void render(FencedCodeBlock node, NodeRendererContext context, HtmlWriter html) {
            String info = node.getInfo().toString().trim();
            String krokiUrl = krokiUrlSupplier.get();
            if (!"mermaid".equalsIgnoreCase(info) || krokiUrl == null || krokiUrl.isEmpty()) {
                context.delegateRender();
                return;
            }
            String payload = KrokiMermaid.encode(node.getContentChars().toString());
            String format = "png".equals(formatSupplier.get()) ? "png" : "svg";
            html.line();
            html.attr("src", baseUrl + "/plugins/servlet/md-share/mermaid/" + payload + "." + format);
            html.attr("class", "md-share-mermaid");
            html.attr("alt", "mermaid diagram");
            html.withAttr().tagVoid("img");
            html.line();
        }

        static final class Factory implements DelegatingNodeRendererFactory {
            private final String baseUrl;
            private final java.util.function.Supplier<String> krokiUrlSupplier;
            private final java.util.function.Supplier<String> formatSupplier;

            Factory(String baseUrl,
                    java.util.function.Supplier<String> krokiUrlSupplier,
                    java.util.function.Supplier<String> formatSupplier) {
                this.baseUrl = baseUrl;
                this.krokiUrlSupplier = krokiUrlSupplier;
                this.formatSupplier = formatSupplier;
            }

            @Override
            public NodeRenderer apply(DataHolder options) {
                return new MermaidNodeRenderer(baseUrl, krokiUrlSupplier, formatSupplier);
            }

            @Override
            public Set<Class<?>> getDelegates() {
                return null; // delegate to the core renderer
            }
        }
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
        return render(markdown, "svg");
    }

    public String render(String markdown, String mermaidFormat) {
        mermaidImageFormat.set("png".equals(mermaidFormat) ? "png" : "svg");
        try {
            return renderer.render(parser.parse(markdown == null ? "" : markdown));
        } finally {
            mermaidImageFormat.remove();
        }
    }
}
