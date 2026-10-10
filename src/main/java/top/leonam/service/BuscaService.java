package top.leonam.service;

import top.leonam.bd.BuscaImpl;
import top.leonam.util.Web;
import java.util.ArrayList;
import java.util.regex.Pattern;

public final class BuscaService {
    public static final int MAX_CARACTERES = 160;
    private static final int POR_PAGINA = 10;
    private static final Pattern PALAVRA = Pattern.compile("[\\p{L}\\p{N}]+(?:[\\p{M}]+[\\p{L}\\p{N}]*)*");
    private static final Pattern LINK_MARKDOWN = Pattern.compile("!?\\[([^]]*)\\]\\([^)]*\\)");
    private static final Pattern CABECALHO = Pattern.compile("(?:^|\\s)#{1,6}\\s*");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");
    private final BuscaImpl repository;

    public BuscaService(BuscaImpl repository) { this.repository = repository; }

    public String gerarHtml(String consulta, int pagina) {
        consulta = consulta == null ? "" : consulta.strip();
        if (consulta.isEmpty()) return "<p class=\"search-hint\">Pesquise no título e no conteúdo dos posts.</p>";
        if (consulta.length() > MAX_CARACTERES) return "<p>Use até 160 caracteres na busca.</p>";
        var termos = new ArrayList<String>(8);
        var matcher = PALAVRA.matcher(consulta);
        while (matcher.find()) {
            if (matcher.group().codePointCount(0, matcher.group().length()) < 2) continue;
            if (termos.size() == 8) return "<p>Use até 8 palavras na busca.</p>";
            // Only generated quoted tokens reach MATCH; user FTS operators are never evaluated.
            termos.add("\"" + matcher.group() + "\"");
        }
        if (termos.isEmpty()) return "<p>Digite uma palavra com pelo menos 2 letras ou números.</p>";
        termos.set(termos.size() - 1, termos.getLast() + "*");
        pagina = Math.clamp(pagina, 1, 1000);
        var resultados = repository.buscar(String.join(" AND ", termos), POR_PAGINA + 1, (pagina - 1) * POR_PAGINA);
        var html = new StringBuilder("<h2>Resultados para “").append(Web.escaparHtml(consulta)).append("”</h2>");
        if (resultados.isEmpty()) return html.append("<p>Nenhum post encontrado. Experimente menos palavras ou outro termo.</p>").toString();
        html.append("<p class=\"search-hint\">Ordenados por relevância · página ").append(pagina).append("</p><div class=\"search-results\">");
        for (int i = 0; i < Math.min(POR_PAGINA, resultados.size()); i++) {
            var post = resultados.get(i);
            html.append("<article class=\"search-result\"><h3><a href=\"/blog/")
                .append(Web.segmento(post.slug())).append("\">").append(Web.escaparHtml(post.titulo()))
                .append("</a></h3><p>").append(Web.escaparHtml(limparTrecho(post.trecho()))
                    .replace("\uE000", "<mark>").replace("\uE001", "</mark>"))
                .append("</p></article>");
        }
        html.append("</div><nav class=\"pagination\" aria-label=\"Paginação da busca\">");
        if (pagina > 1) link(html, consulta, pagina - 1, "Anterior");
        if (resultados.size() > POR_PAGINA && pagina < 1000) link(html, consulta, pagina + 1, "Próxima");
        return html.append("</nav>").toString();
    }

    private String limparTrecho(String trecho) {
        if (trecho == null) return "";
        trecho = LINK_MARKDOWN.matcher(trecho).replaceAll("$1");
        trecho = CABECALHO.matcher(trecho).replaceAll(" ");
        trecho = ESPACOS.matcher(trecho.replace("```", "").replace("`", "").replace("**", "")).replaceAll(" ").strip();
        if (trecho.length() > 600) {
            int fim = Character.isHighSurrogate(trecho.charAt(599)) ? 599 : 600;
            trecho = trecho.substring(0, fim) + " …";
            if (trecho.lastIndexOf('\uE000') > trecho.lastIndexOf('\uE001')) trecho += '\uE001';
        }
        return trecho;
    }

    private void link(StringBuilder html, String consulta, int pagina, String rotulo) {
        var url = "/buscar?q=" + Web.segmento(consulta) + "&amp;page=" + pagina;
        html.append("<a href=\"").append(url).append("\" hx-get=\"").append(url)
            .append("\" hx-target=\"#resultados-busca\" hx-swap=\"innerHTML\" hx-replace-url=\"true\"")
            .append(" hx-sync=\"#busca-form:replace\" hx-indicator=\"#busca-carregando\">")
            .append(rotulo).append("</a>");
    }
}
