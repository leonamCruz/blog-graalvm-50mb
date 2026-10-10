package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;
import top.leonam.service.TagService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TagController extends BaseController {

    private final TagService tagService;

    public TagController(TagService tagService) {

        this.tagService = tagService;

        acoes.put("GET", this::tag);
    }

    private void tag(HttpExchange exchange) throws IOException {

        var caminho = exchange.getRequestURI().getPath();

        var slug = caminho.substring("/tag/".length());

        var html = tagService.processarEGerarHtml(slug, obterPagina(exchange.getRequestURI()));

        if (html == null) {

            enviarResposta(
                    exchange,
                    404,
                    "Tag não encontrada"
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
