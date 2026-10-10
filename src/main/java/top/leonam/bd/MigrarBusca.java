package top.leonam.bd;

/** Run the same startup migration without opening an HTTP port. */
public final class MigrarBusca {
    private MigrarBusca() {}
    public static void main(String[] args) { Database.inicializarBanco(); }
}
