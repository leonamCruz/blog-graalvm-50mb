package top.leonam.bd;

import top.leonam.model.entity.Post;
import top.leonam.model.repository.PostRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PostImpl implements PostRepository {

    private static final String BUSCAR_ULTIMOS_5 = """
        SELECT id, titulo, capa, slug, criado_em, categoria_id
        FROM post
        WHERE publicado = 1
        ORDER BY criado_em DESC, id DESC
        LIMIT 5
        """;

    private static final String BUSCAR_POSTS_PAGINADOS = """
        SELECT id, titulo, capa, slug, criado_em, categoria_id
        FROM post
        WHERE publicado = 1
        ORDER BY criado_em DESC, id DESC
        LIMIT ? OFFSET ?
        """;

    private static final String CONTAR_POSTS_EXCLUINDO_ULTIMOS_5 = """
        SELECT COUNT(*)
        FROM post
        WHERE publicado = 1
        """;

    private static final String BUSCAR_POR_SLUG = """
        SELECT id, titulo, capa, slug, markdown, criado_em, categoria_id
        FROM post
        WHERE slug = ?
        AND publicado = 1
        """;

    private static final String BUSCAR_POR_CATEGORIA = """
        SELECT id, titulo, capa, slug, criado_em, categoria_id
        FROM post
        WHERE categoria_id = ?
        AND publicado = 1
        ORDER BY criado_em DESC, id DESC
        LIMIT ? OFFSET ?
        """;

    private static final String BUSCAR_POR_TAG = """
        SELECT
            p.id,
            p.titulo,
            p.capa,
            p.slug,
            p.criado_em,
            p.categoria_id
        FROM post p
        INNER JOIN post_tag pt
            ON pt.post_id = p.id
        WHERE pt.tag_id = ?
        AND p.publicado = 1
        ORDER BY p.criado_em DESC, p.id DESC
        LIMIT ? OFFSET ?
        """;
    private static final String BUSCAR_TODOS_SLUGS = """
        SELECT p.slug
        FROM post p
        WHERE p.publicado = 1
        """;
    @Override
    public Inicio buscarInicio() {
        var posts = new ArrayList<Post>(10);
        int restantes = 0;
        try (var conn = Database.conectar(); var stmt = conn.prepareStatement("""
                SELECT id, titulo, capa, slug, criado_em, categoria_id,
                    (SELECT COUNT(*) FROM post WHERE publicado = 1) AS total
                FROM post WHERE publicado = 1
                ORDER BY criado_em DESC, id DESC LIMIT 10
                """); var rs = stmt.executeQuery()) {
            while (rs.next()) {
                posts.add(mapearPost(rs));
                restantes = Math.max(0, rs.getInt("total") - 5);
            }
            return new Inicio(posts, restantes);
        } catch (SQLException e) { throw new IllegalStateException("Erro ao carregar início", e); }
    }

    @Override
    public List<Post> buscarUltimos5() {

        var posts = new ArrayList<Post>();

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(BUSCAR_ULTIMOS_5);
             var rs = stmt.executeQuery()) {

            while (rs.next()) {
                posts.add(mapearPost(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao buscar os últimos 5 posts",
                    e
            );
        }

        return posts;
    }

    @Override
    public List<Post> buscarPostsPaginados(
            int limite,
            int offset
    ) {

        var posts = new ArrayList<Post>();

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(
                     BUSCAR_POSTS_PAGINADOS
             )) {

            stmt.setInt(1, limite);
            stmt.setInt(2, offset);

            try (var rs = stmt.executeQuery()) {

                while (rs.next()) {
                    posts.add(mapearPost(rs));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao buscar posts paginados",
                    e
            );
        }

        return posts;
    }

    @Override
    public int contarPostsExcluindoUltimos5() {

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(
                     CONTAR_POSTS_EXCLUINDO_ULTIMOS_5
             );
             var rs = stmt.executeQuery()) {

            if (rs.next()) {
                return Math.max(0, rs.getInt(1) - 5);
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao contar posts restantes",
                    e
            );
        }

        return 0;
    }

    @Override
    public Post buscarPorSlug(String slug) {

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(BUSCAR_POR_SLUG)) {

            stmt.setString(1, slug);

            try (var rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                var post = mapearPost(rs);

                post.markdown = rs.getString("markdown");

                return post;
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao buscar post pelo slug: " + slug,
                    e
            );
        }
    }

    @Override
    public List<Post> buscarPorCategoriaId(long categoriaId) {
        return buscarPorCategoriaId(categoriaId, -1, 0);
    }

    @Override
    public List<Post> buscarPorCategoriaId(long categoriaId, int limite, int offset) {

        var posts = new ArrayList<Post>();

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(
                     BUSCAR_POR_CATEGORIA
             )) {

            stmt.setLong(1, categoriaId);
            stmt.setInt(2, limite);
            stmt.setInt(3, offset);

            try (var rs = stmt.executeQuery()) {

                while (rs.next()) {
                    posts.add(mapearPost(rs));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao buscar posts da categoria",
                    e
            );
        }

        return posts;
    }

    @Override
    public List<Post> buscarPorTagId(long tagId) {
        return buscarPorTagId(tagId, -1, 0);
    }

    @Override
    public List<Post> buscarPorTagId(long tagId, int limite, int offset) {

        var posts = new ArrayList<Post>();

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(BUSCAR_POR_TAG)) {

            stmt.setLong(1, tagId);
            stmt.setInt(2, limite);
            stmt.setInt(3, offset);

            try (var rs = stmt.executeQuery()) {

                while (rs.next()) {
                    posts.add(mapearPost(rs));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao buscar posts da tag",
                    e
            );
        }

        return posts;
    }

    @Override
    public List<String> buscarSomenteSlugs() {

        var slugs = new ArrayList<String>();

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(BUSCAR_TODOS_SLUGS);
             var rs = stmt.executeQuery()) {

            while (rs.next()) {
                slugs.add(rs.getString("slug"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar slugs dos posts", e);
        }

        return slugs;
    }

    private Post mapearPost(ResultSet rs) throws SQLException {

        var post = new Post();

        post.id = rs.getLong("id");
        post.titulo = rs.getString("titulo");
        post.capa = rs.getString("capa");
        post.slug = rs.getString("slug");

        var criadoEm = rs.getTimestamp("criado_em");

        if (criadoEm != null) {
            post.criadoEm = criadoEm.toLocalDateTime();
        }

        post.categoriaId = rs.getLong("categoria_id");

        return post;
    }
}
