package com.example.panstwamiasta.service;

import com.example.panstwamiasta.room.Room;
import com.example.panstwamiasta.websocket.RoomSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Service
public class RoomBroadcastService {

    public static final String REDIS_CHANNEL = "room:updates";

    @Autowired
    private RoomSessionRegistry sessionRegistry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Lazy
    private RoomService roomService;

    public void publishRoomUpdate(String roomCode) {
        redisTemplate.convertAndSend(REDIS_CHANNEL, roomCode);
    }

    public void broadcastRoom(String roomCode) {
        Room room = roomService.getRoom(roomCode);
        if (room == null) {
            return;
        }

        String payload;
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("type", "room_state");
            envelope.set("data", objectMapper.valueToTree(room));
            payload = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            return;
        }

        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessionRegistry.getSessions(roomCode)) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException ignored) {
                    // Session will be cleaned up on close
                }
            }
        }
    }

    public void sendToSession(WebSocketSession session, String type, String errorMessage) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", type);
            if (errorMessage != null) {
                node.put("message", errorMessage);
            }
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(node)));
        } catch (IOException ignored) {
        }
    }

    public void sendRoomState(WebSocketSession session, Room room) {
        try {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("type", "room_state");
            envelope.set("data", objectMapper.valueToTree(room));
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
        } catch (IOException ignored) {
        }
    }
}
