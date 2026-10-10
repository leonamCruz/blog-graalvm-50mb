package top.leonam.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class Web {
    private Web() {}

    public static String escaparHtml(String valor) {
        if (valor == null) return "";
        StringBuilder out = null;
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            String escaped = switch (c) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '\'' -> "&#39;";
                default -> null;
            };
            if (escaped != null && out == null) {
                out = new StringBuilder(valor.length() + 16);
                out.append(valor, 0, i);
            }
            if (out != null) {
                if (escaped == null) out.append(c); else out.append(escaped);
            }
        }
        return out == null ? valor : out.toString();
    }

    public static String segmento(String valor) {
        return valor == null ? "" : URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static boolean aceitaGzip(com.sun.net.httpserver.HttpExchange exchange) {
        var valor = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        if (valor == null) return false;
        Double gzip = null, wildcard = null;
        for (var item : valor.split(",")) {
            var partes = item.trim().split(";");
            double qualidade = 1;
            for (int i = 1; i < partes.length; i++) {
                var parametro = partes[i].trim();
                if (parametro.startsWith("q=")) {
                    try { qualidade = Double.parseDouble(parametro.substring(2)); }
                    catch (NumberFormatException e) { qualidade = 0; }
                }
            }
            if (!Double.isFinite(qualidade) || qualidade < 0 || qualidade > 1) qualidade = 0;
            if (partes[0].equalsIgnoreCase("gzip")) gzip = qualidade;
            if (partes[0].equals("*")) wildcard = qualidade;
        }
        return gzip != null ? gzip > 0 : wildcard != null && wildcard > 0;
    }

    public static String caminho(String valor) {
        var out = new StringBuilder();
        boolean primeiro = true;
        for (var parte : valor.split("/", -1)) {
            if (!primeiro) out.append('/');
            primeiro = false;
            out.append(segmento(parte));
        }
        return out.toString();
    }
}
