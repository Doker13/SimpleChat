package com.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;

import java.net.Socket;
import java.net.ServerSocket;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.project.entity.dto.IncomingMessage;
import com.project.entity.dto.OutgoingMessage;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        String clientInfo = String.format("%s:%d", clientSocket.getInetAddress(), clientSocket.getPort());
        log.info("New client connected: {}", clientInfo);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {

            String jsonMessage;
            while ((jsonMessage = in.readLine()) != null) {
                log.info("[{}] Received JSON: {}", clientInfo, jsonMessage);

                try {
                    IncomingMessage incomingMessage = objectMapper.readValue(jsonMessage, IncomingMessage.class);
                    log.info("[{}] Type: {}, Body: {}", clientInfo, incomingMessage.getType(), incomingMessage.getBody());

                    OutgoingMessage outgoingMessage = new OutgoingMessage(200, "Success");
                    String responseJson = objectMapper.writeValueAsString(outgoingMessage);
                    out.write(responseJson + "\n");
                    out.flush();

                } catch (JsonProcessingException e) {
                    log.error("[{}] Invalid JSON format: {}", clientInfo, e.getMessage());
                    OutgoingMessage errorResponse = new OutgoingMessage(400, "Invalid JSON format");
                    String errorJson = objectMapper.writeValueAsString(errorResponse);
                    out.write(errorJson + "\n");
                    out.flush();
                }
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