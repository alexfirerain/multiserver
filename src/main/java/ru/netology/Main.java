package ru.netology;

import java.util.List;

public class Main {
  static final List<String> validPaths = List.of("/index.html",
          "/spring.svg", "/spring.png", "/resources.html",
          "/styles.css", "/app.js", "/links.html", "/forms.html",
          "/classic.html", "/events.html", "/events.js");
  public static final int SERVER_PORT = 9999;


  public static void main(String[] args) {
    System.out.println("MAIN");
    Server server = new Server(12, validPaths, "public");
    server.listen(SERVER_PORT);

  }
}


