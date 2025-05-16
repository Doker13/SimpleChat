package com.project.repository;

import com.project.DatabaseManager;
import com.project.jooq.tables.*;
import com.project.model.dto.ChatResponse;
import com.project.model.dto.FileDTO;
import com.project.model.dto.MessageDTO;
import org.jooq.DSLContext;

import java.util.List;
import java.util.UUID;

public class ChatRepository {

    public ChatResponse getMessagesByChatId(UUID chatId) {
        DSLContext dsl = DatabaseManager.dsl();
        Messages m = Messages.MESSAGES;
        Users u = Users.USERS;
        Files f = Files.FILES;

        List<MessageDTO> messages = dsl.select(
                        u.ID,
                        m.CONTENT,
                        m.SENT_AT,
                        u.USERNAME,
                        u.DISPLAY_NAME,
                        f.ID,
                        f.FILE_NAME,
                        f.EXTENSION,
                        f.FILE_SIZE
                )
                .from(m)
                .join(u).on(m.SENDER_ID.eq(u.ID))
                .leftJoin(f).on(f.ENTITY_ID.eq(m.ID))
                .where(m.CHAT_ID.eq(chatId))
                .orderBy(m.SENT_AT.asc())
                .fetch(record -> {
                    MessageDTO dto = new MessageDTO();
                    dto.setContent(record.get(m.CONTENT));
                    dto.setTime(record.get(m.SENT_AT));
                    dto.setUserName(record.get(u.USERNAME));
                    dto.setDisplayName(record.get(u.DISPLAY_NAME));
                    dto.setUserId(record.get(u.ID));

                    if (record.get(f.FILE_NAME) != null) {
                        FileDTO fileDTO = new FileDTO();
                        fileDTO.setFileId(record.get(f.ID));
                        fileDTO.setFileName(record.get(f.FILE_NAME));
                        fileDTO.setExtension(record.get(f.EXTENSION));
                        fileDTO.setSize(record.get(f.FILE_SIZE));
                        dto.setFile(fileDTO);
                    }

                    return dto;
                });

        ChatResponse response = new ChatResponse();
        response.setId(chatId);
        response.setMessages(messages);

        return response;
    }

    public UUID createChat(String chatName, UUID admId, List<UUID> userIds) {
        DSLContext dsl = DatabaseManager.dsl();
        Chats c = Chats.CHATS;
        ChatMembers cm = ChatMembers.CHAT_MEMBERS;
        boolean isGroup = userIds.size() > 2;

        UUID chatId = dsl.insertInto(c)
                .set(c.NAME, chatName)
                .set(c.IS_GROUP, isGroup)
                .set(c.CREATED_BY, admId)
                .returning(c.ID)
                .fetchOne()
                .getId();

        userIds.forEach(userId -> {
            dsl.insertInto(cm)
                    .set(cm.CHAT_ID, chatId)
                    .set(cm.USER_ID, userId)
                    .execute();
        });

        return chatId;
    }
}
