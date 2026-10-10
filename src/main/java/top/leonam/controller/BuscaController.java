package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;
import top.leonam.service.BuscaService;
import top.leonam.service.IndexService;
import top.leonam.util.Web;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class BuscaController extends BaseController {
    private final BuscaService busca;
    private final IndexService index;

    public BuscaController(BuscaService busca, IndexService index) {
        this.busca = busca;
        this.index = index;
        acoes.put("GET", this::pesquisar);
    }

    private void pesquisar(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestURI().getPath().equals("/buscar")) {
            enviarResposta(exchange, 404, "Não encontrado");
            return;
        }
        var params = obterQueryParams(exchange.getRequestURI());
        var consulta = params.getOrDefault("q", "");
        var fragmento = busca.gerarHtml(consulta, obterPagina(exchange.getRequestURI()));
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=UTF-8");
        headers.set("Cache-Control", "no-store");
        headers.set("Vary", "HX-Request, Accept-Encoding");
        byte[] bytes;
        if ("true".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("HX-Request"))) {
            bytes = fragmento.getBytes(StandardCharsets.UTF_8);
        } else {
            var gzip = Web.aceitaGzip(exchange);
            if (gzip) headers.set("Content-Encoding", "gzip");
            bytes = index.processarHtml(gzip, consulta, fragmento);
        }
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
