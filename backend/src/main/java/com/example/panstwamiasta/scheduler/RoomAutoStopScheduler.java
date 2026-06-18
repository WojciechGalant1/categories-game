package com.example.panstwamiasta.scheduler;

import com.example.panstwamiasta.repository.RoomRepository;
import com.example.panstwamiasta.room.Room;
import com.example.panstwamiasta.service.RoomBroadcastService;
import com.example.panstwamiasta.websocket.RoomSessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.transaction.Transactional;

import java.util.Set;

@Component
public class RoomAutoStopScheduler {

    @Autowired
    private RoomSessionRegistry sessionRegistry;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomBroadcastService broadcastService;

    @Scheduled(fixedRate = 1000)
    @Transactional
    public void checkExpiredRounds() {
        Set<String> activeCodes = sessionRegistry.getActiveRoomCodes();
        if (activeCodes.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (String code : activeCodes) {
            int updated = roomRepository.autoStopIfExpired(
                    code, now, Room.RoomStatus.playing, Room.RoomStatus.reviewing);
            if (updated > 0) {
                broadcastService.publishRoomUpdate(code);
            }
        }
    }
}
