package top.leonam;

import com.sun.net.httpserver.HttpServer;
import top.leonam.bd.*;
import top.leonam.controller.*;
import top.leonam.service.*;
import top.leonam.util.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** Run through tools/verificar.sh: all database and filesystem fixtures live in a temporary cwd. */
public class SegurancaRegression {
    static int checks;
    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Database.inicializarBanco();
        Database.inicializarBanco();
        try (var conn = Database.conectar(); var stmt = conn.createStatement()) {
            try (var rs = stmt.executeQuery("PRAGMA foreign_keys")) {
                check(rs.next() && rs.getInt(1) == 1, "foreign keys on every connection");
            }
            try (var rs = stmt.executeQuery("PRAGMA cache_size")) {
                check(rs.next() && rs.getInt(1) == -1024, "1 MiB cache per connection");
            }
            stmt.executeUpdate("INSERT INTO post(titulo,capa,slug,markdown) VALUES ('Teste','/img/jeff.webp','teste','<img src=x onerror=alert(1)>')");
            stmt.executeUpdate("INSERT INTO post(titulo,capa,slug,markdown,publicado) VALUES ('Rascunho','x','privado','segredo',0)");
            try (var rs = stmt.executeQuery("EXPLAIN QUERY PLAN SELECT id FROM post WHERE publicado = 1 ORDER BY criado_em DESC, id DESC LIMIT 5")) {
                check(rs.next() && rs.getString(4).contains("idx_post_publicado_data"), "listing uses index");
            }
        }
        try (var conn = Database.conectar(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO categoria(id,nome,slug) VALUES (1,'Categoria','cat')");
            stmt.executeUpdate("INSERT INTO tag(id,nome,slug) VALUES (1,'Tag','tag')");
            for (int i = 0; i < 60; i++) {
                stmt.executeUpdate("INSERT INTO post(titulo,capa,slug,markdown,categoria_id) VALUES ('Extra','x','extra-" + i + "','x',1)");
                stmt.executeUpdate("INSERT INTO post_tag VALUES (last_insert_rowid(),1)");
            }
        }
        var repo = new PostImpl();
        var lista = new PostListService();
        var categoria = new CategoriaService(new CategoriaImpl(), repo, lista);
        var tag = new TagService(new TagImpl(), repo, lista);
        check(categoria.processarEGerarHtml("cat", 1).split("post-card", -1).length == 51, "category limited to 50 cards");
        check(categoria.processarEGerarHtml("cat", 2).split("post-card", -1).length == 11, "category second page");
        check(tag.processarEGerarHtml("tag", 1).contains("Próxima"), "tag next link");
        check(!tag.processarEGerarHtml("tag", 2).contains("Próxima"), "tag final page");
        check(repo.buscarPorTagId(1).size() == 60, "legacy repository API preserved");
        check(repo.buscarPorSlug("privado") == null, "draft private");
        check(repo.buscarPorSlug("' OR 1=1 --") == null, "SQL injection blocked");
        var arquivos = new ArquivoService();
        Files.writeString(Path.of("secret.txt"), "segredo");
        Files.writeString(Path.of("arquivos/a #&.txt"), "olá");
        Files.writeString(Path.of("arquivos/ativo.html"), "<script>alert(1)</script>");
        Files.createSymbolicLink(Path.of("arquivos/link"), Path.of("../secret.txt"));
        check(arquivos.obterCaminhoSeguro("../secret.txt") == null, "traversal denied");
        check(arquivos.obterCaminhoSeguro("link") == null, "symlink denied");
        check(arquivos.obterCaminhoSeguro("\0") == null, "invalid path denied");
        check(arquivos.listarHtml("").contains("a%20%23%26.txt"), "file URL encoded");
        check(!arquivos.listarHtml("").contains("/download/link"), "symlink hidden");
        check(Web.escaparHtml("<>&\"'").equals("&lt;&gt;&amp;&quot;&#39;"), "escaping");
        check(Paginacao.paginas(500000, 1000000).size() <= 9, "bounded pagination");
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 16);
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/blog/", new PostController(new PostService(repo)));
        server.createContext("/arquivos", new ArquivoController(arquivos));
        server.createContext("/js/", new StaticController());
        server.createContext("/fonts/", new StaticController());
        server.createContext("/sitemap.xml", new SitemapController(repo));
        server.createContext("/", new IndexController(new IndexService(repo, new CategoriaImpl(), new TagImpl())));
        server.createContext("/falha", new BaseController() {{ acoes.put("GET", ex -> { throw new IllegalStateException("SEGREDO_INTERNO"); }); }});
        var client = HttpClient.newHttpClient();
        var bloqueados = new CountDownLatch(64);
        var liberar = new CountDownLatch(1);
        server.createContext("/lento", new BaseController() {{ acoes.put("GET", ex -> {
            bloqueados.countDown();
            try { liberar.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            enviarResposta(ex, 200, "OK");
        }); }});
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        try {
            for (var path : new String[]{"/", "/blog/teste"}) {
                var plain = client.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofString());
                check(plain.statusCode() == 200 && plain.body().contains("<!DOCTYPE html>"), "identity response " + path);
                check(plain.headers().firstValue("Content-Encoding").isEmpty(), "no forced gzip");
                var gzip = client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Accept-Encoding", "gzip").build(), HttpResponse.BodyHandlers.ofByteArray());
                try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(gzip.body()))) {
                    check(new String(in.readAllBytes(), StandardCharsets.UTF_8).equals(plain.body()), "gzip equals identity");
                }
                var denied = client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Accept-Encoding", "gzip;q=0, *;q=1").build(), HttpResponse.BodyHandlers.ofString());
                check(denied.headers().firstValue("Content-Encoding").isEmpty(), "q=0 wins over wildcard");
            }
            var font = client.send(HttpRequest.newBuilder(URI.create(base + "/fonts/vt323-latin-043a60145af2.woff2")).build(), HttpResponse.BodyHandlers.ofByteArray());
            check(font.statusCode() == 200 && font.body().length == 7192, "local WOFF2 served");
            check(font.headers().firstValue("Content-Type").orElse("").equals("font/woff2"), "WOFF2 MIME type");
            check(font.headers().firstValue("Cache-Control").orElse("").contains("immutable"), "versioned font immutable cache");
            var download = client.send(HttpRequest.newBuilder(URI.create(base + "/arquivos/download/ativo.html?view=true")).build(), HttpResponse.BodyHandlers.ofString());
            check(download.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment;"), "HTML never inline");
            check(download.headers().firstValue("X-Content-Type-Options").orElse("").equals("nosniff"), "nosniff");
            for (var path : new String[]{"/arquivos/download/link", "/sitemap.xml-extra", "/arquivos-invalido", "/blog/privado"}) {
                check(client.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 404, "404 " + path);
            }
            var failed = client.send(HttpRequest.newBuilder(URI.create(base + "/falha")).build(), HttpResponse.BodyHandlers.ofString());
            check(failed.statusCode() == 500 && !failed.body().contains("SEGREDO"), "errors don't disclose details");
            check(client.send(HttpRequest.newBuilder(URI.create(base + "/sitemap.xml")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 405, "method denied");
            var asset = client.send(HttpRequest.newBuilder(URI.create(base + "/js/purify.min.js")).build(), HttpResponse.BodyHandlers.ofString());
            check(asset.statusCode() == 200 && asset.body().contains("DOMPurify"), "local sanitizer served");
            var page = client.send(HttpRequest.newBuilder(URI.create(base + "/blog/teste")).build(), HttpResponse.BodyHandlers.ofString()).body();
            check(page.contains("DOMPurify.sanitize") && page.contains("contentEl.textContent = markdownEl.value"), "safe rendering and fallback wired");
            check(page.contains("&lt;img src=x onerror=alert(1)&gt;"), "markdown escaped before rendering");
            var requests = new java.util.ArrayList<CompletableFuture<HttpResponse<String>>>();
            for (int i = 0; i < 64; i++) {
                requests.add(client.sendAsync(HttpRequest.newBuilder(URI.create(base + "/lento")).build(), HttpResponse.BodyHandlers.ofString()));
            }
            check(bloqueados.await(8, TimeUnit.SECONDS), "64 active requests without admission limit");
            var busy = client.send(HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString());
            check(busy.statusCode() == 200, "home succeeds while 64 requests are active");
            liberar.countDown();
            for (var request : requests) check(request.get(5, TimeUnit.SECONDS).statusCode() == 200, "active request completed");
            check(client.send(HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 200, "capacity recovered");
        } finally {
            liberar.countDown();
            server.stop(0);
            executor.close();
            client.close();
        }
        System.out.println("OK: " + checks + " verificações de segurança e HTTP");
    }
}
