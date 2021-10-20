package ru.netology;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Содержит структуру запроса к северу: метод, путь, набор заголовков
 * и карту параметров, а также виртуальное тело запроса.
 * Данная реализация также содержит значение пути по умолчанию
 * и значение лимита на длину запроса.
 */
public class Request {
    private final String method;
    private final String originalPath;
    private final String path;
    private final Map<String, List<String>> queryParams;
    private final Map<String, String> headers;
    private final InputStream in;

    private static final String defaultPath = "/index.html";   // начальный путь
    private static final int limit = 4096;

    private Request(String method, String originalPath, String path, Map<String, List<String>> queryParams, Map<String, String> headers, InputStream in) {
        this.method = method;
        this.originalPath = originalPath;
        this.path = path;
        this.queryParams = queryParams;
        this.headers = headers;
        this.in = in;
        System.out.println(this);
    }

    @Override
    public String toString() {
        StringBuilder desc = new StringBuilder(
                        ("""
                                \tЗАПРОС:
                                метод\t=\t%s
                                путь\t=\t%s
                                \tзаголовки:
                                """).formatted(method, path));

        for (Map.Entry<String, String> header : headers.entrySet())
            desc
                .append(header.getKey()).append("\t=\t")
                .append(header.getValue()).append("\n");

        if (queryParams != null && !queryParams.isEmpty()) {
            desc.append("\tПараметры запроса:\n");
            for (Map.Entry<String, List<String>> query : queryParams.entrySet())
                for (String value : query.getValue())
                    desc.append(query.getKey()).append(" = ").append(value).append("\n");
        }

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
        final var requestParts = requestLine.split(" ");

        if (requestParts.length != 3 ||
                !requestParts[1].startsWith("/")) {
            throw new IOException("Invalid request");
        }

        final var method = requestParts[0];
        final var originalPath = requestParts[1];

        Map<String, List<String>> params = new HashMap<>();

        final String path;
        if (!originalPath.contains("?")) {
            path = originalPath;
            params = null;
        } else {
            int queryIndex = originalPath.indexOf("?");
            path = originalPath.substring(0, queryIndex);
            final var queries = originalPath.substring(queryIndex + 1);

            for (String line : queries.split("&")) {
                int delimiterIndex = line.indexOf("=");
                String name = URLDecoder.decode(line.substring(0, delimiterIndex), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(line.substring(delimiterIndex + 1), StandardCharsets.UTF_8);
                params.putIfAbsent(name, new ArrayList<>());
                params.get(name).add(value);
            }
        }

        String line;
        Map<String, String> headers = new HashMap<>();
        while (!(line = in.readLine()).isBlank()) {
            var i = line.indexOf(":");
            var headerName = line.substring(0, i);
            var headerValue = line.substring(i + 2);
            headers.put(headerName, headerValue);
        }
        // запрос с виртуальным телом
        return new Request(method, originalPath, path, params, headers, inputStream);
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


    /**
     * Возвращает список значений, соответствующие запрашиваемому параметру.
     * @param name имя параметра.
     * @return  массив присутствующих значений параметра
     * или пустой массив строк, если параметр отсутствует.
     */
    public String[] getQueryParam(String name) {
        if (!queryParams.containsKey(name))
            return new String[0];
        return queryParams.get(name).toArray(String[]::new);
    }

    /**
     * Возвращает карту из ключей параметров и значений типа 'список значений'.
     * @return опциональное значение поля queryParam.
     */
    public Optional<Map<String, List<String>>> getQueryParams() {
        return Optional.of(queryParams);
    }


    // from google guava with modifications
    @SuppressWarnings("GrazieInspection")
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public String getOriginalPath() {
        return originalPath;
    }

    public boolean hasQueryParams() {
        return queryParams != null && !queryParams.isEmpty();
    }
}
