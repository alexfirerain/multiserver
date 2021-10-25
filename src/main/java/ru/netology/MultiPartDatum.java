package ru.netology;

import java.util.Map;

public class MultiPartDatum {
    private final Map<String, String> headers;
    private final byte[] body;

    public MultiPartDatum(Map<String, String> headers, byte[] body) {
        this.headers = headers;
        this.body = body;
    }
}
