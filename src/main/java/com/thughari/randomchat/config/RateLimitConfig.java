package com.thughari.randomchat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
public class RateLimitConfig {

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        
        // Set maximum message size to 64KB
        container.setMaxTextMessageBufferSize(64 * 1024);
        
        // Set maximum binary message size to 64KB
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        
        // Set maximum number of sessions per remote address
        container.setMaxSessionsPerRemote(2);
        
        return container;
    }
}
