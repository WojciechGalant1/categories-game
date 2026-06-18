package com.example.panstwamiasta.config;

import com.example.panstwamiasta.service.RoomBroadcastService;
import com.example.panstwamiasta.service.RoomCleanupService;
import com.example.panstwamiasta.service.RoomTtlService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RoomBroadcastService broadcastService,
            RoomCleanupService roomCleanupService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        container.addMessageListener(
                (message, pattern) -> broadcastService.broadcastRoom(new String(message.getBody())),
                new ChannelTopic(RoomBroadcastService.REDIS_CHANNEL));

        container.addMessageListener(
                (message, pattern) -> {
                    String expiredKey = new String(message.getBody());
                    String code = RoomTtlService.extractRoomCodeFromDeletionKey(expiredKey);
                    if (code != null) {
                        roomCleanupService.deleteRoom(code);
                    }
                },
                new PatternTopic("__keyevent@0__:expired"));

        return container;
    }
}
