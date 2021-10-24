package ru.netology;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Scanner;

public class Main {
    public static final int POOL_SIZE = 64;
    public static final String PUBLIC_DIR = "public";
    public static final int SERVER_PORT = 9999;

    public static void main(String[] args) {
        Server server = new Server(POOL_SIZE, PUBLIC_DIR, SERVER_PORT);

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
            String content = Files.readString(filePath);

            Document doc = Jsoup.parse(content, "UTF-8");
            Element loginReport = doc.getElementById("login");
            Element passwordReport = doc.getElementById("password");

            if (request.getQueryParam("login").isPresent() &&
                request.getQueryParam("password").isPresent() &&
                loginReport != null && passwordReport != null) {

                loginReport.text(String.format("Принят логин: %s",
                            request.getQueryParam("login").get()[0]));
                passwordReport.text(String.format("Принят пароль: %s",
                            request.getQueryParam("password").get()[0]));
                content = doc.html();
            }

            responseStream.write("""
                    HTTP/1.1 200 OK\r
                    Content-Type: %s\r
                    Content-Length: %d\r
                    Connection: close\r
                    \r
                    """.formatted(Files.probeContentType(filePath), content.length())
                .getBytes());
            responseStream.write(content.getBytes());
            responseStream.flush();
        });

        // обработчик пост-формы с "пост-формы" на главную
        server.addHandler("POST", "/index.html", (request, responseStream) -> {

        });


        server.start();

        Scanner scanner = new Scanner(System.in);
        while (true)
            if ("stop".equalsIgnoreCase(scanner.nextLine()))
                break;

            // можно добавить установку порта с консоли или даже запуск с параметрами
            // тогда класс Main превращается в оболочку для управления сервером

        server.stopServer();
    }
}


