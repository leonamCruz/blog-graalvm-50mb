package top.leonam.bd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Database {
    private static final String URL = "jdbc:sqlite:post.sqlite";
    private static final Logger logger = Logger.getLogger("Database");

    private static final ConnectionPool POOL = new ConnectionPool(URL, 8);

    public static Connection conectar() throws SQLException {
        return POOL.acquire();
    }

    public static void inicializarBanco(){
        try (var is = Database.class.getClassLoader().getResourceAsStream("schema.sql")) {

            if (is == null) {
                logger.log(Level.SEVERE,"Arquivo schema.sql não encontrado no resources.");
                throw new IllegalStateException("schema.sql ausente");
            }

            String sql;
            try (var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                sql = reader.lines().collect(Collectors.joining("\n"));
            }

            try (Connection conn = conectar();
                 var stmt = conn.createStatement()) {
                for (var comando : sql.split(";")) {
                    if (!comando.isBlank()) stmt.executeUpdate(comando);
                }

                stmt.execute("PRAGMA foreign_keys = ON;");

                BuscaMigration.aplicar(conn);

                logger.log(Level.INFO, "Banco inicializado com sucesso via schema.sql!");
            }

        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Falha ao inicializar banco", e);
        }
    }
}
