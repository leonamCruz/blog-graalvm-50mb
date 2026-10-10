package top.leonam;

import com.sun.net.httpserver.HttpServer;
import top.leonam.bd.CategoriaImpl;
import top.leonam.bd.BuscaImpl;
import top.leonam.bd.Database;
import top.leonam.bd.PostImpl;
import top.leonam.bd.TagImpl;
import top.leonam.controller.*;
import top.leonam.service.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    static Logger logger = Logger.getLogger("MAIN");

    public static int PORTA_PADRAO = 7979;
    public static int BACKLOG_PADRAO = 1024;

    static void main(String[] args) throws IOException {
        System.setProperty("sun.net.httpserver.nodelay", "true");
        System.setProperty("sun.net.httpserver.maxReqTime", "15");
        System.setProperty("sun.net.httpserver.maxRspTime", "15");

        System.setProperty("jdk.httpserver.maxConnections", "-1");
        System.setProperty("sun.net.httpserver.maxReqHeaders", "64");
        System.setProperty("sun.net.httpserver.maxReqHeaderSize", "16384");
        System.setProperty("sun.net.httpserver.idleInterval", "15");

        Database.inicializarBanco();

        var postRepository = new PostImpl();
        var categoriaRepository = new CategoriaImpl();
        var tagRepository = new TagImpl();

        var postListService = new PostListService();
        var arquivoService = new ArquivoService();
        var indexService = new IndexService(postRepository, categoriaRepository, tagRepository);
        var postService = new PostService(postRepository);
        var categoriaService = new CategoriaService(categoriaRepository, postRepository, postListService);
        var tagService = new TagService(tagRepository, postRepository, postListService);

        var indexController = new IndexController(indexService);
        var arquivoController = new ArquivoController(arquivoService);
        var postController = new PostController(postService);
        var categoriaController = new CategoriaController(categoriaService);
        var tagController = new TagController(tagService);
        var staticController = new StaticController();
        var postPaginationService = new PostPaginationService(postRepository);
        var postPaginationController = new PostPaginationController(postPaginationService);
        var sitemapController = new SitemapController(postRepository);

        var servidor = HttpServer.create(new InetSocketAddress(PORTA_PADRAO),BACKLOG_PADRAO);

        servidor.createContext("/blog/", postController);
        servidor.createContext("/buscar", new BuscaController(new BuscaService(new BuscaImpl()), indexService));
        servidor.createContext("/posts", postPaginationController);
        servidor.createContext("/categoria/", categoriaController);
        servidor.createContext("/tag/", tagController);
        servidor.createContext("/fonts/", staticController);
        servidor.createContext("/css/", staticController);
        servidor.createContext("/js/", staticController);
        servidor.createContext("/img/", staticController);
        servidor.createContext("/arquivos", arquivoController);
        servidor.createContext("/sitemap.xml", sitemapController);
        servidor.createContext("/", indexController);
        servidor.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        servidor.start();

        logger.log(Level.INFO, "Servidor rodando na porta " + PORTA_PADRAO);

    }
}
