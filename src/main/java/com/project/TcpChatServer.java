package com.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

            WebSocketAutoRegistrar.scanAndRegister("com.project.controller");
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
        private final String clientInfo;
        private final WebSocketSession session = new WebSocketSession();

        private static class FileUploadState {
            String command;
            WebSocketRouteRegistry.RouteHandler handler;
            Object metadata;
            long totalSize;
            int totalChunks;
            List<byte[]> receivedChunks = new ArrayList<>();
            boolean expectingBinary = false;
            String currentFileId;

            void addChunk(byte[] chunk) {
                receivedChunks.add(chunk);
            }

            boolean isComplete() {
                return receivedChunks.size() == totalChunks;
            }
        }

        private final Map<String, FileUploadState> fileUploads = new ConcurrentHashMap<>();

        public WebSocketHandler(Socket socket, ExecutorService threadPool) {
            this.clientSocket = socket;
            this.threadPool = threadPool;
            this.clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        }

        @Override
        public void run() {
            log.info("[{}] New connection.", clientInfo);

            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                 OutputStream out = clientSocket.getOutputStream()) {

                if (performHandshake(in, out)) {
                    log.info("[{}] WebSocket handshake successful! Route: {}", clientInfo, route);
                    handleClientConnection(out);
                } else {
                    log.error("[{}] WebSocket handshake failed!", clientInfo);
                }
            } catch (IOException e) {
                log.error("[{}] WebSocket error: ", clientInfo, e);
            } finally {
                closeClientConnection();
            }
        }

        private void handleClientConnection(OutputStream out) throws IOException {
            var inputHandler = threadPool.submit(() -> {
                try {
                    handleClientData();
                } catch (IOException e) {
                    log.error("[{}] Input handler error: ", clientInfo, e);
                }
            });

            try {
                handleOutgoingMessages(out);
            } catch (IOException | InterruptedException e) {
                log.error("[{}] Output handler error: ", clientInfo, e);
            } finally {
                inputHandler.cancel(true);
            }
        }

        private void handleClientData() throws IOException {
            InputStream input = clientSocket.getInputStream();

            while (true) {
                int firstByte = input.read();
                if (firstByte == -1) break;

                int opcode = firstByte & 0x0F;

                if (opcode == 0x08) {
                    closeClientConnection();
                    break;
                }

                byte[] message = readWebSocketFrame(firstByte, input);
                if (message == null) break;

                try {
                    if (opcode == 0x02) {
                        handleBinaryFrame(message);
                    } else if (opcode == 0x01) {
                        handleTextFrame(message);
                    } else {
                        log.warn("[{}] Unsupported frame opcode: {}", clientInfo, opcode);
                        session.send("error","{\"error\":\"Unsupported frame type\"}");
                    }
                } catch (Exception e) {
                    log.error("[{}] Error processing message", clientInfo, e);
                    session.send("error","{\"error\":\"Server error\"}");
                }
            }
        }

        private void handleTextFrame(byte[] messageData) throws Exception {
            String message = new String(messageData);
            JsonNode jsonNode = objectMapper.readTree(message);

            validateRequiredFields(jsonNode);

            String command = jsonNode.get("command").asText();
            MessageType type = MessageType.fromString(jsonNode.get("type").asText());

            WebSocketRouteRegistry.RouteHandler handler = WebSocketRouteRegistry.getHandler(route, command);

            if (handler == null) {
                throw new IllegalArgumentException("No handler found for command: " + command);
            }

            switch (type) {
                case META_DATA:
                    validateMetaDataFields(jsonNode);
                    handleMetaDataFrame(jsonNode, handler);
                    break;

                case PRE_CHUNK:
                    validatePreChunkFields(jsonNode);
                    handlePreChunkFrame(jsonNode, handler);
                    break;

                case TEXT:
                    validateRegularMessageFields(jsonNode);
                    processRegularMessage(jsonNode, handler);
                    break;
            }
        }

        private void validateRequiredFields(JsonNode jsonNode) {
            if (!jsonNode.has("command")) {
                throw new IllegalArgumentException("Message must contain 'command' field");
            } else if (!jsonNode.has("type")) {
                throw new IllegalArgumentException("Message must contain 'type' field");
            }

            try {
                MessageType.fromString(jsonNode.get("type").asText());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid message type");
            }
        }

        private void validateMetaDataFields(JsonNode jsonNode) {
            if (!jsonNode.has("id")) {
                throw new IllegalArgumentException("Message must contain file id");
            } else if (!jsonNode.has("fileSize")) {
                throw new IllegalArgumentException("Message must contain size of file");
            } else if (!jsonNode.has("totalChunks")) {
                throw new IllegalArgumentException("Message must contain number of chunks");
            }
        }

        private void validatePreChunkFields(JsonNode jsonNode) {
            if (!jsonNode.has("id")) {
                throw new IllegalArgumentException("Message must contain file id");
            } else if (!jsonNode.has("chunkSize")) {
                throw new IllegalArgumentException("Message must contain size of chunk");
            } else if (!jsonNode.has("chunkNum")) {
                throw new IllegalArgumentException("Message must contain chunk number");
            }
        }

        private void validateRegularMessageFields(JsonNode jsonNode) {
            if (!jsonNode.has("message")) {
                throw new IllegalArgumentException("Message must contain 'message' field");
            }
        }

        private void handleMetaDataFrame(JsonNode jsonNode, WebSocketRouteRegistry.RouteHandler handler) {
            if (!handler.isBinary()) {
                throw new IllegalArgumentException("Handler for command " + jsonNode.get("command").asText() + " is not binary");
            }

            String fileId = jsonNode.get("id").asText();
            if (fileUploads.containsKey(fileId)) {
                throw new IllegalStateException("File upload with id " + fileId + " already in progress");
            }

            FileUploadState uploadState = new FileUploadState();
            uploadState.command = jsonNode.get("command").asText();
            uploadState.handler = handler;
            uploadState.totalSize = jsonNode.get("fileSize").asLong();
            uploadState.totalChunks = jsonNode.get("totalChunks").asInt();

            try {
                if (jsonNode.has("message")) {
                    JsonNode messageNode = jsonNode.get("message");
                    uploadState.metadata = objectMapper.treeToValue(
                            messageNode,
                            handler.getMethod().getParameterTypes()[0]
                    );
                }
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid metadata format in 'message' field", e);
            }

            fileUploads.put(fileId, uploadState);
        }

        private void handlePreChunkFrame(JsonNode jsonNode, WebSocketRouteRegistry.RouteHandler handler) {
            if (!handler.isBinary()) {
                throw new IllegalArgumentException("Handler for command " + jsonNode.get("command").asText() + " is not binary");
            }

            String fileId = jsonNode.get("id").asText();
            FileUploadState uploadState = fileUploads.get(fileId);

            if (uploadState == null) {
                throw new IllegalStateException("No file upload with id " + fileId + " in progress");
            }

            int chunkNum = jsonNode.get("chunkNum").asInt();
            long chunkSize = jsonNode.get("chunkSize").asLong();

            if (chunkNum != uploadState.receivedChunks.size()) {
                throw new IllegalStateException("Invalid chunk sequence for file " + fileId +
                        ". Expected: " + uploadState.receivedChunks.size() +
                        ", got: " + chunkNum);
            }

            uploadState.expectingBinary = true;
            uploadState.currentFileId = fileId;
        }

        private void handleBinaryFrame(byte[] binaryData) throws Exception {

            FileUploadState uploadState = fileUploads.values().stream()
                    .filter(state -> state.expectingBinary && state.currentFileId != null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unexpected binary data received"));

            String fileId = uploadState.currentFileId;

            uploadState.addChunk(binaryData);
            uploadState.expectingBinary = false;
            uploadState.currentFileId = null;

            if (uploadState.isComplete()) {
                completeFileUpload(fileId, uploadState);
                fileUploads.remove(fileId);
            }
        }

        private void completeFileUpload(String fileId, FileUploadState uploadState)
                throws Exception {
            byte[] fileData = new byte[(int) uploadState.totalSize];
            int offset = 0;
            for (byte[] chunk : uploadState.receivedChunks) {
                System.arraycopy(chunk, 0, fileData, offset, chunk.length);
                offset += chunk.length;
            }

            Object controller = WebSocketRouteRegistry.getControllerInstance(uploadState.handler.getMethod());
            injectSession(controller);

            uploadState.handler.getMethod().invoke(controller, uploadState.metadata, fileData);

            log.info("[{}] File {} uploaded successfully", clientInfo, fileId);
        }

        private void processRegularMessage(JsonNode jsonNode, WebSocketRouteRegistry.RouteHandler handler) throws Exception {
            if (handler.isBinary()) {
                throw new IllegalArgumentException("Handler for command " + jsonNode.get("command").asText() + " expects binary data");
            }

            Object controller = WebSocketRouteRegistry.getControllerInstance(handler.getMethod());
            injectSession(controller);

            Class<?> paramType = handler.getMethod().getParameterTypes()[0];
            Object paramValue;

            if (paramType == String.class) {
                paramValue = jsonNode.toString();
            } else {
                JsonNode requestNode = jsonNode.get("message");
                paramValue = objectMapper.treeToValue(requestNode, paramType);
            }

            handler.getMethod().invoke(controller, paramValue);
        }

        private void injectSession(Object controller) {
            try {
                Method setSession = controller.getClass().getMethod("setSession", WebSocketSession.class);
                setSession.invoke(controller, session);
            } catch (NoSuchMethodException e) {
                log.debug("[{}] Controller doesn't require session", clientInfo);
            } catch (Exception e) {
                log.error("[{}] Error injecting session", clientInfo, e);
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
            boolean masked = (secondByte & 0x80) != 0;
            int payloadLength = secondByte & 0x7F;

            if (payloadLength == 126) {
                payloadLength = (input.read() & 0xFF) << 8 | (input.read() & 0xFF);
            } else if (payloadLength == 127) {
                long longLength = ((long) input.read() & 0xFF) << 56
                        | ((long) input.read() & 0xFF) << 48
                        | ((long) input.read() & 0xFF) << 40
                        | ((long) input.read() & 0xFF) << 32
                        | ((long) input.read() & 0xFF) << 24
                        | ((long) input.read() & 0xFF) << 16
                        | ((long) input.read() & 0xFF) << 8
                        | ((long) input.read() & 0xFF);

                if (longLength > Integer.MAX_VALUE) {
                    throw new IOException("Payload too large");
                }
                payloadLength = (int) longLength;
            }

            byte[] mask = new byte[4];
            if (masked) {
                input.readNBytes(mask, 0, 4);
            }

            byte[] payload = new byte[payloadLength];
            int totalRead = 0;
            while (totalRead < payloadLength) {
                int chunkSize = Math.min(8192, payloadLength - totalRead);
                int read = input.read(payload, totalRead, chunkSize);
                if (read == -1) {
                    throw new IOException("Unexpected end of stream");
                }
                totalRead += read;
            }

            if (masked) {
                for (int i = 0; i < payloadLength; i++) {
                    payload[i] ^= mask[i % 4];
                }
            }

            return payload;
        }

        private void handleOutgoingMessages(OutputStream out) throws IOException, InterruptedException {
            try {
                while (!clientSocket.isClosed()) {
                    var message = session.poll(100, TimeUnit.MILLISECONDS);
                    if (message == null) {
                        if (clientSocket.isInputShutdown() || Thread.currentThread().isInterrupted()) {
                            break;
                        }
                        continue;
                    }
                    sendWebSocketFrame(out, message.data(), message.isBinary());
                }
            } catch (IOException e) {
                if (!clientSocket.isClosed()) {
                    throw e;
                }
            }
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

        private void closeClientConnection() {
            try {
                if (!clientSocket.isClosed()) {
                    if (!clientSocket.isOutputShutdown()) {
                        sendCloseFrame(clientSocket.getOutputStream());
                    }
                    clientSocket.close();
                }
                log.info("[{}] Connection closed gracefully.", clientInfo);
            } catch (IOException e) {
                log.error("[{}] Error closing client socket: ", clientInfo, e);
            }
        }

        private void sendCloseFrame(OutputStream out) throws IOException {
            if (clientSocket.isClosed() || clientSocket.isOutputShutdown()) return;

            byte[] payload = { (byte)(1000 >> 8), (byte)(1000 & 0xFF) };

            out.write(0x88); // FIN + opcode 0x8
            out.write(payload.length);
            out.write(payload);
            out.flush();
        }
    }
}