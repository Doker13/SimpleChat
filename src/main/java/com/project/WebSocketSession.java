package com.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class WebSocketSession {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<String> outgoingMessages = new LinkedBlockingQueue<>();
    private final BlockingQueue<byte[]> outgoingBinaryMessages = new LinkedBlockingQueue<>();

    @SneakyThrows
    public void addOutgoingMessage(Object message) {
        String json;
        if (message instanceof String) {
            json = (String) message;
        } else {
            json = objectMapper.writeValueAsString(message);
        }
        outgoingMessages.put(json);
    }

    public void addOutgoingMessage(String message) {
        outgoingMessages.add(message);
    }

    public void addOutgoingBinaryMessage(byte[] data) {
        outgoingBinaryMessages.add(data);
    }

    public String pollOutgoingMessage() throws InterruptedException {
        return outgoingMessages.take();
    }

    public byte[] pollOutgoingBinaryMessage() throws InterruptedException {
        return outgoingBinaryMessages.take();
    }
}