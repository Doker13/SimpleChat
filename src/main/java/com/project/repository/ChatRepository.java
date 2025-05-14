package com.project.repository;

import com.project.DatabaseManager;
import com.project.entity.dto.ChatResponse;
import com.project.entity.dto.FileDTO;
import com.project.entity.dto.MessageDTO;
import com.project.jooq.tables.Files;
import com.project.jooq.tables.Messages;
import com.project.jooq.tables.Users;
import org.jooq.DSLContext;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

public class ChatRepository {

    public ChatResponse getMessagesByChatId(UUID chatId) {
        DSLContext dsl = DatabaseManager.dsl();
        Messages m = Messages.MESSAGES;
        Users u = Users.USERS;
        Files f = Files.FILES;

        List<MessageDTO> messages = dsl.select(
                        m.CONTENT,
                        m.SENT_AT,
                        u.USERNAME,
                        u.DISPLAY_NAME,
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

                    if (record.get(f.FILE_NAME) != null) {
                        FileDTO fileDTO = new FileDTO();
                        fileDTO.setFileName(record.get(f.FILE_NAME));
                        fileDTO.setExtension(record.get(f.EXTENSION));
                        fileDTO.setSize(BigInteger.valueOf(record.get(f.FILE_SIZE)));
                        dto.setFile(fileDTO);
                    }

                    return dto;
                });

        ChatResponse response = new ChatResponse();
        response.setId(chatId);
        response.setMessages(messages);

        return response;
    }
}
