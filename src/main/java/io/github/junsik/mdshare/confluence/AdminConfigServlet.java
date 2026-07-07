package io.github.junsik.mdshare.confluence;

import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * /plugins/servlet/md-share/admin — Confluence 관리자만 접근하는 설정 화면.
 * Kroki URL 을 PluginSettings 에 저장하므로 서버 재시작 없이 즉시 반영된다.
 */
public class AdminConfigServlet extends HttpServlet {

    private final PluginConfig config;
    private final PermissionManager permissionManager;

    public AdminConfigServlet(PluginConfig config, PermissionManager permissionManager) {
        this.config = config;
        this.permissionManager = permissionManager;
    }

    private boolean requireAdmin(HttpServletResponse response) throws IOException {
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null || !permissionManager.isConfluenceAdministrator(user)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Confluence administrators only");
            return false;
        }
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireAdmin(response)) {
            return;
        }
        boolean saved = "1".equals(request.getParameter("saved"));
        response.setContentType("text/html; charset=utf-8");
        response.getWriter().write(page(config.getKrokiUrl(), saved));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!requireAdmin(response)) {
            return;
        }
        request.setCharacterEncoding("UTF-8");
        config.setKrokiUrl(request.getParameter("krokiUrl"));
        response.sendRedirect(request.getRequestURI() + "?saved=1");
    }

    private static String page(String krokiUrl, boolean saved) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>md-share settings</title></head>"
                + "<body style=\"font-family:sans-serif;max-width:640px;margin:48px auto\">"
                + "<h1>md-share for Confluence</h1>"
                + (saved ? "<p style=\"color:#2d7a46\">저장되었습니다. 즉시 적용됩니다 (재시작 불필요).</p>" : "")
                + "<form method=\"post\">"
                + "<h3>Kroki URL</h3>"
                + "<p>mermaid 다이어그램을 서버사이드 이미지로 렌더링하는 Kroki 서비스 주소."
                + " 비우면 다이어그램은 브라우저에서만 렌더링되고 PDF 내보내기에서는 코드로 표시됩니다.</p>"
                + "<input type=\"text\" name=\"krokiUrl\" value=\"" + escapeHtml(krokiUrl) + "\""
                + " placeholder=\"https://kroki.example.com\" style=\"width:100%;padding:6px\"/>"
                + "<p><button type=\"submit\">저장</button></p>"
                + "</form></body></html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
