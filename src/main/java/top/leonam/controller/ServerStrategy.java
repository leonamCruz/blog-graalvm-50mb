package top.leonam.controller;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public interface ServerStrategy {
    void executar(HttpExchange exchange) throws IOException;
}
