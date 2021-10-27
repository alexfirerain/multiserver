package ru.netology;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MultiPartDatum {
    private final Map<String, String> headers;
    private final byte[] body;

    public MultiPartDatum(Map<String, String> headers, byte[] body) {
        this.headers = headers;
        this.body = body;
    }

    public MultiPartDatum(byte[] partHeaders, byte[] partBody) {
        System.out.println("Вошли в конструктор части");        // мониторинг

        var headerLines = (new String(partHeaders)).split("\r\n");

        Arrays.stream(headerLines).forEach(System.out::println); // мониторинг

        Map<String, String> headerSlices = new HashMap<>();

        for (String line : headerLines) {
            int delimiter = line.indexOf(":");
            if (delimiter == -1) continue;
            var headerName = line.substring(0, delimiter);
            var headerValue = line.substring(delimiter + 2);
            headerSlices.put(headerName, headerValue);
        }
        headers = headerSlices;
        body = partBody;

        System.out.println("MPD: " + new String(body));

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
    public void saveBodyToFile(String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(body, 0, body.length);
        }

    }


}
