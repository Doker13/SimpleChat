package com.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.controller.ChatController;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
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
                WebSocketRouteRegistry.registerController(new ChatController());
                log.info("Server started on port {}", PORT);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(new WebSocketHandler(clientSocket, threadPool));
                }
            } catch (IOException e) {
                log.error("Server error: ", e);
            } finally {
                threadPool.shutdown();
            }
        } catch (NullPointerException e) {
            log.error("Can't create thread pool: ", e);
        }
    }

    @Slf4j
    static class WebSocketHandler implements Runnable {
        private final Socket clientSocket;
        private final ExecutorService threadPool;
        private String route;
        private final WebSocketSession session = new WebSocketSession();

        public WebSocketHandler(Socket socket, ExecutorService threadPool) {
            this.clientSocket = socket;
            this.threadPool = threadPool;
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

                boolean isJson = WebSocketRouteRegistry.getJsonHandler(route) != null;
                boolean isFile = WebSocketRouteRegistry.getFileHandler(route) != null;

                if (!isJson && !isFile) {
                    log.warn("[{}] Unknown route: {}", clientInfo, route);
                    sendWebSocketMessage(out, "{\"error\":\"Unknown route.\"}");
                    return;
                }

                threadPool.execute(() -> {
                    try {
                        if (isJson) handleJson(clientInfo);
                        else handleFile(clientInfo);
                    } catch (IOException e) {
                        log.error("[{}] Input handler error: ", clientInfo, e);
                    }
                });

                try {
                    handleOutgoingMessages(out);
                } catch (IOException | InterruptedException e) {
                    log.error("[{}] Output handler error: ", clientInfo, e);
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

        private void handleJson(String clientInfo) throws IOException {
            InputStream input = clientSocket.getInputStream();
            while (true) {
                byte[] message = readWebSocketMessage(input);
                if (message == null) break;

                String jsonStr = new String(message);
                try {
                    var handler = WebSocketRouteRegistry.getJsonHandler(route);
                    if (handler != null) {
                        Object controller = WebSocketRouteRegistry.getControllerInstance(handler);

                        try {
                            Method setSession = controller.getClass().getMethod("setSession", WebSocketSession.class);
                            setSession.invoke(controller, session);
                        } catch (NoSuchMethodException e) {
                            log.error("[{}] Controller do not require sessions: ", clientInfo, e);
                        }

                        Class<?> paramType = handler.getParameterTypes()[0];

                        Object paramValue;
                        if (paramType == String.class) {
                            paramValue = jsonStr;
                        } else {
                            paramValue = objectMapper.readValue(jsonStr, paramType);
                        }

                        handler.invoke(controller, paramValue);
                    }
                } catch (Exception e) {
                    log.error("[{}] JSON route error", clientInfo, e);
                    session.addOutgoingMessage("{\"error\":\"Invalid JSON or internal error.\"}");
                }
            }
        }

        private void handleFile(String clientInfo) throws IOException {
            InputStream input = clientSocket.getInputStream();
            while (true) {
                byte[] message = readWebSocketMessage(input);
                if (message == null) break;

                try {
                    var handler = WebSocketRouteRegistry.getFileHandler(route);
                    if (handler != null) {
                        Object controller = WebSocketRouteRegistry.getControllerInstance(handler);
                        try {
                            Method setSession = controller.getClass().getMethod("setSession", WebSocketSession.class);
                            setSession.invoke(controller, session);
                        } catch (NoSuchMethodException e) {
                            log.error("[{}] Controller do not require sessions: ", clientInfo, e);
                        }

                        handler.invoke(controller, (Object) message);
                    }
                } catch (Exception e) {
                    log.error("[{}] File route error", clientInfo, e);
                    session.addOutgoingMessage("{\"error\":\"File route error.\"}");
                }
            }
        }

        private void handleOutgoingMessages(OutputStream out) throws IOException, InterruptedException {
            while (!clientSocket.isClosed()) {
                String textMessage = session.pollOutgoingMessage();
                if (textMessage != null) {
                    sendWebSocketMessage(out, textMessage);
                }

                byte[] binaryMessage = session.pollOutgoingBinaryMessage();
                if (binaryMessage != null) {
                    sendWebSocketBinaryMessage(out, binaryMessage);
                }
            }
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