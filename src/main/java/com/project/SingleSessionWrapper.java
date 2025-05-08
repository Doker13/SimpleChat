package com.project;

public class SingleSessionWrapper implements SessionSender {
    private final WebSocketSession session;

    public SingleSessionWrapper(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void send(String command, Object message) {
        if (session != null) {
            session.send(command, message);
        }
    }

    @Override
    public void sendFile(String command, byte[] fileData, Object metadata) {
        if (session != null) {
            session.sendFile(command, fileData, metadata);
        }
    }
}
