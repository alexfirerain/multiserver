package ru.netology;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

            if (request.getQueryParam("login").isPresent()) {

                content = setTextToElement(content,
                        "login",
                        "Принят логин: %s"
                                .formatted(request.getQueryParam("login").get()[0]));
            }

            if (request.getQueryParam("password").isPresent()) {
                content = setTextToElement(content,
                        "password",
                        "Принят пароль: %s"
                                .formatted(request.getQueryParam("password").get()[0]));
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
            final var filePath = Path.of(".", server.getPublic_dir(), request.getPath());
            String content = Files.readString(filePath);

            if (!request.hasAnyParams()) {
                content = setTextToElement(content,
                        "response",
                        "Никаких параметров не принято!");
            } else {

                StringBuilder report = new StringBuilder("Приняты следующие значения:\n");

                for (Map.Entry<String, List<String>> entry : request.getAllParams().entrySet()) {

                    report.append(entry.getKey()).append(":<br/>\n");

                    for (String value : entry.getValue()) {
                        report.append("\t>").append(value).append("<br/>\n");
                    }
                }

                content = setTextToElement(content, "response", report.toString());

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


        server.start();

        Scanner scanner = new Scanner(System.in);
        while (true)
            if ("stop".equalsIgnoreCase(scanner.nextLine()))
                break;

            // можно добавить установку порта с консоли или даже запуск с параметрами
            // тогда класс Main превращается в оболочку для управления сервером

        server.stopServer();
    }

    /**
     * Определяет входную строку как html-документ и, если в нём найден элемент
     * со специфицированным id, заменяет его текстовое содержание на переданный текст.
     * @param content входной html-документ.
     * @param id      id элемента, в который нужно вставить текст.
     * @param text    вставляемый текст.
     * @return  html-документ с произведённой заменой.
     */
    public static String setTextToElement(String content, String id, String text) {
        Document doc = Jsoup.parse(content, "UTF-8");
        Element element = doc.getElementById(id);
        if (element != null) {
            element.append(text);
        }
        return doc.html();
    }

//    public static String addCildNodeTo(String content, String targetId, String topicId, String txt) {
//        Document doc = Jsoup.parse(content, "UTF-8");
//        Element element = doc.getElementById(targetId);
//        if (element != null) {
//            element.append()
//        }
//        element.text(text);
//        return doc.html();
//    }

}


