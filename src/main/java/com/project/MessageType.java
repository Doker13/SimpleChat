package com.project;

import lombok.Getter;

@Getter
public enum MessageType {
    TEXT("text"),
    META_DATA("metaData"),
    PRE_CHUNK("preChunk");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public static MessageType fromString(String value) {
        for (MessageType type : MessageType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + value);
    }

}