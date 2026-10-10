package top.leonam.service;

import top.leonam.model.repository.PostRepository;
import top.leonam.model.repository.TagRepository;

public class TagService {

    private final TagRepository tagRepository;
    private final PostRepository postRepository;
    private final PostListService postListService;

    public TagService(
            TagRepository tagRepository,
            PostRepository postRepository,
            PostListService postListService) {

        this.tagRepository = tagRepository;
        this.postRepository = postRepository;
        this.postListService = postListService;
    }

    public String processarEGerarHtml(String slug) { return processarEGerarHtml(slug, 1); }

    public String processarEGerarHtml(String slug, int pagina) {
        var tag = tagRepository.buscarPorSlug(slug);

        if (tag == null)return null;

        pagina = Math.clamp(pagina, 1, Integer.MAX_VALUE / 50);
        var posts = postRepository.buscarPorTagId(tag.id, 51, (pagina - 1) * 50);

        var temProxima = posts.size() > 50;
        return postListService.gerarHtml(temProxima ? posts.subList(0, 50) : posts)
                + top.leonam.util.Paginacao.navegacao("/tag/" + top.leonam.util.Web.segmento(slug), pagina, temProxima);
    }

}
