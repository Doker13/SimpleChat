package com.project.controller;

import com.project.WebSocketSession;
import com.project.annotation.WebSocketRoute;
import com.project.entity.dto.MessageDTO;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Slf4j
public class ChatController {
    private WebSocketSession session;

    @WebSocketRoute("/chat")
    public void handleChat(MessageDTO message) {
        log.info("Received chat message from {}: {}", message.getSender(), message.getMessage());

        session.send(message);
    }

    @WebSocketRoute("/file")
    public void handleFile(byte[] data) {
        log.info("Received file data of length {}", data.length);
        session.send(data);
    }
}