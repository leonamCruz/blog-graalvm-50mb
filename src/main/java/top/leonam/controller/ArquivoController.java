package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;
import top.leonam.service.ArquivoService;

import java.io.IOException;
import java.nio.file.Files;

public class ArquivoController extends BaseController {

    private final ArquivoService service;

    public ArquivoController(ArquivoService service) {
        this.service = service;
        acoes.put("GET", this::handleGet);
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        if (path.equals("/arquivos/list") || path.startsWith("/arquivos/list/")) {
            // Extrai o caminho direto da URL substituindo a base
            String subPath = path.length() <= "/arquivos/list/".length() ? "" : path.substring("/arquivos/list/".length());
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            var html = service.listarHtml(subPath);
            enviarResposta(exchange, 200, html.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        } else if (path.startsWith("/arquivos/download/")) {
            // Extrai o caminho direto da URL substituindo a base
            String subPath = path.substring("/arquivos/download/".length());
            var arquivo = service.obterCaminhoSeguro(subPath);

            if (arquivo != null && Files.isRegularFile(arquivo)) {
                String viewParam = extrairParametro(query, "view");
                String nomeArquivo = top.leonam.util.Web.segmento(arquivo.getFileName().toString());

                // Decide se baixa ou se tenta abrir no navegador baseado no parâmetro "view"
                if ("true".equalsIgnoreCase(viewParam) && arquivo.getFileName().toString().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp|txt)")) {
                    String mimeType = Files.probeContentType(arquivo);
                    if (mimeType == null) {
                        mimeType = "application/octet-stream";
                    }
                    exchange.getResponseHeaders().set("Content-Type", mimeType);
                    exchange.getResponseHeaders().set("Content-Disposition", "inline; filename*=UTF-8''" + nomeArquivo + "");
                } else {
                    // Força um tipo genérico para obrigar o navegador a fazer o download
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename*=UTF-8''" + nomeArquivo + "");
                }

                exchange.sendResponseHeaders(200, Files.size(arquivo));
                Files.copy(arquivo, exchange.getResponseBody());
                exchange.getResponseBody().close();
            } else {
                enviarResposta(exchange, 404, "Arquivo nao encontrado".getBytes());
            }

        } else if (path.equals("/arquivos") || path.equals("/arquivos/")) {
            var htmlBase = service.carregarTemplate();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            enviarResposta(exchange, 200, htmlBase.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            enviarResposta(exchange, 404, "Não encontrado");
        }
    }

    private String extrairParametro(String query, String chave) {
        if (query == null) return "";
        for (String par : query.split("&")) {
            String[] split = par.split("=");
            if (split.length == 2 && split[0].equals(chave)) {
                return split[1];
            }
        }
        return "";
    }

    private void enviarResposta(HttpExchange exchange, int status, byte[] resposta) throws IOException {
        exchange.sendResponseHeaders(status, resposta.length);
        try (var os = exchange.getResponseBody()) {
            os.write(resposta);
        }
    }
}
