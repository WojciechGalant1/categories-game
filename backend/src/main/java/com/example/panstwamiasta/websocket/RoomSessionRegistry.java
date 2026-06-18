package com.example.panstwamiasta.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomSessionRegistry {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionRoomCodes = new ConcurrentHashMap<>();

    public void register(String roomCode, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomCode, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionRoomCodes.put(session.getId(), roomCode);
    }

    public void unregister(WebSocketSession session) {
        String roomCode = sessionRoomCodes.remove(session.getId());
        if (roomCode == null) {
            return;
        }
        Set<WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomCode, sessions);
            }
        }
    }

    public Set<WebSocketSession> getSessions(String roomCode) {
        Set<WebSocketSession> sessions = roomSessions.get(roomCode);
        return sessions == null ? Set.of() : Set.copyOf(sessions);
    }

    public Set<String> getActiveRoomCodes() {
        return Collections.unmodifiableSet(roomSessions.keySet());
    }
}
