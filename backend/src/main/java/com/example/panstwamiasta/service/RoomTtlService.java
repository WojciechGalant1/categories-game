package com.example.panstwamiasta.service;

import com.example.panstwamiasta.room.Room;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomTtlService {

    static final String DELETION_PREFIX = "room:deletion:";

    private static final String DECREMENT_AND_SCHEDULE_LUA = """
            local count = redis.call('DECR', KEYS[1])
            if count < 0 then
              redis.call('SET', KEYS[1], '0')
              count = 0
            end
            if count <= 0 then
              redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])
              return 1
            end
            return 0
            """;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${app.room.ttl.lobby-seconds:180}")
    private int lobbySeconds;

    @Value("${app.room.ttl.in-game-seconds:600}")
    private int inGameSeconds;

    private final DefaultRedisScript<Long> decrementAndScheduleScript = new DefaultRedisScript<>();

    public RoomTtlService() {
        decrementAndScheduleScript.setScriptText(DECREMENT_AND_SCHEDULE_LUA);
        decrementAndScheduleScript.setResultType(Long.class);
    }

    public int ttlSecondsForStatus(Room.RoomStatus status) {
        if (status == Room.RoomStatus.playing || status == Room.RoomStatus.reviewing) {
            return inGameSeconds;
        }
        return lobbySeconds;
    }

    public void cancelDeletion(String roomCode) {
        redisTemplate.delete(DELETION_PREFIX + roomCode);
    }

    public void cleanup(String roomCode) {
        redisTemplate.delete(RoomConnectionCounterService.WS_COUNT_PREFIX + roomCode);
        redisTemplate.delete(DELETION_PREFIX + roomCode);
    }

    public void decrementAndMaybeSchedule(String roomCode, int ttlSeconds) {
        String countKey = RoomConnectionCounterService.WS_COUNT_PREFIX + roomCode;
        String deletionKey = DELETION_PREFIX + roomCode;
        redisTemplate.execute(
                decrementAndScheduleScript,
                List.of(countKey, deletionKey),
                String.valueOf(ttlSeconds));
    }

    public static String extractRoomCodeFromDeletionKey(String key) {
        if (key == null || !key.startsWith(DELETION_PREFIX)) {
            return null;
        }
        return key.substring(DELETION_PREFIX.length());
    }
}
