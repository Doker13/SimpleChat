package com.project.service;

import com.project.model.dto.ChatResponse;
import com.project.model.dto.ChatsDTO;
import com.project.model.dto.CreateChatRequest;
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

    public ChatsDTO createChat(CreateChatRequest request) {
        ChatsDTO chatsDTO = new ChatsDTO();
        chatsDTO.setId(chatRepository.createChat(request.getChatName(), request.getAdmId(), request.getUserIds()));
        chatsDTO.setName(request.getChatName());
        return chatsDTO;
    }
}
