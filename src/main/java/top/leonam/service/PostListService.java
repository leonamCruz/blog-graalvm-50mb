package top.leonam.service;

import top.leonam.model.entity.Post;
import top.leonam.util.Web;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PostListService {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String gerarHtml(List<Post> posts) {
        var html = new StringBuilder();
        for (var post : posts) {
            var slug = Web.segmento(post.slug);
            html.append("<article class=\"post-card\"><h3><a href=\"/blog/")
                .append(slug).append("\">").append(Web.escaparHtml(post.titulo))
                .append("</a></h3><img src=\"").append(Web.escaparHtml(post.capa))
                .append("\" alt=\"Capa do Post\"><p class=\"post-date\">")
                .append(post.criadoEm == null ? "" : post.criadoEm.format(DATA))
                .append("</p><a href=\"/blog/").append(slug)
                .append("\">Ler artigo →</a></article>");
        }
        return html.toString();
    }
}
