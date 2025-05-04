package com.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketSession {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int CHUNK_SIZE = 64 * 1024;

    private final BlockingQueue<MessageWrapper> highPriorityQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<MessageWrapper> lowPriorityQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger fileIdGenerator = new AtomicInteger(0);

    public record MessageWrapper(byte[] data, boolean isBinary, boolean isFileChunk) {}

    public void send(String command, Object message) {
        try {
            ObjectNode json = objectMapper.createObjectNode();
            json.put("command", command);
            json.put("type", "text");
            json.set("message", objectMapper.valueToTree(message));

            byte[] data = objectMapper.writeValueAsBytes(json);
            highPriorityQueue.put(new MessageWrapper(data, false, false));
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message", e);
        }
    }

    public void sendFile(String command, byte[] fileData, Object metadata) {
        try {
            String fileId = "file-" + fileIdGenerator.incrementAndGet();
            int totalChunks = (int) Math.ceil((double) fileData.length / CHUNK_SIZE);

            ObjectNode metaData = objectMapper.createObjectNode();
            metaData.put("command", command);
            metaData.put("type", "metaData");
            metaData.put("id", fileId);
            metaData.put("fileSize", fileData.length);
            metaData.put("totalChunks", totalChunks);
            metaData.set("message", objectMapper.valueToTree(metadata));

            lowPriorityQueue.put(new MessageWrapper(
                    objectMapper.writeValueAsBytes(metaData), false, false));

            for (int i = 0; i < totalChunks; i++) {
                int offset = i * CHUNK_SIZE;
                int chunkSize = Math.min(CHUNK_SIZE, fileData.length - offset);
                byte[] chunk = Arrays.copyOfRange(fileData, offset, offset + chunkSize);

                ObjectNode preChunk = objectMapper.createObjectNode();
                preChunk.put("command", command);
                preChunk.put("type", "preChunk");
                preChunk.put("id", fileId);
                preChunk.put("chunkSize", chunkSize);
                preChunk.put("chunkNum", i);

                lowPriorityQueue.put(new MessageWrapper(
                        objectMapper.writeValueAsBytes(preChunk), false, true));
                lowPriorityQueue.put(new MessageWrapper(chunk, true, true));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send file", e);
        }
    }

    public MessageWrapper poll(int timeout, TimeUnit unit) throws InterruptedException {
        MessageWrapper message = highPriorityQueue.poll();
        if (message != null) {
            return message;
        }

        message = lowPriorityQueue.poll(timeout, unit);

        if (message != null && message.isFileChunk()) {
            MessageWrapper nextChunk = lowPriorityQueue.poll();
            if (nextChunk != null) {
                return new MessageWrapper(
                        nextChunk.data(),
                        nextChunk.isBinary(),
                        false
                );
            }
        }

        return message;
    }
}