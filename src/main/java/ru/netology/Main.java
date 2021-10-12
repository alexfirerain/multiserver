package ru.netology;

import java.io.BufferedOutputStream;
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

    // добавление обработчиков
    server.addHandler("GET", "/messages", (request, responseStream) -> {
      // TODO: handlers code
    });
    server.addHandler("POST", "/messages", (request, responseStream) -> {
      // TODO: handlers code
    });

    server.listen(SERVER_PORT);
  }
}


