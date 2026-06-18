package com.example.panstwamiasta.config;

import com.example.panstwamiasta.service.RoomBroadcastService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RoomBroadcastService broadcastService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> broadcastService.broadcastRoom(new String(message.getBody())),
                new ChannelTopic(RoomBroadcastService.REDIS_CHANNEL));
        return container;
    }
}
