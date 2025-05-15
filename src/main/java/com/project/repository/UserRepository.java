package com.project.repository;

import com.project.DatabaseManager;
import com.project.jooq.tables.ChatMembers;
import com.project.jooq.tables.Chats;
import com.project.jooq.tables.Users;
import com.project.model.dto.AuthResponse;
import com.project.model.dto.ChatsDTO;
import com.project.model.dto.SignUpRequest;
import com.project.model.dto.UserSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;

import java.util.List;

@Slf4j
public class UserRepository {
    public AuthResponse getAuthResponseByUsername(String username) {
        DSLContext dsl = DatabaseManager.dsl();
        Users u = Users.USERS;
        Chats c = Chats.CHATS;
        ChatMembers cm = ChatMembers.CHAT_MEMBERS;

        AuthResponse response = dsl.select(u.ID, u.PASSWORD, u.DISPLAY_NAME, u.EMAIL)
                .from(u)
                .where(u.USERNAME.eq(username))
                .fetchOneInto(AuthResponse.class);

        if (response == null) return null;

        List<ChatsDTO> chats = dsl.select(c.ID, c.NAME)
                .from(c)
                .join(cm).on(c.ID.eq(cm.CHAT_ID))
                .where(cm.USER_ID.eq(response.getId()))
                .fetchInto(ChatsDTO.class);

        response.setChats(chats);
        return response;
    }

    public boolean existsByEmail(String email) {
        DSLContext dsl = DatabaseManager.dsl();
        Users u = Users.USERS;

        return dsl.fetchExists(
                dsl.selectFrom(u).where(u.EMAIL.eq(email))
        );
    }

    public boolean existsByUsername(String username) {
        DSLContext dsl = DatabaseManager.dsl();
        Users u = Users.USERS;

        return dsl.fetchExists(
                dsl.selectFrom(u).where(u.USERNAME.eq(username))
        );
    }

    public AuthResponse createUser(SignUpRequest request) {
        DSLContext dsl = DatabaseManager.dsl();
        Users u = Users.USERS;

        dsl.insertInto(u)
                .set(u.USERNAME, request.getUsername())
                .set(u.PASSWORD, request.getPassword())
                .set(u.DISPLAY_NAME, request.getDisplayName())
                .set(u.EMAIL, request.getEmail())
                .set(u.BIRTHDAY, request.getBirthday())
                .execute();

        return dsl.select(u.ID, u.PASSWORD, u.DISPLAY_NAME, u.EMAIL)
                .from(u)
                .where(u.USERNAME.eq(request.getUsername()))
                .fetchOneInto(AuthResponse.class);

    }

    public List<UserSearchResponse> getDisplayNames(String displayName) {
        DSLContext dsl = DatabaseManager.dsl();
        Users u = Users.USERS;

        return dsl.select(u.ID, u.DISPLAY_NAME)
                .from(u)
                .where(u.DISPLAY_NAME.likeIgnoreCase("%" + displayName + "%"))
                .limit(30)
                .fetchInto(UserSearchResponse.class);
    }
}