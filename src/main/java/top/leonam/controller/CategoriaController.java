package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;
import top.leonam.service.CategoriaService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CategoriaController extends BaseController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {

        this.categoriaService = categoriaService;

        acoes.put("GET", this::categoria);
    }

    private void categoria(HttpExchange exchange) throws IOException {

        var caminho = exchange.getRequestURI().getPath();

        var slug = caminho.substring("/categoria/".length());

        var html = categoriaService.processarEGerarHtml(slug, obterPagina(exchange.getRequestURI()));

        if (html == null) {

            enviarResposta(
                    exchange,
                    404,
                    "Categoria não encontrada"
            );

            return;
        }

        var bytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders()
                .set("Content-Type", "text/html; charset=UTF-8");

        exchange.sendResponseHeaders(200, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
