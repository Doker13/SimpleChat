package com.project.controller;

import com.project.WebSocketSessionManager;
import com.project.annotation.Binary;
import com.project.annotation.Command;
import com.project.annotation.WebSocketRoute;
import com.project.entity.dto.AuthRequest;
import com.project.entity.dto.AuthResponse;
import com.project.entity.dto.ErrorMessage;
import com.project.entity.dto.SignUpRequest;
import com.project.service.AuthService;
import lombok.extern.slf4j.Slf4j;

import static com.project.test.FileSaver.saveFileToProjectRoot;
import static com.project.test.FileReader.readFileFromProjectRoot;

@Slf4j
@WebSocketRoute(route = "/test")
public class ChatController {
    private WebSocketSessionManager session;
    private static AuthService authService;

    public ChatController(AuthService authService) {
        ChatController.authService = authService;
    }

    public void setSession(WebSocketSessionManager session) {
        this.session = session;
    }

    @Command("sign_up")
    public void handleSignUp(SignUpRequest request) {
        try {
            AuthResponse response = authService.register(request);
            session.currentSession().send("sign_up", response);
        } catch (Exception e) {
            ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
            session.currentSession().send("sign_up", errorMessage);
        }
    }

    @Command("login")
    public void handleLogin(AuthRequest request) {
        try {
            AuthResponse response = authService.authorize(request);
            session.currentSession().send("login", response);
        } catch (Exception e) {
            ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
            session.currentSession().send("login", errorMessage);
        }
    }

    @Command("file")
    @Binary
    public void handleFile(String fileName, byte[] data) {
        String savedFilePath = saveFileToProjectRoot(fileName, data);
        byte[] fileData = readFileFromProjectRoot(fileName);
        session.currentSession().sendFile("save", fileData, "1" + fileName);
    }
}