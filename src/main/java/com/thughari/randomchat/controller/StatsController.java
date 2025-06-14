package com.thughari.randomchat.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.thughari.randomchat.handler.SignalingHandler;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class StatsController {

	@Autowired
	private SignalingHandler signalingHandler;

	@GetMapping("/api/active-users")
	@Async("virtualThreadTaskExecutor")
	public CompletableFuture<Map<String, Integer>> getActiveUsers() {
		int activeUsers = signalingHandler.getActiveConnections();
		return CompletableFuture.completedFuture(Map.of("count", activeUsers));
	}
}