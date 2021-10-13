package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public final List<String> validPaths;
    public final String PUBLIC_DIR;
    private final ExecutorService connections;
    private final Map<String, Map<String, Handler>> handlers = new ConcurrentHashMap<>();

    public Server(int poolSize, List<String> validPaths, String public_dir) {
        this.validPaths = validPaths;
        PUBLIC_DIR = public_dir;
        connections = Executors.newFixedThreadPool(poolSize);
    }

    public void listen(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                    final var socket = serverSocket.accept();
                    connections.submit(() -> handleConnection(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleConnection(Socket socket) {
//        System.out.println("HANDLING " + socket.getRemoteSocketAddress());
            try (socket;
                 final var in = socket.getInputStream();
                 final var out = socket.getOutputStream())
            {
                var request = Request.fromInputStream(in);
                final var path = request.getPath();



                if (!validPaths.contains(path)) {
                    out.write(("""
                            HTTP/1.1 404 Not Found\r
                            Content-Length: 0\r
                            Connection: close\r
                            \r
                            """).getBytes());
                    out.flush();
                    return;
                }

                final var filePath = Path.of(".", "public", path);
                final var mimeType = Files.probeContentType(filePath);

                // special case for classic
                if (path.equals("/classic.html")) {
                    final var template = Files.readString(filePath);
                    final var content = template.replace(
                            "{time}",
                            LocalDateTime.now().toString()
                    ).getBytes();
                    out.write((
                            ("""
                                    HTTP/1.1 200 OK\r
                                    Content-Type: %s\r
                                    Content-Length: %d\r
                                    Connection: close\r
                                    \r
                                    """).formatted(mimeType, content.length)
                    ).getBytes());
                    out.write(content);
                    out.flush();
                    return;
                }

                final var length = Files.size(filePath);
                out.write((
                        ("""
                                HTTP/1.1 200 OK\r
                                Content-Type: %s\r
                                Content-Length: %d\r
                                Connection: close\r
                                \r
                                """).formatted(mimeType, length)
                ).getBytes());
                Files.copy(filePath, out);
                out.flush();
            } catch (IOException e) {
                System.out.println("HANDLE_ERROR");
                e.printStackTrace();
            }
    }

    // повторные назначения переписывают прежние
    public void addHandler(String method, String path, Handler handler) {
            handlers.putIfAbsent(method, new ConcurrentHashMap<>());
            handlers.get(method).put(path, handler);
    }
}



