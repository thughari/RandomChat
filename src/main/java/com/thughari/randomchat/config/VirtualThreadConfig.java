package com.thughari.randomchat.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VirtualThreadConfig {

    @Bean("virtualThreadTaskExecutor")
    public ExecutorService virtualThreadTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}