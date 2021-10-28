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
public class Server extends Thread {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final List<String> allowedMethods = List.of(GET, POST);
    private static final String HOSTNAME = "localhost";

    private final ExecutorService connections;
    /**
     * Библиотека обработчиков по методу и ресурсу.
     */
    private final Map<String, Map<String, Handler>> handlers = new ConcurrentHashMap<>();
    private String public_dir;

    private int server_port = 9999;         // на всякий значение по умолчанию

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
     * Создаёт новый Сервер с указанной степенью параллельностью и серверным портом,
     * а также с указанной папкой ресурсов.
     * @param poolSize     максимальное количество одновременно обрабатываемых потоков.
     * @param public_dir   расположение папки с ресурсами.
     * @param server_port  номер порта, на котором будет слушать.
     */
    public Server(int poolSize, String public_dir, int server_port) {
        this.public_dir = public_dir;
        connections = Executors.newFixedThreadPool(poolSize);
        this.server_port = server_port;
    }

    /**
     * Начинает слушать входящие подключения на указанном порту.
     */
    @Override
    public void run() {
        try (final var serverSocket = new ServerSocket(server_port)) {
            while (!interrupted()) {
                final var socket = serverSocket.accept();
                connections.submit(() -> handleConnection(socket));
            }
        } catch (IOException e) {
            System.out.println("Прослушивание порта завершилось: " + e.getMessage());
            e.printStackTrace();
        }
            connections.shutdownNow();
    }

    /**
     * Обрабатывает входящее подключение: считывает поток, формирует из него запрос
     * и затем обрабатывает этот запрос согласно установленным правилам.
     *
     * @param socket обрабатываемое подключение.
     */
    private void handleConnection(Socket socket) {
        System.out.println("HANDLING " + socket.getRemoteSocketAddress());  // мониторинг
        try (socket;
             final var in = socket.getInputStream();
             final var out = socket.getOutputStream()) {


            final var request = Request.fromInputStream(in);
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
                    badRequestResponse(socket.getOutputStream());
                } else {
                    serverErrorResponse(socket.getOutputStream());
                }
            } catch (IOException ex) {
                System.out.println("ERROR_RESPONSE_ERROR");
                ex.printStackTrace();
            }
        } catch (NumberFormatException e) {
            System.out.println("HANDLE_ERROR");
            e.printStackTrace();
            try {
                badRequestResponse(socket.getOutputStream());
            } catch (IOException ex) {
                System.out.println("ERROR_RESPONSE_ERROR");
                ex.printStackTrace();
            }
        }
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
     * Сообщает, является ли запрашиваемая пара метод-путь случаем специфицированной обработки.
     * @param method метод запроса.
     * @param path   запрашиваемый путь
     * @return  {@code true}, если на этот метод и этот путь зарегистрирован обработчик.
     */
    private boolean isSpecified(String method, String path) {
        return handlers.get(method) != null &&
                handlers.get(method).get(path) != null;
    }

    private boolean isAllowed(String method) {
        return allowedMethods.contains(method);
    }

    /**
     * Прерывает выполнение серверного потока.
     */
    public void stopServer() {
        interrupt();
        // виртуальное подключение к серверу, чтобы разблокировать его ожидание на порту
        try {
            new Socket(HOSTNAME, server_port).close();
        } catch (IOException e) {
            System.out.println("VIRTUAL_CONNECTION_ERROR");
            e.printStackTrace();
        }
    }

    public String getPublic_dir() {
        return public_dir;
    }

    public void setServer_port(int server_port) {
        this.server_port = server_port;
    }

    /**
     * Стандартный обработчик отсутствующего ресурса.
     * @param out   кому слать.
     * @throws IOException при невозможности отослать.
     */
    protected void notFoundResponse(OutputStream out) throws IOException {
        out.write(("""
                HTTP/1.1 404 Not Found\r
                Content-Length: 0\r
                Connection: close\r
                \r
                """).getBytes());
        out.flush();
    }

    /**
     * Стандартный обработчик некорректного запроса.
     * @param out   куда отсылать ответ.
     * @throws IOException при невозможности нормально отослать.
     */
    protected void badRequestResponse(OutputStream out) throws IOException {
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

    /**
     * Стандартный обработчик неимплементированного метода.
     * @param out   куда слать.
     * @throws IOException при невозможности отослать.
     */
    protected void notImplementedResponse(OutputStream out) throws IOException {
        out.write(("""
                HTTP/1.1 501 Not Implemented\r
                Content-Length: 0\r
                Connection: close\r
                \r
                """).getBytes());
        out.flush();
    }

    /**
     * Стандартный обработчик ошибки сервера.
     * @param out   куда слать.
     * @throws IOException при невозможности отослать.
     */
    protected void serverErrorResponse(OutputStream out) throws IOException {
        out.write(("""
                HTTP/1.1 500 Internal Server Error\r
                Content-Length: 0\r
                Connection: close\r
                \r
                """).getBytes());
        out.flush();
    }
}



