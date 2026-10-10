package top.leonam.bd;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;

/** Run only in a temporary cwd; --banco-real only reads EXPLAIN on the existing DB. */
public final class BuscaPlanoRegression {
    private static int checks;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--banco-real")) {
            try (var conn = Database.conectar()) { verificarPlanos(conn, "banco real"); }
            return;
        }
        Database.inicializarBanco();
        try (var conn = Database.conectar()) {
            inserir(conn, 0, 1000);
            verificarPlanos(conn, "1.000 posts");
            medir(conn, "1.000 posts");
            inserir(conn, 1000, 100000);
            verificarPlanos(conn, "100.000 posts");
            medir(conn, "100.000 posts");
        }
        System.out.println("OK: " + checks + " verificações de plano de execução FTS5");
    }
    private static void inserir(Connection conn, int inicio, int fim) throws Exception {
        conn.setAutoCommit(false);
        try (var stmt = conn.prepareStatement("INSERT INTO post(titulo,capa,slug,markdown) VALUES (?,?,?,?)")) {
            for (int i=inicio; i<fim; i++) {
                stmt.setString(1, "Documento " + i);
                stmt.setString(2, "x");
                stmt.setString(3, "documento-" + i);
                stmt.setString(4, "palavrafrequente conteúdo de teste índice segurança estudo " + (i<5 ? "termoraro " : "") + (i%1000==0 ? "termogrupo" : ""));
                stmt.addBatch();
                if (i%500==499) stmt.executeBatch();
            }
            stmt.executeBatch();
            conn.commit();
        } catch (Exception e) { conn.rollback(); throw e; }
        finally { conn.setAutoCommit(true); }
    }
    private static void verificarPlanos(Connection conn, String cenario) throws Exception {
        for (var query : new String[]{"\"termoraro\"*", "\"palavrafrequente\"*", "\"segur\"*", "\"termogrupo\" AND \"segur\"*", "\"inexistente\"*"}) {
            for (int offset : new int[]{0, 10}) {
                var detalhes = new ArrayList<String>();
                try (var stmt = conn.prepareStatement("EXPLAIN QUERY PLAN " + BuscaImpl.SQL)) {
                    stmt.setString(1, query); stmt.setInt(2,11); stmt.setInt(3,offset);
                    try (var rs=stmt.executeQuery()) { while(rs.next()) detalhes.add(rs.getString("detail")); }
                }
                check(detalhes.stream().anyMatch(d -> d.contains("post_fts VIRTUAL TABLE INDEX") && d.matches(".*:M[0-9].*")), "MATCH FTS index " + cenario + query);
                check(detalhes.stream().anyMatch(d -> d.contains("SEARCH p USING INTEGER PRIMARY KEY")), "posts fetched by primary key " + cenario + query);
                check(detalhes.stream().noneMatch(d -> d.startsWith("SCAN p ") || d.equals("SCAN p") || d.startsWith("SCAN post ")), "no full post scan " + cenario + query);
                if (offset==0) System.out.println(cenario+" | "+query+" | "+String.join("; ",detalhes));
            }
        }
    }
    private static void medir(Connection conn, String cenario) throws Exception {
        for (var termo : new String[]{"termoraro", "termogrupo", "palavrafrequente", "inexistente"}) {
            var amostras=new double[15];
            for (int i=-3; i<amostras.length; i++) {
                long start=System.nanoTime();
                try (var stmt=conn.prepareStatement(BuscaImpl.SQL)) {
                    stmt.setString(1,"\""+termo+"\"*"); stmt.setInt(2,11); stmt.setInt(3,0);
                    try(var rs=stmt.executeQuery()) { while(rs.next()) { rs.getString(1); rs.getString(2); rs.getString(3); } }
                }
                if(i>=0) amostras[i]=(System.nanoTime()-start)/1e6;
            }
            Arrays.sort(amostras);
            System.out.printf(java.util.Locale.ROOT,"%s | %s | mediana %.3f ms%n",cenario,termo,amostras[amostras.length/2]);
        }
    }
}
