package ru.netology;

import java.util.Map;

public class Request {
    private final String method;
    private final String path;
    private final Map<String, String> headers;

    private Request(String method, String path, Map<String, String> headers) {
        this.method = method;
        this.path = path;
        this.headers = headers;
    }

    public static Request buildRequest(String requestLine) {
        return null;
    }

}
