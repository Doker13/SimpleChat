package com.project.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    UUID userId;
    String content;
    String userName;
    String displayName;
    LocalDateTime time;
    FileDTO file;
}