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
    public static final String FILES_DIR = "files";     // в настоящей реализации не используется
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
                content = setTextToElement(content, "login", "Принят логин: %s"
                                .formatted(request.getQueryParam("login").get()[0]));
            }

            if (request.getQueryParam("password").isPresent()) {
                content = setTextToElement(content, "password", "Принят пароль: %s"
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

        // обработчик пост-формы на главную
        server.addHandler("POST", "/index.html", (request, responseStream) -> {
            final var filePath = Path.of(".", server.getPublic_dir(), request.getPath());
            String content = Files.readString(filePath);

            if (!request.hasAnyParams()) {
                content = setTextToElement(content,
                        "response",
                        "Никаких параметров не принято!");
            } else {
                Document page = Jsoup.parse(content, "UTF-8");
                Element target = page.getElementById("response");
                if (target != null) {
                    target.append("<h3>Приняты следующие значения:</h3>");
                    for (Map.Entry<String, List<String>> entry : request.getAllParams().entrySet()) {

                        Element paramName = new Element("b").append(entry.getKey());
                        target.appendChild(paramName);

                        Element valueList = new Element("ul");
                        target.appendChild(valueList);

                        for (String value : entry.getValue())
                            valueList.append("<li>%s</li>".formatted(value));
                    }
                }
                content = page.html();
                //TODO: Это просто чтобы страница передалась целиком ! требует решения!!
                content += "DATA_TO_TRICK_JSOUP_BUG:0123456789abcdef_" +
                        "0123456789abcdef0123456789abcdef_0123456789" +
                        "0123456789abcdef0123456789abcdef_0123456789" +
                        "0123456789abcdef0123456789abcde";
            }
            System.out.println(content);    // мониторинг
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

        // обработчик многочастного запроса на "upload"
        server.addHandler(Server.POST, "/upload-forms.html", (request, responseStream) -> {
            if(!request.isMultipart()) {
                server.badRequestResponse(responseStream);
            }
            final var filePath = Path.of(".", server.getPublic_dir(), request.getPath());
            String content = Files.readString(filePath);
            Document page = Jsoup.parse(content, "UTF-8");

            Element answer = page.getElementById("response");
            Element imageHolder = page.getElementById("image-holder");

            if(answer != null) {
                List<MultiPartDatum> parts = request.getMultiPartData();
                answer.append("Из формы получены значения:<br/>");
                for (MultiPartDatum part : parts)
                    if (part.isText() && part.formDataName().isPresent())
                        answer.append(part.formDataName().get()).append(" = ")
                                .append(part.getBodyString()).append("<br/>");
            }

            MultiPartDatum image = request.getFormDatumByName("image");
            if (image != null && image.hasBody()) {
                var filename = image.formDataFilename();
                var savedFilename = "image" + filename.map(s -> s.substring(s.lastIndexOf("."))).orElse("");

                image.saveBodyToFile(Path.of(".", PUBLIC_DIR, savedFilename));

                if (imageHolder != null) {
                    imageHolder.appendChild(new Element("img").attr("src", savedFilename));
                    imageHolder.prepend("Загружен файл:" + filename.orElse("без названия") + "<br/>");
                }
            }
            if (image == null && imageHolder != null) {
                imageHolder.append("Файла не загружено.");
            }

            content = page.html();
            //TODO: Это просто чтобы страница передалась целиком ! требует решения!!
            content += "DATA_TO_TRICK_JSOUP_BUG:0123456789abcdef_" +
                    "0123456789abcdef0123456789abcdef_0123456789" +
                    "0123456789abcdef0123456789abcdef_0123456789" +
                    "0123456789abcdef0123456789abcde";

            System.out.println(content);    // мониторинг
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
    private static String setTextToElement(String content, String id, String text) {
        Document doc = Jsoup.parse(content, "UTF-8");
        Element element = doc.getElementById(id);
        if (element != null) {
            element.append(text);
        }
        return doc.html();
    }


}


