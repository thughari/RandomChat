package com.thughari.randomchat.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TurnConfigController {

    @Value("${turn.server.username}")
    private String username;

    @Value("${turn.server.credential}")
    private String credential;

    @GetMapping("/api/turn-config")
    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<Map<String, String>> getTurnConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("username", username);
        config.put("credential", credential);
        return CompletableFuture.completedFuture(config);
    }
}