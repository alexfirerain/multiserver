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
    private final Map<String, List<String>> postParams;

    private static final String defaultPath = "/index.html";   // начальный путь
    private static final int limit = 4096;

    private Request(String method, String originalPath, String path, Map<String, List<String>> queryParams, Map<String, String> headers, String body, Map<String, List<String>> postParams) {
        this.method = method;
        this.originalPath = originalPath;
        this.path = path;
        this.queryParams = queryParams;
        this.headers = headers;
        this.body = body;
        this.postParams = postParams;
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
    public static Request fromInputStream(InputStream inputStream) throws IOException, NumberFormatException {
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

        final var rqMethod = requestLineParts[0];
        final var rqOriginalPath = requestLineParts[1];

        final String rqPath;
        Map<String, List<String>> rqParams = new HashMap<>();
        if (!rqOriginalPath.contains("?")) {
            rqPath = rqOriginalPath;
        } else {
            int queryIndex = rqOriginalPath.indexOf("?");
            rqPath = rqOriginalPath.substring(0, queryIndex);
            final var queryString = rqOriginalPath.substring(queryIndex + 1);

            for (String line : queryString.split("&")) {
                int delimiterIndex = line.indexOf("=");
                String name = URLDecoder.decode(line.substring(0, delimiterIndex), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(line.substring(delimiterIndex + 1), StandardCharsets.UTF_8);
                rqParams.putIfAbsent(name, new ArrayList<>());
                rqParams.get(name).add(value);
            }
        }

        final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        final var headersStart = requestLineEnd + requestLineDelimiter.length;
        final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
        if (headersEnd == -1) {
            throw new IOException("Invalid request");
        }
        in.reset();
        in.skip(headersStart);
        final var headersBytes = in.readNBytes(headersEnd - headersStart);
        final var headerPairs = new String(headersBytes).split("\r\n");
        Map<String, String> rqHeaders = new HashMap<>();
        for (String line : headerPairs) {
            var i = line.indexOf(":");
            var headerName = line.substring(0, i);
            var headerValue = line.substring(i + 2);
            rqHeaders.put(headerName, headerValue);
        }

        // читаем тело
        byte[] bodyBytes = new byte[0];
        if (!rqMethod.equals("GET")) {
            in.skip(headersDelimiter.length);
            final var contentLengthValue = rqHeaders.get("Content-Length");
            if (contentLengthValue != null)
                bodyBytes = in.readNBytes(Integer.parseInt(contentLengthValue));
        }
        // TODO: получим параметры из запроса, если они в теле
        Map<String, List<String>> rqPostParams = new HashMap<>();




        // запрос с неразобранным телом
        return new Request(rqMethod, rqOriginalPath, rqPath, rqParams, rqHeaders, new String(bodyBytes), rqPostParams);
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
     * Возвращает опционально значение для запрашиваемого заголовка, если он найден.
     * @param header заголовок, значение которого нужно узнать.
     * @return  значение запрошенного заголовка либо, если он не найден, пустую опциональ.
     */
    public Optional<String> getHeader(String header) {
        return Optional.ofNullable(headers.get(header));
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
     * Возвращает опционально значения, соответствующие запрашиваемому параметру.
     * @param name имя параметра.
     * @return  опциональ с массивом значений параметра либо, если параметр отсутствует, пустую.
     */
    public Optional<String[]> getQueryParam(String name) {
        return queryParams.get(name) == null ?
                Optional.empty() :
                Optional.of(queryParams.get(name).toArray(String[]::new));
    }

    /**
     * Возвращает карту из ключей типа 'параметр' и значений типа 'список значений'.
     * @return значение поля queryParam.
     */
    public Map<String, List<String>> getQueryParams() {
        return queryParams;
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

    /**
     * Сообщает, распознаны ли в запросе квери-парамс.
     * @return  true, если присутствует хотя бы один параметр.
     */
    public boolean hasQueryParams() {
        return queryParams != null && !queryParams.isEmpty();
    }

    public Map<String, List<String>> getPostParams() {
        return postParams;
    }

}
