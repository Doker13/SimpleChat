package com.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.entity.dto.IncomingMessage;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class TcpChatServer {
    private static final int PORT = 8080;
    protected static final String WS_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        try (ExecutorService threadPool = Executors.newVirtualThreadPerTaskExecutor()) {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                log.info("Server started on port {}", PORT);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(new WebSocketHandler(clientSocket));
                }
            } catch (IOException e) {
                log.error("Server error: ", e);
            } finally {
                threadPool.shutdown();
            }
        } catch (NullPointerException e) {
            log.error("Can't create thread: ", e);
        }
    }

    @Slf4j
    static class WebSocketHandler implements Runnable {
        private final Socket clientSocket;
        private String route;

        public WebSocketHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            String clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
            log.info("[{}] New connection.", clientInfo);

            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream out = clientSocket.getOutputStream()) {

                String webSocketKey = performHandshake(in, out);
                if (webSocketKey == null) {
                    log.error("[{}] WebSocket handshake failed!", clientInfo);
                    return;
                }

                log.info("[{}] WebSocket handshake successful! Route: {}", clientInfo, route);

                while (true) {
                    byte[] message = readWebSocketMessage(clientSocket.getInputStream());

                    if (message == null) {
                        log.info("[{}] Client disconnected from {}", clientInfo, route);
                        break;
                    }

                    if (route.equals("/upload")) {
                        handleFileUpload(clientInfo, out, message);
                    } else if (route.equals("/download")) {
                        handleFileDownload(clientInfo, out);
                    } else if (route.equals("/chat")) {
                        handleChatMessage(clientInfo, out, message);
                    } else {
                        handleDefaultMessage(clientInfo, out, message);
                    }
                }

            } catch (IOException e) {
                log.error("[{}] WebSocket error: ", clientInfo, e);
            } finally {
                try {
                    clientSocket.close();
                    log.info("[{}] Connection closed.", clientInfo);
                } catch (IOException e) {
                    log.error("[{}] Error closing client socket: ", clientInfo, e);
                }
            }
        }

        private void handleFileUpload(String clientInfo, OutputStream out, byte[] message) throws IOException {
            log.info("[{}] Received file upload on {}", clientInfo, route);

            File file = new File("uploaded_file");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(message);
            }

            sendWebSocketMessage(out, "File received successfully.");
        }

        private void handleFileDownload(String clientInfo, OutputStream out) throws IOException {
            log.info("[{}] Sending file to {}", clientInfo, route);

            File file = new File("uploaded_file");
            byte[] fileData = Files.readAllBytes(file.toPath());

            sendWebSocketBinaryMessage(out, fileData);
        }

        private void handleChatMessage(String clientInfo, OutputStream out, byte[] message) throws IOException {
            try {
                String jsonMessage = new String(message);
                IncomingMessage incomingMessage = objectMapper.readValue(jsonMessage, IncomingMessage.class);
                log.info("[{}] Received chat message: {}", clientInfo, incomingMessage);

                sendWebSocketMessage(out, jsonMessage);
            } catch (IOException e) {
                log.error("[{}] Invalid message format for /chat endpoint", clientInfo, e);
                sendWebSocketMessage(out, "{\"error\":\"Invalid message format. Expected IncomingMessage JSON\"}");
            }
        }

        private void handleDefaultMessage(String clientInfo, OutputStream out, byte[] message) throws IOException {
            log.info("[{}] Received message on {}: {}", clientInfo, route, new String(message));
            sendWebSocketMessage(out, new String(message));
        }

        private String performHandshake(BufferedReader in, OutputStream out) throws IOException {
            String line;
            String webSocketKey = null;

            while (!(line = in.readLine()).isEmpty()) {
                if (line.startsWith("GET ")) {
                    route = line.split(" ")[1];
                } else if (line.startsWith("Sec-WebSocket-Key: ")) {
                    webSocketKey = line.substring(19);
                }
            }

            if (webSocketKey == null || route == null) {
                return null;
            }

            String acceptKey = generateWebSocketAcceptKey(webSocketKey);

            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

            out.write(response.getBytes());
            out.flush();

            return webSocketKey;
        }

        private String generateWebSocketAcceptKey(String webSocketKey) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update((webSocketKey + TcpChatServer.WS_MAGIC_STRING).getBytes());
                return Base64.getEncoder().encodeToString(md.digest());
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate WebSocket accept key", e);
            }
        }

        private byte[] readWebSocketMessage(InputStream input) throws IOException {
            int firstByte = input.read();
            if (firstByte == -1) {
                return null;
            }

            int opcode = firstByte & 0x0F;
            if (opcode == 0x08) {
                return null;
            }

            boolean isBinary = opcode == 0x02;

            int payloadLength = input.read() & 127;

            if (payloadLength == 126) {
                payloadLength = (input.read() << 8) | input.read();
            } else if (payloadLength == 127) {
                throw new IOException("Payload too large");
            }

            byte[] mask = new byte[4];
            input.read(mask, 0, 4);

            byte[] encodedMessage = new byte[payloadLength];
            input.read(encodedMessage, 0, payloadLength);

            byte[] decodedMessage = new byte[payloadLength];
            for (int i = 0; i < payloadLength; i++) {
                decodedMessage[i] = (byte) (encodedMessage[i] ^ mask[i % 4]);
            }

            return isBinary ? decodedMessage : new String(decodedMessage).getBytes();
        }

        private void sendWebSocketMessage(OutputStream out, String message) throws IOException {
            byte[] messageBytes = message.getBytes();
            int messageLength = messageBytes.length;

            out.write(0x81);

            if (messageLength <= 125) {
                out.write(messageLength);
            } else if (messageLength <= 65535) {
                out.write(126);
                out.write((messageLength >> 8) & 0xFF);
                out.write(messageLength & 0xFF);
            } else {
                throw new IOException("Message too large");
            }

            out.write(messageBytes);
            out.flush();
        }

        private void sendWebSocketBinaryMessage(OutputStream out, byte[] fileData) throws IOException {
            int fileLength = fileData.length;

            out.write(0x82);

            if (fileLength <= 125) {
                out.write(fileLength);
            } else if (fileLength <= 65535) {
                out.write(126);
                out.write((fileLength >> 8) & 0xFF);
                out.write(fileLength & 0xFF);
            } else {
                throw new IOException("File too large");
            }

            out.write(fileData);
            out.flush();
        }
    }
}