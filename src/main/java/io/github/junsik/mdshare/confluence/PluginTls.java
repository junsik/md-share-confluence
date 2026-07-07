package io.github.junsik.mdshare.confluence;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * TLS trust for this plugin's own outbound fetches (md-share, Kroki).
 *
 * Old Confluence bundles an old JRE whose cacerts predates the Let's Encrypt
 * roots, and fixing the JVM truststore needs a server restart. Instead, the
 * plugin combines the JVM's default trust with the bundled ISRG roots —
 * scoped to this plugin's connections only, no restart, no global change.
 */
final class PluginTls {

    private static final String[] BUNDLED_CERTS = {"/certs/isrgrootx1.pem", "/certs/isrg-root-x2.pem"};

    private static volatile SSLSocketFactory socketFactory;

    private PluginTls() {
    }

    static void apply(HttpURLConnection connection) {
        if (connection instanceof HttpsURLConnection) {
            SSLSocketFactory factory = factory();
            if (factory != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(factory);
            }
        }
    }

    private static SSLSocketFactory factory() {
        SSLSocketFactory current = socketFactory;
        if (current != null) {
            return current;
        }
        synchronized (PluginTls.class) {
            if (socketFactory == null) {
                socketFactory = build();
            }
            return socketFactory;
        }
    }

    private static SSLSocketFactory build() {
        try {
            X509TrustManager jvmDefault = defaultTrustManager(null);
            X509TrustManager bundled = defaultTrustManager(bundledKeyStore());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new CompositeTrustManager(jvmDefault, bundled)}, null);
            return context.getSocketFactory();
        } catch (Exception e) {
            // trust customisation failed — fall back to the JVM default behaviour
            return null;
        }
    }

    private static X509TrustManager defaultTrustManager(KeyStore keyStore) throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalStateException("no X509TrustManager available");
    }

    private static KeyStore bundledKeyStore() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        CertificateFactory certificates = CertificateFactory.getInstance("X.509");
        for (String resource : BUNDLED_CERTS) {
            try (InputStream in = PluginTls.class.getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                int index = 0;
                for (java.security.cert.Certificate certificate : certificates.generateCertificates(in)) {
                    keyStore.setCertificateEntry(resource + "-" + index++, certificate);
                }
            }
        }
        return keyStore;
    }

    /** Trusts a chain when either the JVM default or the bundled roots trust it. */
    private static final class CompositeTrustManager implements X509TrustManager {
        private final X509TrustManager primary;
        private final X509TrustManager secondary;

        CompositeTrustManager(X509TrustManager primary, X509TrustManager secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                primary.checkClientTrusted(chain, authType);
            } catch (CertificateException e) {
                secondary.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                primary.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                secondary.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> issuers = new ArrayList<>();
            java.util.Collections.addAll(issuers, primary.getAcceptedIssuers());
            java.util.Collections.addAll(issuers, secondary.getAcceptedIssuers());
            return issuers.toArray(new X509Certificate[0]);
        }
    }
}
