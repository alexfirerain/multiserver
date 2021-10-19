package ru.netology;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Слушает подключения и обрабатывает HTTP-запросы.
 */
public class Server {
    private String public_dir;
    private final ExecutorService connections;
    /**
     * Библиотека обработчиков по методу и ресурсу.
     */
    private final Map<String, Map<String, Handler>> handlers = new ConcurrentHashMap<>();
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final List<String> allowedMethods = List.of(GET, POST);

    /**
     * Создаёт новый Сервер с указанной степенью параллельности и значением публичной директории.
     *
     * @param poolSize   максимальное количество одновременно обрабатываемых потоков.
     * @param public_dir расположение папки с ресурсами.
     */
    public Server(int poolSize, String public_dir) {
        this.public_dir = public_dir;
        connections = Executors.newFixedThreadPool(poolSize);
    }

    /**
     * Начинает слушать входящие подключения на указанном порту.
     * В случае ошибки потока или соединения сообщает об этом в консоль.
     *
     * @param port прослушиваемый порт.
     */
    public void listen(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                final var socket = serverSocket.accept();
                connections.submit(() -> handleConnection(socket));
            }
        } catch (IOException e) {
            System.out.println("Прослушивание порта завершилось: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Обрабатывает входящее подключение: считывает поток, формирует из него запрос
     * и затем обрабатывает этот запрос согласно установленным правилам.
     *
     * @param socket обрабатываемое подключение.
     */
    private void handleConnection(Socket socket) {
        System.out.println("HANDLING " + socket.getRemoteSocketAddress());
        try (socket;
             final var in = socket.getInputStream();
             final var out = socket.getOutputStream()) {


            final var request = Request.fromInputStream(in);
            System.out.println(request);
            final var method = request.getMethod();
            final var path = request.getPath();

            // запрос GET по неспецифицированному пути (поведение по умолчанию)
            if ("GET".equals(method) &&
                    !isSpecified(method, path)) {

                final var filePath = Path.of(".", public_dir, path);

                if (Files.isRegularFile(filePath)) {
                    generalHandler.handle(request, out);
                } else {
                    notFoundResponse(out);
                }
            }

            // неизвестный метод
            if (handlers.get(method) == null &&
                    !isAllowed(method)) {
                notImplementedResponse(out);
                return;
            }

            // обработка по методу и пути из библиотеки
            handlers.get(method).get(path).handle(request, out);

        } catch (IOException e) {
            try {
                System.out.println("HANDLE_ERROR");
                e.printStackTrace();
                if ("Invalid request".equals(e.getMessage())) {
                    badRequest(socket.getOutputStream());
                } else {
                    serverErrorResponse(socket.getOutputStream());
                }
            } catch (IOException ex) {
                System.out.println("ERROR_RESPONSE_ERROR");
                ex.printStackTrace();
            }
        }
    }

    /**
     * Стандартный обработчик ошибки сервера.
     * @param out   куда слать.
     * @throws IOException  при невозможности отослать.
     */
    private void serverErrorResponse(OutputStream out) throws IOException {
        out.write(("""
                HTTP/1.1 500 Internal Server Error\r
                Content-Length: 0\r
                Connection: close\r
                \r
                """).getBytes());
        out.flush();
    }

    /**
     * Стандартный обработчик неимплементированного метода.
     * @param out   куда слать.
     * @throws IOException  при невозможности отослать.
     */
    private void notImplementedResponse(OutputStream out) throws IOException {
        out.write(("""
                HTTP/1.1 501 Not Implemented\r
                Content-Length: 0\r
                Connection: close\r
                \r
                """).getBytes());
        out.flush();
    }

    /**
     * Стандартный обработчик отсутствующего ресурса.
     * @param out   кому слать.
     * @throws IOException при невозможности отослать.
     */
    private void notFoundResponse(OutputStream out) throws IOException {
        out.write(("""
                HTTP/1.1 404 Not Found\r
                Content-Length: 0\r
                Connection: close\r
                \r
                """).getBytes());
        out.flush();
    }

    /**
     * Стандартный обработчик запроса GET на ресурсы,
     * обработка которых в Библиотеке не специфицирована.
     */
    public final Handler generalHandler = (request, responseStream) -> {
        final var filePath = Path.of(".", public_dir, request.getPath());
        responseStream.write((
                ("""
                        HTTP/1.1 200 OK\r
                        Content-Type: %s\r
                        Content-Length: %d\r
                        Connection: close\r
                        \r
                        """).formatted(Files.probeContentType(filePath),
                        Files.size(filePath))
        ).getBytes());
        Files.copy(filePath, responseStream);
        responseStream.flush();
    };

    /**
     * Добавляет в библиотеку новый обработчик.
     * Повторные назначения на тот же метод и ресурс переписывают прежние.
     */
    public void addHandler(String method, String path, Handler handler) {
        handlers.putIfAbsent(method, new ConcurrentHashMap<>());
        handlers.get(method).put(path, handler);
    }


    /**
     * Стандартный обработчик некорректного запроса.
     * @param out   куда отсылать ответ.
     * @throws IOException при невозможности нормально отослать.
     */
    private static void badRequest(OutputStream out) throws IOException {
        out.write((
                """
                        HTTP/1.1 400 Bad Request\r
                        Content-Length: 0\r
                        Connection: close\r
                        \r
                        """
        ).getBytes());
        out.flush();
    }

    private boolean isSpecified(String method, String path) {
        return handlers.get(method).get(path) != null;
    }

    private boolean isAllowed(String method) {
        return allowedMethods.contains(method);
    }

    public String getPublic_dir() {
        return public_dir;
    }

}



