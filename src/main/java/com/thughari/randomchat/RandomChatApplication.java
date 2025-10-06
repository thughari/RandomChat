package com.thughari.randomchat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RandomChatApplication {

	public static void main(String[] args) {
		SpringApplication.run(RandomChatApplication.class, args);
	}

}