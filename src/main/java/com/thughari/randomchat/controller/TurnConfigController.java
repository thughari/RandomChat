package com.thughari.randomchat.controller;

import com.thughari.randomchat.service.TwilioTurnService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class TurnConfigController {

    private final TwilioTurnService twilioTurnService;

    // Constructor injection
    public TurnConfigController(TwilioTurnService twilioTurnService) {
        this.twilioTurnService = twilioTurnService;
    }

    @GetMapping("/api/turn-config")
    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<List<Map<String, String>>> getTurnConfig() {
        List<Map<String, String>> iceServers = twilioTurnService.getTwilioIceServers();
        return CompletableFuture.completedFuture(iceServers);
    }
}