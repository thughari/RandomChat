// package com.thughari.randomchat.config;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.scheduling.annotation.Async;
// import org.springframework.scheduling.annotation.EnableScheduling;
// import org.springframework.scheduling.annotation.Scheduled;
// import org.springframework.web.client.RestTemplate;

// @Configuration
// @EnableScheduling
// public class KeepAlive {

// 	private static final Logger logger = LoggerFactory.getLogger(KeepAlive.class);

// 	@Value("${app.keepalive.url}")
// 	private String pingUrl;

// 	private final RestTemplate restTemplate = new RestTemplate();

// 	@Scheduled(fixedRate = 300_000)
// 	@Async("virtualThreadTaskExecutor")
// 	public void keepAliveLog() {
// 		try {
// 			String response = restTemplate.getForObject(pingUrl, String.class);
// 			logger.info("KeepAlive self-ping successful: {}", response);
// 		} catch (Exception e) {
// 			logger.error("KeepAlive self-ping failed", e);
// 		}
// 	}
// }
