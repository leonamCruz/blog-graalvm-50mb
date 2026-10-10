package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;
import top.leonam.service.PostPaginationService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class PostPaginationController extends BaseController {

    private final PostPaginationService postPaginationService;

    public PostPaginationController(
            PostPaginationService postPaginationService
    ) {
        this.postPaginationService =
                postPaginationService;

        acoes.put(
                "GET",
                this::listar
        );
    }

    private void listar(
            HttpExchange exchange
    ) throws IOException {

        if (!exchange.getRequestURI().getPath().equals("/posts")) {
            enviarResposta(exchange, 404, "Não encontrado");
            return;
        }

        var params =
                obterQueryParams(
                        exchange.getRequestURI()
                );

        var paginaTexto =
                params.getOrDefault(
                        "page",
                        "1"
                );

        int pagina;

        try {
            pagina = Integer.parseInt(
                    paginaTexto
            );
        } catch (NumberFormatException e) {
            pagina = 1;
        }

        if (pagina < 1) {
            pagina = 1;
        }

        var html =
                postPaginationService
                        .processarEGerarHtml(pagina);

        var bytes =
                html.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        "text/html; charset=UTF-8"
                );

        exchange.sendResponseHeaders(
                200,
                bytes.length
        );

        try (var output =
                     exchange.getResponseBody()) {

            output.write(bytes);
        }
    }
}
