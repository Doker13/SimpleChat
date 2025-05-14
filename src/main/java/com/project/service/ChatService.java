package com.project.service;

import com.project.entity.dto.ChatResponse;
import com.project.repository.ChatRepository;

import java.util.UUID;

public class ChatService {
    private final ChatRepository chatRepository;

    public ChatService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public ChatResponse getChatMessages(UUID chatId) {
        return chatRepository.getMessagesByChatId(chatId);
    }
}
