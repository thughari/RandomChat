package com.thughari.randomchat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RandomChatApplication {

	public static void main(String[] args) {
		SpringApplication.run(RandomChatApplication.class, args);
	}

	@Bean("virtualThreadTaskExecutor")
	public Executor virtualThreadTaskExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}
}