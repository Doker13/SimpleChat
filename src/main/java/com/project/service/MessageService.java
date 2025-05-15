package com.project.service;

import com.project.model.dto.MessageDTO;
import com.project.model.dto.MessageRequest;
import com.project.model.dto.MessageResponse;
import com.project.model.dto.NewMessageDTO;
import com.project.repository.ChatMembersRepository;
import com.project.repository.FileRepository;
import com.project.repository.MessageRepository;

import java.util.List;
import java.util.UUID;

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

    public NewMessageDTO saveMessage(MessageRequest request) throws Exception {
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

        MessageDTO messageDTO = messageRepository.fetchMessageDTOById(messageId);
        MessageResponse response = new MessageResponse();
        response.setChatId(request.getChatId());
        response.setMessage(messageDTO);

        List<UUID> userIds = chatMembersRepository.getChatMemberIds(request.getChatId(), request.getUserId());

        NewMessageDTO newMessageDTO = new NewMessageDTO();
        newMessageDTO.setResponse(response);
        newMessageDTO.setUserIds(userIds);

        return newMessageDTO;
    }
}
