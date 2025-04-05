package com.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class WebSocketSession {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<MessageWrapper> outgoingMessages = new LinkedBlockingQueue<>();

    public record MessageWrapper(byte[] data, boolean isBinary) {}

    @SneakyThrows
    public void send(Object message) {
        if (message instanceof byte[] bytes) {
            outgoingMessages.put(new MessageWrapper(bytes, true));
        } else {
            String json = message instanceof String str ? str : objectMapper.writeValueAsString(message);
            outgoingMessages.put(new MessageWrapper(json.getBytes(), false));
        }
    }

    public MessageWrapper poll() throws InterruptedException {
        return outgoingMessages.take();
    }
}