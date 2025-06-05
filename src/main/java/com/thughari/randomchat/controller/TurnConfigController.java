package com.thughari.randomchat.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TurnConfigController {
    private static final Logger logger = LoggerFactory.getLogger(TurnConfigController.class);

    @Value("${turn.server.username}")
    private String username;

    @Value("${turn.server.credential}")
    private String credential;

    @GetMapping("/api/turn-config")
    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<Map<String, String>> getTurnConfig() {
        logger.info("getTurnConfig on thread: {}", Thread.currentThread());
        Map<String, String> config = new HashMap<>();
        config.put("username", username);
        config.put("credential", credential);
        return CompletableFuture.completedFuture(config);
    }
}