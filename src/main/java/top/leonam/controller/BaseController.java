package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseController implements HttpHandler {

    protected Map<String, ServerStrategy> acoes = new HashMap<>();

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(BaseController.class.getName());

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
        headers.set("Content-Security-Policy", "object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
        try {
            if (exchange.getRequestURI().toASCIIString().length() > 4096) {
                enviarResposta(exchange, 414, "URL muito longa");
                return;
            }
            var estrategia = acoes.get(exchange.getRequestMethod());
            if (estrategia != null) estrategia.executar(exchange);
            else {
                headers.set("Allow", "GET");
                enviarResposta(exchange, 405, "Método não permitido");
            }
        } catch (IllegalArgumentException e) {
            if (exchange.getResponseCode() == -1) enviarResposta(exchange, 400, "Requisição inválida");
        } catch (Exception e) {
            LOG.log(java.util.logging.Level.WARNING, "Falha ao atender requisição", e);
            if (exchange.getResponseCode() == -1) enviarResposta(exchange, 500, "Erro interno");
        } finally {
            exchange.close();
        }
    }

    protected Map<String, String> obterQueryParams(URI uri) {
        var params = new HashMap<String, String>();
        var query = uri.getRawQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] par = param.split("=", 2);
                if (par.length > 1) {
                    params.put(java.net.URLDecoder.decode(par[0], StandardCharsets.UTF_8), java.net.URLDecoder.decode(par[1], StandardCharsets.UTF_8));
                } else {
                    params.put(par[0], "");
                }
            }
        }
        return params;
    }

    protected int obterPagina(URI uri) {
        try { return Math.max(1, Integer.parseInt(obterQueryParams(uri).getOrDefault("page", "1"))); }
        catch (NumberFormatException e) { return 1; }
    }

    protected Long obterIdDaUrl(URI uri) {
        String path = uri.getPath();
        String[] partes = path.split("/");
        if (partes.length > 2) {
            try {
                return Long.parseLong(partes[partes.length - 1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    protected void enviarResposta(HttpExchange exchange, int status, String resposta) throws IOException {
        byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
