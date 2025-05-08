package com.project.controller;

import com.project.WebSocketSessionManager;
import com.project.annotation.Binary;
import com.project.annotation.Command;
import com.project.annotation.WebSocketRoute;
import com.project.entity.dto.AuthRequest;
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

    @Command("login")
    public void handleChat(AuthRequest request) {
        //AuthResponse response = authService.authenticate(request);
        //session.send(response);
        System.out.println(request);
    }

    @Command("file")
    @Binary
    public void handleFile(String fileName, byte[] data) {
        String savedFilePath = saveFileToProjectRoot(fileName, data);
        System.out.println("Файл успешно сохранен: " + savedFilePath);
        byte[] fileData = readFileFromProjectRoot(fileName);
        session.currentSession().sendFile("file", fileData, "1" + fileName);
    }
}