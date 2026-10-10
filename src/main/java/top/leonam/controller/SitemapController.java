package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import top.leonam.bd.PostImpl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SitemapController extends BaseController {

    private static final String SITE_URL = "https://leonam.top";

    private final PostImpl postRepository;

    public SitemapController(PostImpl postRepository) {
        this.postRepository = postRepository;
        acoes.put("GET", this::sitemap);
    }

    private void sitemap(HttpExchange exchange) throws IOException {

        if (!exchange.getRequestURI().getPath().equals("/sitemap.xml")) {
            enviarResposta(exchange, 404, "Não encontrado");
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        var xml = new StringBuilder();

        xml.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);

        xml.append("""
                <url>
                    <loc>%s/</loc>
                </url>
                """.formatted(SITE_URL));

        xml.append("""
                <url>
                    <loc>%s/arquivos</loc>
                </url>
                """.formatted(SITE_URL));

        for (var post : postRepository.buscarSomenteSlugs()) {

            xml.append("""
                    <url>
                        <loc>%s/blog/%s</loc>
                    </url>
                    """.formatted(
                    SITE_URL,
                    top.leonam.util.Web.segmento(post)
            ));
        }

        xml.append("</urlset>");

        var resposta = xml.toString();

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/xml; charset=UTF-8"
        );

        var bytes = resposta.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(200, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
