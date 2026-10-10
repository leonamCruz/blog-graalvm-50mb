package top.leonam.service;

import top.leonam.model.repository.PostRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PostService {

    private static final java.util.regex.Pattern[] DESCRICAO_PADROES = {
        java.util.regex.Pattern.compile("```[\\s\\S]*?```"),
        java.util.regex.Pattern.compile("`([^`]*)`"),
        java.util.regex.Pattern.compile("!\\[([^]]*)\\]\\([^)]*\\)"),
        java.util.regex.Pattern.compile("\\[([^]]*)\\]\\([^)]*\\)"),
        java.util.regex.Pattern.compile("<[^>]+>"),
        java.util.regex.Pattern.compile("#{1,6}\\s*"),
        java.util.regex.Pattern.compile(">\\s*"),
        java.util.regex.Pattern.compile("\\*\\*([^*]+)\\*\\*"),
        java.util.regex.Pattern.compile("\\*([^*]+)\\*"),
        java.util.regex.Pattern.compile("__([^_]+)__"),
        java.util.regex.Pattern.compile("_([^_]+)_"),
        java.util.regex.Pattern.compile("~~([^~]+)~~"),
        java.util.regex.Pattern.compile("[-*+]\\s+"),
        java.util.regex.Pattern.compile("\\d+\\.\\s+"),
        java.util.regex.Pattern.compile("\\s+")
    };

    private static final String SITE_URL = "https://leonam.top";

    private static final DateTimeFormatter FORMATADOR_DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter FORMATADOR_ISO =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final ZoneId ZONA_HORARIA =
            ZoneId.of("America/Sao_Paulo");

    private final PostRepository postRepository;
    private final top.leonam.util.HtmlCache cache = new top.leonam.util.HtmlCache(8 * 1024 * 1024, java.time.Duration.ofSeconds(30));

    private final String templateBase = lerTemplate();

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public byte[] processarEComprimirHtml(String slug) {
        return processarHtml(slug, true);
    }

    public byte[] processarHtml(String slug, boolean gzip) {
        var page = cache.get(slug, () -> renderizar(slug));
        return page == null ? null : page.bytes(gzip);
    }

    private String renderizar(String slug) {

        var post = postRepository.buscarPorSlug(slug);

        if (post == null) {
            return null;
        }

        var url = SITE_URL + "/blog/" + top.leonam.util.Web.segmento(post.slug);
        var imagem = transformarUrlAbsoluta(post.capa);
        var descricao = gerarDescricao(post.markdown);

        var criadoEmIso = post.criadoEm
                .atZone(ZONA_HORARIA)
                .format(FORMATADOR_ISO);

        var jsonLd = gerarJsonLd(
                post.titulo,
                descricao,
                url,
                imagem,
                criadoEmIso
        );

        var html = carregarTemplate()
                .replace("{{post.titulo}}", escaparHtml(post.titulo))
                .replace("{{post.slug}}", top.leonam.util.Web.segmento(post.slug))
                .replace("{{post.capa}}", escaparHtml(post.capa))
                .replace("{{post.criadoEm}}", post.criadoEm.format(FORMATADOR_DATA))
                .replace("{{post.criadoEmIso}}", escaparHtml(criadoEmIso))
                .replace("{{post.descricao}}", escaparHtml(descricao))
                .replace("{{post.url}}", escaparHtml(url))
                .replace("{{post.imagem}}", escaparHtml(imagem))
                .replace("{{post.jsonLd}}", jsonLd)
                .replace("{{post.markdown}}", escaparHtml(post.markdown));

        return html;
    }

    private String gerarDescricao(String markdown) {

        if (markdown == null || markdown.isBlank()) return "Artigo publicado no Leonam Top.";


        var texto = markdown;

        texto = DESCRICAO_PADROES[0].matcher(texto).replaceAll(" ");
        texto = DESCRICAO_PADROES[1].matcher(texto).replaceAll("$1");
        texto = DESCRICAO_PADROES[2].matcher(texto).replaceAll("$1");
        texto = DESCRICAO_PADROES[3].matcher(texto).replaceAll("$1");
        texto = DESCRICAO_PADROES[4].matcher(texto).replaceAll(" ");
        texto = DESCRICAO_PADROES[5].matcher(texto).replaceAll("");
        texto = DESCRICAO_PADROES[6].matcher(texto).replaceAll("");
        texto = DESCRICAO_PADROES[7].matcher(texto).replaceAll("$1");
        texto = DESCRICAO_PADROES[8].matcher(texto).replaceAll("$1");
        texto = DESCRICAO_PADROES[9].matcher(texto).replaceAll("$1");
        texto = DESCRICAO_PADROES[10].matcher(texto).replaceAll("$1");
        texto = DESCRICAO_PADROES[11].matcher(texto).replaceAll("$1");
        texto = DESCRICAO_PADROES[12].matcher(texto).replaceAll("");
        texto = DESCRICAO_PADROES[13].matcher(texto).replaceAll("");
        texto = DESCRICAO_PADROES[14].matcher(texto).replaceAll(" ");
        texto = texto.trim();

        if (texto.isBlank()) return "Artigo publicado no Leonam Top.";


        if (texto.length() > 160) texto = texto.substring(0, 157).trim() + "...";
        return texto;
    }

    private String gerarJsonLd(
            String titulo,
            String descricao,
            String url,
            String imagem,
            String criadoEmIso) {

        return """
                {
                  "@context": "https://schema.org",
                  "@type": "BlogPosting",
                  "headline": "%s",
                  "description": "%s",
                  "image": "%s",
                  "url": "%s",
                  "datePublished": "%s",
                  "author": {
                    "@type": "Person",
                    "name": "Leonam Top",
                    "url": "%s/"
                  },
                  "mainEntityOfPage": {
                    "@type": "WebPage",
                    "@id": "%s"
                  }
                }
                """.formatted(
                escaparJson(titulo),
                escaparJson(descricao),
                escaparJson(imagem),
                escaparJson(url),
                escaparJson(criadoEmIso),
                escaparJson(SITE_URL),
                escaparJson(url)
        );
    }

    private String transformarUrlAbsoluta(String url) {

        if (url == null || url.isBlank()) return "";
        if (url.startsWith("https://") || url.startsWith("http://")) return url;
        if (url.startsWith("/")) return SITE_URL + url;

        return SITE_URL + "/" + url;
    }

    private String carregarTemplate() { return templateBase; }

    private String lerTemplate() {

        try (var input = getClass()
                .getClassLoader()
                .getResourceAsStream("templates/post.html")) {

            if (input == null) {
                throw new RuntimeException(
                        "Template templates/post.html não encontrado"
                );
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );


        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao carregar templates/post.html",
                    e
            );
        }
    }

    private String escaparHtml(String valor) {
        return top.leonam.util.Web.escaparHtml(valor);
    }

    private String escaparJson(String valor) {

        if (valor == null) return "";

        var resultado = new StringBuilder();

        for (int i = 0; i < valor.length(); i++) {
            var c = valor.charAt(i);

            switch (c) {
                case '\\' -> resultado.append("\\\\");
                case '"' -> resultado.append("\\\"");
                case '\b' -> resultado.append("\\b");
                case '\f' -> resultado.append("\\f");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                case '<' -> resultado.append("\\u003c");
                case '>' -> resultado.append("\\u003e");
                case '&' -> resultado.append("\\u0026");
                default -> {
                    if (c < 0x20) resultado.append(String.format("\\u%04x", (int) c));
                    else resultado.append(c);
                }
            }
        }

        return resultado.toString();
    }
}
