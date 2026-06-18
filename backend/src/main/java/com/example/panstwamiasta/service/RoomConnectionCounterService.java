package com.example.panstwamiasta.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RoomConnectionCounterService {

    static final String WS_COUNT_PREFIX = "room:ws:count:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RoomTtlService roomTtlService;

    public void increment(String roomCode) {
        String key = WS_COUNT_PREFIX + roomCode;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count < 1) {
            redisTemplate.opsForValue().set(key, "1");
        }
    }

    public void decrementAndMaybeSchedule(String roomCode, int ttlSeconds) {
        roomTtlService.decrementAndMaybeSchedule(roomCode, ttlSeconds);
    }

    public long getCount(String roomCode) {
        String value = redisTemplate.opsForValue().get(WS_COUNT_PREFIX + roomCode);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
