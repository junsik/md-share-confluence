package io.github.junsik.mdshare.confluence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.Deflater;

/**
 * Server-side mermaid rendering through a Kroki service (https://kroki.io).
 *
 * Diagram sources are encoded with Kroki's stateless GET format (zlib deflate
 * + base64url), so image URLs carry the whole diagram and nothing has to be
 * stored between page render and image fetch — which is what makes the same
 * URL work during PDF/Word export.
 */
public final class KrokiMermaid {

    static final String KROKI_URL_PROPERTY = "mdshare.confluence.kroki-url";

    private static final int TIMEOUT_MILLIS = 15_000;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private KrokiMermaid() {
    }

    /** Kroki GET encoding: zlib deflate + base64url without padding. */
    public static String encode(String source) {
        byte[] input = source.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length / 2 + 16);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    public static byte[] fetchPng(String krokiBaseUrl, String payload) throws IOException {
        if (krokiBaseUrl == null || krokiBaseUrl.isEmpty()) {
            throw new IOException("Kroki is not configured");
        }
        HttpURLConnection connection =
                (HttpURLConnection) new URL(krokiBaseUrl + "/mermaid/png/" + payload).openConnection();
        PluginTls.apply(connection);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "image/png");
        int status = connection.getResponseCode();
        if (status != 200) {
            throw new IOException("Kroki returned HTTP " + status);
        }
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new IOException("diagram image exceeds " + MAX_IMAGE_BYTES + " bytes");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }
}
