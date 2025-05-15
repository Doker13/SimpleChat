package com.project.repository;

import com.project.DatabaseManager;
import com.project.jooq.tables.Files;
import com.project.jooq.tables.Messages;
import com.project.jooq.tables.Users;
import com.project.model.dto.FileDTO;
import com.project.model.dto.MessageDTO;
import com.project.model.dto.MessageRequest;
import org.jooq.DSLContext;

import java.math.BigInteger;
import java.util.UUID;

public class MessageRepository {

    public UUID saveMessage(MessageRequest request) {
        DSLContext dsl = DatabaseManager.dsl();
        Messages m = Messages.MESSAGES;

        return dsl.insertInto(m)
                .set(m.CHAT_ID, request.getChatId())
                .set(m.SENDER_ID, request.getUserId())
                .set(m.CONTENT, request.getContent())
                .set(m.HAVE_FILE, request.getFileId() != null)
                .returning(m.ID)
                .fetchOne()
                .getId();
    }

    public MessageDTO fetchMessageDTOById(UUID messageId) {
        DSLContext dsl = DatabaseManager.dsl();
        Messages m = Messages.MESSAGES;
        Users u = Users.USERS;
        Files f = Files.FILES;

        return dsl.select(
                        m.SENDER_ID,
                        m.CONTENT,
                        m.SENT_AT,
                        u.USERNAME,
                        u.DISPLAY_NAME,
                        f.ID.as("file_id"),
                        f.FILE_NAME,
                        f.EXTENSION,
                        f.FILE_SIZE
                )
                .from(m)
                .join(u).on(m.SENDER_ID.eq(u.ID))
                .leftJoin(f).on(f.ENTITY_ID.eq(m.ID))
                .where(m.ID.eq(messageId))
                .fetchOne(record -> {
                    FileDTO fileDTO = null;
                    if (record.get("file_id") != null) {
                        fileDTO = new FileDTO();
                        fileDTO.setFileId(record.get("file_id", UUID.class));
                        fileDTO.setFileName(record.get("file_name", String.class));
                        fileDTO.setExtension(record.get("extension", String.class));
                        fileDTO.setSize(record.get("file_size", BigInteger.class));
                    }

                    MessageDTO dto = new MessageDTO();
                    dto.setUserId(record.get(m.SENDER_ID));
                    dto.setContent(record.get(m.CONTENT));
                    dto.setTime(record.get(m.SENT_AT));
                    dto.setUserName(record.get(u.USERNAME));
                    dto.setDisplayName(record.get(u.DISPLAY_NAME));
                    dto.setFile(fileDTO);
                    return dto;
                });
    }
}

