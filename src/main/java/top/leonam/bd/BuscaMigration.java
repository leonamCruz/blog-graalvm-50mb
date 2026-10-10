package top.leonam.bd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.logging.Logger;

/** Additive, transactional migration. post remains the source of truth. */
public final class BuscaMigration {
    private static final String VERSAO = "post_fts5_v1";
    private static final Logger LOG = Logger.getLogger(BuscaMigration.class.getName());
    private BuscaMigration() {}

    public static void aplicar(Connection conn) throws SQLException, IOException {
        if (aplicada(conn)) return;
        // VACUUM INTO produces a consistent SQLite snapshot, including committed WAL data.
        var pasta = Path.of("backups");
        Files.createDirectories(pasta);
        var backup = pasta.resolve("post-antes-fts-" + Instant.now().toEpochMilli() + "-" + System.nanoTime() + ".sqlite");
        try (var stmt = conn.prepareStatement("VACUUM INTO ?")) {
            stmt.setString(1, backup.toAbsolutePath().toString());
            stmt.execute();
        }
        LOG.info("Backup anterior à busca: " + backup.toAbsolutePath());

        conn.setAutoCommit(false);
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_migrations (versao TEXT PRIMARY KEY, aplicada_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            // This write obtains SQLite's writer lock before the backfill; migrations serialize.
            try (var marker = conn.prepareStatement("INSERT OR IGNORE INTO schema_migrations(versao) VALUES (?)")) {
                marker.setString(1, VERSAO);
                if (marker.executeUpdate() == 0) {
                    conn.commit();
                    return;
                }
            }
            stmt.execute("""
                CREATE VIRTUAL TABLE post_fts USING fts5(
                    titulo, markdown, content='post', content_rowid='id',
                    tokenize='unicode61 remove_diacritics 2', prefix='2 3'
                )
                """);
            stmt.execute("""
                CREATE TRIGGER post_fts_insert AFTER INSERT ON post BEGIN
                    INSERT INTO post_fts(rowid, titulo, markdown) VALUES (new.id, new.titulo, new.markdown);
                END
                """);
            stmt.execute("""
                CREATE TRIGGER post_fts_delete AFTER DELETE ON post BEGIN
                    INSERT INTO post_fts(post_fts, rowid, titulo, markdown)
                        VALUES ('delete', old.id, old.titulo, old.markdown);
                END
                """);
            stmt.execute("""
                CREATE TRIGGER post_fts_update AFTER UPDATE OF id, titulo, markdown ON post BEGIN
                    INSERT INTO post_fts(post_fts, rowid, titulo, markdown)
                        VALUES ('delete', old.id, old.titulo, old.markdown);
                    INSERT INTO post_fts(rowid, titulo, markdown) VALUES (new.id, new.titulo, new.markdown);
                END
                """);
            stmt.execute("INSERT INTO post_fts(post_fts) VALUES ('rebuild')");
            stmt.execute("INSERT INTO post_fts(post_fts, rank) VALUES ('integrity-check', 1)");
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static boolean aplicada(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement(); var rs = stmt.executeQuery("SELECT 1 FROM sqlite_schema WHERE type='table' AND name='schema_migrations'")) {
            if (!rs.next()) return false;
        }
        try (var stmt = conn.prepareStatement("SELECT 1 FROM schema_migrations WHERE versao = ?")) {
            stmt.setString(1, VERSAO);
            try (var rs = stmt.executeQuery()) { return rs.next(); }
        }
    }
}
