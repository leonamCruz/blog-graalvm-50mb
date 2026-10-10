package top.leonam.util;

public final class Paginacao {
    private Paginacao() {}
    public static String navegacao(String caminho, int atual, boolean proxima) {
        var html = new StringBuilder("<nav class=\"pagination\" aria-label=\"Paginação\">");
        if (atual > 1) link(html, caminho, atual - 1, "Anterior");
        if (proxima) link(html, caminho, atual + 1, "Próxima");
        return html.append("</nav>").toString();
    }

    private static void link(StringBuilder html, String caminho, int pagina, String titulo) {
        var url = caminho + "?page=" + pagina;
        html.append("<a href=\"").append(url).append("\" hx-get=\"").append(url)
            .append("\" hx-target=\"#posts\" hx-swap=\"innerHTML\" hx-push-url=\"true\">")
            .append(titulo).append("</a>");
    }

    public static java.util.List<Integer> paginas(int atual, int total) {
        var paginas = new java.util.ArrayList<Integer>(9);
        paginas.add(1);
        for (int p = Math.max(2, atual - 3); p <= Math.min(total - 1, atual + 3); p++) paginas.add(p);
        if (total > 1) paginas.add(total);
        return paginas;
    }
}
