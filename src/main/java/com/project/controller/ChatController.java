package com.project.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.WebSocketSession;
import com.project.annotation.FileRoute;
import com.project.annotation.JSONRoute;
import com.project.entity.dto.MessageDTO;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Slf4j
public class ChatController {
    private WebSocketSession session;

    @JSONRoute("/chat")
    public void handleChat(MessageDTO message) throws JsonProcessingException {
        log.info("Received chat message from {}: {}", message.getSender(), message.getMessage());

        session.addOutgoingMessage(message);
    }

    @FileRoute("/file")
    public void handleFile(byte[] data) {
        log.info("Received file data of length {}", data.length);
        session.addOutgoingBinaryMessage(data);
    }
}