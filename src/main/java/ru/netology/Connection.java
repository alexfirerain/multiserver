package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class Connection implements Runnable {

    private final Socket socket;
    private final Server server;

    public Connection(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        System.out.println("RUNNING @ " + socket.getRemoteSocketAddress());
            try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 final var out = new BufferedOutputStream(socket.getOutputStream()))
            {
                final var requestLine = in.readLine();
                final var parts = requestLine.split(" ");

                if (parts.length != 3) {
                    // just close socket
                    return;
                }

                final var path = parts[1];
                if (!isValidPath(path)) {
                    deny(out);
                    return;
                }

                // special case for classic
                if (path.equals("/classic.html")) {
                    classicResponse(path, out);
                    return;
                }

                respond(path, out);

        } catch (IOException e) {
                e.printStackTrace();
            }
    }

    private void deny(BufferedOutputStream out) throws IOException {
        out.write(Server.ERROR_MSG.getBytes());
        out.flush();
    }

    private void respond(String path, BufferedOutputStream out) throws IOException {
        final var filePath = Path.of(".", server.PUBLIC_DIR, path);
        out.write((
                ("""
                                    HTTP/1.1 200 OK\r
                                    Content-Type: %s\r
                                    Content-Length: %d\r
                                    Connection: close\r
                                    \r
                                    """).formatted(Files.probeContentType(filePath), Files.size(filePath))
        ).getBytes());
        Files.copy(filePath, out);
        out.flush();
    }

    private void classicResponse(String path, BufferedOutputStream out) throws IOException {
        final var filePath = Path.of(".", server.PUBLIC_DIR, path);
        final var content = setDateInClassic(path);
        out.write("""
                HTTP/1.1 200 OK\r
                Content-Type: %s\r
                Content-Length: %d\r
                Connection: close\r
                \r
                    """.formatted(Files.probeContentType(filePath), content.length)
                .getBytes());
        out.write(content);
        out.flush();
    }

    private byte[] setDateInClassic(String path) throws IOException {
        return Files.readString(Path.of(".", server.PUBLIC_DIR, path))
                .replace("{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
    }

    public boolean isValidPath(String path) {
        return server.validPaths.contains(path);
    }

}
