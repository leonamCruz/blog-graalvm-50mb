package top.leonam.bd;

import top.leonam.model.entity.Tag;
import top.leonam.model.repository.TagRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TagImpl implements TagRepository {

    private static final String BUSCAR_POR_SLUG = """
        SELECT id, nome, slug
        FROM tag
        WHERE slug = ?
        """;

    private static final String BUSCAR_TODAS = """
        SELECT id, nome, slug
        FROM tag
        ORDER BY nome
        """;

    @Override
    public Tag buscarPorSlug(String slug) {

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(BUSCAR_POR_SLUG)) {

            stmt.setString(1, slug);

            try (var rs = stmt.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                var tag = new Tag();

                tag.id = rs.getLong("id");
                tag.nome = rs.getString("nome");
                tag.slug = rs.getString("slug");

                return tag;
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao buscar tag pelo slug: " + slug,
                    e
            );
        }
    }

    @Override
    public List<Tag> buscarTodas() {

        var tags = new ArrayList<Tag>();

        try (var conn = Database.conectar();
             var stmt = conn.prepareStatement(BUSCAR_TODAS);
             var rs = stmt.executeQuery()) {

            while (rs.next()) {

                var tag = new Tag();

                tag.id = rs.getLong("id");
                tag.nome = rs.getString("nome");
                tag.slug = rs.getString("slug");

                tags.add(tag);
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao buscar tags",
                    e
            );
        }

        return tags;
    }
}
