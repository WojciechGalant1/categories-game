package com.example.panstwamiasta.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ws")
public class WebSocketProperties {

    private int subscribeTimeoutSeconds = 10;

    public int getSubscribeTimeoutSeconds() {
        return subscribeTimeoutSeconds;
    }

    public void setSubscribeTimeoutSeconds(int subscribeTimeoutSeconds) {
        this.subscribeTimeoutSeconds = subscribeTimeoutSeconds;
    }
}
