package io.github.junsik.mdshare.confluence;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.pages.Attachment;
import com.atlassian.confluence.pages.AttachmentManager;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

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
@Scanned
public class MarkdownMacro implements Macro {

    private final AttachmentManager attachmentManager;
    private final MarkdownRenderer renderer = new MarkdownRenderer();
    private final UrlMarkdownFetcher urlFetcher = new UrlMarkdownFetcher();

    public MarkdownMacro(@ComponentImport AttachmentManager attachmentManager) {
        this.attachmentManager = attachmentManager;
    }

    @Override
    public String execute(Map<String, String> parameters, String body, ConversionContext context)
            throws MacroExecutionException {
        String attachmentName = trimToNull(parameters.get("attachment"));
        String url = trimToNull(parameters.get("url"));

        if (attachmentName != null) {
            return renderAttachment(attachmentName, context);
        }
        if (url != null) {
            UrlMarkdownFetcher.FetchResult result = urlFetcher.fetch(url);
            if (result.error != null) {
                return errorBox(result.error);
            }
            return renderMarkdown(result.markdown);
        }
        if (body != null && !body.trim().isEmpty()) {
            return renderMarkdown(body);
        }
        return errorBox("Nothing to render: provide a Markdown attachment, an md-share URL, or a macro body.");
    }

    private String renderAttachment(String attachmentName, ConversionContext context) {
        ContentEntityObject entity = context.getEntity();
        if (entity == null) {
            return errorBox("The attachment parameter only works on pages and blog posts.");
        }
        Attachment attachment = attachmentManager.getAttachment(entity, attachmentName);
        if (attachment == null) {
            return errorBox("Attachment not found on this page: " + attachmentName);
        }
        try (InputStream in = attachmentManager.getAttachmentData(attachment);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return renderMarkdown(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return errorBox("Failed to read attachment: " + attachmentName);
        }
    }

    private String renderMarkdown(String markdown) {
        return "<div class=\"md-share-markdown\">" + renderer.render(markdown) + "</div>";
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
