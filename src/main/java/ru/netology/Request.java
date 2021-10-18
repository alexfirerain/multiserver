package ru.netology;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Содержит структуру запроса к северу: метод, путь, набор заголовков,
 * а также виртуальное тело запроса.
 * Данная реализация также содержит значение пути по умолчанию
 * и запрос по умолчанию.
 */
public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final InputStream in;

    private static final String defaultPath = "/index.html";   // начальный путь
    public static final Request DEFAULT = new Request("GET", defaultPath, new HashMap<>(), null);

    private Request(String method, String path, Map<String, String> headers, InputStream in) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.in = in;
    }

    @Override
    public String toString() {
        StringBuilder desc = new StringBuilder((
                "\tЗАПРОС:%n" +
                        "метод\t=\t%s%n" +
                        "путь\t=\t%s%n" +
                        "\tзаголовки:%n")
                .formatted(method, path));

        for (Map.Entry<String, String> header : headers.entrySet())
            desc
                    .append(header.getKey())
                    .append("\t=\t")
                    .append(header.getValue())
                    .append("\n");

        return desc.append("\n").toString();
    }

    /**
     * Создаёт структурированный запрос на основе входного потока.
     *
     * @param inputStream входной поток.
     * @return структурированный HTTP-запрос.
     * @throws IOException при проблемах со связью или при нерабочем запросе.
     */
    public static Request fromInputStream(InputStream inputStream) throws IOException {
        var in = new BufferedReader(new InputStreamReader(inputStream));

        final var requestLine = in.readLine();
        final var parts = requestLine.split(" ");

        if (parts.length != 3) {
            throw new IOException("Invalid request");
        }

        final var method = parts[0];
        final var path = parts[1];

        String line;
        Map<String, String> headers = new HashMap<>();
        while (!(line = in.readLine()).isBlank()) {
            var i = line.indexOf(":");
            var headerName = line.substring(0, i);
            var headerValue = line.substring(i + 2);
            headers.put(headerName, headerValue);
        }
        // запрос с виртуальным телом
        return new Request(method, path, headers, inputStream);
    }

    public String getMethod() {
        return method;
    }

    /**
     * Возвращает адрес ресурса из запроса или же адрес по умолчанию, если путь в запросе пустой.
     *
     * @return адрес ресурса из запроса или адрес по умолчанию.
     */
    public String getPath() {
        return "".equals(path) || "/".equals(path) ?        // чтобы пустой путь вёл на начальную
                defaultPath : path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Сообщает, какой ресурс принят на сервере по умолчанию. // это связывает реализацию класса с архитектурой сервера.
     *
     * @return значение поля defaultPath.
     */
    public String getDefaultPath() {
        return defaultPath;
    }

    public InputStream getIn() {
        return in;
    }
}
