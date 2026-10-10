package top.leonam.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

/** Byte-bounded LRU with TTL and striped locks to coalesce concurrent misses. */
public final class HtmlCache {
    public record Page(byte[] plain, byte[] gzip) {
        public byte[] bytes(boolean compressed) { return compressed ? gzip : plain; }
    }
    private record Entry(Page page, long expires, long size) {}
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private final Object[] locks = new Object[64];
    private final long maxBytes, ttlNanos;
    private long used;

    public HtmlCache(long maxBytes, java.time.Duration ttl) {
        if (maxBytes <= 0 || ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException();
        this.maxBytes = maxBytes;
        ttlNanos = ttl.toNanos();
        java.util.Arrays.setAll(locks, i -> new Object());
    }

    private synchronized Page find(String key) {
        var entry = entries.get(key);
        if (entry == null) return null;
        if (System.nanoTime() - entry.expires >= 0) {
            entries.remove(key);
            used -= entry.size;
            return null;
        }
        return entry.page;
    }

    public Page get(String key, Supplier<String> render) {
        var hit = find(key);
        if (hit != null) return hit;
        synchronized (locks[(key.hashCode() & Integer.MAX_VALUE) % locks.length]) {
            hit = find(key);
            if (hit != null) return hit;
            var html = render.get();
            if (html == null) return null; // Missing slugs do not fill the cache.
            var plain = html.getBytes(StandardCharsets.UTF_8);
            var page = new Page(plain, compress(plain));
            long size = (long) plain.length + page.gzip.length + 2L * key.length() + 128;
            synchronized (this) {
                if (size <= maxBytes) {
                    while (used + size > maxBytes && !entries.isEmpty()) {
                        var iterator = entries.entrySet().iterator();
                        used -= iterator.next().getValue().size;
                        iterator.remove();
                    }
                    entries.put(key, new Entry(page, System.nanoTime() + ttlNanos, size));
                    used += size;
                }
            }
            return page;
        }
    }

    private static byte[] compress(byte[] plain) {
        try (var out = new ByteArrayOutputStream(); var gzip = new GZIPOutputStream(out)) {
            gzip.write(plain);
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
