package top.leonam.service;

import top.leonam.model.entity.Post;
import top.leonam.model.repository.PostRepository;

import java.util.List;

public class PostPaginationService {

    private static final int POSTS_POR_PAGINA = 5;

    private final PostRepository postRepository;

    public PostPaginationService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public String processarEGerarHtml(int paginaOriginal) {

        var totalPosts =
                postRepository.contarPostsExcluindoUltimos5();

        var totalPaginas =
                (int) Math.ceil(
                        totalPosts /
                                (double) POSTS_POR_PAGINA
                );

        if (totalPaginas == 0) {
            return "";
        }

        var pagina = Math.max(1, paginaOriginal);
        if (pagina > totalPaginas) {
            pagina = totalPaginas;
        }

        var offset =
                POSTS_POR_PAGINA
                        + ((pagina - 1) * POSTS_POR_PAGINA);

        var posts =
                postRepository.buscarPostsPaginados(
                        POSTS_POR_PAGINA,
                        offset
                );

        return gerarHtmlPosts(posts)
                + gerarPaginacao(
                pagina,
                totalPaginas
        );
    }

    private String gerarHtmlPosts(List<Post> posts) {
        return "<div class=\"posts\">" + new PostListService().gerarHtml(posts) + "</div>";
    }

    private String gerarPaginacao(
            int paginaAtual,
            int totalPaginas
    ) {

        if (totalPaginas <= 1) {
            return "";
        }

        var html =
                new StringBuilder();

        html.append("""
                <div class="pagination">
                """);

        for (var pagina : top.leonam.util.Paginacao.paginas(paginaAtual, totalPaginas)) {

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

        html.append("""
                </div>
                """);

        return html.toString();
    }

}
