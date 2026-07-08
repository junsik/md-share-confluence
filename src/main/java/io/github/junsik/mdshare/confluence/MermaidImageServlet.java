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
 * Serves mermaid diagrams from the same Confluence origin:
 * /plugins/servlet/md-share/mermaid/{kroki-encoded-source}.(svg|png)
 *
 * SVG feeds the web view (vector — crisp at any size); PNG feeds PDF/Word
 * export whose old renderer cannot draw SVG and fetches images without a
 * browser session. The URL payload is the diagram itself (content-addressed),
 * so responses are immutable and cacheable.
 */
public class MermaidImageServlet extends HttpServlet {

    // 디스패처가 pathInfo 를 어떻게 자르든 requestURI 는 원형이 보존된다 — 끝부분만 앵커.
    private static final Pattern PATH = Pattern.compile("/mermaid/([A-Za-z0-9_-]+)\\.(png|svg)$");
    private static final int MAX_CACHE_ENTRIES = 256;

    private final PluginConfig config;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public MermaidImageServlet(PluginConfig config) {
        this.config = config;
    }

    /** Returns {payload, format} or null. */
    static String[] extractPayload(String path) {
        Matcher matcher = PATH.matcher(path == null ? "" : path);
        return matcher.find() ? new String[]{matcher.group(1), matcher.group(2)} : null;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String[] target = extractPayload(request.getRequestURI());
        if (target == null) {
            target = extractPayload(request.getPathInfo());
        }
        if (target == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "no diagram payload in path (md-share saw uri=" + request.getRequestURI()
                            + ", pathInfo=" + request.getPathInfo() + ")");
            return;
        }
        String payload = target[0];
        String format = target[1];
        String cacheKey = payload + "." + format;
        byte[] image = cache.get(cacheKey);
        if (image == null) {
            try {
                image = KrokiMermaid.fetch(config.getKrokiUrl(), format, payload);
            } catch (IOException e) {
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "diagram rendering failed");
                return;
            }
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(cacheKey, image);
        }
        response.setContentType("svg".equals(format) ? "image/svg+xml; charset=utf-8" : "image/png");
        response.setContentLength(image.length);
        response.setHeader("Cache-Control", "public, max-age=86400, immutable");
        response.getOutputStream().write(image);
    }
}
