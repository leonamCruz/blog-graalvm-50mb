package top.leonam.bd;

import top.leonam.model.entity.ResultadoBusca;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class BuscaImpl {
        // External-content FTS avoids a second stored copy of the entire Markdown.
        // Always join/filter the source table: drafts must never become search results.
    static final String SQL = """
            SELECT p.titulo, p.slug,
                   snippet(post_fts, 1, char(57344), char(57345), ' … ', 32) AS trecho
            FROM post_fts JOIN post p ON p.id = post_fts.rowid
            WHERE post_fts MATCH ? AND p.publicado = 1
            ORDER BY bm25(post_fts, 8.0, 1.0), p.criado_em DESC, p.id DESC
            LIMIT ? OFFSET ?
            """;
    public List<ResultadoBusca> buscar(String consultaFts, int limite, int offset) {
        try (var conn = Database.conectar(); var stmt = conn.prepareStatement(SQL)) {
            stmt.setString(1, consultaFts);
            stmt.setInt(2, limite);
            stmt.setInt(3, offset);
            try (var rs = stmt.executeQuery()) {
                var resultados = new ArrayList<ResultadoBusca>(limite);
                while (rs.next()) resultados.add(new ResultadoBusca(rs.getString("titulo"), rs.getString("slug"), rs.getString("trecho")));
                return resultados;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha na busca de posts", e);
        }
    }
}
