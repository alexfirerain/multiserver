package ru.netology;

import java.util.List;

public class Main {
    public static final int POOL_SIZE = 64;
    static final List<String> validPaths = List.of("/index.html",
            "/spring.svg", "/spring.png", "/resources.html",
            "/styles.css", "/app.js", "/links.html", "/forms.html",
            "/classic.html", "/events.html", "/events.js");
    public static final String PUBLIC_DIR = "public";
    public static final int SERVER_PORT = 9999;

    public static void main(String[] args) {
        Server server = new Server(POOL_SIZE, validPaths, PUBLIC_DIR);
        server.listen(SERVER_PORT);
    }
}


