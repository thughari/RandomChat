package com.thughari.randomchat.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/ping")
public class Ping {
	
	@GetMapping
	@Async("virtualThreadTaskExecutor")
	public CompletableFuture<String> ping() {
		return CompletableFuture.completedFuture("pong");
	}
	
}
