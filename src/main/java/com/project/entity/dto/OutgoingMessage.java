package com.project.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OutgoingMessage {
    private final int code;
    private final String body;
}