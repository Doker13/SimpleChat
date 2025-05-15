package com.project.repository;

import com.project.DatabaseManager;
import com.project.jooq.tables.ChatMembers;
import org.jooq.DSLContext;

import java.util.List;
import java.util.UUID;

public class ChatMembersRepository {
    public List<UUID> getChatMemberIds(UUID chatId, UUID senderId) {
        DSLContext dsl = DatabaseManager.dsl();
        ChatMembers cm = ChatMembers.CHAT_MEMBERS;

        return dsl.select(cm.USER_ID)
                .from(cm)
                .where(cm.CHAT_ID.eq(chatId).and(cm.USER_ID.ne(senderId)))
                .fetchInto(UUID.class);
    }

}
