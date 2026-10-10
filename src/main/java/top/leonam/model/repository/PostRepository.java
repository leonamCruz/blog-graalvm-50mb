package top.leonam.model.repository;

import top.leonam.model.entity.Post;

import java.util.List;

public interface PostRepository {

    record Inicio(List<Post> posts, int restantes) {}

    default Inicio buscarInicio() {
        var posts = new java.util.ArrayList<>(buscarUltimos5());
        posts.addAll(buscarPostsPaginados(5, 5));
        return new Inicio(posts, contarPostsExcluindoUltimos5());
    }


    List<Post> buscarUltimos5();

    List<Post> buscarPostsPaginados(
            int limite,
            int offset
    );

    int contarPostsExcluindoUltimos5();

    Post buscarPorSlug(String slug);

    List<Post> buscarPorCategoriaId(long categoriaId);

    List<Post> buscarPorCategoriaId(long categoriaId, int limite, int offset);

    List<Post> buscarPorTagId(long tagId);

    List<Post> buscarPorTagId(long tagId, int limite, int offset);

    List<String> buscarSomenteSlugs();
}
