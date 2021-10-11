package ru.netology;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static final String ERROR_MSG =
            """
                    HTTP/1.1 404 Not Found\r
                    Content-Length: 0\r
                    Connection: close\r
                    \r
                    """;

    public final int SERVER_PORT;
    public final List<String> validPaths;
    public final String PUBLIC_DIR;

    private final ExecutorService connections;

    public Server(int server_port, int threads, List<String> validPaths, String public_dir) {
        SERVER_PORT = server_port;
        this.validPaths = validPaths;
        PUBLIC_DIR = public_dir;
        connections = Executors.newFixedThreadPool(threads);
    }

    public void operate() {
        System.out.println("OPERATING");
        try (final var serverSocket = new ServerSocket(SERVER_PORT)) {
            while (true) {
                    final var socket = serverSocket.accept();
                    Connection nextConnect = new Connection(socket, this);
                    connections.execute(nextConnect);
            }
        } catch (IOException e) {
            System.out.println("OPERATE_ERROR");
            e.printStackTrace();
        }
    }


}
