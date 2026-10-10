package top.leonam;

import top.leonam.bd.*;
import top.leonam.service.*;
import top.leonam.util.HtmlCache;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;

/** Runs only against a disposable database in a temporary cwd. */
public final class PerformanceRegression {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Database.inicializarBanco();
        try (var c = Database.conectar(); var s = c.createStatement()) {
            try (var rs = s.executeQuery("PRAGMA journal_mode")) {
                check(rs.next() && rs.getString(1).equalsIgnoreCase("wal"), "WAL enabled");
            }
            s.execute("CREATE TEMP TABLE reuse(value INTEGER)");
            s.execute("INSERT INTO reuse VALUES (7)");
            s.execute("INSERT INTO categoria VALUES(1,'Java','java')");
            s.execute("INSERT INTO tag VALUES(1,'Teste','teste')");
            for (int i = 1; i <= 13; i++) s.execute("INSERT INTO post VALUES(" + i + ",'Post " + i + "','x','p" + i + "','markdown',1,'2026-01-01 00:00:00',1)");
        }
        var lease = Database.conectar();
        try (var s = lease.createStatement(); var rs = s.executeQuery("SELECT value FROM reuse")) {
            check(rs.next() && rs.getInt(1) == 7, "physical connection reused");
        }
        lease.setAutoCommit(false);
        try (var s = lease.createStatement()) { s.execute("INSERT INTO tag VALUES(2,'Rollback','rollback')"); }
        lease.close();
        lease.close();
        check(lease.isClosed(), "lease closed, double close safe");
        boolean rejected = false;
        try { lease.createStatement(); } catch (java.sql.SQLException e) { rejected = true; }
        check(rejected, "returned lease unusable");
        try (var c = Database.conectar(); var s = c.createStatement(); var rs = s.executeQuery("SELECT COUNT(*) FROM tag")) {
            check(c.getAutoCommit(), "autocommit restored");
            check(rs.next() && rs.getInt(1) == 1, "unfinished transaction rolled back");
        }
        var held = new java.util.ArrayList<java.sql.Connection>();
        for (int i = 0; i < 8; i++) held.add(Database.conectar());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var ninth = executor.submit(() -> { try (var c = Database.conectar()) { return c.getAutoCommit(); } });
            Thread.sleep(30);
            check(!ninth.isDone(), "pool waits when full");
            held.removeFirst().close();
            check(ninth.get(3, TimeUnit.SECONDS), "waiting borrower resumes");
        } finally { for (var c : held) c.close(); }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<Future<Integer>>();
            for (int i = 0; i < 100; i++) futures.add(executor.submit(() -> {
                try (var c = Database.conectar(); var s = c.createStatement(); var rs = s.executeQuery("SELECT COUNT(*) FROM post")) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            }));
            for (var f : futures) check(f.get(5, TimeUnit.SECONDS) == 13, "concurrent pool query");
        }
        var repo = new PostImpl();
        var inicio = repo.buscarInicio();
        check(inicio.posts().size() == 10 && inicio.restantes() == 8, "combined initial query");
        check(inicio.posts().getFirst().id == 13 && inicio.posts().getLast().id == 4, "stable initial ordering");
        var post = new PostService(repo);
        var plain = post.processarHtml("p1", false);
        var gzip = post.processarHtml("p1", true);
        check(plain == post.processarHtml("p1", false), "post served from cache");
        try (var in = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            check(java.util.Arrays.equals(plain, in.readAllBytes()), "gzip and plain equivalent");
        }
        var index = new IndexService(repo, new CategoriaImpl(), new TagImpl());
        var home = index.processarHtml(false);
        check(home == index.processarHtml(false), "home served from cache");
        var homeHtml = new String(home, StandardCharsets.UTF_8);
        check(homeHtml.split("fetchpriority=\"high\"", -1).length == 2, "only first carousel cover has high priority");
        check(!homeHtml.contains("{{post.prioridadeImagem}}"), "image priority placeholder resolved");
        check(homeHtml.contains("as=\"font\" type=\"font/woff2\" crossorigin"), "font preloaded with matching CORS mode");
        var search = new String(index.processarHtml(false, "consulta", "RESULTADO"), StandardCharsets.UTF_8);
        check(search.contains("RESULTADO") && !new String(home, StandardCharsets.UTF_8).contains("RESULTADO"), "search isolated from home cache");
        check(post.processarHtml("missing", false) == null, "missing post stays missing");
        var renders = new AtomicInteger();
        var cache = new HtmlCache(1000, Duration.ofMillis(60));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new java.util.ArrayList<Future<HtmlCache.Page>>();
            for (int i = 0; i < 30; i++) futures.add(executor.submit(() -> cache.get("a", () -> { renders.incrementAndGet(); return "hello"; })));
            for (var f : futures) f.get();
            check(renders.get() == 1, "concurrent cache misses coalesced");
        }
        Thread.sleep(80);
        cache.get("a", () -> { renders.incrementAndGet(); return "changed"; });
        check(renders.get() == 2, "TTL refresh");
        var tiny = new HtmlCache(400, Duration.ofMinutes(1));
        var a = tiny.get("a", () -> "a".repeat(100));
        tiny.get("b", () -> "b".repeat(100));
        check(tiny.get("a", () -> "new") != a, "byte-budget eviction");
        var oversized = new HtmlCache(100, Duration.ofMinutes(1));
        check(oversized.get("a", () -> "too big") != oversized.get("a", () -> "too big"), "oversized entries not retained");
        System.out.println("OK: " + checks + " verificações de pool, WAL e cache");
    }
}
