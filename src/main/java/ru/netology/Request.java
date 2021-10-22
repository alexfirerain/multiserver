package ru.netology;

import java.io.*;
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
    private final String body;

    private static final String defaultPath = "/index.html";   // начальный путь
    private static final int limit = 4096;

    private Request(String method, String originalPath, String path, Map<String, List<String>> queryParams, Map<String, String> headers, String body) {
        this.method = method;
        this.originalPath = originalPath;
        this.path = path;
        this.queryParams = queryParams;
        this.headers = headers;
        this.body = body;
        System.out.println(this);           // мониторинг
    }

    @Override
    public String toString() {
        StringBuilder desc = new StringBuilder(
                        ("""
                                \tЗАПРОС:
                                метод\t=\t%s
                                путь\t=\t%s
                                \tЗаголовки:
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

        if (!body.isBlank()) {
            desc.append("\tТело:\n").append(body);
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
        final var in = new BufferedInputStream(inputStream);
        in.mark(limit);
        final var buffer = new byte[limit];
        final var read = in.read(buffer);
        final var requestLineDelimiter = new byte[]{'\r', '\n'};

        final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
//        System.out.println("requestLineEnd = " + requestLineEnd + "\nbufferLength = " + read); // мониторинг
        if (requestLineEnd == -1 && read > 0) {
            throw new IOException("Invalid request");
        }
        final var requestLineParts = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (requestLineParts.length != 3) {
            throw new IOException("Invalid request");
        }

        final var method = requestLineParts[0];
        final var originalPath = requestLineParts[1];

        final String path;
        Map<String, List<String>> params = new HashMap<>();
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

        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) throw new IOException("Invalid request");
        in.reset();
        in.skip(headersStart);
        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        final var headerPairs = new String(headersBytes).split("\r\n");
        Map<String, String> headers = new HashMap<>();
        for (String line : headerPairs) {
            var i = line.indexOf(":");
            var headerName = line.substring(0, i);
            var headerValue = line.substring(i + 2);
            headers.put(headerName, headerValue);
        }

        // читаем тело
        String body = "";
        if (!method.equals("GET")) {
            in.skip(headersDelimiter.length);
            // вычитываем Content-Length, чтобы прочитать body
            final var contentLength = Optional.of(headers.get("Content-Length"));
            if (contentLength.isPresent()) {
                final var length = Integer.parseInt(contentLength.get());
                final var bodyBytes = in.readNBytes(length);
                body = new String(bodyBytes);
            }
        }

        // TODO: получим параметры из запроса, если они в теле



        // запрос с виртуальным телом
        return new Request(method, originalPath, path, params, headers, body);
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

    public String getBody() {
        return body;
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



    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
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
