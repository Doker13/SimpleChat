package com.project.service;

import com.project.model.dto.MessageRequest;
import com.project.repository.ChatMembersRepository;
import com.project.repository.FileRepository;
import com.project.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;

@Slf4j
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatMembersRepository chatMembersRepository;
    private final FileRepository fileRepository;

    public MessageService(
            MessageRepository messageRepository,
            ChatMembersRepository chatMembersRepository,
            FileRepository fileRepository) {
        this.messageRepository = messageRepository;
        this.chatMembersRepository = chatMembersRepository;
        this.fileRepository = fileRepository;
    }

    public List<UUID> saveMessage(MessageRequest request) throws Exception {
        if (request.getContent() == null && request.getFileId() == null) {
            throw new IllegalArgumentException("Either content or fileId must be provided");
        }

        UUID messageId = messageRepository.saveMessage(request);

        if (request.getFileId() != null) {
            boolean updated = fileRepository.updateFileEntityId(request.getFileId(), messageId);
            if (!updated) {
                throw new Exception("Failed to update file with message ID");
            }
        }

        return chatMembersRepository.getChatMemberIds(request.getChatId(), request.getUserId());
    }
}
