package io.github.junsik.mdshare.confluence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small TTL cache so page views do not refetch the same URL on every render. */
final class TtlCache {

    private static final int MAX_ENTRIES = 256;

    private static final class Entry {
        final String value;
        final long expiresAtMillis;

        Entry(String value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;

    TtlCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    String get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMillis < System.currentTimeMillis()) {
            entries.remove(key);
            return null;
        }
        return entry.value;
    }

    void put(String key, String value) {
        if (entries.size() >= MAX_ENTRIES) {
            entries.clear();
        }
        entries.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
    }
}
