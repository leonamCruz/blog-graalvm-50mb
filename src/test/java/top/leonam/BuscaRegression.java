package top.leonam;

import com.sun.net.httpserver.HttpServer;
import top.leonam.bd.*;
import top.leonam.controller.*;
import top.leonam.service.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.concurrent.Executors;

/** Legacy-data migration and search regression; executed only in a temporary cwd. */
public final class BuscaRegression {
    private static int checks;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        // Seed an old database BEFORE migration, with real content and IDs to preserve.
        try (var conn = Database.conectar(); var stmt = conn.createStatement()) {
            var schema = new String(BuscaRegression.class.getClassLoader().getResourceAsStream("schema.sql").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (var sql : schema.split(";")) if (!sql.isBlank()) stmt.execute(sql);
            stmt.execute("INSERT INTO categoria VALUES (9,'Programação','programacao')");
            stmt.execute("INSERT INTO tag VALUES (7,'Banco de dados','banco')");
            stmt.execute("INSERT INTO post VALUES(42,'SQLite e memória','/img/x.webp','sqlite-memoria','Como otimizar consultas e memória com segurança.',1,'2026-01-01 12:00:00',9)");
            stmt.execute("INSERT INTO post VALUES(43,'Notas sobre Java','x','java','Este artigo apresenta SQLite e consultas.',1,'2026-01-01 12:00:00',9)");
            stmt.execute("INSERT INTO post VALUES(44,'Segredo exclusivo','x','segredo','conteudoexclusivo',0,'2026-01-01 12:00:00',9)");
            stmt.execute("INSERT INTO post_tag VALUES(42,7)");
            // Force a mid-migration conflict: verify rollback doesn't leave a partial index.
            stmt.execute("CREATE TRIGGER post_fts_insert AFTER INSERT ON post BEGIN SELECT 1; END");
        }
        boolean failed = false;
        try { Database.inicializarBanco(); } catch (IllegalStateException e) { failed = true; }
        check(failed, "migration failure surfaces");
        try (var conn = Database.conectar(); var stmt = conn.createStatement()) {
            try (var rs = stmt.executeQuery("SELECT count(*) FROM post")) { check(rs.next() && rs.getInt(1) == 3, "posts survive rollback"); }
            try (var rs = stmt.executeQuery("SELECT count(*) FROM sqlite_schema WHERE name='post_fts'")) { check(rs.next() && rs.getInt(1) == 0, "partial FTS rolled back"); }
            stmt.execute("DROP TRIGGER post_fts_insert");
        }
        Database.inicializarBanco();
        long backups;
        try (var files = Files.list(Path.of("backups"))) { backups = files.count(); }
        Database.inicializarBanco();
        try (var files = Files.list(Path.of("backups"))) { check(files.count() == backups, "second startup does not rebuild or duplicate backup"); }
        try (var conn = Database.conectar(); var stmt = conn.createStatement()) {
            try (var rs = stmt.executeQuery("SELECT * FROM post WHERE id=42")) {
                check(rs.next() && rs.getString("titulo").equals("SQLite e memória")
                    && rs.getString("markdown").equals("Como otimizar consultas e memória com segurança.")
                    && rs.getString("criado_em").equals("2026-01-01 12:00:00") && rs.getLong("categoria_id") == 9, "legacy post unchanged");
            }
            try (var rs = stmt.executeQuery("SELECT count(*) FROM post_tag WHERE post_id=42 AND tag_id=7")) { check(rs.next() && rs.getInt(1)==1, "relationships preserved"); }
            try (var rs = stmt.executeQuery("PRAGMA integrity_check")) { check(rs.next() && rs.getString(1).equals("ok"), "database integrity"); }
            stmt.execute("INSERT INTO post_fts(post_fts, rank) VALUES ('integrity-check', 1)");
        }
        try (var files = Files.list(Path.of("backups"))) {
            var backup = files.findFirst().orElseThrow();
            try (var conn = DriverManager.getConnection("jdbc:sqlite:" + backup); var stmt = conn.createStatement(); var rs = stmt.executeQuery("SELECT count(*) FROM post")) {
                check(rs.next() && rs.getInt(1)==3, "backup contains all legacy posts");
            }
        }
        var repo = new BuscaImpl();
        check(repo.buscar("\"sqlite\"*", 11, 0).getFirst().slug().equals("sqlite-memoria"), "title relevance beats body-only match");
        var busca = new BuscaService(repo);
        check(busca.gerarHtml("memoria", 1).contains("sqlite-memoria"), "accent insensitive search");
        check(busca.gerarHtml("segur", 1).contains("<mark>segurança</mark>"), "prefix and snippet highlighting");
        check(busca.gerarHtml("sqlite consultas", 1).contains("java"), "multiple terms searched");
        check(!busca.gerarHtml("conteudoexclusivo", 1).contains("/blog/segredo"), "draft not searchable");
        check(busca.gerarHtml("\" OR * NEAR ( )", 1).contains("Nenhum post"), "FTS operators treated as words");
        check(busca.gerarHtml("a".repeat(161), 1).contains("160"), "query length bounded");
        check(busca.gerarHtml("aa bb cc dd ee ff gg hh ii", 1).contains("8 palavras"), "token count bounded");
        check(busca.gerarHtml("*()\"", 1).contains("pelo menos 2"), "punctuation safe");
        check(busca.gerarHtml("", 1).contains("Pesquise"), "clear search");
        try (var conn = Database.conectar(); var stmt = conn.createStatement()) {
            stmt.execute("UPDATE post SET markdown='palavranova <img src=x onerror=alert(1)>' WHERE id=42");
            check(busca.gerarHtml("palavranova", 1).contains("sqlite-memoria"), "updates synchronized");
            check(!busca.gerarHtml("segur", 1).contains("sqlite-memoria"), "old terms removed");
            check(!busca.gerarHtml("palavranova", 1).contains("<img src=x"), "snippet HTML escaped");
            stmt.execute("UPDATE post SET markdown='palavranova ![Capa](/arquivos/download/imagem.png) **conteúdo**' WHERE id=42");
            var trechoLimpo = busca.gerarHtml("palavranova", 1);
            check(!trechoLimpo.contains("/arquivos/download/") && trechoLimpo.contains("Capa") && !trechoLimpo.contains("**"), "snippet without Markdown link noise");
            stmt.execute("UPDATE post SET publicado=1 WHERE id=44");
            check(busca.gerarHtml("conteudoexclusivo", 1).contains("/blog/segredo"), "published draft searchable");
            stmt.execute("UPDATE post SET publicado=0 WHERE id=44");
            check(!busca.gerarHtml("conteudoexclusivo", 1).contains("/blog/segredo"), "unpublished post hidden immediately");
            stmt.execute("INSERT INTO post(titulo,capa,slug,markdown) VALUES ('Apagável','x','apagavel','termodeletavel')");
            check(busca.gerarHtml("termodeletavel", 1).contains("apagavel"), "insert synchronized");
            stmt.execute("DELETE FROM post WHERE slug='apagavel'");
            check(!busca.gerarHtml("termodeletavel", 1).contains("/blog/apagavel"), "delete synchronized");
            for (int i=0; i<12; i++) stmt.execute("INSERT INTO post(titulo,capa,slug,markdown) VALUES ('Paginado','x','paginado-"+i+"','termpaginado')");
            check(busca.gerarHtml("termpaginado", 1).split("class=\"search-result\"", -1).length==11, "ten results per page");
            check(busca.gerarHtml("termpaginado", 1).contains("Próxima"), "next page");
            check(busca.gerarHtml("termpaginado", 2).split("class=\"search-result\"", -1).length==3, "remaining results");
            stmt.execute("INSERT INTO post_fts(post_fts, rank) VALUES ('integrity-check', 1)");
        }
        var index = new IndexService(new PostImpl(), new CategoriaImpl(), new TagImpl());
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/buscar", new BuscaController(busca, index));
        server.createContext("/", new IndexController(index));
        server.start();
        try (var client = HttpClient.newHttpClient()) {
            var url = "http://localhost:" + server.getAddress().getPort();
            var request = HttpRequest.newBuilder(URI.create(url+"/buscar?q=sqlite")).header("HX-Request", "true").build();
            var fragment = client.send(request, HttpResponse.BodyHandlers.ofString());
            check(fragment.statusCode()==200 && !fragment.body().contains("<!DOCTYPE"), "HTMX fragment");
            check(fragment.body().contains("sqlite-memoria"), "HTTP search results");
            check(fragment.headers().firstValue("Vary").orElse("").contains("HX-Request"), "fragment cache varies");
            var page = client.send(HttpRequest.newBuilder(URI.create(url+"/buscar?q=sqlite")).build(), HttpResponse.BodyHandlers.ofString());
            check(page.body().contains("<!DOCTYPE html>") && page.body().contains("value=\"sqlite\""), "reload and non-JS full page");
            check(page.body().contains("hx-sync=\"this:replace\"") && page.body().contains("delay:350ms"), "debounce and replace stale requests");
            var home = client.send(HttpRequest.newBuilder(URI.create(url+"/")).build(), HttpResponse.BodyHandlers.ofString());
            check(home.body().contains("role=\"search\"") && !home.body().contains("{{busca."), "home form resolved");
            int form = home.body().indexOf("id=\"busca-form\"");
            int navInicio = home.body().indexOf("<nav aria-label=\"Navegação principal\"");
            int navFim = home.body().indexOf("</nav>", navInicio);
            check(form > navInicio && form < navFim, "search form inside navbar");
            check(client.send(HttpRequest.newBuilder(URI.create(url+"/buscar-extra?q=sqlite")).build(), HttpResponse.BodyHandlers.ofString()).statusCode()==404, "exact search route");
            var xss = client.send(HttpRequest.newBuilder(URI.create(url+"/buscar?q=%3Cscript%3Ealert%281%29%3C%2Fscript%3E")).header("HX-Request","true").build(), HttpResponse.BodyHandlers.ofString());
            check(!xss.body().contains("<script>"), "query reflected safely");
        } finally { server.stop(0); executor.close(); }
        System.out.println("OK: " + checks + " verificações de migração, FTS5 e HTMX");
    }
}
