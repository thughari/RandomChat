package com.thughari.randomchat.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/ping")
public class Ping {
	
	@GetMapping
	public String ping() {
		return "pong";
	}
	
}
