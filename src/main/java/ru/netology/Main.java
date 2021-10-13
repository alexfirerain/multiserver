package ru.netology;

import java.io.BufferedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
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

    // обработчик "классики"
    server.addHandler("GET", "/classic.html", (request, responseStream) -> {

      final var filePath = Path.of(".", PUBLIC_DIR, request.getPath());
      final var mimeType = Files.probeContentType(filePath);
      final var template = Files.readString(filePath);
      final var content = template.replace(
              "{time}",
              LocalDateTime.now().toString()
      ).getBytes();
      responseStream.write((
              ("""
                                    HTTP/1.1 200 OK\r
                                    Content-Type: %s\r
                                    Content-Length: %d\r
                                    Connection: close\r
                                    \r
                                    """).formatted(mimeType, content.length)
      ).getBytes());
      responseStream.write(content);
      responseStream.flush();
    });

    server.addHandler("POST", "/messages", (request, responseStream) -> {
      // TODO: handlers code
    });

    server.listen(SERVER_PORT);
  }
}


