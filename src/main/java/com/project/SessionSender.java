package com.project;

public interface SessionSender {
    void send(String command, Object message);
    void sendFile(String command, byte[] fileData, Object metadata);
}