package com.project;

import java.util.Collection;

public class MultiSessionWrapper implements SessionSender {
    private final Collection<WebSocketSession> sessions;

    public MultiSessionWrapper(Collection<WebSocketSession> sessions) {
        this.sessions = sessions;
    }

    @Override
    public void send(String command, Object message) {
        sessions.forEach(session -> session.send(command, message));
    }

    @Override
    public void sendFile(String command, byte[] fileData, Object metadata) {
        sessions.forEach(session -> session.sendFile(command, fileData, metadata));
    }
}
