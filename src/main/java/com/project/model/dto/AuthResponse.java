package com.project.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private UUID id;
    private String password;
    private String userName;
    private String displayName;
    private String email;
    private List<ChatsDTO> chats;
}