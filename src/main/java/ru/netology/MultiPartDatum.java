package ru.netology;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MultiPartDatum {
    private final Map<String, String> headers;
    private final Map<String, String> contentDispositionProperties;
    private final byte[] body;

    /**
     * Создаёт многочасть из двух входных массивов байтов.
     * @param headersArea байты, соответствующие заголовкам.
     * @param bodyArea  байты, соответствующие телу.
     */
    public MultiPartDatum(byte[] headersArea, byte[] bodyArea) {
        var headerLines = (new String(headersArea)).split("\r\n");

        Map<String, String> headerSlices = new HashMap<>();
        for (String line : headerLines) {
            int delimiter = line.indexOf(":");
            if (delimiter == -1) continue;
            var headerName = line.substring(0, delimiter);
            var headerValue = line.substring(delimiter + 2);
            headerSlices.put(headerName, headerValue);
        }
        headers = headerSlices;

        contentDispositionProperties = new HashMap<>();
        var disposition = headers.get("Content-Disposition");
        if (disposition != null) {
            for (String property : disposition.split(";")) {
                int delimiter = property.indexOf("=");
                if (delimiter == -1) continue;
                var propertyName = property.substring(0, delimiter).trim();
                var propertyValue = property.substring(delimiter + 2, property.length() - 1).trim();
                contentDispositionProperties.put(propertyName, propertyValue);
            }
        }

        body = bodyArea;

//        System.out.println(this);       // мониторинг
    }

    @Override
    public String toString() {
        StringBuilder representation = new StringBuilder("Многочасть:\n");
        if (!headers.isEmpty()) {
            representation.append("\tЗаголовки:\n");
            for (Map.Entry<String, String> header : headers.entrySet())
                representation
                        .append(header.getKey()).append(" = ")
                        .append(header.getValue()).append("\n");
        }
        if (!contentDispositionProperties.isEmpty()) {
            representation.append("\tСвойства расположения:\n");
            for (Map.Entry<String, String> property : contentDispositionProperties.entrySet())
                representation
                        .append(property.getKey()).append(" = ")
                        .append(property.getValue()).append("\n");
        }
        if (formDataName().isPresent())
            representation.append("Имя формы: ").append(formDataName().get()).append("\n");

        representation.append("Тип содержимого: ").append(contentType().isPresent() ?
                contentType().get() : "не указан").append("\n");

        if (formDataFilename().isPresent())
            representation.append("Имя файла: ").append(formDataFilename().get()).append("\n");

        representation.append("Тело:\n")
                .append(isText() ?
                        getBodyString() :
                        "бинарные данные (%d байт)".formatted(bodySize()));

        return representation.toString();
    }

    /**
     * Возвращает карту из всех заголовков части.
     * @return  значение поля headers.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Возвращает массив байтов, соответствующий телу.
     * @return значение поля body.
     */
    public byte[] getBody() {
        return body;
    }

    /**
     * Возвращает текстовое представление тела.
     * @return новую строку, созданную из байтов тела.
     */
    public String getBodyString() {
        return new String(body);
    }

    /**
     * Возвращает опционально значение запрашиваемого заголовка.
     * @param name заголовок, значение которого узнать.
     * @return  опциональ со значением, соответствующим заголовку, если такой найден.
     */
    public Optional<String> getHeader(String name) {
        return Optional.ofNullable(headers.get(name));
    }

    /**
     * Сохраняет байты тела в файл по указанному адресу.
     * @param filePath путь, по которому будет записан файл.
     * @throws IOException при ошибках вывода.
     */
    public void saveBodyToFile(Path filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(body, 0, body.length);
        }

    }

    /**
     * Сообщает, к какому имени формы привязана эта часть.
     * @return опциональ со значением свойства 'name', либо пустую, если такое свойство отсутствует.
     */
    public Optional<String> formDataName() {
        return extractDispositionProperty("name");
    }

    /**
     * Сообщает, какое имя файла было передано в заголовке.
     * @return  опциональ со значением свойства 'filename', либо пустую, если такое свойство отсутствует.
     */
    public Optional<String> formDataFilename() {
        return extractDispositionProperty("filename");
    }

    /**
     * Сообщает опционально тип содержимого, если он определён для части.
     * @return опциональ со значением заголовка 'Content-Type', если таковой присутствует.
     */
    public Optional<String> contentType() {
        return headers.get("Content-Type") == null ?
                Optional.empty() :
                Optional.of(headers.get("Content-Type"));
    }

    /**
     * Сообщает, является ли тело части непустым.
     * @return true, если размер тела больше нуля.
     */
    public boolean hasBody() {
        return body.length > 0;
    }

    /**
     * Сообщает размер тела части.
     * @return размер массива body.
     */
    public int bodySize() {
        return body.length;
    }

    /**
     * Сообщает, является ли содержимое части номинально текстовым.
     * @return true, если 'Content-Type' не указан или имеет значение 'text/plain'; false в ином случае.
     */
    public boolean isText() {
        return contentType().isEmpty() || contentType().get().startsWith("text/plain");
    }

    /**
     * Извлекает из значения заголовка части 'Content-Disposition' свойство с определённым именем.
     * @param propertyName извлекаемое свойство.
     * @return  опциональ со значением указанного свойства либо пустую, если свойство не обнаружено в этом заголовке.
     */
    private Optional<String> extractDispositionProperty(String propertyName) {
        String disposition = headers.get("Content-Disposition");
        if (disposition == null ||
                !disposition.startsWith("form-data;") ||
                !contentDispositionProperties.containsKey(propertyName))
            return Optional.empty();
        return Optional.of(contentDispositionProperties.get(propertyName));
    }


}
