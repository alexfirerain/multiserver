package ru.netology;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Содержит структуру запроса к северу: метод, путь, набор заголовков
 * и карту параметров, а также тело запроса.
 * Данная реализация также содержит значение пути по умолчанию
 * и значение лимита на длину запроса.
 */
public class Request {
    private static final int limit = 4096;
    private static final byte[] REQUEST_LINE_DELIMITER = {'\r', '\n'};
    private static final byte[] HEADERS_DELIMITER = {'\r', '\n', '\r', '\n'};
    private static final String defaultPath = "/index.html";   // начальный путь

    private final String method;
    private final String originalPath;
    private final String path;
    private final Map<String, List<String>> queryParams;
    private final Map<String, String> headers;
    private final String body;
    private final Map<String, List<String>> postParams;
    private final List<MultiPartDatum> multiPartData;



    private Request(String method, String originalPath, String path,
                    Map<String, List<String>> queryParams, Map<String, String> headers,
                    String body, Map<String, List<String>> postParams, List<MultiPartDatum> multiPartData) {
        this.method = method;
        this.originalPath = originalPath;
        this.path = path;
        this.queryParams = queryParams;
        this.headers = headers;
        this.body = body;
        this.postParams = postParams;
        this.multiPartData = multiPartData;
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

        if (hasQueryParams()) {
            desc.append("\tПараметры запроса из адресной строки́:\n");
            for (Map.Entry<String, List<String>> query : queryParams.entrySet())
                for (String value : query.getValue())
                    desc.append(query.getKey()).append(" = ").append(value).append("\n");
        }

        if (!body.isBlank()) {
            desc.append("\tТело:\n").append(body);
        }

        if (hasPostParams()) {
            desc.append("\n\tПараметры запроса из те́ла:\n");
            for (Map.Entry<String, List<String>> query : postParams.entrySet())
                for (String value : query.getValue())
                    desc.append(query.getKey()).append(" = ").append(value).append("\n");
        }
        if (hasMultiPartData()) {
            desc.append("\nПрисутствует частей запроса: ").append(multiPartData.size());
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


        final var requestLineEnd = indexOf(buffer, REQUEST_LINE_DELIMITER, 0, read);
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
        Map<String, List<String>> rqQParams = new HashMap<>();
        if (!rqOriginalPath.contains("?")) {
            rqPath = rqOriginalPath;
        } else {
            int queryIndex = rqOriginalPath.indexOf("?");
            rqPath = rqOriginalPath.substring(0, queryIndex);
            final var queryString = rqOriginalPath.substring(queryIndex + 1);
            rqQParams = paramStringToMap(queryString, "application/x-www-form-urlencoded");
        }

        // постановили окончатель заголовков

        // начало заголовков = конец строки + длина окончателя строки
        final var headersStart = requestLineEnd + REQUEST_LINE_DELIMITER.length;
        // конец заголовков = где начинается окончатель заголовков
        final var headersEnd = indexOf(buffer, HEADERS_DELIMITER, headersStart, read);
        if (headersEnd == -1) {
            throw new IOException("Invalid request");
        }
        // вернулись читать с начала ввода
        in.reset();
        // промотали до начала заголовков
        in.skip(headersStart);
        // байты заголовков = байты отсюда до (длины заголовков)
        final var headersBytes = in.readNBytes(headersEnd - headersStart);

        // трактуем байты заголовков как строку и нарезаем по меткам конца строк на массив
        final var headerPairs = new String(headersBytes).split("\r\n");
        Map<String, String> rqHeaders = new HashMap<>();
        // каждую строку разбиваем по двоеточию и картируем
        for (String line : headerPairs) {
            var i = line.indexOf(":");
            var headerName = line.substring(0, i);
            var headerValue = line.substring(i + 2);
            rqHeaders.put(headerName, headerValue);
        }

        // читаем тело
        byte[] bodyBytes = new byte[0];
        if (!rqMethod.equals("GET")) {
            // промотали ввод на длину окончателя заголовков
            in.skip(HEADERS_DELIMITER.length);
            // читаем значение заголовка длины содержимого
            final var contentLengthString = rqHeaders.get("Content-Length");
            // если такой заголовок есть
            if (contentLengthString != null)
                // байты тела = столько байтов отсюда, как указано в нём
                bodyBytes = in.readNBytes(Integer.parseInt(contentLengthString));
        }

        final var body = new String(bodyBytes);
        Map<String, List<String>> rqPostParams = new HashMap<>();
        List<MultiPartDatum> rqMultiPartData = new ArrayList<>();

        var contentType = rqHeaders.get("Content-Type");
        //если существует тело и тип содержимого
        if (body.length() > 0 && contentType != null) {
            // если не многочастный тип
            if (!contentType.startsWith("multipart/form-data")){
                // прочесть соответствующие типу параметры
                rqPostParams = paramStringToMap(body, contentType);
            // если же тип многочастный
            } else {
                // узнать разделитель
                final var boundary = contentType.substring(contentType.indexOf("=") + 1).getBytes();
                // текущая позиция в теле на конце разделителя
                int cur = boundary.length;
                // скопировать с текущей позиции по конец заголовков
                var partHeaders = Arrays.copyOfRange(bodyBytes,
                        cur, indexOf(bodyBytes, HEADERS_DELIMITER,
                                cur, indexOf(bodyBytes, boundary,
                                        cur, bodyBytes.length)));





            }
        }






        // запрос с разобранным телом
        return new Request(rqMethod, rqOriginalPath, rqPath, rqQParams, rqHeaders, body, rqPostParams, rqMultiPartData);
    }

    /**
     * Создаёт из полученной строки́ Карту <Имя, Список<Значение>>,
     * разбивая материал пары ключ/значение в соответствии с указанной кодировкой.
     * @param material  разбираемая строка.
     * @param encType   тип содержимого (предполагается указанный в заголовке запроса).
     * @return  карту параметров "имя-значение".
     */
    private static Map<String, List<String>> paramStringToMap(String material, String encType) {
        Map<String, List<String>> map = new HashMap<>();

        switch (encType) {

            case "application/x-www-form-urlencoded":
                for (String line : material.split("&")) {
                    int delimiterIndex = line.indexOf("=");
                    String name = URLDecoder.decode(line.substring(0, delimiterIndex), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(line.substring(delimiterIndex + 1), StandardCharsets.UTF_8);
                    map.putIfAbsent(name, new ArrayList<>());
                    map.get(name).add(value);
                }
                break;

            case "text/plain":
                for (String line : material.split("\r\n")) {
                    int delimiterIndex = line.indexOf("=");
                    String name = line.substring(0, delimiterIndex);
                    String value = line.substring(delimiterIndex + 1);
                    map.putIfAbsent(name, new ArrayList<>());
                    map.get(name).add(value);
                }
                break;

            // TODO: разбор многочастного запроса.

        }
        return map;
    }

    /**
     * Сообщает метод запроса.
     * @return  значение поля method.
     */
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

    /**
     * Возвращает карту распознанных заголовков.
     * @return значение поля headers.
     */
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

    /**
     * Возвращает строковое представление переданного тела запроса.
     * @return значение поля body.
     */
    public String getBody() {
        return body;
    }

    /**
     * Возвращает опционально значения из строки запроса, соответствующие запрашиваемому параметру.
     * @param name имя параметра.
     * @return  опциональ с массивом значений параметра либо, если параметр отсутствует, пустую.
     */
    public Optional<String[]> getQueryParam(String name) {
        return queryParams.get(name) == null ?
                Optional.empty() :
                Optional.of(queryParams.get(name).toArray(String[]::new));
    }

    /**
     * Возвращает опционально значения из тела запроса, соответствующие запрашиваемому параметру.
     * @param name имя параметра.
     * @return  опциональ с массивом значений параметра либо, если параметр отсутствует, пустую.
     */
    public Optional<String[]> getPostParam(String name) {
        return postParams.get(name) == null ?
                Optional.empty() :
                Optional.of(postParams.get(name).toArray(String[]::new));
    }

    /**
     * Возвращает опционально массив всех значений, соответствующих заданному параметру запроса,
     * переданы ли они адресной строкой или телом.
     * @param name запрашиваемый параметр.
     * @return  опциональ с массивом строковых значений.
     */
    public Optional<String[]> getAnyParam(String name) {
        var values = getAllParams().get(name);
            if (values == null) {
                return Optional.empty();
            } else {
                return Optional.of(values.toArray(String[]::new));
            }

    }

    /**
     * Возвращает карту из ключей типа 'параметр' и значений типа 'список значений'.
     * @return значение поля queryParam.
     */
    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }


    // from google guava with modifications
    /**
     * Находит в указанном массиве, с какого индекса начинается (в первый раз) указанная последовательность.
     * @param array  указанный массив.
     * @param target указанная последовательность.
     * @param start  с какого индекса в массиве искать.
     * @param max    по какой индекс в массиве искать.
     * @return  индекс указанной последовательности или -1, если она не обнаружена.
     */
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

    /**
     * Возвращает полный адресный путь запроса, т.е. путь и параметры, как они получены.
     * @return  полную строку запроса.
     */
    public String getOriginalPath() {
        return originalPath;
    }

    /**
     * Сообщает, распознаны ли в пути параметры запроса.
     * @return  true, если присутствует хотя бы один параметр.
     */
    public boolean hasQueryParams() {
        return queryParams != null && !queryParams.isEmpty();
    }
    /**
     * Сообщает, распознаны ли в параметры запроса теле.
     * @return {@code true}, если хотя бы один пост-параметр опознан.
     */
    private boolean hasPostParams() {
        return postParams != null && !postParams.isEmpty();
    }

    /**
     * Сообщает, присутствуют ли в запросе данные в многочастной форме.
     * @return true, если присутствует хотя бы одна многочастная сущность.
     */
    private boolean hasMultiPartData() {
        return multiPartData != null && !multiPartData.isEmpty();
    }

    /**
     * Возвращает карту из ключей типа 'параметр' и значений типа 'список значений'.
     * @return значение поля postParam.
     */
    public Map<String, List<String>> getPostParams() {
        return postParams;
    }

    /**
     * Создаёт и отдаёт объединённую карту параметров запроса, переданных в адресной строке и в теле.
     * @return карту всех параметров запроса.
     */
    public Map<String, List<String>> getAllParams() {
        Map<String, List<String>> allParams = new HashMap<>(queryParams);
        for (Map.Entry<String, List<String>> params : postParams.entrySet()) {
            allParams.putIfAbsent(params.getKey(), new ArrayList<>());
            allParams.get(params.getKey()).addAll(params.getValue());
        }
        return allParams;
    }

    /**
     * Сообщает, присутствуют ли в запросе параметры (в строке или в теле).
     * @return true, если распознан хотя бы один параметр.
     */
    public boolean hasAnyParams() {
        return hasQueryParams() || hasPostParams();
    }

    /**
     * Сообщает, является ли тип содержимого запроса text/plain.
     * @return true, если тип содержимого запроса text/plain.
     */
    public boolean isTextPlain() {
        var contentType = getHeader("Content-Type");
        return contentType.isPresent() && "text/plain".equals(contentType.get());
    }
    /**
     * Сообщает, является ли тип содержимого запроса application/x-www-form-urlencoded.
     * @return true, если тип содержимого запроса application/x-www-form-urlencoded.
     */
    public boolean isUrlEncoded() {
        var contentType = getHeader("Content-Type");
        return contentType.isPresent() && "application/x-www-form-urlencoded".equals(contentType.get());
    }
    /**
     * Сообщает, является ли тип содержимого запроса multipart/form-data.
     * @return true, если тип содержимого запроса multipart/form-data.
     */
    public boolean isMultipart() {
        var contentType = getHeader("Content-Type");
        return contentType.isPresent() && contentType.get().startsWith("multipart/form-data");
    }


    /**
     * Доставатель значения параметра, который не используется в данной реализации.
     * @param headers список строк "имя + значение параметра".
     * @param header  искомый параметр.
     * @return  первое найденное значение указанного параметра.
     */
    @Deprecated
    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }
}
