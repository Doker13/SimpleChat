package com.project.repository;

import com.project.DatabaseManager;
import com.project.jooq.tables.Messages;
import com.project.model.dto.MessageRequest;
import org.jooq.DSLContext;

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
}
