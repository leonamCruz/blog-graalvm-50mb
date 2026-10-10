package top.leonam.service;

import top.leonam.model.entity.Categoria;
import top.leonam.model.entity.Post;
import top.leonam.model.entity.Tag;
import top.leonam.model.repository.CategoriaRepository;
import top.leonam.model.repository.PostRepository;
import top.leonam.model.repository.TagRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class IndexService {

    private static final int POSTS_POR_PAGINA = 5;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final String templateBase = lerTemplate();
    // Two nested entries (base + home), each with 15s TTL: at most 30s of stale data.
    private final top.leonam.util.HtmlCache cache = new top.leonam.util.HtmlCache(1024 * 1024, java.time.Duration.ofSeconds(15));

    private static final String SITE_URL =
            "https://leonam.top";

    private static final String SITE_TITULO =
            "Leonam Top";

    private static final String SITE_DESCRICAO =
            "Desenvolvimento, Java, tecnologias e outras coisas que valem a pena registrar.";

    private static final String SITE_IMAGEM =
            SITE_URL + "/img/jeff.webp";

    private final PostRepository postRepository;
    private final CategoriaRepository categoriaRepository;
    private final TagRepository tagRepository;

    public IndexService(
            PostRepository postRepository,
            CategoriaRepository categoriaRepository,
            TagRepository tagRepository
    ) {
        this.postRepository = postRepository;
        this.categoriaRepository = categoriaRepository;
        this.tagRepository = tagRepository;
    }

    public byte[] processarEComprimirHtml() {
        return processarHtml(true);
    }

    public byte[] processarHtml(boolean gzip) {
        return cache.get("home", () -> completarBase("", "")).bytes(gzip);
    }

    public byte[] processarHtml(boolean gzip, String consulta, String resultadosBusca) {
        if (consulta.isEmpty() && resultadosBusca.isEmpty()) return processarHtml(gzip);
        var html = completarBase(consulta, resultadosBusca);
        return gzip ? comprimirGzip(html) : html.getBytes(StandardCharsets.UTF_8);
    }

    private String completarBase(String consulta, String resultadosBusca) {
        var base = new String(cache.get("base", this::renderizarBase).plain(), StandardCharsets.UTF_8);
        return base.replace("{{busca.resultados}}", resultadosBusca)
                .replace("{{busca.valor}}", escaparHtml(consulta));
    }

    private String renderizarBase() {
        var template = carregarTemplate();
        var inicio = postRepository.buscarInicio();
        var todos = inicio.posts();
        var ultimosPosts = todos.subList(0, Math.min(5, todos.size()));
        var posts = todos.subList(Math.min(5, todos.size()), todos.size());
        var totalPosts = inicio.restantes();

        var categorias =
                categoriaRepository.buscarTodas();

        var tags =
                tagRepository.buscarTodas();

        var jsonLd =
                gerarJsonLd();

        var html = template
                .replace(
                        "{{site.titulo}}",
                        escaparHtml(SITE_TITULO)
                )
                .replace(
                        "{{site.descricao}}",
                        escaparHtml(SITE_DESCRICAO)
                )
                .replace(
                        "{{site.url}}",
                        escaparHtml(SITE_URL)
                )
                .replace(
                        "{{site.imagem}}",
                        escaparHtml(SITE_IMAGEM)
                )
                .replace(
                        "{{site.jsonLd}}",
                        jsonLd
                );

        html = substituirPosts(
                html,
                "ultimosPosts",
                ultimosPosts
        );

        html = substituirPosts(
                html,
                "posts",
                posts
        );

        html = substituirCategorias(
                html,
                categorias
        );

        html = substituirTags(
                html,
                tags
        );

        html = html.replace(
                "{{paginacao}}",
                gerarPaginacao(totalPosts)
        );

        return html;
    }

    private String gerarJsonLd() {

        return """
                {
                  "@context": "https://schema.org",
                  "@graph": [
                    {
                      "@type": "WebSite",
                      "@id": "%s/#website",
                      "url": "%s/",
                      "name": "%s",
                      "alternateName": "Leonam Top"
                    },
                    {
                      "@type": "WebPage",
                      "@id": "%s/#webpage",
                      "url": "%s/",
                      "name": "%s",
                      "description": "%s",
                      "isPartOf": {
                        "@id": "%s/#website"
                      }
                    }
                  ]
                }
                """.formatted(
                escaparJson(SITE_URL),
                escaparJson(SITE_URL),
                escaparJson(SITE_TITULO),
                escaparJson(SITE_URL),
                escaparJson(SITE_URL),
                escaparJson(SITE_TITULO),
                escaparJson(SITE_DESCRICAO),
                escaparJson(SITE_URL)
        );
    }

    private byte[] comprimirGzip(String texto) {

        try (var baos = new ByteArrayOutputStream();
             var gzip = new GZIPOutputStream(baos)) {

            gzip.write(
                    texto.getBytes(StandardCharsets.UTF_8)
            );

            gzip.finish();

            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao comprimir HTML da Index",
                    e
            );
        }
    }

    private String carregarTemplate() { return templateBase; }

    private String lerTemplate() {

        try (var input =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream(
                                     "templates/index.html"
                             )) {

            if (input == null) {
                throw new RuntimeException(
                        "Template index.html não encontrado"
                );
            }

            return new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao carregar index.html",
                    e
            );
        }
    }

    private String substituirPosts(
            String html,
            String nomeBloco,
            List<Post> posts
    ) {

        var inicio =
                "{{#" + nomeBloco + "}}";

        var fim =
                "{{/" + nomeBloco + "}}";

        var inicioIndex =
                html.indexOf(inicio);

        var fimIndex =
                html.indexOf(fim);

        if (inicioIndex == -1 || fimIndex == -1) {
            return html;
        }

        var bloco =
                html.substring(
                        inicioIndex + inicio.length(),
                        fimIndex
                );

        var resultado =
                new StringBuilder();

        boolean primeiraImagem = true;
        for (var post : posts) {

            var item = bloco
                    .replace(
                            "{{post.slug}}",
                            top.leonam.util.Web.segmento(post.slug)
                    )
                    .replace(
                            "{{post.titulo}}",
                            escaparHtml(post.titulo)
                    )
                    .replace(
                            "{{post.capa}}",
                            escaparHtml(post.capa)
                    )
                    .replace(
                            "{{post.criadoEm}}",
                            formatarData(post)
                    );

            item = item.replace("{{post.prioridadeImagem}}",
                    nomeBloco.equals("ultimosPosts") && primeiraImagem
                            ? "fetchpriority=\"high\"" : "loading=\"lazy\"");
            primeiraImagem = false;
            resultado.append(item);
        }

        return html.substring(0, inicioIndex)
                + resultado
                + html.substring(
                fimIndex + fim.length()
        );
    }

    private String substituirCategorias(
            String html,
            List<Categoria> categorias
    ) {

        var inicio =
                "{{#categorias}}";

        var fim =
                "{{/categorias}}";

        var inicioIndex =
                html.indexOf(inicio);

        var fimIndex =
                html.indexOf(fim);

        if (inicioIndex == -1 || fimIndex == -1) {
            return html;
        }

        var bloco =
                html.substring(
                        inicioIndex + inicio.length(),
                        fimIndex
                );

        var resultado =
                new StringBuilder();

        for (var categoria : categorias) {

            var item = bloco
                    .replace(
                            "{{categoria.slug}}",
                            top.leonam.util.Web.segmento(categoria.slug)
                    )
                    .replace(
                            "{{categoria.nome}}",
                            escaparHtml(categoria.nome)
                    );

            resultado.append(item);
        }

        return html.substring(0, inicioIndex)
                + resultado
                + html.substring(
                fimIndex + fim.length()
        );
    }

    private String substituirTags(
            String html,
            List<Tag> tags
    ) {

        var inicio =
                "{{#tags}}";

        var fim =
                "{{/tags}}";

        var inicioIndex =
                html.indexOf(inicio);

        var fimIndex =
                html.indexOf(fim);

        if (inicioIndex == -1 || fimIndex == -1) {
            return html;
        }

        var bloco =
                html.substring(
                        inicioIndex + inicio.length(),
                        fimIndex
                );

        var resultado =
                new StringBuilder();

        for (var tag : tags) {

            var item = bloco
                    .replace(
                            "{{tag.slug}}",
                            top.leonam.util.Web.segmento(tag.slug)
                    )
                    .replace(
                            "{{tag.nome}}",
                            escaparHtml(tag.nome)
                    );

            resultado.append(item);
        }

        return html.substring(0, inicioIndex)
                + resultado
                + html.substring(
                fimIndex + fim.length()
        );
    }

    private String gerarPaginacao(int totalPosts) {

        if (totalPosts <= 0) {
            return "";
        }

        var totalPaginas =
                (int) Math.ceil(
                        totalPosts /
                                (double) POSTS_POR_PAGINA
                );

        if (totalPaginas <= 1) {
            return "";
        }

        var html =
                new StringBuilder();

        for (var pagina : top.leonam.util.Paginacao.paginas(1, totalPaginas)) {

            html.append("""
                <a
                    href="/posts?page=%d"
                    hx-get="/posts?page=%d"
                    hx-target="#outros-posts"
                    hx-swap="innerHTML"
                    hx-push-url="true">
                    %d
                </a>
                """.formatted(
                    pagina,
                    pagina,
                    pagina
            ));
        }

        return html.toString();
    }

    private String formatarData(Post post) {

        if (post.criadoEm == null) {
            return "";
        }

        return post.criadoEm.format(
                DATA
        );
    }

    private String escaparHtml(String valor) {
        return top.leonam.util.Web.escaparHtml(valor);
    }

    private String escaparJson(String valor) {

        if (valor == null) {
            return "";
        }

        var resultado =
                new StringBuilder();

        for (int i = 0; i < valor.length(); i++) {
            var caractere = valor.charAt(i);

            switch (caractere) {

                case '\\' ->
                        resultado.append("\\\\");

                case '"' ->
                        resultado.append("\\\"");

                case '\b' ->
                        resultado.append("\\b");

                case '\f' ->
                        resultado.append("\\f");

                case '\n' ->
                        resultado.append("\\n");

                case '\r' ->
                        resultado.append("\\r");

                case '\t' ->
                        resultado.append("\\t");

                case '<' ->
                        resultado.append("\\u003c");

                case '>' ->
                        resultado.append("\\u003e");

                case '&' ->
                        resultado.append("\\u0026");

                default -> {

                    if (caractere < 0x20) {

                        resultado.append(
                                String.format(
                                        "\\u%04x",
                                        (int) caractere
                                )
                        );

                    } else {
                        resultado.append(caractere);
                    }
                }
            }
        }

        return resultado.toString();
    }
}
