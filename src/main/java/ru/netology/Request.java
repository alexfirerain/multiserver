package ru.netology;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final InputStream in;

    private final String defaultPath = "/index.html";   // начальный путь

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

    public String getPath() {
        return "".equals(path) || "/".equals(path) ?        // чтобы пустой путь вёл на начальную
                defaultPath : path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public InputStream getIn() {
        return in;
    }
}
