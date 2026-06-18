package com.example.panstwamiasta.websocket;

import com.example.panstwamiasta.model.Player;
import com.example.panstwamiasta.room.Room;
import com.example.panstwamiasta.service.RoomBroadcastService;
import com.example.panstwamiasta.service.RoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final String ROOM_PATH_PREFIX = "/api/ws/rooms/";

    @Autowired
    private RoomService roomService;

    @Autowired
    private RoomBroadcastService broadcastService;

    @Autowired
    private RoomSessionRegistry sessionRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asText("");

        if ("ping".equals(type)) {
            broadcastService.sendToSession(session, "pong", null);
            return;
        }

        if ("subscribe".equals(type)) {
            handleSubscribe(session, node);
            return;
        }

        broadcastService.sendToSession(session, "error", "Unknown message type");
    }

    private void handleSubscribe(WebSocketSession session, JsonNode node) {
        if (!session.getAttributes().containsKey("subscribed")) {
            String roomCode = extractRoomCode(session);
            if (roomCode == null) {
                broadcastService.sendToSession(session, "error", "Invalid room code");
                return;
            }

            String playerIdStr = node.path("playerId").asText(null);
            if (playerIdStr == null || playerIdStr.isBlank()) {
                broadcastService.sendToSession(session, "error", "playerId required");
                return;
            }

            UUID playerId;
            try {
                playerId = UUID.fromString(playerIdStr);
            } catch (IllegalArgumentException e) {
                broadcastService.sendToSession(session, "error", "Invalid playerId");
                return;
            }

            Room room = roomService.getRoom(roomCode);
            if (room == null) {
                broadcastService.sendToSession(session, "error", "Room not found");
                return;
            }

            boolean inRoom = room.getPlayers().stream()
                    .map(Player::getId)
                    .anyMatch(id -> id.equals(playerId));
            if (!inRoom) {
                broadcastService.sendToSession(session, "error", "Player not in room");
                return;
            }

            session.getAttributes().put("subscribed", true);
            session.getAttributes().put("roomCode", roomCode);
            sessionRegistry.register(roomCode, session);
            broadcastService.sendRoomState(session, room);
        }
    }

    private String extractRoomCode(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String path = session.getUri().getPath();
        if (!path.startsWith(ROOM_PATH_PREFIX)) {
            return null;
        }
        String code = path.substring(ROOM_PATH_PREFIX.length());
        return code.isBlank() ? null : code;
    }
}
