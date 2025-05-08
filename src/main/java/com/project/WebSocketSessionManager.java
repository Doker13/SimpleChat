package com.project;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class WebSocketSessionManager {
    private static final ConcurrentMap<UUID, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private static final ThreadLocal<WebSocketSession> currentSession = new ThreadLocal<>();

    public void registerSession(UUID userId) {
        Objects.requireNonNull(userId, "User ID cannot be null");

        WebSocketSession session = currentSession.get();
        if (session != null) {
            userSessions.compute(userId, (key, sessions) -> {
                if (sessions == null) {
                    sessions = new CopyOnWriteArraySet<>();
                }
                sessions.add(session);
                return sessions;
            });
        }
    }

    public void unregisterSession(WebSocketSession session) {
        Objects.requireNonNull(session, "Session cannot be null");
        userSessions.values().forEach(sessions -> sessions.remove(session));
    }

    public void setCurrentSession(WebSocketSession session) {
        currentSession.set(session);
    }

    public WebSocketSession currentSession() {
        return currentSession.get();
    }

    public SessionSender current() {
        return new SingleSessionWrapper(currentSession.get());
    }

    public SessionSender otherSession(UUID userId) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null && !sessions.isEmpty()) {
            return new SingleSessionWrapper(sessions.iterator().next());
        }
        return new SingleSessionWrapper(null);
    }

    public SessionSender otherSessions(UUID userId) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Set<WebSocketSession> sessions = userSessions.getOrDefault(userId, Collections.emptySet());
        return new MultiSessionWrapper(sessions);
    }

    public SessionSender otherSessions(Collection<UUID> userIds) {
        Objects.requireNonNull(userIds, "User IDs cannot be null");
        Set<WebSocketSession> allSessions = new HashSet<>();
        for (UUID userId : userIds) {
            allSessions.addAll(userSessions.getOrDefault(userId, Collections.emptySet()));
        }
        return new MultiSessionWrapper(allSessions);
    }
}