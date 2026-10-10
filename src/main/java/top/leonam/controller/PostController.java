package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;
import top.leonam.service.PostService;

import java.io.IOException;

public class PostController extends BaseController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;

        acoes.put("GET", this::post);
    }

    private void post(HttpExchange exchange) throws IOException {

        try {
            var caminho = exchange.getRequestURI().getPath();
            var slug = caminho.substring("/blog/".length());

            // Recebe diretamente o byte[] comprimido
            var gzip = top.leonam.util.Web.aceitaGzip(exchange);
        var bytesGzip = postService.processarHtml(slug, gzip);

            if (bytesGzip == null) {
                enviarResposta(exchange, 404, "Post não encontrado");
                return;
            }

            // Headers obrigatórios para o navegador entender a compressão
            if (gzip) exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        exchange.getResponseHeaders().set("Vary", "Accept-Encoding");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");

            exchange.sendResponseHeaders(200, bytesGzip.length);

            try (var output = exchange.getResponseBody()) {
                output.write(bytesGzip);
            }

        } catch (Exception e) {
            throw new IOException("Falha ao renderizar post", e);
        }
    }
}
