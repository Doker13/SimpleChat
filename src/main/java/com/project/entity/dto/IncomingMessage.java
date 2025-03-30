package com.project.entity.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class IncomingMessage {
    private final String type;
    private final String body;

    @JsonCreator
    public IncomingMessage(
            @JsonProperty("type") String type,
            @JsonProperty("body") String body) {
        this.type = type;
        this.body = body;
    }
}