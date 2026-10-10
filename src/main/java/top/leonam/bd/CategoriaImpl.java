package top.leonam.bd;

import top.leonam.model.entity.Categoria;
import top.leonam.model.repository.CategoriaRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CategoriaImpl implements CategoriaRepository {

    private static final String BUSCAR_POR_SLUG = """
        SELECT id, nome, slug
        FROM categoria
        WHERE slug = ?
        """;

    private static final String BUSCAR_TODAS = """
        SELECT id, nome, slug
        FROM categoria
        ORDER BY nome
        """;

    @Override
    public Categoria buscarPorSlug(String slug) {

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(BUSCAR_POR_SLUG)) {

            stmt.setString(1, slug);

            try (var rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                var categoria = new Categoria();

                categoria.id = rs.getLong("id");
                categoria.nome = rs.getString("nome");
                categoria.slug = rs.getString("slug");

                return categoria;
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao buscar categoria pelo slug: " + slug,
                    e
            );
        }
    }

    @Override
    public List<Categoria> buscarTodas() {

        var categorias = new ArrayList<Categoria>();

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(BUSCAR_TODAS);
             var rs = stmt.executeQuery()) {

            while (rs.next()) {

                var categoria = new Categoria();

                categoria.id = rs.getLong("id");
                categoria.nome = rs.getString("nome");
                categoria.slug = rs.getString("slug");

                categorias.add(categoria);
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao buscar categorias",
                    e
            );
        }

        return categorias;
    }
}
