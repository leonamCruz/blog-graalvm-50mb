package top.leonam.bd;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exclusive leases: close() returns the physical connection instead of closing it. */
final class ConnectionPool {
    private final String url;
    private final Semaphore slots;
    private final ConcurrentLinkedDeque<Connection> idle = new ConcurrentLinkedDeque<>();

    ConnectionPool(String url, int size) {
        this.url = url;
        slots = new Semaphore(size);
    }

    Connection acquire() throws SQLException {
        try { slots.acquire(); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Espera por conexão interrompida", e);
        }
        Connection physical = null;
        try {
            physical = idle.pollFirst();
            if (physical != null && physical.isClosed()) physical = null;
            if (physical == null) {
                physical = DriverManager.getConnection(url);
                try (var stmt = physical.createStatement()) {
                    stmt.execute("PRAGMA busy_timeout = 3000");
                    try (var rs = stmt.executeQuery("PRAGMA journal_mode = WAL")) {
                        if (!rs.next() || !"wal".equalsIgnoreCase(rs.getString(1)))
                            throw new SQLException("SQLite não ativou WAL");
                    }
                    stmt.execute("PRAGMA foreign_keys = ON");
                    stmt.execute("PRAGMA cache_size = -1024");
                }
            }
            var conn = physical;
            var closed = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(ConnectionPool.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                var name = method.getName();
                if (method.getDeclaringClass() == Object.class) return switch (name) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "SQLite connection lease";
                };
                if (name.equals("close")) {
                    if (closed.compareAndSet(false, true)) release(conn);
                    return null;
                }
                if (name.equals("isClosed")) return closed.get() || conn.isClosed();
                if (closed.get()) throw new SQLException("Conexão devolvida ao pool");
                if (name.equals("unwrap")) {
                    if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                    throw new SQLException("A conexão física pertence ao pool");
                }
                if (name.equals("isWrapperFor")) return ((Class<?>) args[0]).isInstance(proxy);
                try { return method.invoke(conn, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        } catch (SQLException | RuntimeException | Error e) {
            if (physical != null) try { physical.close(); } catch (SQLException ignored) {}
            slots.release();
            throw e;
        }
    }

    private void release(Connection conn) throws SQLException {
        try {
            if (!conn.isClosed()) {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                    conn.setAutoCommit(true);
                }
                conn.clearWarnings();
                idle.addFirst(conn);
            }
        } catch (SQLException e) {
            try { conn.close(); } catch (SQLException close) { e.addSuppressed(close); }
            throw e;
        } finally { slots.release(); }
    }
}
