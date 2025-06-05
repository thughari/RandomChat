package com.thughari.randomchat.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TurnConfigController {
    
    @Value("${turn.server.username}")
    private String username;
    
    @Value("${turn.server.credential}")
    private String credential;
    
    @GetMapping("/api/turn-config")
    public Map<String, String> getTurnConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("username", username);
        config.put("credential", credential);
        return config;
    }
}
