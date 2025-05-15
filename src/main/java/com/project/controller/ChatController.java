package com.project.controller;

import com.project.WebSocketSessionManager;
import com.project.annotation.Binary;
import com.project.annotation.Command;
import com.project.annotation.WebSocketRoute;
import com.project.model.dto.*;
import com.project.service.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
@WebSocketRoute(route = "/test")
public class ChatController {
    private WebSocketSessionManager session;
    private static AuthService authService;
    private static ChatService chatService;
    private static MessageService messageService;
    private static FileService fileService;
    private static UserService userService;

    public ChatController(
            AuthService authService,
            ChatService chatService,
            MessageService messageService,
            FileService fileService,
            UserService userService) {
        ChatController.authService = authService;
        ChatController.chatService = chatService;
        ChatController.messageService = messageService;
        ChatController.fileService = fileService;
        ChatController.userService = userService;
    }

    public void setSession(WebSocketSessionManager session) {
        this.session = session;
    }

    @Command("sign_up")
    public void handleSignUp(SignUpRequest request) {
        try {
            AuthResponse response = authService.register(request);
            session.currentSession().send("sign_up", response);
            session.registerSession(response.getId());
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
            session.registerSession(response.getId());
        } catch (Exception e) {
            ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
            session.currentSession().send("login", errorMessage);
        }
    }

    @Command("chat")
    public void handleChat(UUID chatId) {
        try {
            ChatResponse response = chatService.getChatMessages(chatId);
            session.currentSession().send("chat", response);
        } catch (Exception e) {
            ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
            session.currentSession().send("chat", errorMessage);
        }
    }

    @Command("message")
    public void handleMessage(MessageRequest message) {
        try {
            NewMessageDTO messageData = messageService.saveMessage(message);
            session.otherSessions(messageData.getUserIds()).send("message", messageData.getResponse());
        } catch (Exception e) {
            ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
            session.currentSession().send("message", errorMessage);
        }
    }

    @Command("file")
    @Binary
    public void handleFile(FileDTO fileMeta, byte[] data) {
        try {
            UUID fileId = fileService.saveFile(fileMeta, data);
            session.currentSession().send("file", fileId);
        } catch (Exception e) {
            ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
            session.currentSession().send("file", errorMessage);
        }
    }

    @Command("get_file")
    public void handleGetFile(UUID fileId) {
        try {
            byte[] fileData = fileService.getFileData(fileId);
            session.currentSession().sendFile("get_file", fileData, fileId);
        } catch (Exception e) {
            ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
            session.currentSession().send("get_file", errorMessage);
        }
    }

    @Command("search")
    public void handleSearch(String displayName) {
        try {
            List<UserSearchResponse> response = userService.searchUsers(displayName);
            session.currentSession().send("search", response);
        } catch (Exception e) {
            ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
            session.currentSession().send("search", errorMessage);
        }
    }

    @Command("create_chat")
    public void handleCreateChat(CreateChatRequest request) {
        try {
            ChatsDTO response = chatService.createChat(request);
            session.currentSession().send("create_chat", response);
            session.otherSessions(request.getUserIds()).send("create_chat", request);
        } catch (Exception e) {
            ErrorMessage errorMessage = new ErrorMessage(e.getMessage());
            session.currentSession().send("create_chat", errorMessage);
        }
    }
}