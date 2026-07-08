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

    // Confluence 의 플러그인 서블릿 디스패처는 pathInfo 로 /plugins/servlet 뒤 전체 경로를
    // 넘긴다 (url-pattern 기준으로 잘라주지 않음) — 끝부분만 앵커해서 두 형태 모두 받는다.
    private static final Pattern PATH = Pattern.compile("/mermaid/([A-Za-z0-9_-]+)\\.png$");
    private static final int MAX_CACHE_ENTRIES = 256;

    private final PluginConfig config;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public MermaidImageServlet(PluginConfig config) {
        this.config = config;
    }

    static String extractPayload(String path) {
        Matcher matcher = PATH.matcher(path == null ? "" : path);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 디스패처마다 pathInfo 를 다르게 잘라 주므로(전체 경로/패턴 이후만/null),
        // 항상 원형이 보존되는 requestURI 를 우선으로 payload 를 찾는다.
        String payload = extractPayload(request.getRequestURI());
        if (payload == null) {
            payload = extractPayload(request.getPathInfo());
        }
        if (payload == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "no diagram payload in path (md-share v0.3.2 saw uri=" + request.getRequestURI()
                            + ", pathInfo=" + request.getPathInfo() + ")");
            return;
        }
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
