package io.github.junsik.mdshare.confluence;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serves mermaid diagrams as PNG from the same Confluence origin:
 * /plugins/servlet/md-share/mermaid/{kroki-encoded-source}.png
 *
 * The PDF/Word exporter fetches images without a browser session, and the
 * old export renderer has no data-URI support — a plain same-origin image
 * URL is the one thing it handles reliably. The URL payload is the diagram
 * itself (content-addressed), so responses are immutable and cacheable.
 */
public class MermaidImageServlet extends HttpServlet {

    private static final Pattern PATH = Pattern.compile("^/mermaid/([A-Za-z0-9_-]+)\\.png$");
    private static final int MAX_CACHE_ENTRIES = 256;

    private final PluginConfig config;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public MermaidImageServlet(PluginConfig config) {
        this.config = config;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Matcher matcher = PATH.matcher(request.getPathInfo() == null ? "" : request.getPathInfo());
        if (!matcher.matches()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String payload = matcher.group(1);
        byte[] png = cache.get(payload);
        if (png == null) {
            try {
                png = KrokiMermaid.fetchPng(config.getKrokiUrl(), payload);
            } catch (IOException e) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "diagram rendering failed");
                return;
            }
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(payload, png);
        }
        response.setContentType("image/png");
        response.setContentLength(png.length);
        response.setHeader("Cache-Control", "public, max-age=86400, immutable");
        response.getOutputStream().write(png);
    }
}
