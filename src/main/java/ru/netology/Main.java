package ru.netology;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class Main {
    public static final int POOL_SIZE = 64;
    public static final String PUBLIC_DIR = "public";
    public static final int SERVER_PORT = 9999;

    public static void main(String[] args) {
        Server server = new Server(POOL_SIZE, PUBLIC_DIR);

        // обработчик "классики"
        server.addHandler("GET", "/classic.html", (request, responseStream) -> {

            final var filePath = Path.of(".", server.getPublic_dir(), request.getPath());
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

        // обработчик "формы"
        server.addHandler("GET", "/forms.html", (request, responseStream) ->{

            if (!request.hasQueryParams()) {
                server.generalHandler.handle(request, responseStream);
                return;
            }

            final var filePath = Path.of(".", server.getPublic_dir(), request.getPath());
            final var mimeType = Files.probeContentType(filePath);
            final var template = Files.readString(filePath);

            final byte[] content = template.replace(
                    "{authorization}",
                    String.format("""
                                    <br/>
                                    Принят логин: %s
                                    Принят пароль: %s
                                """, request.getQueryParam("login")[0],
                                     request.getQueryParam("password")[0])
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

        server.listen(SERVER_PORT);
    }
}


