package io.github.junsik.mdshare.confluence;

import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

/**
 * Runtime plugin configuration stored in Confluence (PluginSettings), editable
 * through the admin servlet without a server restart. The old system property
 * is kept as a fallback for installations that already configured it.
 */
public class PluginConfig {

    static final String KROKI_URL_SETTINGS_KEY = "io.github.junsik.md-share-confluence.kroki-url";

    private final PluginSettingsFactory settingsFactory;

    public PluginConfig(PluginSettingsFactory settingsFactory) {
        this.settingsFactory = settingsFactory;
    }

    public String getKrokiUrl() {
        Object stored = settingsFactory.createGlobalSettings().get(KROKI_URL_SETTINGS_KEY);
        String url = stored instanceof String ? ((String) stored).trim() : "";
        if (url.isEmpty()) {
            url = System.getProperty(KrokiMermaid.KROKI_URL_PROPERTY, "").trim();
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public void setKrokiUrl(String url) {
        settingsFactory.createGlobalSettings()
                .put(KROKI_URL_SETTINGS_KEY, url == null ? "" : url.trim());
    }
}
