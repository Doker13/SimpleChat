package com.project.controller;

import com.project.WebSocketSession;
import com.project.annotation.WebSocketRoute;
import com.project.entity.dto.AuthRequest;
import com.project.entity.dto.AuthResponse;
import com.project.service.AuthService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Setter
public class ChatController {
    private WebSocketSession session;
    private static AuthService authService;

    public ChatController(AuthService authService) {
        this.authService = authService;
    }

    @WebSocketRoute("/login")
    public void handleChat(AuthRequest request) {
        AuthResponse response = authService.authenticate(request);
        session.send(response);
    }

    @WebSocketRoute("/file")
    public void handleFile(byte[] data) {
        log.info("Received file data of length {}", data.length);
        session.send(data);
    }
}