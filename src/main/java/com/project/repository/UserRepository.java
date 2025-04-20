package com.project.repository;

import com.project.DatabaseManager;
import com.project.entity.dto.AuthResponse;
import com.project.entity.dto.ChatDTO;
import com.project.jooq.tables.Users;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;


import com.project.jooq.tables.Chats;
import com.project.jooq.tables.ChatMembers;

import java.util.List;

@Slf4j
public class UserRepository {
    public AuthResponse getAuthResponseByUsername(String username) {
        DSLContext dsl = DatabaseManager.dsl();
        Users u = Users.USERS;
        Chats c = Chats.CHATS;
        ChatMembers cm = ChatMembers.CHAT_MEMBERS;

        try {
            AuthResponse response = dsl.select(u.ID, u.PASSWORD, u.DISPLAY_NAME, u.EMAIL)
                    .from(u)
                    .where(u.USERNAME.eq(username))
                    .fetchOneInto(AuthResponse.class);

            if (response == null) return null;

            List<ChatDTO> chats = dsl.select(c.ID, c.NAME)
                    .from(c)
                    .join(cm).on(c.ID.eq(cm.CHAT_ID))
                    .where(cm.USER_ID.eq(response.getId()))
                    .fetchInto(ChatDTO.class);

            response.setChats(chats);
            return response;
        } catch (Exception e) {
            log.error("Error fetching user data", e);
            return null;
        }
    }
}