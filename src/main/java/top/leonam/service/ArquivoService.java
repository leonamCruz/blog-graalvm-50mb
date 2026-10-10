package top.leonam.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class ArquivoService {

    private final Path baseDir;
    private final String template = lerTemplate();

    public ArquivoService() {

        this.baseDir =
                Paths.get("arquivos")
                        .toAbsolutePath()
                        .normalize();

        try {
            if (!Files.exists(baseDir)) {
                Files.createDirectories(baseDir);
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao criar diretório base de arquivos",
                    e
            );
        }
    }

    public String carregarTemplate() { return template; }

    private String lerTemplate() {

        try (var input = getClass().getClassLoader().getResourceAsStream("templates/arquivos.html")) {
            if (input == null) {
                throw new RuntimeException("Template arquivos.html não encontrado");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar arquivos.html", e);
        }
    }

    public String listarHtml(String subPath) throws IOException {

        var target = obterCaminhoSeguro(subPath);

        if (target == null || !Files.isDirectory(target)) {
            return """
                    <p>
                        Diretório inválido ou acesso negado.
                    </p>
                    """;
        }

        var html = new StringBuilder("<ul style='list-style: none; padding: 0;'>");

        if (!target.equals(baseDir)) {
            var parent = baseDir.relativize(target.getParent()).toString().replace("\\", "/");
            var linkVoltar = parent.isEmpty() ? "/arquivos/list" : "/arquivos/list/" + top.leonam.util.Web.caminho(parent);

            html.append("""
                    <li>
                        <button
                            class="button button-outline"
                            hx-get="%s"
                            hx-target="#file-browser">
                            ⬅ Voltar
                        </button>
                    </li>
                    <br>
                    """.formatted(linkVoltar));
        }

        try (Stream<Path> stream = Files.list(target)) {

            var itens = stream.filter(p -> !Files.isSymbolicLink(p)).limit(1001).sorted((p1, p2) -> {
                var p1Diretorio = Files.isDirectory(p1);
                var p2Diretorio = Files.isDirectory(p2);

                if (p1Diretorio && !p2Diretorio) return -1;
                if (!p1Diretorio && p2Diretorio) return 1;

                return p1.getFileName().toString().compareToIgnoreCase(p2.getFileName().toString());
            }).toList();

            if (itens.size() > 1000) {
                return "<p>Diretório excede o limite de 1000 itens; organize em subpastas.</p>";
            }
            for (var p : itens) {
                var relativePath = baseDir.relativize(p).toString().replace("\\", "/");
                var nome = p.getFileName().toString();

                var caminhoHtml = top.leonam.util.Web.caminho(relativePath);
                var nomeHtml = escaparHtml(nome);

                if (Files.isDirectory(p)) {
                    html.append("""
                            <li>
                                📁
                                <a
                                    href="#"
                                    hx-get="/arquivos/list/%s"
                                    hx-target="#file-browser">
                                    %s
                                </a>
                            </li>
                            """.formatted(caminhoHtml, nomeHtml));
                } else {
                    // Define ?view=true por padrão para visualização ao clicar.
                    // Para forçar o download, é só acessar sem o view=true.
                    html.append("""
                            <li>
                                📄
                                <a
                                    href="/arquivos/download/%s?view=true"
                                    target="_blank"
                                    rel="noopener">
                                    %s
                                </a>
                            </li>
                            """.formatted(caminhoHtml, nomeHtml));
                }
            }
        }

        html.append("</ul>");

        return html.toString();
    }

    public Path obterCaminhoSeguro(String subPath) {

        if (subPath == null) {
            subPath = "";
        }

        try {
            var target = baseDir.resolve(subPath).normalize();
            if (!target.startsWith(baseDir)) return null;
            // Reject symlinks in every component, even links pointing inside the public root.
            var current = baseDir;
            for (var part : baseDir.relativize(target)) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) return null;
            }
            var realBase = baseDir.toRealPath();
            var realTarget = target.toRealPath();
            return realTarget.startsWith(realBase) ? realTarget : null;
        } catch (IOException | java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    private String escaparHtml(String valor) {
        return top.leonam.util.Web.escaparHtml(valor);
    }
}
