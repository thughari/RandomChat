package com.thughari.randomchat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class KeepAlive {

	private static final Logger logger = LoggerFactory.getLogger(KeepAlive.class);
	
	@Scheduled(fixedRate = 300_000)
	@Async("virtualThreadTaskExecutor")
	public void keepAliveLog() {
		logger.info("KeepAlive ping at {}", System.currentTimeMillis());
	}
}
