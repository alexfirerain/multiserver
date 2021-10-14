package ru.netology;

import java.io.IOException;
import java.io.OutputStream;

@FunctionalInterface
public interface Handler {
    /**
     * Производит отработку запроса в поток отдачи.
     *
     * @param request        запрос к серверу.
     * @param responseStream исходящий поток, в который направляется ответ.
     * @throws IOException  при недоступности ресурса или ошибке соединения.
     */
    void handle(Request request, OutputStream responseStream) throws IOException;
}
