package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;
import top.leonam.service.IndexService;

import java.io.IOException;

public class IndexController extends BaseController {

    private final IndexService indexService;

    public IndexController(IndexService indexService) {
        this.indexService = indexService;

        acoes.put("GET", this::index);
    }

    private void index(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals("/")) {
            retornarErro404(exchange);
            return;
        }

        var gzip = top.leonam.util.Web.aceitaGzip(exchange);
        var bytesGzip = indexService.processarHtml(gzip);
        if (gzip) exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        exchange.getResponseHeaders().set("Vary", "Accept-Encoding");
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytesGzip.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytesGzip);
        }
    }

    private void retornarErro404(HttpExchange exchange) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream("templates/404.html")) {
            byte[] html404 = (input != null)
                    ? input.readAllBytes()
                    : "<h1>404 - Página não encontrada</h1>".getBytes();

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(404, html404.length);
            try (var output = exchange.getResponseBody()) {
                output.write(html404);
            }
        }
    }
}
