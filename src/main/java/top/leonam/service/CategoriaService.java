package top.leonam.service;

import top.leonam.model.repository.CategoriaRepository;
import top.leonam.model.repository.PostRepository;

public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final PostRepository postRepository;
    private final PostListService postListService;

    public CategoriaService(
            CategoriaRepository categoriaRepository,
            PostRepository postRepository,
            PostListService postListService) {

        this.categoriaRepository = categoriaRepository;
        this.postRepository = postRepository;
        this.postListService = postListService;
    }

    public String processarEGerarHtml(String slug) { return processarEGerarHtml(slug, 1); }

    public String processarEGerarHtml(String slug, int pagina) {
        var categoria = categoriaRepository.buscarPorSlug(slug);

        if (categoria == null) return null;

        pagina = Math.clamp(pagina, 1, Integer.MAX_VALUE / 50);
        var posts = postRepository.buscarPorCategoriaId(categoria.id, 51, (pagina - 1) * 50);

        var temProxima = posts.size() > 50;
        return postListService.gerarHtml(temProxima ? posts.subList(0, 50) : posts)
                + top.leonam.util.Paginacao.navegacao("/categoria/" + top.leonam.util.Web.segmento(slug), pagina, temProxima);
    }

}
