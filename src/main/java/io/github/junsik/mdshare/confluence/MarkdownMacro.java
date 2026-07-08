package io.github.junsik.mdshare.confluence;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.plugins.whitelist.OutboundWhitelist;
import com.atlassian.plugins.whitelist.WhitelistService;
import com.atlassian.webresource.api.assembler.PageBuilderService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * {md-share} macro: renders Markdown from (in priority order) a page
 * attachment, an allowlisted URL (md-share share links are normalised to
 * their raw endpoint), or the macro body.
 */
public class MarkdownMacro implements Macro {

    private static final String MERMAID_RESOURCE = "io.github.junsik.md-share-confluence:md-share-mermaid";

    private final AttachmentManager attachmentManager;
    private final PageBuilderService pageBuilderService;
    private final PluginConfig pluginConfig;
    private final MarkdownRenderer renderer;
    private final UrlMarkdownFetcher urlFetcher;

    public MarkdownMacro(AttachmentManager attachmentManager,
                         PageBuilderService pageBuilderService,
                         SettingsManager settingsManager,
                         PluginConfig pluginConfig,
                         WhitelistService whitelistService,
                         OutboundWhitelist outboundWhitelist) {
        this.attachmentManager = attachmentManager;
        this.pageBuilderService = pageBuilderService;
        this.pluginConfig = pluginConfig;
        // 절대 URL 이어야 PDF/Word export 의 이미지 fetch 가 확실히 동작한다.
        this.renderer = new MarkdownRenderer(
                settingsManager.getGlobalSettings().getBaseUrl(), pluginConfig::getKrokiUrl);
        this.urlFetcher = new UrlMarkdownFetcher(whitelistService, outboundWhitelist);
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext context)
            throws MacroExecutionException {
        String attachmentName = trimToNull(parameters.get("attachment"));
        String url = trimToNull(parameters.get("url"));

        String mermaidFormat = mermaidFormatFor(context);
        if (attachmentName != null) {
            return renderAttachment(attachmentName, context, mermaidFormat);
        }
        if (url != null) {
            UrlMarkdownFetcher.FetchResult result = urlFetcher.fetch(url);
            if (result.error != null) {
                return errorBox(result.error);
            }
            return renderMarkdown(result.markdown, mermaidFormat);
        }
        if (body != null && !body.trim().isEmpty()) {
            return renderMarkdown(body, mermaidFormat);
        }
        return errorBox("Nothing to render: provide a Markdown attachment, an md-share URL, or a macro body.");
    }

    /** PDF/Word export 렌더러는 SVG 를 못 그리므로 그 경로만 PNG 를 쓴다. */
    private static String mermaidFormatFor(ConversionContext context) {
        String outputType = context == null ? "" : String.valueOf(context.getOutputType());
        return ("pdf".equalsIgnoreCase(outputType) || "word".equalsIgnoreCase(outputType)) ? "png" : "svg";
    }

    private String renderAttachment(String attachmentName, ConversionContext context, String mermaidFormat) {
        ContentEntityObject entity = context.getEntity();
        if (entity == null) {
            return errorBox("The attachment parameter only works on pages and blog posts.");
        }
        Attachment attachment = attachmentManager.getAttachment(entity, attachmentName);
        if (attachment == null) {
            return errorBox("Attachment not found on this page: " + attachmentName
                    + markdownAttachmentHint(entity));
        }
        try (InputStream in = attachmentManager.getAttachmentData(attachment);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return renderMarkdown(new String(out.toByteArray(), StandardCharsets.UTF_8), mermaidFormat);
        } catch (IOException e) {
            return errorBox("Failed to read attachment: " + attachmentName);
        }
    }

    /** 오타를 스스로 고칠 수 있도록 이 페이지의 markdown 첨부 파일명을 알려준다. */
    private String markdownAttachmentHint(ContentEntityObject entity) {
        StringBuilder names = new StringBuilder();
        try {
            for (Attachment candidate : attachmentManager.getLatestVersionsOfAttachments(entity)) {
                String name = candidate.getFileName();
                if (name != null && name.toLowerCase().endsWith(".md")) {
                    names.append(names.length() == 0 ? "" : ", ").append(name);
                }
            }
        } catch (RuntimeException e) {
            return "";
        }
        if (names.length() == 0) {
            return " (this page has no .md attachments)";
        }
        return " — markdown attachments on this page: " + names;
    }

    private String renderMarkdown(String markdown, String mermaidFormat) {
        String html = renderer.render(markdown, mermaidFormat);
        // Kroki 미설정일 때만 클라이언트 JS 폴백 — 설정 시 mermaid 는 이미 img 다.
        if (html.contains("language-mermaid") && pluginConfig.getKrokiUrl().isEmpty()) {
            requireMermaidResources();
        }
        return "<div class=\"md-share-markdown\">" + html + "</div>";
    }

    /** mermaid 번들(2.7MB)은 다이어그램이 실제로 있는 페이지 뷰에서만 로드한다. */
    private void requireMermaidResources() {
        try {
            pageBuilderService.assembler().resources().requireWebResource(MERMAID_RESOURCE);
        } catch (RuntimeException e) {
            // PDF/Word export 같은 비페이지 컨텍스트 — 다이어그램은 코드 블록으로 남는다.
        }
    }

    private static String errorBox(String message) {
        return "<div class=\"md-share-error\">" + escapeHtml(message) + "</div>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.PLAIN_TEXT;
    }

    @Override
    public OutputType getOutputType() {
        return OutputType.BLOCK;
    }
}
