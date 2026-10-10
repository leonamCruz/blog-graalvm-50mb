package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public class StaticController extends BaseController {

    private static final String DIRETORIO = "static";

    public StaticController() {
        acoes.put("GET", this::arquivo);
    }

    private void arquivo(HttpExchange exchange) throws IOException {

        var caminho = exchange.getRequestURI().getPath();

        if (caminho.contains("..")) {
            enviarResposta(exchange, 403, "Acesso negado");
            return;
        }

        var recurso = DIRETORIO + caminho;

        try (var input = getClass()
                .getClassLoader()
                .getResourceAsStream(recurso)) {

            if (input == null) {
                enviarResposta(exchange, 404, "Arquivo não encontrado");
                return;
            }


            exchange.getResponseHeaders()
                    .set("Content-Type", obterContentType(caminho));

            exchange.getResponseHeaders().set("Cache-Control", caminho.startsWith("/fonts/") && caminho.endsWith(".woff2")
                    ? "public, max-age=31536000, immutable" : "public, max-age=3600");
            exchange.sendResponseHeaders(200, 0);

            try (var output = exchange.getResponseBody()) {
                input.transferTo(output);
            }
        }
    }

    private String obterContentType(String caminho) {

        if (caminho.endsWith(".woff2")) {
            return "font/woff2";
        }

        if (caminho.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }

        if (caminho.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }

        if (caminho.endsWith(".png")) {
            return "image/png";
        }

        if (caminho.endsWith(".jpg") || caminho.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        if (caminho.endsWith(".svg")) {
            return "image/svg+xml";
        }

        if (caminho.endsWith(".webp")) {
            return "image/webp";
        }

        if (caminho.endsWith(".ico")) {
            return "image/x-icon";
        }

        return "application/octet-stream";
    }
}
