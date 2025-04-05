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
    private static final String HTTP_UPGRADE_RESPONSE = """
        HTTP/1.1 101 Switching Protocols\r
        Upgrade: websocket\r
        Connection: Upgrade\r
        Sec-WebSocket-Accept: %s\r
        \r
        """;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT);
             ExecutorService threadPool = Executors.newVirtualThreadPerTaskExecutor()) {

            WebSocketRouteRegistry.registerController(new ChatController());
            log.info("Server started on port {}", PORT);

            while (!threadPool.isShutdown()) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new WebSocketHandler(clientSocket, threadPool));
            }
        } catch (IOException e) {
            log.error("Server error: ", e);
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
            String clientInfo = getClientInfo();
            log.info("[{}] New connection.", clientInfo);

            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream out = clientSocket.getOutputStream()) {

                if (performHandshake(in, out)) {
                    log.info("[{}] WebSocket handshake successful! Route: {}", clientInfo, route);
                    handleClientConnection(out, clientInfo);
                } else {
                    log.error("[{}] WebSocket handshake failed!", clientInfo);
                }
            } catch (IOException e) {
                log.error("[{}] WebSocket error: ", clientInfo, e);
            } finally {
                closeClientConnection(clientInfo);
            }
        }

        private void handleClientConnection(OutputStream out, String clientInfo) throws IOException {
            threadPool.execute(() -> {
                try {
                    handleClientData(clientInfo);
                } catch (IOException e) {
                    log.error("[{}] Input handler error: ", clientInfo, e);
                }
            });

            try {
                handleOutgoingMessages(out);
            } catch (IOException | InterruptedException e) {
                log.error("[{}] Output handler error: ", clientInfo, e);
            }
        }

        private void handleClientData(String clientInfo) throws IOException {
            InputStream input = clientSocket.getInputStream();
            WebSocketRouteRegistry.RouteHandler handler = WebSocketRouteRegistry.getHandler(route);

            if (handler == null) {
                log.warn("[{}] Unknown route: {}", clientInfo, route);
                session.send("{\"error\":\"Unknown route.\"}");
                return;
            }

            while (true) {
                int firstByte = input.read();
                if (firstByte == -1) break;

                int opcode = firstByte & 0x0F;

                if ((handler.isBinary() && opcode != 0x02) || (!handler.isBinary() && opcode != 0x01)) {
                    String errorMsg = handler.isBinary()
                            ? "Expected binary data but received text message"
                            : "Expected text message but received binary data";
                    session.send("{\"error\":\"" + errorMsg + "\"}");
                    log.error("[{}] {}", clientInfo, errorMsg);
                    clientSocket.close();
                    break;
                }

                byte[] message = readWebSocketFrame(firstByte, input);
                if (message == null) break;

                try {
                    Object controller = WebSocketRouteRegistry.getControllerInstance(handler.getMethod());
                    injectSession(controller, clientInfo);

                    if (handler.isBinary()) {
                        handler.getMethod().invoke(controller, (Object) message);
                    } else {
                        handleTextMessage(handler, controller, message);
                    }
                } catch (Exception e) {
                    log.error("[{}] Error processing message", clientInfo, e);
                    session.send("{\"error\":\"Message processing error\"}");
                }
            }
        }

        private void handleTextMessage(WebSocketRouteRegistry.RouteHandler handler, Object controller, byte[] message) throws Exception {
            String text = new String(message);
            Class<?> paramType = handler.getMethod().getParameterTypes()[0];

            if (paramType == String.class) {
                handler.getMethod().invoke(controller, text);
            } else {
                Object paramValue = objectMapper.readValue(text, paramType);
                handler.getMethod().invoke(controller, paramValue);
            }
        }

        private void injectSession(Object controller, String clientInfo) {
            try {
                Method setSession = controller.getClass().getMethod("setSession", WebSocketSession.class);
                setSession.invoke(controller, session);
            } catch (NoSuchMethodException e) {
                log.debug("[{}] Controller doesn't require session", clientInfo);
            } catch (Exception e) {
                log.error("[{}] Error injecting session", clientInfo, e);
            }
        }

        private void handleOutgoingMessages(OutputStream out) throws IOException, InterruptedException {
            while (!clientSocket.isClosed()) {
                var message = session.poll();
                sendWebSocketFrame(out, message.data(), message.isBinary());
            }
        }

        private boolean performHandshake(BufferedReader in, OutputStream out) throws IOException {
            String line;
            String webSocketKey = null;
            String path = null;

            while (!(line = in.readLine()).isEmpty()) {
                if (line.startsWith("GET ")) {
                    path = line.split(" ")[1];
                } else if (line.startsWith("Sec-WebSocket-Key: ")) {
                    webSocketKey = line.substring(19);
                }
            }

            if (webSocketKey == null || path == null) {
                return false;
            }

            this.route = path;
            String acceptKey = generateWebSocketAcceptKey(webSocketKey);
            out.write(String.format(HTTP_UPGRADE_RESPONSE, acceptKey).getBytes());
            out.flush();

            return true;
        }

        private String generateWebSocketAcceptKey(String webSocketKey) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                md.update((webSocketKey + WS_MAGIC_STRING).getBytes());
                return Base64.getEncoder().encodeToString(md.digest());
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate WebSocket accept key", e);
            }
        }

        private byte[] readWebSocketFrame(int firstByte, InputStream input) throws IOException {
            int opcode = firstByte & 0x0F;

            if (opcode == 0x08) {
                return null;
            }

            int secondByte = input.read();
            int payloadLength = secondByte & 127;

            if (payloadLength == 126) {
                payloadLength = (input.read() << 8) | input.read();
            } else if (payloadLength == 127) {
                throw new IOException("Payload too large");
            }

            byte[] mask = new byte[4];
            input.readNBytes(mask, 0, 4);

            byte[] encoded = input.readNBytes(payloadLength);
            byte[] decoded = new byte[payloadLength];
            for (int i = 0; i < payloadLength; i++) {
                decoded[i] = (byte) (encoded[i] ^ mask[i % 4]);
            }

            return decoded;
        }

        private void sendWebSocketFrame(OutputStream out, byte[] data, boolean isBinary) throws IOException {
            int dataLength = data.length;

            out.write(isBinary ? 0x82 : 0x81);

            if (dataLength <= 125) {
                out.write(dataLength);
            } else if (dataLength <= 65535) {
                out.write(126);
                out.write((dataLength >> 8) & 0xFF);
                out.write(dataLength & 0xFF);
            } else {
                throw new IOException("Payload too large");
            }

            out.write(data);
            out.flush();
        }

        private String getClientInfo() {
            return clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        }

        private void closeClientConnection(String clientInfo) {
            try {
                clientSocket.close();
                log.info("[{}] Connection closed.", clientInfo);
            } catch (IOException e) {
                log.error("[{}] Error closing client socket: ", clientInfo, e);
            }
        }
    }
}