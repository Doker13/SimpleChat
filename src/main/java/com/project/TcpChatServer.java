package com.project;

import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TcpChatServer {
    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 6;

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log.info("Server started on port {}", PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            log.error("Server error: ", e);
        } finally {
            threadPool.shutdown();
        }
    }
}

@Slf4j
class ClientHandler implements Runnable {
    private final Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        String clientInfo = String.format("%s:%d", clientSocket.getInetAddress(), clientSocket.getPort());
        log.info("New client connected: {}", clientInfo);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {

            String message;
            while ((message = in.readLine()) != null) {
                log.info("[{}] Received: {}", clientInfo, message);
                out.write("Got it!\n");
                out.flush();
            }
        } catch (IOException e) {
            log.error("[{}] Error in client handler: ", clientInfo, e);
        } finally {
            log.info("[{}] Client disconnected", clientInfo);
            try {
                clientSocket.close();
            } catch (IOException e) {
                log.error("[{}] Error closing client socket: ", clientInfo, e);
            }
        }
    }
}