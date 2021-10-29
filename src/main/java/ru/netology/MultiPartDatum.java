package ru.netology;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MultiPartDatum {
    private final Map<String, String> headers;
    private final Map<String, String> contentDispositionProperties;
    private final byte[] body;

    public MultiPartDatum(byte[] headersArea, byte[] bodyArea) {
        var headerLines = (new String(headersArea)).split("\r\n");

//        Arrays.stream(headerLines).forEach(System.out::println); // мониторинг

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

        System.out.println(this);       // мониторинг
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

        representation.append("Тело:\n").append(Arrays.toString(body));

        return representation.toString();
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }
    public String getBodyString() {
        return new String(body);
    }

    public Optional<String> getHeader(String name) {
        return headers.get(name) == null ?
                Optional.empty() :
                Optional.of(headers.get(name));
    }
    public void saveBodyToFile(Path filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(body, 0, body.length);
        }

    }

    public Optional<String> formDataName() {
        return extractDispositionProperty("name");
    }

    public Optional<String> formDataFilename() {
        return extractDispositionProperty("filename");
    }

    public Optional<String> contentType() {
        return headers.get("Content-Type") == null ?
                Optional.empty() :
                Optional.of(headers.get("Content-Type"));
    }

    public boolean hasBody() {
        return body.length > 0;
    }
    public int bodySize() {
        return body.length;
    }

    public boolean isText() {
        return contentType().isEmpty() || contentType().get().startsWith("text/plain");
    }


    private Optional<String> extractDispositionProperty(String propertyName) {
        String disposition = headers.get("Content-Disposition");
        if (disposition == null ||
                !disposition.startsWith("form-data;") ||
                !contentDispositionProperties.containsKey(propertyName))
            return Optional.empty();
        return Optional.of(contentDispositionProperties.get(propertyName));
    }


}
